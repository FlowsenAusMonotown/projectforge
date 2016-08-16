package org.projectforge.web.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.projectforge.web.i18n.I18NService;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertTrue;

public abstract class TestPageBase<T extends TestPageBase>
{

  protected static WebDriver driver;
  protected static WebDriverWait wait;
  protected static String baseUrl;

  @Autowired
  private I18NService i18NService;

  public TestPageBase()
  {

  }

  public static void setWait(WebDriverWait wait)
  {
    TestPageBase.wait = wait;
  }

  public static WebDriver getDriver()
  {
    return driver;
  }

  public static void setDriver(WebDriver driver)
  {
    TestPageBase.driver = driver;
  }

  public static String getBaseUrl()
  {
    return baseUrl;
  }

  public static void setBaseUrl(String baseUrl)
  {
    TestPageBase.baseUrl = baseUrl;
  }

  @BeforeClass
  public void init()
  {
    i18NService.loadResourceBundles();
  }

  /**
   * Wartet auf einen vollständigen Seitenneuaufbau durch den die Referenz auf element invalidiert wird.
   *
   * @param element
   */
  public void waitForPageReload(WebElement element)
  {
    wait.until((WebDriver webDriver) -> {
      try {
        String s = element.getTagName();
        return s != null;
      } catch (StaleElementReferenceException e) {
        return true;
      }
    });
  }

  /**
   * Klickt auf den gegebenen submit button und warte auf eine Aktualisierung der Seite
   *
   * @param id
   */
  public void clickAndWaitForFullPageReload(String id)
  {
    clickAndWaitForFullPageReload(driver.findElement(By.id(id)));
  }

  public void clickAndWaitForFullPageReload(WebElement id)
  {
    final WebElement header = driver.findElement(By.id("topbar"));
    id.click();
    waitForPageReload(header);
  }

  public abstract String getUrlPostfix();

  public String getPageUrl()
  {
    return baseUrl + getUrlPostfix();
  }

  public T callPage()
  {
    driver.get(getPageUrl());
    return (T) this;
  }

  protected T setStringElementById(String elementId, String value)
  {
    WebElement element = driver.findElement(By.id(elementId));
    element.clear();
    element.sendKeys(value);
    return (T) this;
  }

  protected String getStringElementById(String elementId)
  {
    return driver.findElement(By.id(elementId)).getAttribute("value");
  }

  public T currentPageUrlStartsWith(String prefix)
  {
    final String currentUrl = driver.getCurrentUrl();
    assertTrue(currentUrl.startsWith(prefix));
    return (T) this;
  }

  /**
   * does what the method name says
   *
   * @param elementId
   * @param expected
   * @return
   */
  public T assertTextOfElementWithIdEquals(String elementId, String expected)
  {
    Assert.assertEquals(driver.findElement(By.id(elementId)).getAttribute("value"), expected);
    return (T) this;
  }

  public T assertWeAreOnThisPage()
  {
    final String thisPageUrl = getPageUrl();
    currentPageUrlStartsWith(thisPageUrl);
    return (T) this;
  }

  public T expectedErrorMessage(String i18nKey)
  {
    String xpathtoErrorMessage = "//ul/li/span[@class='feedbackPanelERROR']";
    String errorMessage = driver.findElement(By.xpath(xpathtoErrorMessage)).getText();
    String localizedStringForKey = i18NService.getLocalizedStringForKey(i18nKey, Locale.GERMAN);
    Assert.assertEquals(errorMessage, localizedStringForKey);
    return (T) this;
  }

  public void logout()
  {
    String logoutUrl = driver.findElement(By.id("logout")).getAttribute("href");
    driver.get(logoutUrl);
  }

  public T chooseOptionsOfSelect2(String selectInputId, String... groupsToAdd)
  {
    ArrayList<String> groupsToAdd1 = new ArrayList<String>();
    Collections.addAll(groupsToAdd1, groupsToAdd);

    String input = "//div[input/@id='" + selectInputId + "']/div/ul";
    String autocompletions = "//div[@id='select2-drop']/ul/li/div";

    driver.findElement(By.xpath(input)).click();
    int beginSize;
    do {
      beginSize = groupsToAdd1.size();
      try {
        List<WebElement> elements = driver.findElements(By.xpath(autocompletions));
        for (WebElement webElement : elements) {
          String text = webElement.getText();
          if (groupsToAdd1.contains(text)) {
            webElement.click();
            groupsToAdd1.remove(text);
            driver.findElement(By.xpath(input)).click();
            break;
          }
        }
      } catch (Exception e) {
        Assert.fail(e.getMessage());
      }
    } while (groupsToAdd1.size() != beginSize);
    driver.findElement(By.xpath(input)).sendKeys(Keys.ESCAPE);
    return (T) this;
  }

  public T selectDropdownListByValue(String idOfSelectTag, String valueOfOption)
  {
    final WebElement option = driver
        .findElement(By.xpath("//select[@id='" + idOfSelectTag + "']/option[@value='" + valueOfOption + "']"));
    option.click();

    return (T) this;
  }

  protected String getSelectedOption(String form)
  {
    Select select = (Select) driver.findElement(By.id(form));
    return select.getFirstSelectedOption().getAttribute("value");
  }
}
