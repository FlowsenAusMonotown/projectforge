package org.projectforge.business.teamcal.event;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.projectforge.business.address.AddressDO;
import org.projectforge.business.address.AddressDao;
import org.projectforge.business.configuration.ConfigurationService;
import org.projectforge.business.teamcal.event.model.TeamEvent;
import org.projectforge.business.teamcal.event.model.TeamEventAttendeeDO;
import org.projectforge.business.teamcal.event.model.TeamEventAttendeeDao;
import org.projectforge.business.teamcal.event.model.TeamEventAttendeeStatus;
import org.projectforge.business.teamcal.event.model.TeamEventDO;
import org.projectforge.business.teamcal.service.CryptService;
import org.projectforge.business.teamcal.servlet.TeamCalResponseServlet;
import org.projectforge.business.user.I18nHelper;
import org.projectforge.business.user.service.UserService;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.mail.Mail;
import org.projectforge.mail.SendMail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TeamEventServiceImpl implements TeamEventService
{
  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(TeamEventServiceImpl.class);

  @Autowired
  private AddressDao addressDao;

  @Autowired
  private TeamEventAttendeeDao teamEventAttendeeDao;

  @Autowired
  private TeamEventDao teamEventDao;

  @Autowired
  private SendMail sendMail;

  @Autowired
  private TeamEventConverter teamEventConverter;

  @Autowired
  private UserService userService;

  @Autowired
  private CryptService cryptService;

  @Autowired
  private ConfigurationService configService;

  @Override
  public List<Integer> getAssignedAttendeeIds(TeamEventDO data)
  {
    List<Integer> assignedAttendees = new ArrayList<>();
    if (data != null && data.getAttendees() != null) {
      for (TeamEventAttendeeDO attendee : data.getAttendees()) {
        assignedAttendees.add(attendee.getId());
      }
    }
    return assignedAttendees;
  }

  @Override
  public List<TeamEventAttendeeDO> getAddressesAndUserAsAttendee()
  {
    List<TeamEventAttendeeDO> resultList = new ArrayList<>();
    Set<Integer> addedUserIds = new HashSet<>();
    List<AddressDO> allAddressList = addressDao.internalLoadAllNotDeleted().stream()
        .sorted((address1, address2) -> address2.getFullName().compareTo(address1.getFullName()))
        .collect(Collectors.toList());
    for (AddressDO singleAddress : allAddressList) {
      if (StringUtils.isBlank(singleAddress.getEmail()) == false) {
        TeamEventAttendeeDO attendee = new TeamEventAttendeeDO();
        attendee.setStatus(TeamEventAttendeeStatus.NEW);
        attendee.setAddress(singleAddress);
        List<PFUserDO> userWithSameMail = userService.findUserByMail(singleAddress.getEmail());
        if (userWithSameMail.size() > 0 && addedUserIds.contains(userWithSameMail.get(0).getId()) == false) {
          PFUserDO user = userWithSameMail.get(0);
          attendee.setUser(user);
          addedUserIds.add(user.getId());
        }
        resultList.add(attendee);
      }
    }
    return resultList;
  }

  @Override
  public TeamEventAttendeeDO getAttendee(Integer attendeeId)
  {
    return teamEventAttendeeDao.internalGetById(attendeeId);
  }

  @Override
  public void assignAttendees(TeamEventDO data, Set<TeamEventAttendeeDO> itemsToAssign,
      Set<TeamEventAttendeeDO> itemsToUnassign)
  {
    for (TeamEventAttendeeDO assignAttendee : itemsToAssign) {
      if (assignAttendee.getId() == null || assignAttendee.getId() < 0) {
        assignAttendee.setId(null);
        assignAttendee.setStatus(TeamEventAttendeeStatus.IN_PROCESS);
        data.addAttendee(assignAttendee);
        teamEventAttendeeDao.internalSave(assignAttendee);
      }
    }

    if (data.getAttendees() != null && itemsToUnassign.size() > 0) {
      data.getAttendees().removeAll(itemsToUnassign);
      for (TeamEventAttendeeDO deleteAttendee : itemsToUnassign) {
        teamEventAttendeeDao.internalMarkAsDeleted(deleteAttendee);
      }
    }
    teamEventDao.update(data);
  }

  @Override
  public boolean sendTeamEventToAttendees(TeamEventDO data, boolean isNew, boolean hasChanges, boolean isDeleted,
      Set<TeamEventAttendeeDO> addedAttendees)
  {
    boolean result = false;
    String mode = "";
    if (isDeleted) {
      mode = "deleted";
      for (TeamEventAttendeeDO attendee : data.getAttendees()) {
        result = sendMail(data, attendee, mode);
      }
      return result;
    }
    if (isNew) {
      mode = "new";
      for (TeamEventAttendeeDO attendee : data.getAttendees()) {
        result = sendMail(data, attendee, mode);
      }
    } else {
      Set<TeamEventAttendeeDO> sendToList = new HashSet<>();
      if (hasChanges == false && addedAttendees.size() > 0) {
        mode = "new";
        sendToList = addedAttendees;
      } else {
        mode = "update";
        sendToList = data.getAttendees();
      }
      for (TeamEventAttendeeDO attendee : sendToList) {
        result = sendMail(data, attendee, mode);
      }
    }
    return result;
  }

  private Mail createMail(String mode)
  {
    final Mail msg = new Mail();
    PFUserDO user = ThreadLocalUserContext.getUser();
    if (user != null) {
      msg.setFrom(user.getEmail());
      msg.setFromRealname(user.getFullname());
    }
    msg.setContentType(Mail.CONTENTTYPE_HTML);
    msg.setProjectForgeSubject(I18nHelper.getLocalizedMessage("plugins.teamcal.attendee.email.subject." + mode));
    return msg;
  }

  private boolean sendMail(TeamEventDO data, TeamEventAttendeeDO attendee, String mode)
  {
    boolean deleted = "deleted".equals(mode);
    final Mail msg = createMail(mode);
    addAttendeeToMail(attendee, msg);
    DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    formatter.setTimeZone(ThreadLocalUserContext.getUser().getTimeZoneObject());
    String attendeesString = "";
    for (TeamEventAttendeeDO attendeeForString : data.getAttendees()) {
      attendeesString = attendeesString + attendeeForString.toString() + " <br>";
    }

    final Map<String, Object> emailDataMap = new HashMap<>();
    Calendar startDate = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    startDate.setTime(data.getStartDate());
    Calendar endDate = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    endDate.setTime(data.getEndDate());

    String location = data.getLocation() != null ? data.getLocation() : "";
    String note = data.getNote() != null ? data.getNote() : "";
    formatter = new SimpleDateFormat("EEEE", ThreadLocalUserContext.getLocale());
    formatter.setTimeZone(ThreadLocalUserContext.getUser().getTimeZoneObject());
    String startDay = formatter.format(startDate.getTime());
    String endDay = formatter.format(endDate.getTime());

    formatter = new SimpleDateFormat("dd. MMMMM HH:mm", ThreadLocalUserContext.getLocale());
    formatter.setTimeZone(ThreadLocalUserContext.getUser().getTimeZoneObject());
    String beginDateTime = formatter.format(startDate.getTime());
    String endDateTime = formatter.format(endDate.getTime());
    String invitationText;
    if (deleted) {
      invitationText = I18nHelper
          .getLocalizedMessage("plugins.teamcal.attendee.email.content.deleted", data.getCreator().getFullname());
    } else {
      invitationText = I18nHelper
          .getLocalizedMessage("plugins.teamcal.attendee.email.content.new", data.getCreator().getFullname(), data.getSubject());
    }
    String beginText = startDay + ", " + beginDateTime;
    String endText = endDay + ", " + endDateTime;
    String dayOfWeek = startDay;

    String fromToHeader;
    if (startDate.get(Calendar.DATE) == endDate.get(Calendar.DATE)) //Einen Tag
    {
      formatter = new SimpleDateFormat("HH:mm", ThreadLocalUserContext.getLocale());
      formatter.setTimeZone(ThreadLocalUserContext.getUser().getTimeZoneObject());
      String endTime = formatter.format(endDate.getTime());
      fromToHeader =
          beginDateTime + " - " + endTime + " " + I18nHelper.getLocalizedMessage("oclock") + ".";
    } else    //Mehrere Tage
    {
      fromToHeader = beginDateTime;
    }
    if (data.isAllDay()) {
      formatter = new SimpleDateFormat("dd. MMMMM", ThreadLocalUserContext.getLocale());
      formatter.setTimeZone(ThreadLocalUserContext.getUser().getTimeZoneObject());
      fromToHeader = formatter.format(startDate.getTime());
      formatter = new SimpleDateFormat("EEEE, dd. MMMMM", ThreadLocalUserContext.getLocale());
      formatter.setTimeZone(ThreadLocalUserContext.getUser().getTimeZoneObject());
      beginText =
          I18nHelper.getLocalizedMessage("plugins.teamcal.event.allDay") + ", " + formatter.format(startDate.getTime());
      endText = I18nHelper.getLocalizedMessage("plugins.teamcal.event.allDay") + ", " + formatter.format(endDate.getTime());
    }

    emailDataMap.put("dayOfWeek", dayOfWeek);
    emailDataMap.put("fromToHeader", fromToHeader);
    emailDataMap.put("invitationText", invitationText);
    emailDataMap.put("beginText", beginText);
    emailDataMap.put("endText", endText);

    List<String> attendeeList = new ArrayList<>();
    for (TeamEventAttendeeDO attendees : data.getAttendees()) {
      if (attendees.getAddress() != null) {
        attendeeList.add(attendees.getAddress().getEmail());
      } else {
        attendeeList.add(attendees.getUrl());
      }
    }
    emailDataMap.put("attendeeList", attendeeList);
    emailDataMap.put("location", location);
    emailDataMap.put("note", note);
    emailDataMap.put("acceptLink", getResponseLink(data, attendee, TeamEventAttendeeStatus.ACCEPTED));
    emailDataMap.put("declineLink", getResponseLink(data, attendee, TeamEventAttendeeStatus.DECLINED));
    emailDataMap.put("deleted", deleted ? "true" : "false");
    final String content = sendMail.renderGroovyTemplate(msg, "mail/teamEventEmail.html", emailDataMap, ThreadLocalUserContext.getUser());
    msg.setContent(content);
    boolean result = false;
    try {
      if (deleted) {
        result = sendMail.send(msg, null, null);
      } else {
        ByteArrayOutputStream icsFile = teamEventConverter.getIcsFile(data);
        result = sendMail.send(msg, icsFile.toString(StandardCharsets.UTF_8.name()), null);
      }
    } catch (UnsupportedEncodingException e) {
      log.error("Something went wrong sending team event to attendee", e);
    }
    return result;
  }

  private String getResponseLink(TeamEventDO event, TeamEventAttendeeDO attendee, TeamEventAttendeeStatus status)
  {
    final String messageParamBegin = "uid=" + event.getUid() + "&attendee=" + attendee.getId();
    final String acceptParams = cryptService.encryptParameterMessage(messageParamBegin + "&status=" + status.name());
    return configService.getDomain() + TeamCalResponseServlet.PFCALENDAR + "?" + acceptParams;
  }

  private void addAttendeeToMail(TeamEventAttendeeDO attendee, Mail msg)
  {
    if (attendee.getAddress() != null) {
      msg.addTo(attendee.getAddress().getEmail());
    }
    if (StringUtils.isNotBlank(attendee.getUrl())) {
      msg.addTo(attendee.getUrl());
    }
  }

  @Override
  public TeamEventDO findByUid(String reqEventUid)
  {
    return teamEventDao.getByUid(reqEventUid);
  }

  @Override
  public TeamEventAttendeeDO findByAttendeeId(Integer attendeeId, boolean checkAccess)
  {
    TeamEventAttendeeDO result = null;
    if (checkAccess) {
      result = teamEventAttendeeDao.getById(attendeeId);
    } else {
      result = teamEventAttendeeDao.internalGetById(attendeeId);
    }
    return result;
  }

  @Override
  public TeamEventAttendeeDO findByAttendeeId(Integer attendeeId)
  {
    return findByAttendeeId(attendeeId, true);
  }

  @Override
  public void update(TeamEventDO event)
  {
    update(event, true);
  }

  @Override
  public void update(TeamEventDO event, boolean checkAccess)
  {
    teamEventDao.internalUpdate(event, checkAccess);
  }

  @Override
  public List<TeamEvent> getEventList(TeamEventFilter filter, boolean calculateRecurrenceEvents)
  {
    return teamEventDao.getEventList(filter, calculateRecurrenceEvents);
  }

  @Override
  public List<TeamEventDO> getTeamEventDOList(TeamEventFilter filter)
  {
    return teamEventDao.getList(filter);
  }

  @Override
  public TeamEventDO getById(Integer teamEventId)
  {
    return teamEventDao.getById(teamEventId);
  }

  @Override
  public void saveOrUpdate(TeamEventDO teamEvent)
  {
    teamEventDao.saveOrUpdate(teamEvent);
  }

  @Override
  public void markAsDeleted(TeamEventDO teamEvent)
  {
    teamEventDao.markAsDeleted(teamEvent);
  }

  @Override
  public void save(TeamEventDO newEvent)
  {
    teamEventDao.save(newEvent);
  }

  @Override
  public TeamEventDao getTeamEventDao()
  {
    return teamEventDao;
  }

  @Override
  public void updateAttendee(TeamEventAttendeeDO attendee, boolean accesscheck)
  {
    if (accesscheck) {
      teamEventAttendeeDao.update(attendee);
    } else {
      teamEventAttendeeDao.internalUpdate(attendee);
    }
  }

}
