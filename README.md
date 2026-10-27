# VTOP++ Android

Native Android client for the VTOP portal (VIT Bhopal). Pure HTTP client
(OkHttp + Jsoup) — no WebView shown to the user — with Material 3
**Expressive** UI (motion scheme, expressive loading indicators, Material
Symbol icons) and a custom launcher icon.

Status: **v0.1** — native login chain works end-to-end (see
[Releases](https://github.com/vtop-xceed/vtop-android/releases)).

## What it does

- **Auto-login with captcha solving**: credentials are stored in a
  biometric-gated Keystore vault; the captcha is fetched in the background
  and solved automatically (the [auto-captcha][auto-captcha] API) so sign-in
  is one tap.
- **Warm loop**: a foreground session attempt warms the whole login ladder
  (fetch page → captcha → solve → paced POST) before you touch anything.
  Offline boots give up cleanly after bounded retries and surface an
  explicit "No connection" card with *Try again* / *Edit credentials* —
  no infinite spinner dead-ends.
- **Session snapshot**: a persisted, encrypted session snapshot seeds the
  next app start, so a healthy boot goes from splash to live session in
  seconds.
- **Sections**: attendance, timetable, marks and friends are parsed natively
  (`docs/section-map.md`) via a hidden WebView DOM/JS host that replays the
  portal's own section fragments — the user never sees a browser.

[auto-captcha]: https://github.com/vtop-xceed/auto-captcha

## Reading order

1. `docs/architecture.md` — module layout + data flow
2. `docs/vtop-session.md` — auth/session, error model, security rules
3. `docs/section-map.md` — native section parsing map

The full login/section request chain lives in the org repo's
`.github/docs/vtop-api.md`.

## Building

```sh
./gradlew :app:assembleDebug          # debug install
./gradlew :app:assembleRelease        # signed when credentials resolve
./gradlew testDebugUnitTest           # full JVM test suite
```

Install on a device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release signing

Release builds are signed only when signing credentials resolve; a missing
keystore still produces an unsigned-but-buildable release APK with a Gradle
warning, so contributors without the store can run `assembleRelease`.

Credentials are read from this priority order — `local.properties` first
(already gitignored), then environment variables. **Never commit the
keystore file or the passwords.**

### Configuration keys

| Purpose           | local.properties key          | Env var               |
|-------------------|-------------------------------|-----------------------|
| Store file path   | `vtop.signing.storeFile`      | `VTOP_STORE_FILE`     |
| Store password    | `vtop.signing.storePassword`  | `VTOP_STORE_PASSWORD` |
| Key alias         | `vtop.signing.keyAlias`       | `VTOP_KEY_ALIAS`      |
| Key password      | `vtop.signing.keyPassword`    | `VTOP_KEY_PASSWORD`   |

Notes:

- `vtop.signing.keyPassword` / `VTOP_KEY_PASSWORD` are optional; when
  omitted the store password is reused.
- When neither `vtop.signing.storeFile` nor `VTOP_STORE_FILE` is set,
  the store path defaults to `local/keystores/vtop-release.keystore`
  (resolved relative to the root project directory).
- The release build type is signed only when all required credentials
  resolve **and** the store file exists on disk; otherwise the APK is
  left unsigned.

### Generating a release keystore

```sh
mkdir -p local/keystores
STOREPASS=$(openssl rand -hex 8)
keytool -genkeypair -v \
  -keystore local/keystores/vtop-release.keystore \
  -alias vtop -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STOREPASS" -keypass "$STOREPASS" \
  -dname "CN=VTOP Android, OU=VTOP, O=VTOP, C=IN"
```

Then append the resulting values to `local.properties`:

```properties
vtop.signing.storeFile=local/keystores/vtop-release.keystore
vtop.signing.storePassword=<generated-password>
vtop.signing.keyAlias=vtop
```

### Backup warning

> **BACK UP THE KEYSTORE AND ITS PASSWORD OUT OF THIS MACHINE.**
> Losing `local/keystores/vtop-release.keystore` (or its password) means
> losing the app's release signing identity: existing installs can never
> be updated by a build signed with a new keystore, and the package as
> published under that identity is dead. Since the keystore lives under
> `local/` (gitignored), it is not recoverable from git — copy the file
> and the password to a password manager / encrypted off-machine
> storage immediately after generating them.

### Build output

`./gradlew :app:assembleRelease` produces
`app/build/outputs/apk/release/app-release.apk` (signed when
credentials are configured, unsigned + warning otherwise).

## Auto release on version bump

`.github/workflows/release.yml` watches pushes to `main` (and manual
`workflow_dispatch`). On every push it:

1. Reads `versionName` from `app/build.gradle.kts`.
2. Skips if the tag `v<versionName>` already exists.
3. Builds the release APK.
4. Signs it: with the **project release keystore** when the optional CI
   secrets are present, otherwise with an **ephemeral CI key** so the APK
   still installs (note: APKs signed with the ephemeral key cannot upgrade
   installs from other signatures — supply the secrets for real servicing
   releases).
5. Creates the GitHub release `v<versionName>` with auto-generated notes
   and uploads the APK.

**To release: bump `versionName` (and `versionCode`) in
`app/build.gradle.kts`, merge to `main`, done.** The first version-bump
push (this release, `v0.1`) proves the whole path.

### CI signing secrets (optional, for upgradeable releases)

| Secret               | Contents                                              |
|----------------------|-------------------------------------------------------|
| `VTOP_STORE_FILE_B64`| base64 of the release keystore file                   |
| `VTOP_STORE_PASSWORD`| keystore/store password                               |
| `VTOP_KEY_ALIAS`     | key alias (e.g. `vtop`)                               |
| `VTOP_KEY_PASSWORD`  | optional individual key password                      |

```sh
base64 -w0 local/keystores/vtop-release.keystore   # paste into VTOP_STORE_FILE_B64
```

## Security rules

- Credentials and session cookies never leave the device; the credential vault
  is always encrypted at rest (`EncryptedSharedPreferences` with a Keystore
  master key) and, on Class 3 biometric devices, additionally sealed behind a
  per-use biometric/device-credential prompt.
- Logs (`AppLog`) never contain passwords or session cookies.
