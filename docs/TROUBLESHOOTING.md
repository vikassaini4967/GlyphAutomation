# Troubleshooting

Common issues and solutions for the Glyph automation.

---

## "Recaptcha verification failed. Please try again."

**Cause:** reCAPTCHA blocks automated sign-up from your IP.

**Solutions:**

1. **Use a proxy** (different IP):
   ```bash
   mvn exec:java -Dproxy=http://YOUR_PROXY:PORT
   ```
   Use rotating/residential proxies (e.g. Bright Data, Oxylabs) for best results.

2. **Solve manually** (visible browser):
   ```bash
   mvn exec:java -Dheadless=false -DwaitForCaptcha=60
   ```
   Solve the reCAPTCHA in the browser within 60 seconds; the script then clicks Sign Up.

3. **Retry:** The script automatically retries Sign Up once after 60 seconds if reCAPTCHA fails. Use with visible browser so you can solve on retry.

---

## OTP not found in Mailinator

**Symptoms:** `[MAILINATOR] No 6-digit OTP found after 12 attempts`

**Possible causes:**

- Sign-up did not complete (reCAPTCHA blocked) → no email sent
- Email delayed → increase initial wait before opening Mailinator
- Mailinator page structure changed → selectors may need update

**Actions:**

1. Ensure sign-up succeeded (no reCAPTCHA error).
2. Check Mailinator inbox manually: `https://www.mailinator.com/v4/public/inboxes.jsp?to=glyph_qa_XXXX`
3. Run with visible browser to observe Mailinator behavior:
   ```bash
   mvn exec:java -Dheadless=false -DwaitForCaptcha=60
   ```

---

## Mailinator tab was closed

**Symptoms:** `[MAILINATOR] Mailinator tab was closed`

**Cause:** Mailinator or the browser closed the tab (e.g. popup blocker, redirect).

**Actions:**

- Ensure no extensions block `window.open`.
- Run with visible browser to observe.
- If persistent, consider using the same-tab flow (navigate to Mailinator in main tab) — this would require a code change.

---

## Element not found / Timeout

**Symptoms:** `Expected condition failed: waiting for element...`

**Possible causes:**

- Page structure changed
- Slow network
- reCAPTCHA or other overlay blocking interaction

**Actions:**

1. Run with `-Dheadless=false` to see the page.
2. Increase `WAIT_SEC` in code if needed (default 60s).
3. Check for overlays or modals that block the target element.

---

## Chrome/ChromeDriver version mismatch

**Symptoms:** `Session not created` or Chrome fails to start.

**Solution:** WebDriverManager downloads the matching ChromeDriver. Ensure Chrome is up to date:

```bash
# macOS
brew upgrade --cask google-chrome
```

---

## Screenshots not saved

**Symptoms:** No `screenshots/` directory or files.

**Cause:** Screenshots are created only on failure. The directory is created automatically.

**Note:** If the driver crashes before `takeScreenshot()` runs (e.g. "no such window"), the screenshot may not be saved. The code attempts to switch to a valid window before taking screenshots.

---

## CI build fails

**Symptoms:** GitHub Actions job fails.

**Check:**

1. **OTP not received:** CI uses soft-fail (exit 0) when `GITHUB_ACTIONS` is set and OTP is missing. The build should stay green; check logs for `⚠️ OTP not received in CI`.
2. **Chrome not found:** Workflow uses `browser-actions/setup-chrome`. Ensure the workflow file includes this step.
3. **Screenshots:** Upload artifact step has `if-no-files-found: ignore`; empty screenshots dir is acceptable.

---

## CDP / DevTools warnings

**Symptoms:** `Unable to find CDP implementation matching 144`

**Impact:** Non-critical. Selenium works; some DevTools features may be limited.

**Optional fix:** Add a matching `selenium-devtools-v*` dependency if you need CDP features. See Selenium docs for version mapping.
