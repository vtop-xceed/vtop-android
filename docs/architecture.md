# Architecture

Single-module Kotlin app; split out later only if a module boundary
gets real. Standard Compose UI.

## Modules and packages

```
app/src/main/java/dev/vtoppp/vtop/
├── MainActivity.kt
├── di/            AppContainer: manual constructor injection
├── data/
│   ├── network/   VtopHttpClient, VtopSession, endpoint call sites
│   ├── parsers/   Jsoup HTML -> DTOs
│   ├── local/     Room entities/DAOs, DataStore prefs
│   └── repository Layer caches in memory, merges local/network
├── auth/          CredentialVault (Keystore), BiometricGate
├── captcha/       AutoCaptchaClient (/solve, /report)
├── sync/          WorkManager jobs (guard rails on Wi-Fi? no constraint)
└── ui/            Compose screens (dashboard, attendance, timetable,
                   marks, exams, settings)
```

## Data flow

```
UI screen
  -> repository.load(key)   # cache-first, returns stale immediately
  -> refresh()              # VtopSession -> endpoint -> Jsoup -> DTO
  -> Room upsert
  -> UI recompose
```

- Every network call goes through `VtopSession`, which owns cookies,
  CSRF rotation, and the re-login-on-expiry path in
  `docs/vtop-session.md`.
- Repositories expose `StateFlow<Result<T, VtopError>>`; no silent
  fallback to mock data.

## Local storage policy

- Room database: attendance / timetable / marks / exam data only.
  Registered as excluded from Android Auto Backup in
  `dataExtractionRules.xml` because it carries student PII.
- Credentials: NEVER in Room. Only in `CredentialVault`
  (EncryptedSharedPreferences behind an Android Keystore key marked
  `setUserAuthenticationRequired(true)`; biometric unlock via
  `BiometricPrompt` with a `CryptoObject` so the key enforces auth).
- DataStore (non-sensitive prefs only): chosen semester, sync toggles,
  AutoCaptcha endpoint override.

## Dependencies (last items to add, first to review)

- `androidx.compose` BOM, `androidx.navigation`
- `com.squareup.okhttp3:okhttp`, `org.jsoup:jsoup`
- `androidx.room` (+ KSP compiler)
- `androidx.datastore:datastore-preferences`
- `androidx.work:work-runtime-ktx`
- `androidx.biometric`
- Coroutines: `kotlinx.coroutines`

No Hilt / Koin until constructor injection hurts. No image loader;
captcha comes back as base64 -> Bitmap.

## Testing

- Parser tests in `src/test` run fixture HTML through Jsoup mapping
  (fixtures from `.github/docs/fixture-protocol.md`).
- `VtopError` mapping unit tests against raw response snippets.
- No mock-web-server against VTOP in CI; live auth is manual.
