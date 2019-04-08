package org.projectforge.rest.calendar

import com.google.gson.annotations.SerializedName
import org.joda.time.DateMidnight
import org.joda.time.DateTimeFieldType.dayOfMonth
import org.projectforge.business.teamcal.filter.TeamCalCalendarFilter
import org.projectforge.business.teamcal.filter.ViewType
import org.projectforge.business.timesheet.TimesheetDao
import org.projectforge.business.timesheet.TimesheetFilter
import org.projectforge.business.user.service.UserPreferencesService
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.rest.core.RestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * For uploading address immages.
 */
@Component
@Path("calendar")
class CalendarServicesRest() {

    internal class CalendarData(val date: LocalDate, val viewType: CalendarViewType = CalendarViewType.MONTH, val events: List<BigCalendarEvent>)

    internal class BigCalendarEvent(val id: Int, val title: String, val start: Date, val end: Date, val allDay: Boolean = false, val desc: String? = null)

    enum class CalendarViewType {
        @SerializedName("month")
        MONTH,
        @SerializedName("week")
        WEEK,
        @SerializedName("day")
        DAY,
        @SerializedName("agenda")
        AGENDA
    }

    private val log = org.slf4j.LoggerFactory.getLogger(CalendarServicesRest::class.java)

    companion object {
        val OLD_USERPREF_KEY = "TeamCalendarPage.userPrefs";
        val USERPREF_KEY = "CalendarServices.userPrefs";
    }

    @Autowired
    private lateinit var timesheetDao: TimesheetDao

    @Autowired
    private lateinit var userPreferenceService: UserPreferencesService

    private val restHelper = RestHelper()

    @GET
    @Path("initial")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInitialCalendar(): Response {
        return buildEvents();
    }

    @GET
    @Path("events")
    @Produces(MediaType.APPLICATION_JSON)
    fun getEvents(@QueryParam("start") startParam: String?, @QueryParam("end") endParam: String?, @QueryParam("view") viewParam: String?): Response {
        val start = restHelper.parseDate(startParam)
        val end = restHelper.parseDate(endParam)
        val view = when (viewParam) {
            "month" -> CalendarViewType.MONTH
            "week" -> CalendarViewType.WEEK
            "day" -> CalendarViewType.DAY
            "agenda" -> CalendarViewType.AGENDA
            else -> null
        }
        return buildEvents(start, end, view)
    }

    private fun buildEvents(startParam: LocalDate? = null, endParam: LocalDate? = null, viewParam: CalendarViewType? = null): Response {
        var filter = userPreferenceService.getEntry(CalendarFilters::class.java, USERPREF_KEY)
        if (filter == null) {
            // No current user pref entry available. Try the old one (from release 6.* / Wicket Calendarpage):
            val oldFilter = userPreferenceService.getEntry(TeamCalCalendarFilter::class.java, OLD_USERPREF_KEY)
            oldFilter.viewType
            filter = createFrom(oldFilter)

            userPreferenceService.putEntry(USERPREF_KEY, filter, true)
        }
        val events = mutableListOf<BigCalendarEvent>()
        val initialCall = (startParam == null && viewParam == null) // endParam may-be null
        var view: CalendarViewType?
        var start: LocalDate?
        var end: LocalDate?
        var startDate = filter.startDate
        if (startDate == null)
            startDate = LocalDate.now()!!
        if (initialCall) {
            view = filter.viewType
            when (view) {
                CalendarViewType.WEEK -> {
                    start = CalDateUtils.getFirstDayOfWeek(startDate)
                    end = startDate.plusDays(7)
                }
                CalendarViewType.DAY -> {
                    start = startDate
                    end = startDate
                }
                else -> {
                    // Assuming month at default
                    start = startDate!!.withDayOfMonth(1)
                    end = startDate.withDayOfMonth(start.lengthOfMonth())
                }
            }
        } else {
            view = viewParam
            start = startParam
            if (start == null)
                start = LocalDate.now()!!
            end = endParam
            if (end == null) {
                end =
                        when (view) {
                            CalendarViewType.DAY -> start
                            CalendarViewType.WEEK -> start.plusDays(7)
                            else -> start.withDayOfMonth(start.lengthOfMonth())
                        }
            }
        }
        val startTimestamp = LocalDateTime.of(start, LocalTime.MIDNIGHT)
        val endTimestamp = LocalDateTime.of(end!!.plusDays(1), LocalTime.MIDNIGHT)
        //if (filter.isShowTimesheets) {
        val tsFilter = TimesheetFilter()
        tsFilter.userId = ThreadLocalUserContext.getUserId()
        tsFilter.startTime = CalDateUtils.getUtilDate(startTimestamp)
        tsFilter.stopTime = CalDateUtils.getUtilDate(endTimestamp)
        val timesheets = timesheetDao.getList(tsFilter)
        timesheets.forEach {
            events.add(BigCalendarEvent(it.id, it.shortDescription, it.startTime, it.stopTime))
        }
        // }
        val result = CalendarData(startDate, view!!, events)
        return restHelper.buildResponse(result)
    }

    private fun convert(oldViewType: ViewType?): CalendarServicesRest.CalendarViewType? {
        return when (oldViewType) {
            ViewType.BASIC_WEEK -> CalendarViewType.WEEK
            ViewType.BASIC_DAY -> CalendarViewType.DAY
            else -> CalendarViewType.MONTH
        }
    }

    /**
     * For re-using legacy filters (from ProjetForge version up to 6, Wicket-Calendar).
     */
    private fun createFrom(oldFilter: TeamCalCalendarFilter?): CalendarFilters {
        val calendarFilters = CalendarFilters()
        if (oldFilter != null) {
            calendarFilters.activeFilterIndex = oldFilter.activeTemplateEntryIndex
            calendarFilters.firstHour = oldFilter.firstHour
            calendarFilters.slot30 = oldFilter.isSlot30
            calendarFilters.startDate = CalDateUtils.convertToLocalDate(oldFilter.startDate)
            calendarFilters.viewType = convert(oldFilter.viewType)
            oldFilter.templateEntries?.forEach { templateEntry ->
                val displayFilter = CalendarsDisplayFilter()
                displayFilter.defaultCalendarId = templateEntry.defaultCalendarId
                displayFilter.name = templateEntry.name
                displayFilter.showBirthdays = templateEntry.isShowBirthdays
                displayFilter.showBreaks = templateEntry.isShowBreaks
                displayFilter.showPlanning = templateEntry.isShowPlanning
                displayFilter.showStatistics = templateEntry.isShowStatistics
                displayFilter.timesheetUserId = templateEntry.timesheetUserId
                displayFilter.showTimesheets = templateEntry.isShowTimesheets
                templateEntry.calendarProperties?.forEach {
                    val calendarProperties = DisplayedCalendarProperties()
                    calendarProperties.calId = it.calId
                    calendarProperties.colorCode = it.colorCode
                    calendarProperties.visible = it.isVisible
                    displayFilter.calendarDisplayProperties.add(calendarProperties)
                }
                calendarFilters.filters.add(displayFilter)
            }
        }
        return calendarFilters
    }
}