# VTOP++ Android

![Views](https://visitor-badge.laobi.icu/badge?page_id=vtop-xceed.vtop-android&left_text=views)

Native Android client for the VTOP portal (VIT Bhopal). Pure OkHttp + Jsoup HTTP client (no WebView shown to the user) with a Material 3 Expressive UI. See [Releases](https://github.com/vtop-xceed/vtop-android/releases).

## What it does

- One-tap login: credentials sit in a biometric-gated Keystore vault; the captcha is fetched in the background and solved via the [auto-captcha][auto-captcha] API.
- Session snapshot: a persisted, encrypted snapshot seeds the next start; a healthy boot reaches a live session in seconds.
- Offline: bounded retries, then an explicit "No connection" card with *Try again* / *Edit credentials* — no infinite spinners.
- Sections parsed natively (attendance, timetable, marks, academic calendar) — see `docs/section-map.md`. The portal's own fragments are replayed through a hidden WebView host the user never sees.

[auto-captcha]: https://github.com/vtop-xceed/auto-captcha

## Docs

1. `docs/architecture.md` — module layout + data flow
2. `docs/vtop-session.md` — auth/session, error model, security rules
3. `docs/section-map.md` — native section parsing map

## Building

```sh
./gradlew :app:assembleDebug      # debug install; package is dev.vtoppp.vtop.debug
./gradlew :app:assembleRelease    # signed when credentials resolve
./gradlew testDebugUnitTest       # full JVM test suite
```

`adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Release signing

Signing reads `local.properties` first (gitignored), then environment variables. A missing keystore still yields an unsigned-but-buildable release APK with a Gradle warning.

| Purpose         | local.properties key         | Env var              |
|-----------------|------------------------------|----------------------|
| Store file path | `vtop.signing.storeFile`     | `VTOP_STORE_FILE`    |
| Store password  | `vtop.signing.storePassword` | `VTOP_STORE_PASSWORD`|
| Key alias       | `vtop.signing.keyAlias`      | `VTOP_KEY_ALIAS`     |
| Key password    | `vtop.signing.keyPassword`   | `VTOP_KEY_PASSWORD`  |

Key password is optional (falls back to store password). The store path defaults to `local/keystores/vtop-release.keystore`. Back up the keystore and its password off this machine — losing them permanently kills the app's release signing identity.

## Auto release on version bump

`.github/workflows/release.yml` watches pushes to `main` and `workflow_dispatch`. It skips if tag `v<versionName>` exists, builds the APK, signs it (project keystore when CI secrets are present, otherwise an ephemeral CI key — such APKs can't upgrade installs signed with the project key), then creates the `v<versionName>` GitHub release and uploads the APK.

**To release: bump `versionName` + `versionCode` in `app/build.gradle.kts`, merge to `main`, done.**

### CI signing secrets (optional)

| Secret                | Contents                              |
|-----------------------|---------------------------------------|
| `VTOP_STORE_FILE_B64` | base64 of the release keystore        |
| `VTOP_STORE_PASSWORD` | keystore/store password               |
| `VTOP_KEY_ALIAS`      | key alias (e.g. `vtop`)               |
| `VTOP_KEY_PASSWORD`   | optional individual key password       |

`base64 -w0 local/keystores/vtop-release.keystore` — paste into `VTOP_STORE_FILE_B64`.

## Security rules

- Credentials and session cookies never leave the device; the vault is encrypted at rest (`EncryptedSharedPreferences` + Keystore master key), and on Class 3 biometric devices sealed behind a per-use biometric/device-credential prompt.
- Logs (`AppLog`) never contain passwords or session cookies.
