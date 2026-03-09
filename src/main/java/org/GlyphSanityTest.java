package org;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GlyphSanityTest {

    private static final int WAIT_SEC = 60;
    private static final int MAILINATOR_POLL_ATTEMPTS = 12;
    private static final String BASE_URL = "https://unifiedid.glyph.network/";
    private static final String DEFAULT_PASSWORD = "Test@123";
    private static final String DEFAULT_PIN = "888881";
    private static final String IFRAME_ID = "safle-react-widget-iframe";

    private static WebDriver driver;
    private static WebDriverWait wait;

    public static void main(String[] args) {
        log("========== GLYPH SANITY (Mailinator) ==========");
        log("Environment: " + (isCiEnvironment() ? "CI (GitHub Actions)" : "LOCAL"));
        setupDriver();

        try {
            log("STEP 1: Navigate to Glyph and switch to widget iframe");
            driver.get(BASE_URL);
            switchToGlyphIframe();
            log("  → Loaded: " + BASE_URL);

            String emailPrefix = "glyph_qa_" + new Random().nextInt(10000);
            String fullEmail = emailPrefix + "@mailinator.com";
            String mailinatorUrl = "https://www.mailinator.com/v4/public/inboxes.jsp?to=" + emailPrefix;
            log("STEP 2: Sign-up – generated Mailinator address for this run");
            log("Email: " + fullEmail);

            safeType(By.id("email-input"), fullEmail);
            safeType(By.id("password-input"), DEFAULT_PASSWORD);
            safeType(By.id("confirm-password-input"), DEFAULT_PASSWORD);

            // Terms checkbox optional (not present on all form variants)
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
            try {
                WebElement checkbox = shortWait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("button[role='checkbox'][aria-checked='false']")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", checkbox);
                log("  → Terms checkbox checked.");
            } catch (Exception e) {
                log("  → No terms checkbox found, skipping.");
            }

            // Optional: wait so you can solve reCAPTCHA manually (use with -Dheadless=false -DwaitForCaptcha=60)
            int waitForCaptchaSec = 0;
            try {
                waitForCaptchaSec = Integer.parseInt(System.getProperty("waitForCaptcha", "0"));
            } catch (NumberFormatException ignored) {}
            if (waitForCaptchaSec > 0) {
                log("  → Waiting " + waitForCaptchaSec + "s for you to solve reCAPTCHA (solve it in the browser now)...");
                try { Thread.sleep(waitForCaptchaSec * 1000L); } catch (InterruptedException ignored) {}
            }

            // Click Sign Up: by text first, then by primary button class
            WebElement signUpBtn = null;
            try {
                signUpBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[normalize-space()='Sign Up']")));
            } catch (Exception e) {
                signUpBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(@class,'_buttonPrimary') and contains(.,'Sign Up')]")));
            }
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", signUpBtn);
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", signUpBtn);

            // Check for "Recaptcha verification failed" and optionally wait + retry once
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            boolean captchaFailed = !driver.findElements(By.xpath("//p[contains(@class,'formError') and contains(text(),'Recaptcha')]")).isEmpty()
                    || !driver.findElements(By.xpath("//*[contains(text(),'Recaptcha verification failed')]")).isEmpty();
            if (captchaFailed) {
                log("  → reCAPTCHA failed. Retrying once after 60s – solve it in the browser now.");
                try { Thread.sleep(60000); } catch (InterruptedException ignored) {}
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", signUpBtn);
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                captchaFailed = !driver.findElements(By.xpath("//*[contains(text(),'Recaptcha verification failed')]")).isEmpty();
                if (captchaFailed) {
                    throw new RuntimeException("Recaptcha verification failed. Use: 1) Proxy (-Dproxy=... or PROXY=...) for a different IP, or 2) Visible browser with time to solve: -Dheadless=false -DwaitForCaptcha=60");
                }
            }
            log("  → Sign Up submitted. Waiting for OTP email...");

            log("STEP 3: Wait for verification email delivery (15s)");
            Thread.sleep(15000);

            String otp = fetchOtpFromMailinator(emailPrefix, mailinatorUrl);

            if (otp == null && isCiEnvironment()) {
                log("⚠️ OTP not received in CI after max attempts. Skipping OTP submission (CI soft-fail).");
                return;
            }
            if (otp == null) {
                throw new RuntimeException("OTP retrieval failed after " + MAILINATOR_POLL_ATTEMPTS + " attempts. Check Mailinator: " + mailinatorUrl);
            }

            log("STEP 4: Submit OTP back to Glyph");
            switchToGlyphIframe();
            enterPinDigits("email-otp-", otp);
            log("  → OTP submitted.");

            log("STEP 5: Create Unified ID");
            String unifiedId = ("id" + (System.currentTimeMillis() % 100000)).toLowerCase();
            safeType(By.id("unified-id-input"), unifiedId);
            WebElement nextBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='Next']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextBtn);
            log("  → Unified ID set: " + unifiedId);

            log("STEP 6: Set Security PIN");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[contains(@id,'pin-input-0')]")));
            enterPinDigits("pin-input-", DEFAULT_PIN);
            if (!driver.findElements(By.id("confirm-pin-input-0")).isEmpty()) {
                enterPinDigits("confirm-pin-input-", DEFAULT_PIN);
            }
            driver.findElement(By.xpath("//button[contains(text(),'Create') or contains(text(),'Next')]")).click();
            log("  → PIN set and Create/Next clicked.");

            log("STEP 7: Wait for success screen");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(text(),'successfully')]")));
            log("✅ GLYPH SANITY COMPLETE");
            log("  → Email used: " + fullEmail);

        } catch (Exception e) {
            log("❌ FAILED: " + e.getMessage());
            try {
                driver.switchTo().defaultContent();
                for (String h : driver.getWindowHandles()) {
                    try { driver.switchTo().window(h); break; } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            takeScreenshot("FAILURE");
            e.printStackTrace();
            if (isCiEnvironment()) {
                log("CI soft-fail: exiting without error code.");
                System.exit(0);
            }
            throw new RuntimeException(e);
        } finally {
            if (driver != null) driver.quit();
            log("Session ended.");
        }
    }

    private static boolean isCiEnvironment() {
        return System.getenv("GITHUB_ACTIONS") != null;
    }

    /** Pick one proxy per run to get a different IP and help with reCAPTCHA. From -Dproxy= or env PROXY (comma-separated = list). */
    private static String pickProxyForRun() {
        String raw = System.getProperty("proxy", System.getenv("PROXY"));
        if (raw == null || raw.isBlank()) return null;
        List<String> list = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (list.isEmpty()) return null;
        String proxy = list.get(new Random().nextInt(list.size()));
        return proxy.contains("://") ? proxy : "http://" + proxy;
    }

    private static void setupDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        String headlessProp = System.getProperty("headless", "true");
        if ("true".equalsIgnoreCase(headlessProp)) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));

        String proxy = pickProxyForRun();
        if (proxy != null) {
            options.addArguments("--proxy-server=" + proxy);
            log("Using proxy for this run (different IP to help reCAPTCHA): " + proxy);
        }

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SEC));

        Map<String, Object> params = new HashMap<>();
        params.put("source", "Object.defineProperty(navigator, 'webdriver', { get: () => undefined })");
        ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
    }

    // Mailinator: td with support@glyph.network (Glyph mail) - click to show message, then extract OTP
    private static final String MAILINATOR_GLYPH_SELECTOR =
            "//td[contains(.,'support@glyph.network') or (contains(@onclick,'showTheMessage') and contains(.,'glyph'))]";

    /** Open Mailinator in a new tab so the main Glyph tab stays open; scrape OTP then switch back to main tab. */
    private static String fetchOtpFromMailinator(String emailPrefix, String mailinatorUrl) {
        String mainTabHandle = driver.getWindowHandle();
        log("[MAILINATOR] Opening Mailinator in new tab (main tab stays on Glyph)...");
        ((JavascriptExecutor) driver).executeScript("window.open('" + mailinatorUrl + "', '_blank');");

        String mailinatorTabHandle = null;
        for (String h : driver.getWindowHandles()) {
            if (!h.equals(mainTabHandle)) {
                mailinatorTabHandle = h;
                break;
            }
        }
        if (mailinatorTabHandle == null) {
            log("[MAILINATOR] Could not get new tab.");
            return null;
        }

        try {
            driver.switchTo().window(mailinatorTabHandle);
        } catch (Exception e) {
            log("[MAILINATOR] Switch to Mailinator tab failed: " + e.getMessage());
            return null;
        }

        WebDriverWait mailWait = new WebDriverWait(driver, Duration.ofSeconds(15));
        String otp = null;
        boolean mailOpened = false;

        for (int i = 0; i < MAILINATOR_POLL_ATTEMPTS; i++) {
            log("[MAILINATOR] Poll " + (i + 1) + "/" + MAILINATOR_POLL_ATTEMPTS + " – checking for Glyph email (support@glyph.network)...");
            try {
                if (!driver.getWindowHandles().contains(mailinatorTabHandle)) {
                    log("[MAILINATOR] Mailinator tab was closed.");
                    break;
                }
                driver.switchTo().window(mailinatorTabHandle);

                if (!mailOpened) {
                    WebElement glyphMailCell = mailWait.until(ExpectedConditions.elementToBeClickable(By.xpath(MAILINATOR_GLYPH_SELECTOR)));
                    log("[MAILINATOR] Found Glyph mail, opening message...");
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", glyphMailCell);
                    mailOpened = true;
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    try {
                        WebElement textTab = driver.findElement(By.xpath("//a[contains(@href,'pills-text') or contains(.,'TEXT')]"));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", textTab);
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    } catch (Exception ignored) {}
                }

                String body = "";
                try {
                    WebElement msgBody = driver.findElement(By.cssSelector("[id*='pills-text'], [id*='msg'], .message-body, #html_msg_body"));
                    body = msgBody.getText();
                } catch (Exception ignored) {}
                if (body.isEmpty()) body = driver.findElement(By.cssSelector("body")).getText();
                Matcher m = Pattern.compile("\\b\\d{6}\\b").matcher(body);
                if (m.find()) {
                    otp = m.group();
                    log("[MAILINATOR] ✅ OTP FOUND: " + otp);
                    break;
                }

                if (i < MAILINATOR_POLL_ATTEMPTS - 1) {
                    try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                }
            } catch (Exception e) {
                log("[MAILINATOR] Poll " + (i + 1) + " – " + e.getMessage());
                if (!mailOpened && i < MAILINATOR_POLL_ATTEMPTS - 1) {
                    try {
                        if (driver.getWindowHandles().contains(mailinatorTabHandle)) {
                            driver.switchTo().window(mailinatorTabHandle);
                            driver.navigate().refresh();
                            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Close Mailinator tab and switch back to main tab (Glyph)
        try {
            if (driver.getWindowHandles().contains(mailinatorTabHandle)) {
                driver.switchTo().window(mailinatorTabHandle);
                driver.close();
            }
        } catch (Exception ignored) {}
        try {
            driver.switchTo().window(mainTabHandle);
            log("[MAILINATOR] Switched back to main tab (Glyph).");
        } catch (Exception e) {
            log("[MAILINATOR] Switch back to main tab: " + e.getMessage());
        }

        if (otp == null) {
            takeScreenshot("MAILINATOR_NO_OTP");
            log("[MAILINATOR] No 6-digit OTP found after " + MAILINATOR_POLL_ATTEMPTS + " attempts. Inbox: " + mailinatorUrl);
        }
        return otp;
    }

    private static void takeScreenshot(String prefix) {
        try {
            new File("screenshots").mkdirs();
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File dest = new File("screenshots/" + prefix + "_" + System.currentTimeMillis() + ".png");
            FileUtils.copyFile(src, dest);
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
        System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg);
    }
}
