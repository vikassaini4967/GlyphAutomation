# Architecture

This document describes the technical design and flow of the Glyph automation project.

---

## Overview

The automation is a single-class Java application (`GlyphSanityTest`) that uses Selenium WebDriver to drive Chrome through the Glyph Unified ID sign-up flow. OTP verification is handled via Mailinator's public inbox.

---

## Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Selenium | 4.18.1 | Browser automation |
| WebDriverManager | 5.8.0 | ChromeDriver management |
| Commons IO | 2.15.1 | Screenshot file handling |
| Maven | 3.8+ | Build and dependency management |
| Java | 17 | Runtime |

---

## Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. SETUP                                                                 │
│    • WebDriverManager.chromedriver().setup()                              │
│    • ChromeOptions (headless, proxy, no-sandbox)                          │
│    • WebDriverWait 60s                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. GLYPH SIGN-UP (Main Tab)                                               │
│    • driver.get(https://unifiedid.glyph.network/)                         │
│    • switchToGlyphIframe() → safle-react-widget-iframe                    │
│    • Generate email: glyph_qa_{random}@mailinator.com                    │
│    • Fill: email-input, password-input, confirm-password-input            │
│    • Optional: terms checkbox (if present)                               │
│    • Optional: waitForCaptcha (manual solve)                              │
│    • Click Sign Up                                                        │
│    • Detect reCAPTCHA failure → retry once after 60s                      │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. OTP RETRIEVAL (New Tab)                                               │
│    • window.open(mailinatorUrl, '_blank')                                 │
│    • Switch to Mailinator tab                                             │
│    • Poll up to 12× for Glyph email (support@glyph.network)                │
│    • Click row → open message                                             │
│    • Click TEXT tab → plain-text body                                     │
│    • Regex: \b\d{6}\b → extract OTP                                      │
│    • Close Mailinator tab                                                 │
│    • Switch back to main tab (Glyph)                                      │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. OTP SUBMISSION (Main Tab)                                             │
│    • switchToGlyphIframe()                                                │
│    • enterPinDigits("email-otp-", otp) → email-otp-0..5                  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. UNIFIED ID & PIN                                                      │
│    • unified-id-input → id{random}                                        │
│    • Click Next                                                           │
│    • pin-input-0..5 → 888881                                              │
│    • confirm-pin-input-0..5 (if present)                                │
│    • Click Create/Next                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 6. ASSERTION                                                             │
│    • Wait for //h1[contains(text(),'successfully')]                      │
│    • Log success                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Selectors

| Element | Selector | Notes |
|---------|----------|-------|
| Glyph iframe | `#safle-react-widget-iframe` | Widget container |
| Email input | `#email-input` | |
| Password input | `#password-input` | |
| Confirm password | `#confirm-password-input` | |
| Terms checkbox | `button[role='checkbox'][aria-checked='false']` | Optional |
| Sign Up button | `//button[normalize-space()='Sign Up']` | Fallback: `_buttonPrimary` class |
| Mailinator Glyph row | `//td[contains(.,'support@glyph.network')]` | Or `showTheMessage` + glyph |
| OTP input fields | `#email-otp-0` … `#email-otp-5` | |
| PIN input fields | `#pin-input-0` … `#pin-input-5` | |
| Success heading | `//h1[contains(text(),'successfully')]` | |

---

## Tab Management

- **Main tab:** Glyph (unifiedid.glyph.network). Stays open for the entire run.
- **Mailinator tab:** Opened with `window.open(..., '_blank')`, used only for OTP extraction, then closed.
- After OTP extraction, the driver switches back to the main tab before continuing.

---

## Error Handling

- **Screenshots:** On failure, `screenshots/FAILURE_{timestamp}.png` (and `MAILINATOR_NO_OTP_*` when OTP not found).
- **CI soft-fail:** When `GITHUB_ACTIONS` is set and OTP is not received, the process exits with code 0 to keep the build green.
- **Window recovery:** Before taking a failure screenshot, the driver switches to a valid window to avoid "no such window" errors.

---

## Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `WAIT_SEC` | 60 | WebDriverWait timeout |
| `MAILINATOR_POLL_ATTEMPTS` | 12 | Max polls for Glyph email |
| `DEFAULT_PASSWORD` | Test@123 | Sign-up password |
| `DEFAULT_PIN` | 888881 | Security PIN |
| `BASE_URL` | https://unifiedid.glyph.network/ | Glyph base URL |
