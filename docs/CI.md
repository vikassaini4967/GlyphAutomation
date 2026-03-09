# CI/CD

This document describes the continuous integration setup for the Glyph automation project.

---

## GitHub Actions Workflow

**File:** `.github/workflows/daily_run.yml`

### Triggers

| Trigger | Description |
|---------|-------------|
| `push` | On push to `main` |
| `pull_request` | On PR targeting `main` |
| `schedule` | Daily at 10:00 AM IST (`30 4 * * *` UTC) |
| `workflow_dispatch` | Manual run from GitHub Actions UI |

### Jobs

1. **Checkout** — Clone repository
2. **Set up JDK 17** — Temurin, Maven cache
3. **Set up Chrome** — `browser-actions/setup-chrome`
4. **Create screenshots directory** — `mkdir -p screenshots`
5. **Run test** — `mvn -B -e clean compile exec:java`
6. **Upload screenshots** — Artifact `sanity-screenshots` (always, ignore if empty)

---

## Soft-Fail Behavior

When the environment variable `GITHUB_ACTIONS` is set:

- If OTP is **not** received after max Mailinator attempts, the process exits with code **0** (success).
- This prevents CI from failing due to transient email delivery issues.
- Log message: `⚠️ OTP not received in CI after max attempts. Skipping OTP submission (CI soft-fail).`

---

## Artifacts

On every run (success or failure), the workflow uploads the `screenshots/` directory as an artifact named `sanity-screenshots`. Download from the Actions run summary to inspect failure screenshots.

---

## Local CI Simulation

To simulate CI behavior locally:

```bash
export GITHUB_ACTIONS=true
mvn clean compile exec:java
```

---

## Adding Secrets

For proxy or other secrets in CI:

1. Add secret in repo: **Settings → Secrets and variables → Actions**
2. Reference in workflow: `${{ secrets.PROXY_URL }}`
3. Pass to Maven: `mvn exec:java -Dproxy=${{ secrets.PROXY_URL }}`

Example step:

```yaml
- name: Run with proxy
  run: mvn -B -e clean compile exec:java -Dproxy=${{ secrets.PROXY_URL }}
  if: secrets.PROXY_URL != ''
```
