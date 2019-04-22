package org.projectforge.rest

import org.projectforge.business.user.filter.UserFilter
import org.projectforge.common.DateFormatType
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.time.DateFormats
import org.projectforge.framework.time.TimeNotation
import org.projectforge.rest.core.RestHelper
import org.projectforge.rest.pub.SystemStatusRest
import org.springframework.stereotype.Component
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * This rest service should be available without login (public).
 */
@Component
@Path("userStatus")
open class UserStatusRest {
    companion object {
        internal val WEEKDAYS = arrayOf("-", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    }

    data class UserData(var username: String? = null,
                        var organization: String? = null,
                        var fullname: String? = null,
                        var lastName: String? = null,
                        var firstName: String? = null,
                        var userId: Int? = null,
                        var locale: Locale? = null,
                        var timeZone: String? = null,
                        var dateFormat: String? = null,
                        var dateFormatShort: String? = null,
                        var timestampFormatMinutes: String? = null,
                        var timestampFormatSeconds: String? = null,
                        var timestampFormatMillis: String? = null,
                        var jsDateFormat: String? = null,
                        var jsDateFormatShort: String? = null,
                        var jsTimestampFormatMinutes: String? = null,
                        var jsTimestampFormatSeconds: String? = null,
                        var firstDayOfWeekNo: Int? = null,
                        var firstDayOfWeek: String? = null,
                        var timeNotation: TimeNotation? = null)

    data class Result(val userData: UserData,
                      val systemData: SystemStatusRest.SystemData)

    private val log = org.slf4j.LoggerFactory.getLogger(UserStatusRest::class.java)

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun loginTest(@Context request: HttpServletRequest): Response {
        val user = UserFilter.getUser(request)
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build()
        }
        val firstDayOfWeekNo = ThreadLocalUserContext.getJodaFirstDayOfWeek() // Mon - 1, Tue - 2, ..., Sun - 7
        val userData = UserData(username = user.username,
                organization = user.organization,
                fullname = user.fullname,
                firstName = user.firstname,
                lastName = user.lastname,
                userId = user.id,
                locale = ThreadLocalUserContext.getLocale(),
                timeZone = ThreadLocalUserContext.getTimeZone().id,
                timeNotation = DateFormats.ensureAndGetDefaultTimeNotation(),
                dateFormat = DateFormats.getFormatString(DateFormatType.DATE),
                dateFormatShort = DateFormats.getFormatString(DateFormatType.DATE_SHORT),
                timestampFormatMinutes = DateFormats.getFormatString(DateFormatType.DATE_TIME_MINUTES),
                timestampFormatSeconds = DateFormats.getFormatString(DateFormatType.DATE_TIME_SECONDS),
                timestampFormatMillis = DateFormats.getFormatString(DateFormatType.DATE_TIME_MILLIS),
                firstDayOfWeekNo = firstDayOfWeekNo,
                firstDayOfWeek = WEEKDAYS[firstDayOfWeekNo])
        userData.jsDateFormat = convertToJavascriptFormat(userData.dateFormat)
        userData.jsDateFormatShort = convertToJavascriptFormat(userData.dateFormatShort)
        userData.jsTimestampFormatMinutes = convertToJavascriptFormat(userData.timestampFormatMinutes)
        userData.jsTimestampFormatSeconds = convertToJavascriptFormat(userData.timestampFormatSeconds)

        val systemData = SystemStatusRest.getSystemData()
        return RestHelper().buildResponse(Result(userData, systemData))
    }

    /**
     * 'dd.MM.yyyy HH:mm:ss' -> 'DD.MM.YYYY HH:mm:ss'.
     */
    private fun convertToJavascriptFormat(dateFormat: String?): String? {
        if (dateFormat == null) return null
        return dateFormat.replace('d', 'D', false)
                .replace('y', 'Y', false);
    }
}
