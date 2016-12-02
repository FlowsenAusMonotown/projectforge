package org.projectforge.web.vacation.helper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.SortOrder;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.projectforge.business.configuration.ConfigurationService;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.user.I18nHelper;
import org.projectforge.business.vacation.model.VacationAttrProperty;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.model.VacationStatus;
import org.projectforge.business.vacation.service.VacationService;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.web.vacation.VacationEditPage;
import org.projectforge.web.vacation.VacationViewPageSortableDataProvider;
import org.projectforge.web.wicket.CellItemListener;
import org.projectforge.web.wicket.CellItemListenerPropertyColumn;
import org.projectforge.web.wicket.bootstrap.GridBuilder;
import org.projectforge.web.wicket.bootstrap.GridSize;
import org.projectforge.web.wicket.components.LabelBookmarkablePageLinkPanel;
import org.projectforge.web.wicket.flowlayout.DivPanel;
import org.projectforge.web.wicket.flowlayout.DivTextPanel;
import org.projectforge.web.wicket.flowlayout.FieldsetPanel;
import org.projectforge.web.wicket.flowlayout.Heading1Panel;
import org.projectforge.web.wicket.flowlayout.Heading3Panel;
import org.projectforge.web.wicket.flowlayout.TablePanel;
import org.projectforge.web.wicket.flowlayout.TextPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VacationViewHelper
{
  @Autowired
  private VacationService vacationService;

  @Autowired
  private ConfigurationService configService;

  public void createVacationView(GridBuilder gridBuilder, EmployeeDO currentEmployee, boolean showAddButton)
  {
    final Calendar now = new GregorianCalendar(ThreadLocalUserContext.getTimeZone());
    GridBuilder sectionLeftGridBuilder = gridBuilder.newSplitPanel(GridSize.COL33);
    DivPanel sectionLeft = sectionLeftGridBuilder.getPanel();
    sectionLeft.add(new Heading1Panel(sectionLeft.newChildId(), I18nHelper.getLocalizedMessage("menu.vacation.leaveaccount")));

    BigDecimal vacationdays = currentEmployee.getUrlaubstage() != null ? new BigDecimal(currentEmployee.getUrlaubstage()) : BigDecimal.ZERO;
    appendFieldset(sectionLeftGridBuilder, "vacation.annualleave", vacationdays.toString());
    BigDecimal vacationdaysPreviousYear = currentEmployee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(), BigDecimal.class) != null
        ? currentEmployee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(), BigDecimal.class) : BigDecimal.ZERO;
    appendFieldset(sectionLeftGridBuilder, "vacation.previousyearleave", vacationdaysPreviousYear.toString());
    BigDecimal subtotal1 = vacationdays.add(vacationdaysPreviousYear);
    appendFieldset(sectionLeftGridBuilder, "vacation.subtotal", subtotal1.toString());

    BigDecimal vacationdaysPreviousYearUsed =
        currentEmployee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), BigDecimal.class) != null ?
            currentEmployee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), BigDecimal.class) : BigDecimal.ZERO;
    BigDecimal vacationdaysPreviousYearUnused = vacationdaysPreviousYear.subtract(vacationdaysPreviousYearUsed);
    Calendar endDatePreviousYearVacation = configService.getEndDateVacationFromLastYear();
    String endDatePreviousYearVacationString =
        endDatePreviousYearVacation.get(Calendar.DAY_OF_MONTH) + "." + (endDatePreviousYearVacation.get(Calendar.MONTH) + 1) + ".";
    appendFieldset(sectionLeftGridBuilder, "vacation.previousyearleaveunused", vacationdaysPreviousYearUnused.toString(),
        endDatePreviousYearVacationString);
    BigDecimal approvedVacationdays = vacationService.getApprovedVacationdaysForYear(currentEmployee, now.get(Calendar.YEAR));
    appendFieldset(sectionLeftGridBuilder, "vacation.approvedvacation", approvedVacationdays.toString());
    BigDecimal subtotal2 = subtotal1.subtract(vacationdaysPreviousYearUnused).subtract(approvedVacationdays);
    appendFieldset(sectionLeftGridBuilder, "vacation.subtotal", subtotal2.toString());

    BigDecimal planedVacation = vacationService.getPlanedVacationdaysForYear(currentEmployee, now.get(Calendar.YEAR));
    appendFieldset(sectionLeftGridBuilder, "vacation.planedvacation", planedVacation.toString());
    BigDecimal subtotal3 = subtotal2.subtract(planedVacation);
    appendFieldset(sectionLeftGridBuilder, "vacation.availablevacation", subtotal3.toString());

    GridBuilder sectionRightGridBuilder = gridBuilder.newSplitPanel(GridSize.COL33);
    DivPanel sectionRight = sectionRightGridBuilder.getPanel();
    sectionRight.add(new Heading1Panel(sectionRight.newChildId(), I18nHelper.getLocalizedMessage("vacation.isSpecial")));
    appendFieldset(sectionRightGridBuilder, "vacation.isSpecialPlaned",
        String.valueOf(vacationService.getSpecialVacationCount(currentEmployee, now.get(Calendar.YEAR), VacationStatus.IN_PROGRESS)));
    appendFieldset(sectionRightGridBuilder, "vacation.isSpecialApproved",
        String.valueOf(vacationService.getSpecialVacationCount(currentEmployee, now.get(Calendar.YEAR), VacationStatus.APPROVED)));

    GridBuilder sectionBottomGridBuilder = gridBuilder.newSplitPanel(GridSize.COL100);
    DivPanel sectionBottom = sectionBottomGridBuilder.getPanel();
    sectionBottom.add(new Heading3Panel(sectionBottom.newChildId(),
        I18nHelper.getLocalizedMessage("vacation.title.list") + " " + now.get(Calendar.YEAR)));
    if (showAddButton) {
      PageParameters pageParameters = new PageParameters();
      pageParameters.add("employeeId", currentEmployee.getId());
      sectionBottom
          .add(new LabelBookmarkablePageLinkPanel(sectionBottom.newChildId(), VacationEditPage.class, I18nHelper.getLocalizedMessage("add"), pageParameters)
              .addLinkAttribute("class", "btn btn-sm btn-success").addLinkAttribute("style", "margin-bottom: 5px"));
    }
    TablePanel tablePanel = new TablePanel(sectionBottom.newChildId());
    sectionBottom.add(tablePanel);
    final DataTable<VacationDO, String> dataTable = createDataTable(createColumns(), "startDate", SortOrder.ASCENDING,
        currentEmployee);
    tablePanel.add(dataTable);
  }

  private DataTable<VacationDO, String> createDataTable(final List<IColumn<VacationDO, String>> columns,
      final String sortProperty, final SortOrder sortOrder, final EmployeeDO employee)
  {
    final SortParam<String> sortParam = sortProperty != null
        ? new SortParam<String>(sortProperty, sortOrder == SortOrder.ASCENDING) : null;
    return new DefaultDataTable<VacationDO, String>(TablePanel.TABLE_ID, columns,
        createSortableDataProvider(sortParam, employee), 50);
  }

  private ISortableDataProvider<VacationDO, String> createSortableDataProvider(final SortParam<String> sortParam,
      EmployeeDO employee)
  {
    return new VacationViewPageSortableDataProvider<VacationDO>(sortParam, vacationService, employee);
  }

  private List<IColumn<VacationDO, String>> createColumns()
  {
    final List<IColumn<VacationDO, String>> columns = new ArrayList<IColumn<VacationDO, String>>();

    final CellItemListener<VacationDO> cellItemListener = new CellItemListener<VacationDO>()
    {
      private static final long serialVersionUID = 1L;

      @Override
      public void populateItem(final Item<ICellPopulator<VacationDO>> item, final String componentId,
          final IModel<VacationDO> rowModel)
      {
        //Nothing to do here
      }
    };
    columns.add(
        new CellItemListenerPropertyColumn<VacationDO>(VacationDO.class, "startDate", "startDate", cellItemListener));
    columns
        .add(new CellItemListenerPropertyColumn<VacationDO>(VacationDO.class, "endDate", "endDate", cellItemListener));
    columns.add(new CellItemListenerPropertyColumn<VacationDO>(VacationDO.class, "status", "status", cellItemListener));
    columns
        .add(new CellItemListenerPropertyColumn<VacationDO>(VacationDO.class, "workingdays", "workingdays",
            cellItemListener));
    columns
        .add(new CellItemListenerPropertyColumn<VacationDO>(VacationDO.class, "isSpecial", "isSpecial",
            cellItemListener)
        {
          @Override
          public void populateItem(final Item<ICellPopulator<VacationDO>> item, final String componentId,
              final IModel<VacationDO> rowModel)
          {
            final VacationDO vacation = rowModel.getObject();
            if (vacation.getIsSpecial() != null && vacation.getIsSpecial() == Boolean.TRUE) {
              item.add(new TextPanel(componentId, I18nHelper.getLocalizedMessage("yes")));
            } else {
              item.add(new TextPanel(componentId, I18nHelper.getLocalizedMessage("no")));
            }
            cellItemListener.populateItem(item, componentId, rowModel);
          }
        });
    return columns;
  }

  private boolean appendFieldset(GridBuilder gridBuilder, final String label, final String value, final String... labelParameters)
  {
    if (StringUtils.isBlank(value) == true) {
      return false;
    }
    final FieldsetPanel fs = gridBuilder.newFieldset(I18nHelper.getLocalizedMessage(label, labelParameters)).suppressLabelForWarning();
    DivTextPanel divTextPanel = new DivTextPanel(fs.newChildId(), value);
    WebMarkupContainer fieldset = fs.getFieldset();
    fieldset.add(AttributeAppender.append("class", "vacationPanel"));
    if (label.contains("vacation.subtotal") || label.contains("vacation.availablevacation")) {
      WebMarkupContainer fieldsetLabel = (WebMarkupContainer) fieldset.get("label");
      WebMarkupContainer fieldsetControls = (WebMarkupContainer) fieldset.get("controls");
      fieldsetLabel.add(AttributeModifier.replace("class", "control-label-bold"));
      fieldsetControls.add(AttributeModifier.replace("class", "controls-bold"));
    }
    fs.add(divTextPanel);
    return true;
  }

}
