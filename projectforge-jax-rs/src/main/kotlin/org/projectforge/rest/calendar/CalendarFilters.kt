/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Kai Reinhard (k.reinhard@micromata.de)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.rest.calendar

import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import java.time.LocalDate

class CalendarFilters {

    val filters = mutableListOf<CalendarsDisplayFilter>()

    var activeFilterIndex = 0

    @XStreamAsAttribute
    var startDate: LocalDate? = null

    @XStreamAsAttribute
    var firstHour: Int? = 8

    @XStreamAsAttribute
    var slot30: Boolean? = null

    @XStreamAsAttribute
    var viewType: CalendarServicesRest.CalendarViewType? = null

    companion object {

        private val log = org.slf4j.LoggerFactory.getLogger(CalendarFilters::class.java)

        val DEFAULT_COLOR = "#FAAF26"
    }
}