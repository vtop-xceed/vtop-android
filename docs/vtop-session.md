# VTOP session + error model

How the app imitates the VTOP browser session and what it does when
each step breaks. **Verified live on 24 Oct 2026** (see
`/tmp/opencode/vtop/vtop_login.sh` for the reference implementation).

## Login chain (verified end-to-end)

All requests: browser UA (`Chrome/156` style), `cookiesession1` +
`JSESSIONID` cookies kept in one jar.

1. `GET /vtop/login` — splash (role picker). `_csrf` in 3 hidden
   inputs (`stdForm`, `empForm`, `parentForm`).
2. `POST /vtop/prelogin/setup` (`_csrf`, `flag=VTOP`) → `302` to
   `/vtop/init/page` → `302` to `/vtop/login` (student login form).
   The `_csrf` stays stable within the session.
3. Mimic browser asset loads (the server keys the captcha mode off
   this): `GET /vtop/get/bs/css/1`, `/get/ic/css/2`,
   `/assets/css/PreLogin.css`, `/get/jq/js/{1,3,4}`, `/get/bs/js/3`,
   plus one `GET https://www.google.com/recaptcha/api.js`.
4. `POST /vtop/login` with `_csrf`, `username`, `password` (URL
   encoded), empty `gResponse`, empty `captchaStr`.
   - **Success path skipped**: server always redirects (302) to
     `/vtop/login/error` — the page's Google reCAPTCHA is CSP-blocked
     so no browser can ever supply a token; round 1 is a dummy.
5. `GET /vtop/login/error` — rerender with `captchaType=1` and the
   image captcha **inline as a base64 data URI** (`200x40` JPEG) in
   `#captchaBlock`. Extract the data URI directly from HTML.
6. `POST catto… captop /solve` (AutoCaptcha server) with
   `{"image": "<b64>"}` returns `{"text": "..."}`.
7. `POST /vtop/login` again with the same `_csrf`, creds,
   `gResponse` empty and `captchaStr=<SOLUTION>` (uppercase; server
   compares case-insensitively in practice). Success → `302` to
   `/vtop/init/page` with a fresh `JSESSIONID`.
8. Follow: `GET /vtop/init/page` → 302 `/vtop/main/page` → 302
   `/vtop/open` → 302 `/vtop/content` — **the dashboard**.
   `/vtop/content` is 120KB+ HTML with `authorizedID=TESTUSER`,
   the full menu and the section endpoint map.
9. Failure paths: wrong captcha → `302` back to `/vtop/login/error`
   (fresh image, same `_csrf`). Hard errors land on `/vtop/login/error`
   variants of the template with `swal` boxes.

## Home console contract

- Dashboard wraps sections in `#page-wrapper`; nav is
  `<a data-url='<endpoint>' class='dropdown-item menuFontStyle systemBtnMenu'>`.
- Section fetch (whenever the user taps a menu item):
  `POST <data-url>` (relative) with body
  `verifyMenu=true&authorizedID=<id>&_csrf=<csrf>&nocache=<ms>` and
  `Content-Type: application/x-www-form-urlencoded`. The response HTML
  is the section page; if it contains `___INTERNAL___RESPONSE___`,
  redirect handling applies (HTTP 231 session-expired, 232/233 page
  targets) — see `assets/js/vit-custom.js ajaxCall`.
- Dashboard data fragments (all POST, same body shape):
  `get/dashboard/current/cgpa/credits`,
  `get/dashboard/current/semester/course/details`,
  `get/dashboard/last/five/payment/details`,
  `get/dashboard/proctor/message`, `get/last/five/feedbacks`,
  `get/scheduled/events`, `get/upcoming/digital/assignments`.
- Top-level menu map extracted from `content.html` is in
  `/tmp/opencode/vtop/content.html` and mirrors the sections:
  Profile + Credentials + Bank + Spot-light (Student), Academics
  (`academics/common/StudentTimeTable`, `StudentAttendance`,
  `StudentCoursePage`, `Curriculum`, …), COE (`examinations/…`),
  Hostel, Finance, Events, Research, Others.

## Session expiry

Data endpoints that return the login page HTML or a redirect to /
vtop/ mean an expired session. The app NEVER shows that HTML and does
not trust content extracted from stale states; `VtopHttp.refresh`
re-authenticates exactly once per user-visible action.

## Error type

Single sealed enum so the UI knows what to render:

```
VtopError
├── CAPTCHA_REJECTED     # retry captcha, up to a visible attempt cap
├── CSRF_ROTATED         # internal state only (re-read + retry once)
├── SESSION_EXPIRED      # re-login then re-run the callers action
├── RATE_LIMITED         # backoff, surface to user not hidden
└── UNKNOWN              # log sanitized body, show generic card
```

Retry rules:

- Captcha: max 3 attempts, exponential backoff, show "attempt N/3"
  to the user; on the 3rd failure offer manual entry and optionally
  `POST /report` auto-captcha failures via `AutoCaptchaClient`.
- CSRF/expiry: one internal retry, never more.
- Rate limit: 30s minimum wait, no silent retry loop.

## Login rate discipline (never break this)

- Login attempts capped at 1 per 10s; VTOP can lock accounts that
  hammer the login endpoint, and the captcha API is shared infra.
- Request characteristics (headers, redirects, cookie jars) only come
  from real browser captures, never invented.
- Never log credentials, cookies, or CSRF values; sanitize request
  bodies before anything reaches logcat.

## Credential vault + biometric

- `CredentialVault` writes ciphertext (AES-GCM/Keystore) into
  EncryptedSharedPreferences after a `BiometricPrompt` success and
  reads it only after `BiometricPrompt` with a `CryptoObject`.
- Fingerprint/face or device PIN counts - `BiometricManager`
  require `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`.
- `allowBackup="false"`; also exclude the vault file under
  `dataExtractionRules`, never risk a key mismatch + no plaintext
  fallback file.
- Saving creds is opt-in per device (default: never save).
