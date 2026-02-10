package org;

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

    private static final int WAIT_SEC = 60;
    private static final String BASE_URL = "https://unifiedid.glyph.network/";
    private static final String DEFAULT_PASSWORD = "Test@123";
    private static final String DEFAULT_PIN = "888881";
    private static final String IFRAME_ID = "safle-react-widget-iframe";

    private static WebDriver driver;
    private static WebDriverWait wait;

    public static void main(String[] args) {
        log("🚀 STARTING FINAL AUTOMATION SUITE");
        setupDriver();

        try {
            log("STEP 1: Navigating to Glyph URL: " + BASE_URL);
            driver.get(BASE_URL);
            switchToGlyphIframe();

            String emailPrefix = "glyph_qa_" + System.currentTimeMillis();
            String fullEmail = emailPrefix + "@yopmail.com";
            log("✅ Generated Target Email: " + fullEmail);

            log("✍️ Action: Entering Email...");
            safeType(By.id("email-input"), fullEmail);

            log("✍️ Action: Entering Password...");
            safeType(By.id("password-input"), DEFAULT_PASSWORD);

            log("✍️ Action: Confirming Password...");
            safeType(By.id("confirm-password-input"), DEFAULT_PASSWORD);

            log("🖱️ Action: Clicking 'Sign Up' button...");
            WebElement signUpBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='Sign Up']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", signUpBtn);
            log("✅ Sign Up Clicked Successfully.");

            log("⏳ WAITING 15 SECONDS for email delivery...");
            Thread.sleep(15000);

            log("STEP 2: Checking Yopmail Inbox for OTP...");
            String otp = fetchOtpDirectUrl(emailPrefix);

            log("STEP 3: Submitting OTP to Glyph...");
            driver.switchTo().window(driver.getWindowHandles().toArray()[0].toString());
            switchToGlyphIframe();
            enterPinDigits("email-otp-", otp);

            log("STEP 4: Creating Unified ID...");
            String unifiedId = ("id" + (System.currentTimeMillis() % 100000)).toLowerCase();
            safeType(By.id("unified-id-input"), unifiedId);

            WebElement nextBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='Next']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextBtn);
            log("✅ Unified ID Submitted: " + unifiedId);

            log("STEP 5: Setting up Security PIN...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'pin-input-0')]")));
            enterPinDigits("pin-input-", DEFAULT_PIN);

            if (!driver.findElements(By.id("confirm-pin-input-0")).isEmpty()) {
                enterPinDigits("confirm-pin-input-", DEFAULT_PIN);
            }

            driver.findElement(By.xpath("//button[contains(text(),'Create') or contains(text(),'Next')]")).click();

            log("⏳ Registering to Unified ID...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(text(),'successfully')]")));
            log("🎉 SUCCESS: Account Creation Verified!");

        } catch (Exception e) {
            log("❌ FAILED: " + e.getMessage());
            takeScreenshot("FAILURE_DEBUG");
            e.printStackTrace();
        } finally {
            if (driver != null) driver.quit();
            log("Session Ended.");
        }
    }

    private static void setupDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SEC));

        Map<String, Object> params = new HashMap<>();
        params.put("source", "Object.defineProperty(navigator, 'webdriver', { get: () => undefined })");
        ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
    }

    private static String fetchOtpDirectUrl(String emailPrefix) {
        String originalHandle = driver.getWindowHandle();
        ((JavascriptExecutor) driver).executeScript("window.open()");

        for (String handle : driver.getWindowHandles()) {
            if (!handle.equals(originalHandle)) driver.switchTo().window(handle);
        }

        String directInboxUrl = "https://yopmail.com/en/?login=" + emailPrefix;
        log("🔗 Navigating to Inbox: " + directInboxUrl);
        driver.get(directInboxUrl);

        String otp = "";
        int maxAttempts = 10; // hard cap on attempts for CI stability
        for (int i = 0; i < maxAttempts; i++) {
            log("Polling attempt " + (i + 1) + "/" + maxAttempts + "...");
            try {
                driver.switchTo().defaultContent();
                // If inbox frame exists, click the latest email first
                if (driver.findElements(By.id("ifinbox")).size() > 0) {
                    driver.switchTo().frame("ifinbox");
                    java.util.List<WebElement> mails = driver.findElements(By.cssSelector(".m, .lm"));
                    if (!mails.isEmpty()) {
                        mails.get(0).click();
                    }
                    driver.switchTo().defaultContent();
                }

                if (driver.findElements(By.id("ifmail")).size() > 0) {
                    driver.switchTo().frame("ifmail");
                    String body = driver.findElement(By.tagName("body")).getText();
                    Matcher m = Pattern.compile("\\b\\d{6}\\b").matcher(body);
                    if (m.find()) {
                        otp = m.group();
                        log("🎯 OTP CAPTURED: " + otp);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            driver.navigate().refresh();
            try {
                Thread.sleep(6000); // total max wait ~60 seconds
            } catch (InterruptedException ignored) {
            }
        }

        if (otp.isEmpty()) {
            takeScreenshot("OTP_NOT_FOUND");
            throw new RuntimeException("OTP retrieval failed after " + maxAttempts + " attempts.");
        }

        driver.close();
        return otp;
    }

    private static void takeScreenshot(String prefix) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(src, new File("screenshots/" + prefix + "_" + System.currentTimeMillis() + ".png"));
        } catch (IOException ignored) {}
    }

    private static void enterPinDigits(String idPrefix, String value) {
        for (int i = 0; i < value.length(); i++) {
            wait.until(ExpectedConditions.elementToBeClickable(By.id(idPrefix + i))).sendKeys(String.valueOf(value.charAt(i)));
        }
    }

    private static void safeType(By loc, String val) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(loc));
        el.click();
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        el.sendKeys(val);
    }

    private static void switchToGlyphIframe() {
        driver.switchTo().defaultContent();
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id(IFRAME_ID)));
    }

    private static void log(String msg) {
        System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "][LOG] " + msg);
    }
}