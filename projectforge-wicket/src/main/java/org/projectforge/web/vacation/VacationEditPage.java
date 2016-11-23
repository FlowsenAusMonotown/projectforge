/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2014 Kai Reinhard (k.reinhard@micromata.de)
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

package org.projectforge.web.vacation;

import org.apache.log4j.Logger;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.projectforge.business.fibu.api.EmployeeService;
import org.projectforge.business.user.I18nHelper;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.model.VacationStatus;
import org.projectforge.business.vacation.service.VacationService;
import org.projectforge.web.fibu.ISelectCallerPage;
import org.projectforge.web.wicket.AbstractEditPage;
import org.projectforge.web.wicket.AbstractSecuredBasePage;
import org.projectforge.web.wicket.EditPage;

@EditPage(defaultReturnPage = VacationListPage.class)
public class VacationEditPage extends AbstractEditPage<VacationDO, VacationEditForm, VacationService>
    implements ISelectCallerPage
{
  private static final long serialVersionUID = -3899191243765232906L;

  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(VacationEditPage.class);

  @SpringBean
  private VacationService vacationService;

  @SpringBean
  private EmployeeService employeeService;

  private boolean wasNew = false;

  public VacationEditPage(final PageParameters parameters)
  {
    super(parameters, "vacation");
    init();
  }

  @Override
  protected void init()
  {
    super.init();
    if (isNew()) {
      wasNew = true;
    }
  }

  /**
   * @see org.projectforge.web.fibu.ISelectCallerPage#select(java.lang.String, java.lang.Object)
   */
  @Override
  public void select(final String property, final Object selectedValue)
  {
  }

  /**
   * @see org.projectforge.web.fibu.ISelectCallerPage#unselect(java.lang.String)
   */
  @Override
  public void unselect(final String property)
  {
  }

  /**
   * @see org.projectforge.web.fibu.ISelectCallerPage#cancelSelection(java.lang.String)
   */
  @Override
  public void cancelSelection(final String property)
  {
    // Do nothing.
  }

  @Override
  protected VacationService getBaseDao()
  {
    return vacationService;
  }

  @Override
  protected VacationEditForm newEditForm(final AbstractEditPage<?, ?, ?> parentPage, final VacationDO data)
  {
    return new VacationEditForm(this, data);
  }

  @Override
  protected Logger getLogger()
  {
    return log;
  }

  @Override
  public AbstractSecuredBasePage onSaveOrUpdate()
  {
    if (isNew() == false && VacationStatus.REJECTED.equals(form.getStatusBeforeModification()) == true) {
      form.getData().setStatus(VacationStatus.IN_PROGRESS);
    }
    return null;
  }

  @Override
  public AbstractSecuredBasePage afterSaveOrUpdate()
  {
    try {
      if (wasNew) {
        vacationService.sendMailToVacationInvolved(form.getData(), true, false);
      } else {
        if (VacationStatus.IN_PROGRESS.equals(form.getData().getStatus())) {
          vacationService.sendMailToVacationInvolved(form.getData(), false, false);
        }
      }
      if (form.getStatusBeforeModification() != null) {
        if (form.getStatusBeforeModification().equals(VacationStatus.IN_PROGRESS) && VacationStatus.APPROVED.equals(form.getData().getStatus())) {
          vacationService.sendMailToEmployeeAndHR(form.getData(), true);
          vacationService.updateUsedVacationDaysFromLastYear(form.getData());
        }
      }
    } catch (final Exception e) {
      error(I18nHelper.getLocalizedMessage("vacation.error.sendmail"));
    }
    return null;
  }

  @Override
  public WebPage afterDelete()
  {
    try {
      vacationService.sendMailToVacationInvolved(form.getData(), false, true);
      vacationService.deleteUsedVacationDaysFromLastYear(form.getData());
    } catch (final Exception e) {
      error(I18nHelper.getLocalizedMessage("vacation.error.sendmail"));
    }
    return null;
  }

  public WebPage afterUndelete() {
    vacationService.updateUsedVacationDaysFromLastYear(form.getData());
    return null;
  }

}
