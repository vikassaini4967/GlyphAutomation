import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlyphSanityTest {

    // Configuration
    private static final int WAIT_SEC = 60;
    private static final String BASE_URL = "https://unifiedid.glyph.network/";
    private static final String YOPMAIL_URL = "https://yopmail.com/en/";
    private static final String DEFAULT_PASSWORD = "Test@123";
    private static final String DEFAULT_PIN = "888881";
    private static final String IFRAME_ID = "safle-react-widget-iframe";

    private static WebDriver driver;
    private static WebDriverWait wait;

    public static void main(String[] args) {
        log("--------------------------------------------------");
        log("🚀 INITIALIZING GLYPH SIGNUP AUTOMATION");
        log("--------------------------------------------------");

        setupDriver();

        try {
            // STEP 1: Navigate and enter registration details
            log("STEP 1: Navigating to Base URL: " + BASE_URL);
            driver.get(BASE_URL);

            switchToGlyphIframe();

            String emailPrefix = "glyph_qa_" + System.currentTimeMillis();
            String fullEmail = emailPrefix + "@yopmail.com";
            log("Generating unique test email: " + fullEmail);

            log("Entering email, password, and confirmation password...");
            safeType(By.id("email-input"), fullEmail);
            safeType(By.id("password-input"), DEFAULT_PASSWORD);
            safeType(By.id("confirm-password-input"), DEFAULT_PASSWORD);

            log("Clicking 'Sign Up' button via Action Class...");
            WebElement signUpBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='Sign Up']")));
            new Actions(driver).moveToElement(signUpBtn).click().perform();

            log("Waiting 5 seconds for system to trigger OTP email...");
            Thread.sleep(5000);

            // STEP 2: Handle OTP Retrieval
            log("STEP 2: Starting OTP retrieval process from Yopmail...");
            String otp = fetchOtpWithRefresh(emailPrefix);

            // STEP 3: Return to Glyph and Enter OTP
            log("STEP 3: Returning to Glyph tab to enter OTP.");
            driver.switchTo().window(driver.getWindowHandles().toArray()[0].toString());
            switchToGlyphIframe();

            log("Entering 6-digit OTP: " + otp);
            enterPinDigits("email-otp-", otp);

            // STEP 4: Choose Unified ID
            log("STEP 4: Entering Unified ID.");
            String unifiedId = ("id" + (System.currentTimeMillis() % 100000)).toLowerCase();
            log("Generated Unified ID: " + unifiedId);
            safeType(By.id("unified-id-input"), unifiedId);

            log("Clicking 'Next' after Unified ID entry...");
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='Next']"))).click();

            // STEP 5: Set Security PIN
            log("STEP 5: Setting Security PIN.");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'pin-input-0')]")));
            log("Entering primary PIN...");
            enterPinDigits("pin-input-", DEFAULT_PIN);

            if (!driver.findElements(By.id("confirm-pin-input-0")).isEmpty()) {
                log("Entering confirmation PIN...");
                enterPinDigits("confirm-pin-input-", DEFAULT_PIN);
            }

            log("Clicking 'Create' to finalize registration...");
            driver.findElement(By.xpath("//button[contains(text(),'Create') or contains(text(),'Next')]")).click();

            // STEP 6: Final Verification
            log("STEP 6: Waiting for success message...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(text(),'successfully')]")));
            log("✅ SUCCESS: Registration completed for Unified ID: " + unifiedId);

        } catch (Exception e) {
            log("❌ FATAL ERROR: " + e.getMessage());
            takeScreenshot("Failure_Main_Flow");
            e.printStackTrace();
        } finally {
            log("Cleanup: Closing browser and ending session.");
            if (driver != null) driver.quit();
        }
    }

    private static void setupDriver() {
        log("Configuring Chrome Options and Stealth Mode...");
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");


        //git  GITHUB ACTIONS
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        driver = new ChromeDriver(options);

        // CDP Stealth Command to mask Selenium
        Map<String, Object> params = new HashMap<>();
        params.put("source", "Object.defineProperty(navigator, 'webdriver', { get: () => undefined })");
        ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);

        wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SEC));
        log("Chrome Driver initialized successfully.");
    }

    private static String fetchOtpWithRefresh(String emailPrefix) {
        log("Opening Yopmail in a new tab...");
        String originalHandle = driver.getWindowHandle();
        ((JavascriptExecutor) driver).executeScript("window.open()");

        for (String handle : driver.getWindowHandles()) {
            if (!handle.equals(originalHandle)) driver.switchTo().window(handle);
        }

        driver.get(YOPMAIL_URL);
        log("Logging into Yopmail inbox: " + emailPrefix);
        safeType(By.id("login"), emailPrefix);

        log("Clicking Check Inbox button...");
        By loginBtnLocator = By.xpath("//button[@title='Check Inbox @yopmail.com' or contains(@class,'f36')]");
        wait.until(ExpectedConditions.elementToBeClickable(loginBtnLocator)).click();

        String otp = "";
        for (int i = 0; i < 60; i++) {
            log("Polling for OTP... Attempt " + (i + 1) + "/60");
            try {
                driver.switchTo().defaultContent();
                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("ifmail")));
                driver.switchTo().frame("ifmail");

                String body = driver.findElement(By.tagName("body")).getText();
                Matcher m = Pattern.compile("\\b\\d{6}\\b").matcher(body);

                if (m.find()) {
                    otp = m.group();
                    log("🎯 OTP FOUND: " + otp);
                    break;
                }
            } catch (Exception e) {
                log("OTP not found in iframe content yet.");
            }

            log("Refreshing Yopmail inbox and waiting 3 seconds...");
            driver.switchTo().defaultContent();
            try {
                WebElement refreshBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("refresh")));
                refreshBtn.click();
            } catch (Exception e) {
                log("Warn: Refresh button not interactable.");
            }

            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        }

        if (otp.isEmpty()) {
            log("❌ ERROR: OTP not received within timeout.");
            takeScreenshot("OTP_Timeout");
            throw new RuntimeException("OTP retrieval failed.");
        }

        driver.close();
        return otp;
    }

    private static void takeScreenshot(String fileNamePrefix) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        String filePath = "screenshots/" + fileNamePrefix + "_" + timestamp + ".png";
        try {
            FileUtils.copyFile(srcFile, new File(filePath));
            log("📸 Screenshot captured: " + filePath);
        } catch (IOException e) {
            log("Failed to save screenshot: " + e.getMessage());
        }
    }

    private static void enterPinDigits(String idPrefix, String value) {
        for (int i = 0; i < value.length(); i++) {
            WebElement digitInput = wait.until(ExpectedConditions.elementToBeClickable(By.id(idPrefix + i)));
            digitInput.sendKeys(String.valueOf(value.charAt(i)));
        }
    }

    private static void safeType(By locator, String value) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        el.click();
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        el.sendKeys(value);
    }

    private static void switchToGlyphIframe() {
        log("Switching focus to Glyph React Widget Iframe...");
        driver.switchTo().defaultContent();
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id(IFRAME_ID)));
    }

    private static void log(String msg) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timeStamp + "][LOG] " + msg);
    }
}