package org.projectforge.business.vacation.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.projectforge.business.configuration.ConfigurationService;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.fibu.EmployeeDao;
import org.projectforge.business.fibu.api.EmployeeService;
import org.projectforge.business.user.I18nHelper;
import org.projectforge.business.vacation.model.VacationAttrProperty;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.model.VacationStatus;
import org.projectforge.business.vacation.repository.VacationDao;
import org.projectforge.framework.access.AccessException;
import org.projectforge.framework.i18n.UserException;
import org.projectforge.framework.persistence.api.BaseSearchFilter;
import org.projectforge.framework.persistence.history.DisplayHistoryEntry;
import org.projectforge.framework.persistence.jpa.impl.CorePersistenceServiceImpl;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.time.DayHolder;
import org.projectforge.mail.Mail;
import org.projectforge.mail.SendMail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Standard implementation of the vacation service interface.
 *
 * @author Florian Blumenstein
 */
@Service
public class VacationServiceImpl extends CorePersistenceServiceImpl<Integer, VacationDO>
    implements VacationService
{
  @Autowired
  private VacationDao vacationDao;

  @Autowired
  private SendMail sendMailService;

  @Autowired
  private ConfigurationService configService;

  @Autowired
  private EmployeeDao employeeDao;

  @Autowired
  private EmployeeService employeeService;

  @Override
  public BigDecimal getApprovedAndPlanedVacationdaysForYear(EmployeeDO employee, int year)
  {
    final BigDecimal approved = getApprovedVacationdaysForYear(employee, year);
    final BigDecimal planned = getPlannedVacationdaysForYear(employee, year);
    return approved.add(planned);
  }

  @Override
  public void sendMailToVacationInvolved(VacationDO vacationData, boolean isNew, boolean isDeleted)
  {
    //Send mail to manager (employee in copy)
    Mail mail = new Mail();
    String i18nPMContent = "";
    String i18nPMSubject = "";
    String i18nSubContent = "";
    String i18nSubSubject = "";
    if (isNew == true && isDeleted == false) {
      i18nPMContent = I18nHelper.getLocalizedMessage("vacation.mail.pm.application", vacationData.getManager().getUser().getFirstname(),
          vacationData.getEmployee().getUser().getFullname(), vacationData.getStartDate().toString(), vacationData.getEndDate().toString());
      i18nPMSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject", vacationData.getEmployee().getUser().getFullname());
      i18nSubContent = I18nHelper.getLocalizedMessage("vacation.mail.sub.application", vacationData.getSubstitution().getUser().getFirstname(),
          vacationData.getEmployee().getUser().getFullname(), vacationData.getStartDate().toString(), vacationData.getEndDate().toString());
      i18nSubSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject", vacationData.getEmployee().getUser().getFullname());
    }
    if (isNew == false && isDeleted == false) {
      i18nPMContent = I18nHelper.getLocalizedMessage("vacation.mail.pm.application.edit", vacationData.getManager().getUser().getFirstname(),
          vacationData.getEmployee().getUser().getFullname(), vacationData.getStartDate().toString(), vacationData.getEndDate().toString());
      i18nPMSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject.edit", vacationData.getEmployee().getUser().getFullname());
      i18nSubContent = I18nHelper.getLocalizedMessage("vacation.mail.sub.application.edit", vacationData.getSubstitution().getUser().getFirstname(),
          vacationData.getEmployee().getUser().getFullname(), vacationData.getStartDate().toString(), vacationData.getEndDate().toString());
      i18nSubSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject.edit", vacationData.getEmployee().getUser().getFullname());
    }
    if (isDeleted) {
      i18nPMContent = I18nHelper.getLocalizedMessage("vacation.mail.application.deleted", vacationData.getManager().getUser().getFirstname(),
          vacationData.getEmployee().getUser().getFullname(), vacationData.getStartDate().toString(), vacationData.getEndDate().toString());
      i18nPMSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject.deleted", vacationData.getEmployee().getUser().getFullname());
      i18nSubContent = I18nHelper.getLocalizedMessage("vacation.mail.application.deleted", vacationData.getSubstitution().getUser().getFirstname(),
          vacationData.getEmployee().getUser().getFullname(), vacationData.getStartDate().toString(), vacationData.getEndDate().toString());
      i18nSubSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject.deleted", vacationData.getEmployee().getUser().getFullname());
    }
    mail.setContent(i18nPMContent);
    mail.setSubject(i18nPMSubject);
    mail.setContentType(Mail.CONTENTTYPE_HTML);
    mail.setTo(vacationData.getManager().getUser());
    mail.setTo(vacationData.getEmployee().getUser());
    sendMailService.send(mail, null, null);

    //Send mail to substitution (employee in copy)
    mail = new Mail();
    mail.setContent(i18nSubContent);
    mail.setSubject(i18nSubSubject);
    mail.setContentType(Mail.CONTENTTYPE_HTML);
    mail.setTo(vacationData.getSubstitution().getUser());
    mail.setTo(vacationData.getEmployee().getUser());
    sendMailService.send(mail, null, null);
  }

  @Override
  public void sendMailToEmployeeAndHR(VacationDO vacationData, boolean approved)
  {
    Mail mail = new Mail();
    if (approved) {
      //Send mail to HR (employee in copy)
      mail.setContent(I18nHelper.getLocalizedMessage("vacation.mail.hr.approved", vacationData.getEmployee().getUser().getFullname(),
          vacationData.getStartDate().toString(), vacationData.getEndDate().toString(), vacationData.getSubstitution().getUser().getFullname(),
          vacationData.getManager().getUser().getFullname()));
      mail.setSubject(I18nHelper.getLocalizedMessage("vacation.mail.subject", vacationData.getEmployee().getUser().getFullname()));
      mail.setContentType(Mail.CONTENTTYPE_HTML);
      if (configService.getHREmailadress() == null) {
        throw new UserException("HR email address not configured!");
      }
      mail.setTo(configService.getHREmailadress(), "HR-MANAGEMENT");
      mail.setTo(vacationData.getManager().getUser());
      mail.setTo(vacationData.getEmployee().getUser());
      sendMailService.send(mail, null, null);
    }

    //Send mail to substitution (employee in copy)
    String decision = approved ? "approved" : "declined";
    mail = new Mail();
    mail.setContent(I18nHelper.getLocalizedMessage("vacation.mail.employee." + decision, vacationData.getEmployee().getUser().getFirstname(),
        vacationData.getSubstitution().getUser().getFirstname(), vacationData.getEmployee().getUser().getFullname(),
        vacationData.getStartDate().toString(), vacationData.getEndDate().toString(), vacationData.getSubstitution().getUser().getFullname()));
    mail.setSubject(I18nHelper.getLocalizedMessage("vacation.mail.subject.edit", vacationData.getEmployee().getUser().getFullname()));
    mail.setContentType(Mail.CONTENTTYPE_HTML);
    mail.setTo(vacationData.getSubstitution().getUser());
    mail.setTo(vacationData.getEmployee().getUser());
    sendMailService.send(mail, null, null);
  }

  @Override
  public Calendar getEndDateVacationFromLastYear()
  {
    return configService.getEndDateVacationFromLastYear();
  }

  @Override
  public BigDecimal updateUsedVacationDaysFromLastYear(VacationDO vacationData)
  {
    if (vacationData == null || vacationData.getEmployee() == null || vacationData.getStartDate() == null || vacationData.getEndDate() == null) {
      return BigDecimal.ZERO;
    }
    Calendar now = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    Calendar startDate = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    Calendar endDateVacationFromLastYear = getEndDateVacationFromLastYear();
    startDate.setTime(vacationData.getStartDate());
    if (startDate.get(Calendar.YEAR) > now.get(Calendar.YEAR) && vacationData.getStartDate().before(endDateVacationFromLastYear.getTime()) == false) {
      return BigDecimal.ZERO;
    }
    BigDecimal neededDaysForVacationFromLastYear = null;
    if (vacationData.getEndDate().before(endDateVacationFromLastYear.getTime()) == false) {
      neededDaysForVacationFromLastYear = DayHolder.getNumberOfWorkingDays(vacationData.getStartDate(), endDateVacationFromLastYear.getTime());
    } else {
      neededDaysForVacationFromLastYear = DayHolder.getNumberOfWorkingDays(vacationData.getStartDate(), vacationData.getEndDate());
    }

    EmployeeDO employee = vacationData.getEmployee();
    BigDecimal actualUsedDaysOfLastYear = getVacationFromPreviousYearUsed(employee);
    BigDecimal vacationFromPreviousYear = getVacationFromPreviousYear(employee);

    BigDecimal freeDaysFromLastYear = vacationFromPreviousYear.subtract(actualUsedDaysOfLastYear);
    BigDecimal remainValue = freeDaysFromLastYear.subtract(neededDaysForVacationFromLastYear).compareTo(BigDecimal.ZERO) < 0 ?
        BigDecimal.ZERO :
        freeDaysFromLastYear.subtract(neededDaysForVacationFromLastYear);
    BigDecimal newValue = vacationFromPreviousYear.subtract(remainValue);
    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), newValue);
    employeeDao.internalUpdate(employee);
    return newValue;
  }

  @Override
  public void updateUsedNewVacationDaysFromLastYear(EmployeeDO employee, int year)
  {
    BigDecimal availableVacationdays = getAvailableVacationdaysForYear(employee, year, false);
    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(), availableVacationdays);
    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), BigDecimal.ZERO);
    employeeDao.internalSave(employee);
  }

  @Override
  public BigDecimal deleteUsedVacationDaysFromLastYear(VacationDO vacationData)
  {
    if (vacationData == null || vacationData.getEmployee() == null || vacationData.getStartDate() == null || vacationData.getEndDate() == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal vacationDays = DayHolder.getNumberOfWorkingDays(vacationData.getStartDate(), vacationData.getEndDate());

    EmployeeDO employee = vacationData.getEmployee();
    BigDecimal actualUsedDaysOfLastYear = getVacationFromPreviousYearUsed(employee);
    BigDecimal vacationFromPreviousYear = getVacationFromPreviousYear(employee);

    Calendar startDateCalender = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    startDateCalender.setTime(vacationData.getStartDate());
    Calendar firstOfJanOfStartYearCalender = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    firstOfJanOfStartYearCalender.set(startDateCalender.get(Calendar.YEAR), Calendar.JANUARY, 1);
    Calendar endDateCalender = configService.getEndDateVacationFromLastYear();
    List<VacationDO> vacationList = getVacationForDate(vacationData.getEmployee(), startDateCalender.getTime(), endDateCalender.getTime());

    BigDecimal dayCount = BigDecimal.ZERO;
    for (VacationDO v : vacationList) {
      if (v.getEndDate().before(endDateCalender.getTime()))
        dayCount = dayCount.add(v.getWorkingdays());
      else
        dayCount = dayCount.add(DayHolder.getNumberOfWorkingDays(v.getStartDate(), endDateCalender.getTime()));
    }
    BigDecimal newDays = BigDecimal.ZERO;

    if (dayCount.compareTo(vacationFromPreviousYear) < 0) // dayCount < vacationFromPreviousYear
    {
      if (vacationData.getEndDate().compareTo(endDateCalender.getTime()) < 0) {
        newDays = actualUsedDaysOfLastYear.subtract(vacationData.getWorkingdays());
      } else {
        newDays = actualUsedDaysOfLastYear.subtract(DayHolder.getNumberOfWorkingDays(vacationData.getStartDate(), endDateCalender.getTime()));
      }
    }

    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), newDays);
    employeeDao.internalUpdate(employee);
    return newDays;
  }

  @Override
  public boolean couldUserUseVacationService(PFUserDO user, boolean throwException)
  {
    boolean result = true;
    if (user == null || user.getId() == null) {
      return false;
    }
    EmployeeDO employee = employeeService.getEmployeeByUserId(user.getId());
    if (employee == null) {
      if (throwException) {
        throw new AccessException("access.exception.noEmployeeToUser");
      }
      result = false;
    } else if (employee.getUrlaubstage() == null) {
      if (throwException) {
        throw new AccessException("access.exception.employeeHasNoVacationDays");
      }
      result = false;
    }
    return result;
  }

  @Override
  public BigDecimal getApprovedVacationdaysForYear(final EmployeeDO employee, final int year)
  {
    return getVacationDaysForYearByStatus(employee, year, VacationStatus.APPROVED);
  }

  @Override
  public BigDecimal getPlannedVacationdaysForYear(final EmployeeDO employee, final int year)
  {
    return getVacationDaysForYearByStatus(employee, year, VacationStatus.IN_PROGRESS);
  }

  private BigDecimal getVacationDaysForYearByStatus(final EmployeeDO employee, int year, final VacationStatus status)
  {
    return getActiveVacationForYear(employee, year, false)
        .stream()
        .filter(vac -> vac.getStatus().equals(status))
        .map(VacationDO::getWorkingdays)
        .reduce(BigDecimal.ZERO, BigDecimal::add); // sum
  }

  @Override
  public BigDecimal getAvailableVacationdaysForYear(PFUserDO user, int year, boolean checkLastYear)
  {
    if (user == null) {
      return BigDecimal.ZERO;
    }
    EmployeeDO employee = employeeService.getEmployeeByUserId(user.getPk());
    if (employee == null) {
      return BigDecimal.ZERO;
    }
    return getAvailableVacationdaysForYear(employee, year, checkLastYear);
  }

  @Override
  public BigDecimal getAvailableVacationdaysForYear(EmployeeDO employee, int year, boolean checkLastYear)
  {
    if (employee == null) {
      return BigDecimal.ZERO;
    }
    final BigDecimal vacationDays = new BigDecimal(employee.getUrlaubstage());

    final BigDecimal vacationFromPreviousYear;
    final BigDecimal vacationFromPreviousYearUsed;
    final Calendar now = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    if (year > now.get(Calendar.YEAR)) {
      vacationFromPreviousYear = BigDecimal.ZERO;
      vacationFromPreviousYearUsed = BigDecimal.ZERO;
    } else {
      vacationFromPreviousYear = getVacationFromPreviousYear(employee);
      vacationFromPreviousYearUsed = getVacationFromPreviousYearUsed(employee);
    }

    final BigDecimal approvedVacation = getApprovedVacationdaysForYear(employee, year);
    final BigDecimal planedVacation = getPlannedVacationdaysForYear(employee, year);
    final Calendar endDateVacationFromLastYear = configService.getEndDateVacationFromLastYear();
    if (checkLastYear == false || now.after(endDateVacationFromLastYear)) {
      return vacationDays
          .add(vacationFromPreviousYearUsed)
          .subtract(approvedVacation)
          .subtract(planedVacation);
    }
    return vacationDays
        .add(vacationFromPreviousYear)
        .subtract(approvedVacation)
        .subtract(planedVacation);
  }

  private BigDecimal getVacationFromPreviousYearUsed(EmployeeDO employee)
  {
    final BigDecimal prevYearLeaveUsed = employee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), BigDecimal.class);
    return prevYearLeaveUsed != null ? prevYearLeaveUsed : BigDecimal.ZERO;
  }

  private BigDecimal getVacationFromPreviousYear(EmployeeDO employee)
  {
    final BigDecimal prevYearLeave = employee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(), BigDecimal.class);
    return prevYearLeave != null ? prevYearLeave : BigDecimal.ZERO;
  }

  @Override
  public List<VacationDO> getActiveVacationForYear(EmployeeDO employee, int year, boolean withSpecial)
  {
    return vacationDao.getActiveVacationForYear(employee, year, withSpecial);
  }

  @Override
  public List<VacationDO> getAllActiveVacation(EmployeeDO employee, boolean withSpecial)
  {
    return vacationDao.getAllActiveVacation(employee, withSpecial);
  }

  @Override
  public List<VacationDO> getList(BaseSearchFilter filter)
  {
    return vacationDao.getList(filter);
  }

  @Override
  public List<VacationDO> getVacation(List<Serializable> idList)
  {
    return vacationDao.internalLoad(idList);
  }

  @Override
  public List<VacationDO> getVacationForDate(EmployeeDO employee, Date startDate, Date endDate)
  {
    return vacationDao.getVacationForPeriod(employee, startDate, endDate);
  }

  @Override
  public BigDecimal getOpenLeaveApplicationsForUser(PFUserDO user)
  {
    EmployeeDO employee = employeeService.getEmployeeByUserId(user.getId());
    if (employee == null) {
      return BigDecimal.ZERO;
    }
    return vacationDao.getOpenLeaveApplicationsForEmployee(employee);
  }

  @Override
  public BigDecimal getSpecialVacationCount(EmployeeDO employee, int year, VacationStatus status)
  {
    return vacationDao
        .getSpecialVacation(employee, year, status)
        .stream()
        .map(VacationDO::getWorkingdays)
        .reduce(BigDecimal.ZERO, BigDecimal::add); // sum
  }

  @Override
  public boolean hasInsertAccess(PFUserDO user)
  {
    return true;
  }

  @Override
  public boolean hasLoggedInUserInsertAccess()
  {
    return vacationDao.hasLoggedInUserInsertAccess();
  }

  @Override
  public boolean hasLoggedInUserInsertAccess(VacationDO obj, boolean throwException)
  {
    return vacationDao.hasLoggedInUserInsertAccess(obj, throwException);
  }

  @Override
  public boolean hasLoggedInUserUpdateAccess(VacationDO obj, VacationDO dbObj, boolean throwException)
  {
    return vacationDao.hasLoggedInUserUpdateAccess(obj, dbObj, throwException);
  }

  @Override
  public boolean hasLoggedInUserDeleteAccess(VacationDO obj, VacationDO dbObj, boolean throwException)
  {
    return vacationDao.hasLoggedInUserDeleteAccess(obj, dbObj, throwException);
  }

  @Override
  public boolean hasDeleteAccess(PFUserDO user, VacationDO obj, VacationDO dbObj, boolean throwException)
  {
    return vacationDao.hasDeleteAccess(user, obj, dbObj, throwException);
  }

  @Override
  public List<String> getAutocompletion(String property, String searchString)
  {
    return vacationDao.getAutocompletion(property, searchString);
  }

  @Override
  public List<DisplayHistoryEntry> getDisplayHistoryEntries(VacationDO obj)
  {
    return vacationDao.getDisplayHistoryEntries(obj);
  }

  @Override
  public void rebuildDatabaseIndex4NewestEntries()
  {
    vacationDao.rebuildDatabaseIndex4NewestEntries();
  }

  @Override
  public void rebuildDatabaseIndex()
  {
    vacationDao.rebuildDatabaseIndex();
  }

}