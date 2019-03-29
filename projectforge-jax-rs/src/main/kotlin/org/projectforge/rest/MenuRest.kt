package org.projectforge.rest

import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.menu.MenuItem
import org.projectforge.menu.builder.MenuCreator
import org.projectforge.menu.builder.MenuCreatorContext
import org.projectforge.rest.core.RestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Component
@Path("menu")
class MenuRest {
    class Menu(val mainMenu : List<MenuItem>, val favoriteMenu : List<MenuItem>? = null)

    @Autowired
    private lateinit var menuCreator: MenuCreator

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun logout(): Response {
        val mainMenuItems = menuCreator.build(MenuCreatorContext(ThreadLocalUserContext.getUser()))
        //val favoriteMenuItems = menuCreator.
        val menu = Menu(mainMenuItems)
        return RestHelper.buildResponse(menu)
    }
}