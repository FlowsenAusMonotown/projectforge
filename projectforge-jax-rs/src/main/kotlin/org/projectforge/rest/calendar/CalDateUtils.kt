package org.projectforge.rest.calendar

import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields


class CalDateUtils {
    companion object {
        fun convertToLocalDate(dateMidnight: org.joda.time.DateMidnight): java.time.LocalDate? {
            if (dateMidnight == null)
                return null
            return java.time.LocalDate.of(dateMidnight.year, dateMidnight.monthOfYear, dateMidnight.dayOfMonth)
        }

        fun getUtilDate(dateTime: LocalDateTime): java.util.Date {
            if (dateTime == null)
                return java.util.Date()
            val zoneId = ThreadLocalUserContext.getTimeZone().toZoneId()
            return java.util.Date.from(dateTime.atZone(zoneId).toInstant());
        }

        fun getFirstDayOfWeek(date: LocalDate): LocalDate {
            val field = WeekFields.of(CalDateUtils.getFirstDayOfWeek(), 1).dayOfWeek()
            return date.with(field, 1)
        }

        fun getFirstDayOfWeek(): DayOfWeek {
            val firstDayOfWeek = ThreadLocalUserContext.getJodaFirstDayOfWeek()
            return getDayOfWeek(firstDayOfWeek)!!
        }

        /**
         * dayNumber 1 - Monday, 2 - Tuesday, ..., 7 - Sunday
         */
        fun getDayOfWeek(dayNumber: Int): DayOfWeek? {
            return when (dayNumber) {
                1 -> DayOfWeek.MONDAY
                2 -> DayOfWeek.TUESDAY
                3 -> DayOfWeek.WEDNESDAY
                4 -> DayOfWeek.THURSDAY
                5 -> DayOfWeek.FRIDAY
                6 -> DayOfWeek.SATURDAY
                7 -> DayOfWeek.SUNDAY
                else -> null
            }
        }
    }
}