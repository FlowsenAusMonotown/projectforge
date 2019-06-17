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

package org.projectforge.rest

import org.apache.commons.lang3.StringUtils
import org.projectforge.business.address.*
import org.projectforge.business.image.ImageService
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.i18n.translateMsg
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext.getUserId
import org.projectforge.menu.MenuItem
import org.projectforge.menu.MenuItemTargetType
import org.projectforge.rest.AddressImageServicesRest.Companion.SESSION_IMAGE_ATTR
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractBaseRest
import org.projectforge.rest.core.AbstractDTORest
import org.projectforge.rest.core.ExpiringSessionAttributes
import org.projectforge.rest.core.ResultSet
import org.projectforge.rest.dto.Address
import org.projectforge.sms.SmsSenderConfig
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest


@RestController
@RequestMapping("${Rest.URL}/address")
class AddressRest()
    : AbstractDTORest<AddressDO, Address, AddressDao, AddressFilter>(
        AddressDao::class.java,
        AddressFilter::class.java,
        i18nKeyPrefix = "address.title",
        cloneSupported = true) {

    /**
     * For exporting list of addresses.
     */
    private class ListAddress(val address: AddressDO,
                              val id: Int, // Needed for history Service
                              var imageUrl: String? = null,
                              var previewImageUrl: String? = null)

    @Autowired
    private lateinit var addressbookDao: AddressbookDao

    @Autowired
    private lateinit var imageService: ImageService

    @Autowired
    private lateinit var personalAddressDao: PersonalAddressDao

    @Autowired
    private lateinit var smsSenderConfig: SmsSenderConfig

    override fun transformDO(obj: AddressDO, editMode: Boolean): Address {
        val address = Address()
        address.copyFrom(obj)
        val personalAddress = personalAddressDao.getByAddressId(obj.id)
        if (personalAddress != null) {
            address.isFavoriteCard = personalAddress.isFavorite == true
            address.isFavoriteBusinessPhone = personalAddress.isFavoriteBusinessPhone == true
            address.isFavoriteMobilePhone = personalAddress.isFavoriteMobilePhone == true
            address.isFavoriteFax = personalAddress.isFavoriteFax == true
            address.isFavoritePrivatePhone = personalAddress.isFavoritePrivatePhone == true
            address.isFavoritePrivateMobilePhone = personalAddress.isFavoritePrivateMobilePhone == true
        }
        return address
    }

    override fun transformDTO(dto: Address): AddressDO {
        val addressDO = AddressDO()
        dto.copyTo(addressDO)
        return addressDO
    }

    /**
     * Initializes new books for adding.
     */
    override fun newBaseDO(request: HttpServletRequest?): AddressDO {
        val address = super.newBaseDO(request)
        address.addressStatus = AddressStatus.UPTODATE
        address.contactStatus = ContactStatus.ACTIVE
        address.addressbookList = mutableSetOf()
        address.addressbookList?.add(addressbookDao.globalAddressbook)
        return address
    }

    override fun onGetItemAndLayout(request: HttpServletRequest, item: AddressDO, editLayoutData: AbstractBaseRest.EditLayoutData) {
        ExpiringSessionAttributes.removeAttribute(request.session, SESSION_IMAGE_ATTR)
    }

    // TODO Menus: print view, ical export, direct call: see AddressEditPage
    // TODO: onSaveOrUpdate: see AddressEditPage

    override fun validate(validationErrors: MutableList<ValidationError>, obj: AddressDO) {
        if (StringUtils.isAllBlank(obj.name, obj.firstName, obj.organization)) {
            validationErrors.add(ValidationError(translate("address.form.error.toFewFields"), fieldId = "name"))
        }
        if (obj.addressbookList.isNullOrEmpty()) {
            validationErrors.add(ValidationError(translateMsg("validation.error.fieldRequired",
                    translate("address.addressbooks")), fieldId = "addressbooks"))
        }
    }

    override fun beforeSaveOrUpdate(request: HttpServletRequest, obj: AddressDO, dto: Address) {
        val session = request.session
        val bytes = ExpiringSessionAttributes.getAttribute(session, SESSION_IMAGE_ATTR)
        if (bytes != null && bytes is ByteArray) {
            obj.imageData = bytes
            obj.imageDataPreview = imageService.resizeImage(bytes)
            ExpiringSessionAttributes.removeAttribute(session, SESSION_IMAGE_ATTR)
        } else {
            if (obj.imageData != null) {
                val dbAddress = baseDao.getById(obj.id)
                obj.imageData = dbAddress.imageData
                obj.imageDataPreview = dbAddress.imageDataPreview
            } else {
                obj.imageDataPreview = null
            }
        }
    }

    override fun afterSaveOrUpdate(obj: AddressDO, dto: Address) {
        val address = baseDao.getOrLoad(obj.id)
        val personalAddress = PersonalAddressDO()
        personalAddress.address = address
        personalAddress.isFavoriteCard = dto.isFavoriteCard
        personalAddress.isFavoriteBusinessPhone = dto.isFavoriteBusinessPhone
        personalAddress.isFavoritePrivatePhone = dto.isFavoritePrivatePhone
        personalAddress.isFavoriteMobilePhone = dto.isFavoriteMobilePhone
        personalAddress.isFavoritePrivateMobilePhone = dto.isFavoritePrivateMobilePhone
        personalAddress.isFavoriteFax = dto.isFavoriteFax
        personalAddressDao.setOwner(personalAddress, getUserId()) // Set current logged in user as owner.
        personalAddressDao.saveOrUpdate(personalAddress)
        //return null
    }

    /**
     * LAYOUT List page
     */
    override fun createListLayout(): UILayout {
        val addressLC = LayoutContext(lc)
        addressLC.idPrefix = "address."
        val layout = super.createListLayout()
                .add(UITable.UIResultSetTable()
                        .add(addressLC, "lastUpdate")
                        .add(UITableColumn("address.imagePreview", "address.image", dataType = UIDataType.CUSTOMIZED))
                        .add(addressLC, "name", "firstName", "organization", "email")
                        .add(UITableColumn("phoneNumbers", "address.phoneNumbers", dataType = UIDataType.CUSTOMIZED))
                        .add(lc, "addressbookList"))
        layout.getTableColumnById("address.lastUpdate").formatter = Formatter.DATE
        LayoutUtils.addListFilterContainer(layout,
                UICheckbox("filter", label = "filter"),
                UICheckbox("newest", label = "filter.newest"),
                UICheckbox("favorites", label = "address.filter.myFavorites"),
                UICheckbox("dublets", label = "address.filter.doublets"))
        var menuIndex = 0
        if (smsSenderConfig.isSmsConfigured()) {
            layout.add(MenuItem("address.writeSMS", i18nKey = "address.tooltip.writeSMS", url = "wa/sendSms"), menuIndex++)
        }
        val exportMenu = MenuItem("address.export", i18nKey = "export")
        exportMenu.add(MenuItem("address.vCardExport",
                i18nKey = "address.book.vCardExport",
                url = "${getRestPath()}/exportFavoritesVCards",
                tooltip = "address.book.vCardExport.tooltip.content",
                tooltipTitle = "address.book.vCardExport.tooltip.title",
                type = MenuItemTargetType.DOWNLOAD))
        exportMenu.add(MenuItem("address.export",
                i18nKey = "address.book.export",
                url = "${getRestPath()}/exportAsExcel",
                tooltipTitle = "address.book.export",
                tooltip = "address.book.export.tooltip",
                type = MenuItemTargetType.DOWNLOAD))
        exportMenu.add(MenuItem("address.exportFavoritePhoneList",
                i18nKey = "address.book.exportFavoritePhoneList",
                url = "${getRestPath()}/exportFavoritePhoneList",
                tooltipTitle = "address.book.exportFavoritePhoneList.tooltip.title",
                tooltip = "address.book.exportFavoritePhoneList.tooltip.content",
                type = MenuItemTargetType.DOWNLOAD))
        layout.add(exportMenu, menuIndex)
        layout.getMenuById(GEAR_MENU)?.add(MenuItem("address.exportAppleScript4Notes",
                i18nKey = "address.book.export.appleScript4Notes",
                url = "${getRestPath()}/downloadAppleScript",
                tooltipTitle = "address.book.export.appleScript4Notes",
                tooltip = "address.book.export.appleScript4Notes.tooltip",
                type = MenuItemTargetType.DOWNLOAD))
        return LayoutUtils.processListPage(layout)
    }

    /**
     * LAYOUT Edit page
     */
    override fun createEditLayout(dataObject: AddressDO): UILayout {
        val addressbookDOs = addressbookDao.allAddressbooksWithFullAccess
        val addressbooks = mutableListOf<UISelectValue<Int>>()
        addressbookDOs.forEach {
            addressbooks.add(UISelectValue(it.id, it.title!!))
        }
        val layout = super.createEditLayout(dataObject)
                //autoCompletion = AutoCompletion(url = "addressBook/ac?search="))))
                .add(UIRow()
                        .add(UIFieldset(6)
                                .add(createFavoriteRow(UISelect("addressbookList", lc,
                                        multi = true,
                                        values = addressbooks,
                                        labelProperty = "title",
                                        valueProperty = "id"),
                                        "isFavoriteCard")))
                        .add(UIFieldset(6)
                                .add(UIRow()
                                        .add(UICol(length = 6)
                                                .add(lc, "addressStatus", createRowCol = false))
                                        .add(UICol(length = 6)
                                                .add(lc, "contactStatus", createRowCol = false)))))
                .add(UIRow()
                        .add(UIFieldset(6)
                                .add(lc, "name", "firstName", "form", "title", "email", "privateEmail"))
                        .add(UIFieldset(6)
                                .add(lc, "birthday", "communicationLanguage", "organization", "division", "positionText", "website", createRowCol = false)))
                .add(UIRow()
                        .add(UIFieldset()
                                .add(UIRow()
                                        .add(UICol(6)
                                                .add(createFavoriteRow(UIInput("businessPhone", lc),
                                                        "isFavoriteBusinessPhone"))
                                                .add(createFavoriteRow(UIInput("mobilePhone", lc),
                                                        "isFavoriteMobilePhone"))
                                                .add(createFavoriteRow(UIInput("fax", lc),
                                                        "isFavoriteFax")))
                                        .add(UICol(6)
                                                .add(createFavoriteRow(UIInput("privatePhone", lc),
                                                        "isFavoritePrivatePhone"))
                                                .add(createFavoriteRow(UIInput("privateMobilePhone", lc),
                                                        "isFavoritePrivateMobilePhone"))))))
                .add(UIRow()
                        .add(UIFieldset(6, title = "address.heading.businessAddress")
                                .add(lc, "addressText", createRowCol = false)
                                .add(UIRow()
                                        .add(UICol(length = 2)
                                                .add(UIInput("zipCode", lc)))
                                        .add(UICol(length = 10)
                                                .add(UIInput("city", lc))))
                                .add(UIRow()
                                        .add(UICol(length = 6)
                                                .add(UIInput("country", lc)))
                                        .add(UICol(length = 6)
                                                .add(UIInput("state", lc)))))
                        .add(UIFieldset(6, "address.heading.postalAddress")
                                .add(lc, "postalAddressText", createRowCol = false)
                                .add(UIRow()
                                        .add(UICol(length = 2)
                                                .add(UIInput("postalZipCode", lc)))
                                        .add(UICol(length = 10)
                                                .add(UIInput("postalCity", lc))))
                                .add(UIRow()
                                        .add(UICol(length = 6)
                                                .add(UIInput("postalCountry", lc)))
                                        .add(UICol(length = 6)
                                                .add(UIInput("postalState", lc))))))
                .add(UIRow()
                        .add(UIFieldset()
                                .add(UIRow()
                                        .add(UICol(6)
                                                .add(UILabel("address.heading.privateAddress"))
                                                .add(lc, "privateAddressText", createRowCol = false)
                                                .add(UIRow()
                                                        .add(UICol(length = 2)
                                                                .add(UIInput("privateZipCode", lc)))
                                                        .add(UICol(length = 10)
                                                                .add(UIInput("privateCity", lc))))
                                                .add(UIRow()
                                                        .add(UICol(length = 6)
                                                                .add(UIInput("privateCountry", lc)))
                                                        .add(UICol(length = 6)
                                                                .add(UIInput("privateState", lc)))))
                                        .add(UICol(6)
                                                .add(UILabel("address.image"))
                                                .add(UICustomized("address.edit.image"))))
                                .add(lc, "comment", createRowCol = false)))

        layout.getInputById("name").focus = true
        layout.getTextAreaById("comment").cssClass = CssClassnames.MT_5
        layout.addTranslations("delete", "file.upload.dropArea", "address.image.upload.error")
        if (dataObject.id != null) {
            layout.add(MenuItem("address.printView",
                    i18nKey = "printView",
                    url = "wa/addressView?id=${dataObject.id}",
                    type = MenuItemTargetType.REDIRECT))
            layout.add(MenuItem("address.vCardSingleExport",
                    i18nKey = "address.book.vCardSingleExport",
                    url = "${getRestPath()}/exportVCard/${dataObject.id}",
                    type = MenuItemTargetType.DOWNLOAD))
            layout.add(MenuItem("address.directCall",
                    i18nKey = "address.directCall.call",
                    url = "wa/phoneCall?addressId=${dataObject.id}",
                    type = MenuItemTargetType.REDIRECT))
        }
        return LayoutUtils.processEditPage(layout, dataObject, this)
    }

    override fun processResultSetBeforeExport(resultSet: ResultSet<Any>) {
        val list: List<ListAddress> = resultSet.resultSet.map { it ->
            ListAddress(it as AddressDO,
                    id = it.id,
                    imageUrl = if (it.imageData != null) "address/image/${it.id}" else null,
                    previewImageUrl = if (it.imageDataPreview != null) "address/imagePreview/${it.id}" else null)
        }
        resultSet.resultSet = list
        resultSet.resultSet.forEach { it ->
            (it as ListAddress).address.imageData = null
            it.address.imageDataPreview = null
        }
    }

    override fun processItemBeforeExport(item: Any) {
        if ((item as AddressDO).imageData != null || item.imageDataPreview != null) {
            item.imageData = byteArrayOf(1)
            item.imageDataPreview = byteArrayOf(1)
        }
    }

    private fun createFavoriteRow(inputElement: UIElement, id: String): UIRow {
        return UIRow()
                .add(UICol(length = 9)
                        .add(inputElement))
                .add(UICol(length = 3)
                        .add(UICheckbox(id, label = "favorite")))
    }
}