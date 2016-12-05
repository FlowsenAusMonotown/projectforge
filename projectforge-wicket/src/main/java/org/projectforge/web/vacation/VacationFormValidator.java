package org.projectforge.web.vacation;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.validation.IFormValidator;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.user.I18nHelper;
import org.projectforge.business.vacation.model.VacationAttrProperty;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.model.VacationStatus;
import org.projectforge.business.vacation.service.VacationService;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.framework.time.DayHolder;
import org.projectforge.web.wicket.components.DatePanel;
import org.projectforge.web.wicket.flowlayout.CheckBoxPanel;

import com.vaynberg.wicket.select2.Select2Choice;

public class VacationFormValidator implements IFormValidator
{
  private static final long serialVersionUID = -8478416045860851983L;

  // Components for form validation.
  private final FormComponent<?>[] dependentFormComponents = new FormComponent[4];

  private VacationService vacationService;

  private VacationDO data;

  private CheckBoxPanel isSpecialCheckbox;

  public VacationFormValidator(VacationService vacationService, VacationDO data)
  {
    this.vacationService = vacationService;
    this.data = data;
  }

  @Override
  public void validate(final Form<?> form)
  {
    final DatePanel startDatePanel = (DatePanel) dependentFormComponents[0];
    final DatePanel endDatePanel = (DatePanel) dependentFormComponents[1];
    final DropDownChoice<VacationStatus> statusChoice = (DropDownChoice<VacationStatus>) dependentFormComponents[2];
    final Select2Choice<EmployeeDO> employeeSelect = (Select2Choice<EmployeeDO>) dependentFormComponents[3];

    EmployeeDO employee = employeeSelect.getConvertedInput();
    if (employee == null) {
      employee = data.getEmployee();
    }

    if (VacationStatus.IN_PROGRESS.equals(data.getStatus()) && (VacationStatus.APPROVED.equals(statusChoice.getConvertedInput()) || VacationStatus.REJECTED
        .equals(statusChoice.getConvertedInput()))) {
      return;
    }

    Calendar startDate = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    startDate.setTime(data.getStartDate());
    Calendar endDate = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    endDate.setTime(data.getEndDate());

    if (endDatePanel.getConvertedInput().before(startDatePanel.getConvertedInput())) {
      form.error(I18nHelper.getLocalizedMessage("vacation.validate.endbeforestart"));
      return;
    }

    if (endDate.get(Calendar.YEAR) > startDate.get(Calendar.YEAR)) {
      form.error(I18nHelper.getLocalizedMessage("vacation.validate.vacationIn2Years"));
      return;
    }

    List<VacationDO> vacationListForPeriod = vacationService.getVacationForDate(employee,
        startDatePanel.getConvertedInput(), endDatePanel.getConvertedInput());
    if (vacationListForPeriod != null && data.getPk() != null) {
      vacationListForPeriod = vacationListForPeriod.stream().filter(vac -> vac.getPk().equals(data.getPk()) == false)
          .collect(Collectors.toList());
    }
    if (vacationListForPeriod != null && vacationListForPeriod.size() > 0) {
      form.error(I18nHelper.getLocalizedMessage("vacation.validate.leaveapplicationexists"));
    }

    if (isSpecialCheckbox != null && isSpecialCheckbox.getCheckBox().getValue() != null && isSpecialCheckbox.getCheckBox().getValue().equals("on")) {
      return;
    }

    boolean enoughDaysLeft = true;
    Calendar endDateVacationFromLastYear = vacationService.getEndDateVacationFromLastYear();

    //Positiv
    BigDecimal vacationDays = new BigDecimal(employee.getUrlaubstage());
    BigDecimal vacationDaysFromLastYear = employee.getAttribute(
        VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(),
        BigDecimal.class) != null ? employee.getAttribute(
        VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(),
        BigDecimal.class) : BigDecimal.ZERO;

    //Negative
    BigDecimal usedVacationDaysWholeYear = vacationService.getApprovedAndPlanedVacationdaysForYear(employee, startDate.get(Calendar.YEAR));
    BigDecimal usedVacationDaysFromLastYear = employee.getAttribute(
        VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(),
        BigDecimal.class) != null ? employee.getAttribute(
        VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(),
        BigDecimal.class) : BigDecimal.ZERO;
    BigDecimal usedVacationDaysWithoutDaysFromLastYear = usedVacationDaysWholeYear
        .subtract(usedVacationDaysFromLastYear);

    //Available
    BigDecimal availableVacationDays = vacationDays.subtract(usedVacationDaysWithoutDaysFromLastYear);
    BigDecimal availableVacationDaysFromLastYear = vacationDaysFromLastYear.subtract(usedVacationDaysFromLastYear);

    //Need
    BigDecimal neededVacationDays = DayHolder.getNumberOfWorkingDays(startDatePanel.getConvertedInput(),
        endDatePanel.getConvertedInput());
    BigDecimal neededVacationDaysBeforeEndFromLastYear = DayHolder.getNumberOfWorkingDays(startDatePanel.getConvertedInput(),
        endDateVacationFromLastYear.getTime());

    //Add the old data working days to available days
    if (data.getPk() != null) {
      BigDecimal oldDataWorkingDays = DayHolder.getNumberOfWorkingDays(data.getStartDate(), data.getEndDate());
      availableVacationDays = availableVacationDays.add(oldDataWorkingDays);
    }

    //Vacation after end days from last year
    if (startDatePanel.getConvertedInput().after(endDateVacationFromLastYear.getTime())) {
      if (availableVacationDays.subtract(neededVacationDays).compareTo(BigDecimal.ZERO) < 0) {
        enoughDaysLeft = false;
      }
    }
    //Vacation before end days from last year
    if (endDatePanel.getConvertedInput().before(endDateVacationFromLastYear.getTime())
        || endDatePanel.getConvertedInput().equals(endDateVacationFromLastYear.getTime())) {
      if (availableVacationDays.add(availableVacationDaysFromLastYear).subtract(neededVacationDays)
          .compareTo(BigDecimal.ZERO) < 0) {
        enoughDaysLeft = false;
      }
    }
    //Vacation over end days from last year
    if ((startDatePanel.getConvertedInput().before(endDateVacationFromLastYear.getTime())
        || startDatePanel.getConvertedInput().equals(endDateVacationFromLastYear.getTime()))
        && endDatePanel.getConvertedInput().after(endDateVacationFromLastYear.getTime())) {
      BigDecimal restFromLastYear = availableVacationDaysFromLastYear.subtract(neededVacationDaysBeforeEndFromLastYear);
      if (restFromLastYear.compareTo(BigDecimal.ZERO) <= 0) {
        if (availableVacationDays.subtract(neededVacationDays).compareTo(BigDecimal.ZERO) < 0) {
          enoughDaysLeft = false;
        }
      } else {
        if (availableVacationDays.subtract(neededVacationDays.subtract(restFromLastYear))
            .compareTo(BigDecimal.ZERO) < 0) {
          enoughDaysLeft = false;
        }
      }
    }

    if (enoughDaysLeft == false) {
      form.error(I18nHelper.getLocalizedMessage("vacation.validate.notEnoughVacationDaysLeft"));
    }
  }

  @Override
  public FormComponent<?>[] getDependentFormComponents()
  {
    return dependentFormComponents;
  }

  public void setIsSpecialCheckbox(CheckBoxPanel isSpecialCheckbox)
  {
    this.isSpecialCheckbox = isSpecialCheckbox;
  }
}
