/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
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

import org.projectforge.business.calendar.*
import org.projectforge.business.teamcal.admin.TeamCalCache
import org.projectforge.business.user.service.UserPreferencesService
import org.projectforge.favorites.Favorites
import org.projectforge.framework.i18n.addTranslations
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.time.PFDateTime
import org.projectforge.rest.config.Rest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.*

/**
 * Rest services for the user's settings of calendar filters.
 */
@RestController
@RequestMapping("${Rest.URL}/calendar")
class CalendarFilterServicesRest {
    class CalendarInit(var date: PFDateTime? = null,
                       @Suppress("unused") var view: CalendarView? = CalendarView.WEEK,
                       var teamCalendars: List<StyledTeamCalendar>? = null,
                       var filterFavorites: List<Favorites.FavoriteIdTitle>? = null,
                       var currentFilter: CalendarFilter? = null,
                       var activeCalendars: MutableList<StyledTeamCalendar>? = null,
                       /**
                        * This is the list of possible default calendars (with full access). The user may choose one which is
                        * used as default if creating a new event. The pseudo calendar -1 for own time sheets is
                        * prepended. If chosen, new time sheets will be created at default.
                        */
                       var listOfDefaultCalendars: List<TeamCalendar>? = null,
                       var styleMap: CalendarStyleMap? = null,
                       var translations: Map<String, String>? = null)

    companion object {
        private const val PREF_KEY_FAV_LIST = "calendar.favorite.list"
        internal const val PREF_KEY_CURRENT_FAV = "calendar.favorite.current"
        private const val PREF_KEY_STATE = "calendar.state"
        private const val PREF_KEY_STYLES = "calendar.styles"
    }

    private val log = org.slf4j.LoggerFactory.getLogger(CalendarFilterServicesRest::class.java)

    @Autowired
    private lateinit var teamCalCache: TeamCalCache

    @Autowired
    private lateinit var userPreferenceService: UserPreferencesService

    @GetMapping("initial")
    fun getInitialCalendar(): CalendarInit {
        val initial = CalendarInit()
        val list = teamCalCache.allAccessibleCalendars
        val userId = ThreadLocalUserContext.getUserId()
        val calendars = list.map { teamCalDO ->
            TeamCalendar(teamCalDO, userId, teamCalCache)
        }.toMutableList()
        calendars.removeIf { it.access == TeamCalendar.ACCESS.NONE } // Don't annoy admins.

        calendars.add(0, TeamCalendar.createFavoritesBirthdaysPseudoCalendar())
        calendars.add(0, TeamCalendar.createAllBirthdaysPseudoCalendar())

        val currentFilter = getCurrentFilter()
        initial.currentFilter = currentFilter

        val styleMap = getStyleMap()
        initial.styleMap = styleMap

        initial.teamCalendars = StyledTeamCalendar.map(calendars, styleMap) // Add the styles of the styleMap to the exported calendars.

        val state = getFilterState()
        initial.date = PFDateTime.from(state.startDate)
        initial.view = state.view

        initial.activeCalendars = currentFilter.calendarIds.map { id ->
            StyledTeamCalendar(calendars.find { it.id == id }, // Might be not accessible / null, see below.
                    style = styleMap.get(id), // Add the styles of the styleMap to the exported calendar.
                    visible = currentFilter.isVisible(id)
            )
        }.toMutableList()

        initial.activeCalendars?.removeIf { it.id == null } // Access to this calendars is not given (anymore).

        initial.activeCalendars?.sortWith(compareBy(ThreadLocalUserContext.getLocaleComparator()) { it.title })

        val favorites = getFilterFavorites()
        initial.filterFavorites = favorites.idTitleList

        val listOfDefaultCalendars = mutableListOf<TeamCalendar>()
        initial.activeCalendars?.forEach { activeCal ->
            val cal = calendars.find { it.id == activeCal.id }
            if (cal != null && (cal.access == TeamCalendar.ACCESS.OWNER || cal.access == TeamCalendar.ACCESS.FULL)) {
                // Calendar with full access:
                listOfDefaultCalendars.add(TeamCalendar(id = cal.id, title = cal.title))
            }
        }
        listOfDefaultCalendars.sortBy { it.title?.toLowerCase() }
        listOfDefaultCalendars.add(0, TeamCalendar(id = -1, title = translate("calendar.option.timesheeets"))) // prepend time sheet pseudo calendar
        initial.listOfDefaultCalendars = listOfDefaultCalendars

        initial.translations = addTranslations(
                "select.placeholder",
                "calendar.filter.dialog.title",
                "calendar.filter.visible",
                "calendar.defaultCalendar",
                "calendar.defaultCalendar.tooltip",
                "calendar.navigation.today",
                "calendar.view.agenda",
                "calendar.view.day",
                "calendar.view.month",
                "calendar.view.week",
                "calendar.view.workWeek",
                "favorites",
                "delete",
                "rename",
                "save")
        return initial
    }

