package org.projectforge.plugins.eed.excelimport;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.fibu.EmployeeSalaryDO;
import org.projectforge.business.fibu.EmployeeSalaryType;
import org.projectforge.business.fibu.api.EmployeeSalaryService;
import org.projectforge.business.fibu.api.EmployeeService;
import org.projectforge.excel.ExcelImport;
import org.projectforge.framework.i18n.UserException;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.framework.persistence.utils.ImportStorage;
import org.projectforge.framework.persistence.utils.ImportedElement;
import org.projectforge.framework.persistence.utils.ImportedSheet;
import org.projectforge.plugins.eed.model.EmployeeConfigurationDO;
import org.projectforge.plugins.eed.service.EmployeeConfigurationService;

import de.micromata.genome.db.jpa.tabattr.entities.JpaTabAttrBaseDO;

public class EmployeeSalaryExcelImporter
{
  private static final Logger log = Logger.getLogger(EmployeeBillingExcelRow.class);

  private static final String NAME_OF_EXCEL_SHEET = "employeeSalaries";

  private static final int ROW_INDEX_OF_COLUMN_NAMES = 0;

  private static final String[] DIFF_PROPERTIES = { "bruttoMitAgAnteil", "comment" };

  private final EmployeeConfigurationService emplConfigService;

  private final EmployeeService employeeService;

  private final EmployeeSalaryService employeeSalaryService;

  private final ImportStorage<EmployeeSalaryDO> storage;

  private final Date dateToSelectAttrRow;

  public EmployeeSalaryExcelImporter(final EmployeeService employeeService, final EmployeeSalaryService employeeSalaryService,
      final EmployeeConfigurationService employeeConfigurationService,
      final ImportStorage<EmployeeSalaryDO> storage,
      final Date dateToSelectAttrRow)
  {
    this.employeeService = employeeService;
    this.employeeSalaryService = employeeSalaryService;
    this.emplConfigService = employeeConfigurationService;
    this.storage = storage;
    this.dateToSelectAttrRow = dateToSelectAttrRow;
  }

  public void doImport(final InputStream is) throws IOException
  {
    final ExcelImport<EmployeeSalaryExcelRow> importer = new ExcelImport<>(is);
    importer.setActiveSheet(0);
    importEmployeeSalary(importer);
  }

  private void importEmployeeSalary(final ExcelImport<EmployeeSalaryExcelRow> importer)
  {
    final ImportedSheet<EmployeeSalaryDO> importedSheet = new ImportedSheet<>();
    storage.addSheet(importedSheet);
    importedSheet.setName(NAME_OF_EXCEL_SHEET);
    importer.setNameRowIndex(ROW_INDEX_OF_COLUMN_NAMES);
    importer.setStartingRowIndex(ROW_INDEX_OF_COLUMN_NAMES + 1);

    final List<String> columnNames = new ArrayList<>();
    EmployeeConfigurationDO emplConfigDO = emplConfigService.getSingleEmployeeConfigurationDO();
    Map<String, JpaTabAttrBaseDO<EmployeeConfigurationDO, Integer>> attrs = emplConfigDO.getAttrs();
    String staffNrColumnName = attrs.get(EmployeeConfigurationService.STAFFNR_COLUMN_NAME_ATTR) != null ?
        attrs.get(EmployeeConfigurationService.STAFFNR_COLUMN_NAME_ATTR).getStringData() :
        null;
    String salaryColumnName = attrs.get(EmployeeConfigurationService.SALARY_COLUMN_NAME_ATTR) != null ?
        attrs.get(EmployeeConfigurationService.SALARY_COLUMN_NAME_ATTR).getStringData() :
        null;
    String remarkColumnName = attrs.get(EmployeeConfigurationService.REMARK_COLUMN_NAME_ATTR) != null ?
        attrs.get(EmployeeConfigurationService.REMARK_COLUMN_NAME_ATTR).getStringData() :
        null;
    // mapping from excel column name to the bean field name
    final Map<String, String> map = new HashMap<>();
    if (StringUtils.isEmpty(staffNrColumnName) || StringUtils.isEmpty(salaryColumnName)) {
      throw new UserException("plugins.eed.salaryimport.validation.nocolumndefinition");
    } else {
      columnNames.add(staffNrColumnName);
      columnNames.add(salaryColumnName);
      map.put(staffNrColumnName, "staffnumber");
      map.put(salaryColumnName, "salary");
      if (StringUtils.isEmpty(remarkColumnName) == false) {
        columnNames.add(remarkColumnName);
        map.put(remarkColumnName, "remark");
      }
    }
    if (importer.getColumnNames().containsAll(columnNames) == false) {
      throw new UserException("plugins.eed.salaryimport.validation.columndefinitionnotfound", columnNames.get(0), columnNames.get(1));
    }
    importer.setColumnMapping(map);

    final EmployeeSalaryExcelRow[] rows = importer.convertToRows(EmployeeSalaryExcelRow.class);
    for (final EmployeeSalaryExcelRow row : rows) {
      if (row.getStaffnumber() != null) {
        final ImportedElement<EmployeeSalaryDO> element = convertRowToDo(row);
        importedSheet.addElement(element);
      }
    }
  }

  private ImportedElement<EmployeeSalaryDO> convertRowToDo(final EmployeeSalaryExcelRow row)
  {
    final ImportedElement<EmployeeSalaryDO> element = new ImportedElement<>(storage.nextVal(), EmployeeSalaryDO.class, DIFF_PROPERTIES);
    Calendar selectedDateCalendar = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    selectedDateCalendar.setTime(this.dateToSelectAttrRow);
    EmployeeDO employee = null;
    EmployeeSalaryDO employeeSalary = null;
    if (row.getStaffnumber() != null) {
      employee = employeeService.getEmployeeByStaffnumber(row.getStaffnumber());
      // validate ID and USER: make sure that full name has not changed
      if (employee == null) {
        element.putErrorProperty("user", row.getStaffnumber());
      } else {
        employeeSalary = employeeSalaryService.getEmployeeSalaryByDate(employee, selectedDateCalendar);
        if (employeeSalary == null) {
          employeeSalary = new EmployeeSalaryDO();
          employeeSalary.setEmployee(employee);
          employeeSalary.setYear(selectedDateCalendar.get(Calendar.YEAR));
          //For view we have to add one to the month. Before save it will be subed.
          employeeSalary.setMonth(selectedDateCalendar.get(Calendar.MONTH) + 1);
          employeeSalary.setType(EmployeeSalaryType.GEHALT);
        } else {
          //For view we have to add one to the month. Before save it will be subed.
          employeeSalary.setMonth(selectedDateCalendar.get(Calendar.MONTH) + 1);
        }
        employeeSalary.setBruttoMitAgAnteil(row.getSalary());
        if (StringUtils.isBlank(row.getRemark()) == false) {
          employeeSalary.setComment(row.getRemark());
        }
      }
    } else {
      // this employee salaery is just created to show it in the EmployeeSalaryImportStoragePanel, it will never be imported to the DB
      employeeSalary = new EmployeeSalaryDO();
      element.putErrorProperty("user", row.getStaffnumber());
    }
    element.setValue(employeeSalary);
    return element;
  }

}
