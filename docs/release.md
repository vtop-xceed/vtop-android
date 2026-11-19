# Maintainer notes

Where the maintainer-only details live now that the README is user-facing.

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

(User-facing version is in the README. The implementation specifics.)

- Credentials and session cookies never leave the device; the vault is encrypted at rest (`EncryptedSharedPreferences` + Keystore master key), sealed behind a per-use biometric/device-credential prompt on Class 3 biometric devices.
- Logs (`AppLog`) never contain passwords or session cookies.