    @GetMapping("changeStyle")
    fun changeCalendarStyle(@RequestParam("calendarId", required = true) calendarId: Int,
                            @RequestParam("bgColor") bgColor: String?) {
        var style = getStyleMap().get(calendarId)
        if (style == null) {
            style = CalendarStyle()
            getStyleMap().add(calendarId, style)
        }
        if (!bgColor.isNullOrBlank()) {
            if (CalendarStyle.validateHexCode(bgColor)) {
                style.bgColor = bgColor
            } else {
                throw IllegalArgumentException("Hex code of color doesn't fit '#a1b' or '#a1b2c3', can't change background color: '$bgColor'.")
            }
        }
    }

    @GetMapping("setVisibility")
    fun setVisibility(@RequestParam("calendarId", required = true) calendarId: Int,
                      @RequestParam("visible", required = true) visible: Boolean) {
        val currentFilter = getCurrentFilter()
        currentFilter.setVisibility(calendarId, visible)
    }

    @GetMapping("createNewFilter")
    fun createNewFilter(@RequestParam("newFilterName", required = true) newFilterName: String) {
        val currentFilter = getCurrentFilter()
        currentFilter.name = newFilterName
        val favorites = getFilterFavorites()
        favorites.add(currentFilter)
    }

    @GetMapping("deleteFilter")
    fun removeFilter(@RequestParam("id", required = true) id: Int) {
        val favorites = getFilterFavorites()
        favorites.remove(id)
    }

    @GetMapping("selectFilter")
    fun selectFilter(@RequestParam("id", required = true) id: Int) {
        val favorites = getFilterFavorites()
        val currentFilter = favorites.get(id)
        if (currentFilter != null)
            userPreferenceService.putEntry(PREF_KEY_CURRENT_FAV, currentFilter, true)
        else
            log.warn("Can't select filter $id, because it's not found in favorites list.")
    }

    // Ensures filter list (stored one, restored from legacy filter or a empty new one).
    private fun getFilterFavorites(): Favorites<CalendarFilter> {
        var filterList: Favorites<CalendarFilter>? = null
        try {
            @Suppress("UNCHECKED_CAST", "USELESS_ELVIS")
            filterList = userPreferenceService.getEntry(Favorites::class.java, PREF_KEY_FAV_LIST) as Favorites<CalendarFilter>
                    ?: migrateFromLegacyFilter()?.list
        } catch (ex: Exception) {
            log.error("Exception while getting user preferenced favorites: ${ex.message}. This might be OK for new releases. Ignoring filter.")
        }
        if (filterList == null) {
            // Creating empty filter list (user has no filter list yet):
            filterList = Favorites<CalendarFilter>()
            userPreferenceService.putEntry(PREF_KEY_FAV_LIST, filterList, true)
        }
        return filterList
    }

    private fun getCurrentFilter(): CalendarFilter {
        var currentFilter = userPreferenceService.getEntry(CalendarFilter::class.java, PREF_KEY_CURRENT_FAV)
                ?: migrateFromLegacyFilter()?.current
        if (currentFilter == null) {
            // Creating empty filter (user has no filter list yet):
            currentFilter = CalendarFilter()
            userPreferenceService.putEntry(PREF_KEY_CURRENT_FAV, currentFilter, true)
        }
        currentFilter.afterDeserialization()
        return currentFilter
    }

    private fun getFilterState(): CalendarFilterState {
        var state = userPreferenceService.getEntry(CalendarFilterState::class.java, PREF_KEY_STATE)
                ?: migrateFromLegacyFilter()?.state
        if (state == null) {
            state = CalendarFilterState()
            userPreferenceService.putEntry(PREF_KEY_STATE, state, true)
        }
        if (state.startDate == null)
            state.startDate = LocalDate.now()
        if (state.view == null)
            state.view = CalendarView.MONTH
        return state
    }


    internal fun getStyleMap(): CalendarStyleMap {
        var styleMap = userPreferenceService.getEntry(CalendarStyleMap::class.java, PREF_KEY_STYLES)
                ?: migrateFromLegacyFilter()?.styleMap
        if (styleMap == null) {
            styleMap = CalendarStyleMap()
            userPreferenceService.putEntry(PREF_KEY_STYLES, styleMap, true)
        }
        return styleMap
    }

    private fun migrateFromLegacyFilter(): CalendarLegacyFilter? {
        val legacyFilter = CalendarLegacyFilter.migrate(userPreferenceService) ?: return null
        log.info("User's legacy calendar filter migrated.")
        userPreferenceService.putEntry(PREF_KEY_FAV_LIST, legacyFilter.list, true)
        userPreferenceService.putEntry(PREF_KEY_CURRENT_FAV, legacyFilter.current, true)
        // Filter state is now separately stored:
        userPreferenceService.putEntry(PREF_KEY_STATE, legacyFilter.state, true)
        // Filter styles are now separately stored:
        userPreferenceService.putEntry(PREF_KEY_STYLES, legacyFilter.styleMap, true)
        return legacyFilter
    }

    internal fun updateCalendarFilter(startDate: Date?,
                                      view: CalendarView?,
                                      activeCalendarIds: Set<Int>?) {
        getFilterState().updateCalendarFilter(startDate, view)
        if (!activeCalendarIds.isNullOrEmpty()) {
            getCurrentFilter().calendarIds = activeCalendarIds.toMutableSet()
        }
    }
}
