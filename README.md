# VTOP++ Android

Native Android client for the VTOP portal. Status: **planning / not started**.

## Decided approach

Pure HTTP client (OkHttp + Jsoup), no WebView. The app talks to the
same endpoints the browser does (see `.github/docs/vtop-api.md`), with
the AutoCaptcha API (`auto-captcha` repo) fetched in the background to
log in.

Consequences we accept: brittle to VTOP renames (so fixtures are a
build-time artifact, see `docs/vtop-session.md`), and the reverse-
engineering spike is phase 0 - read `docs/vtop-session.md` first.
Pivot gate: if replicating the login chain overshoots ~2 weeks of
work, re-open the question of a WebView fallback once, then close it.

## Feature set (from the org roadmap)

1. Native login: user/pass + captcha image fetched and auto-solved,
   fingerprint-gated credential unlock (biometric-gated Keystore).
2. Offline cache: attendance, timetable, marks, exams in Room; sync
   via WorkManager; last-sync stamps.
3. Quick actions modeled on what the browser is used for, without the
   session-fragility of wrappers.

## Reading order

1. `docs/architecture.md` - module layout + data flow
2. `docs/vtop-session.md` - auth/session, error model, security rules
3. `docs/fixture-protocol.md` (org repo) - spike protocol to capture
   the VTOP request/response chain before any code

## Release signing

Release builds are signed only when signing credentials resolve; a
missing keystore still produces an unsigned-but-buildable release APK
with a Gradle warning, so contributors without the store can run
`assembleRelease`.

Credentials are read from this priority order — `local.properties`
first (already gitignored), then environment variables. **Never
commit the keystore file or the passwords.**

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

> **BACK UP THE KEYSTORE AND ITS PASSWORD OUTSIDE OF THIS MACHINE.**
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
