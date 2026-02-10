package org;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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

    // 1secmail Configuration
    private static final String MAIL_DOMAIN = "1secmail.com"; 

    private static WebDriver driver;
    private static WebDriverWait wait;

    public static void main(String[] args) {
        log("🚀 STARTING FINAL API-BASED AUTOMATION SUITE");
        setupDriver();

        try {
            log("STEP 1: Navigating to Glyph URL: " + BASE_URL);
            driver.get(BASE_URL);
            switchToGlyphIframe();

            // GENERATE EMAIL (API BASED)
            String mailUser = "glyph_ci_" + System.currentTimeMillis();
            String fullEmail = mailUser + "@" + MAIL_DOMAIN;
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

            log("⏳ WAITING for Email Delivery (via API)...");
            
            // FETCH OTP VIA API (No Browser Interaction)
            String otp = fetchOtpFromApi(mailUser, MAIL_DOMAIN);

            if (otp == null) {
                takeScreenshot("OTP_FAILURE");
                throw new RuntimeException("Failed to retrieve OTP from API after max attempts.");
            }

            log("STEP 3: Submitting OTP to Glyph...");
            // Ensure we are focused on the correct window/frame
            driver.switchTo().defaultContent();
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

            // Handle optional confirm pin if it appears
            if (!driver.findElements(By.id("confirm-pin-input-0")).isEmpty()) {
                enterPinDigits("confirm-pin-input-", DEFAULT_PIN);
            }

            // Click Create/Next/Submit (handles variations)
            WebElement createBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(),'Create') or contains(text(),'Next') or contains(text(),'Submit')]")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", createBtn);

            log("⏳ Registering to Unified ID...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(text(),'successfully')]")));
            log("🎉 SUCCESS: Account Creation Verified!");

        } catch (Exception e) {
            log("❌ FAILED: " + e.getMessage());
            takeScreenshot("FAILURE_DEBUG");
            e.printStackTrace();
            if (isCiEnvironment()) System.exit(1); 
        } finally {
            if (driver != null) driver.quit();
            log("Session Ended.");
        }
    }

    // ==========================================
    //  1SECMAIL API HELPER METHODS
    // ==========================================
    
    private static String fetchOtpFromApi(String user, String domain) {
        int attempts = 0;
        int maxAttempts = 20; // 20 * 3s = 60 seconds max wait
        
        while (attempts < maxAttempts) {
            attempts++;
            log("API Polling Attempt " + attempts + "/" + maxAttempts + "...");
            
            try {
                // 1. Check Inbox
                String inboxUrl = "https://www.1secmail.com/api/v1/?action=getMessages&login=" + user + "&domain=" + domain;
                String inboxResponse = sendGetRequest(inboxUrl);
                
                // Response is a JSON array: [{"id": 12345, "from": "...", "subject": "..."}]
                // We use simple regex to find the ID to avoid external JSON dependencies
                Pattern idPattern = Pattern.compile("\"id\":\\s*(\\d+)");
                Matcher idMatcher = idPattern.matcher(inboxResponse);

                if (idMatcher.find()) {
                    String messageId = idMatcher.group(1);
                    log("📩 Email Received! ID: " + messageId);

                    // 2. Fetch Message Body
                    String msgUrl = "https://www.1secmail.com/api/v1/?action=readMessage&login=" + user + "&domain=" + domain + "&id=" + messageId;
                    String msgResponse = sendGetRequest(msgUrl);

                    // 3. Extract OTP (Assuming 6 digits)
                    Matcher otpMatcher = Pattern.compile("\\b\\d{6}\\b").matcher(msgResponse);
                    if (otpMatcher.find()) {
                        String otp = otpMatcher.group();
                        log("🎯 OTP CAPTURED VIA API: " + otp);
                        return otp;
                    }
                }
                
                Thread.sleep(3000); // Wait 3 seconds before next poll
                
            } catch (Exception e) {
                log("⚠️ API Error: " + e.getMessage());
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    private static String sendGetRequest(String urlToRead) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlToRead);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Basic User-Agent to avoid being flagged
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    // ==========================================
    //  SELENIUM & UTILITY METHODS
    // ==========================================

    private static boolean isCiEnvironment() {
        return System.getenv("GITHUB_ACTIONS") != null;
    }

    private static void setupDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        
        // Stealth flags to prevent app from blocking the Sign Up click
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SEC));

        Map<String, Object> params = new HashMap<>();
        params.put("source", "Object.defineProperty(navigator, 'webdriver', { get: () => undefined })");
        ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
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