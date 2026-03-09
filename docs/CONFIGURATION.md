# Configuration

This document describes all configuration options for the Glyph automation.

---

## System Properties

Pass via `-Dproperty=value` when running Maven or the JAR.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `headless` | boolean | `true` | Run Chrome in headless mode. Set to `false` for visible browser. |
| `proxy` | string | — | HTTP proxy URL. Use comma-separated list for random pick per run. |
| `waitForCaptcha` | integer | `0` | Seconds to wait before clicking Sign Up, allowing manual reCAPTCHA solve. |

### Examples

```bash
# Visible browser
mvn exec:java -Dheadless=false

# Single proxy
mvn exec:java -Dproxy=http://proxy.example.com:8080

# Multiple proxies (one chosen at random per run)
mvn exec:java -Dproxy="http://p1:8080,http://p2:8080"

# 90 seconds to solve reCAPTCHA manually
mvn exec:java -Dheadless=false -DwaitForCaptcha=90

# Combined
mvn exec:java -Dheadless=false -DwaitForCaptcha=60 -Dproxy=http://proxy:8080
```

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `PROXY` | Same as `-Dproxy`. Comma-separated for multiple proxies. |
| `GITHUB_ACTIONS` | Set by GitHub Actions. When present, OTP failure causes soft-fail (exit 0). |

### Example

```bash
export PROXY="http://proxy1:8080,http://proxy2:8080"
mvn exec:java
```

---

## Proxy Format

- **With scheme:** `http://host:port` or `https://host:port`
- **Without scheme:** `host:port` → treated as `http://host:port`
- **Multiple:** Comma-separated; one is chosen at random per run

---

## exec-maven-plugin

The `exec-maven-plugin` is configured to:

- **Main class:** `org.GlyphSanityTest`
- **System property passthrough:** `headless` is passed from `-Dheadless` (default `true`)

Other properties (`proxy`, `waitForCaptcha`) are read directly by the application via `System.getProperty()`.

---

## Fat JAR

When building with `mvn clean package`, the shaded JAR includes all dependencies. Pass system properties when running:

```bash
java -Dheadless=false -DwaitForCaptcha=60 -jar target/GlyphAutomation-1.0-SNAPSHOT.jar
```
