# Releases — how Khatiyan ships

## Pipeline

Pushing a tag like `v1.0.0` triggers `.github/workflows/release.yml`, which:

1. Runs the full unit-test suite (`testDebugUnitTest` — JVM math tests + Robolectric Room tests).
2. Builds `assembleRelease` **and** `assembleDebug`.
3. Verifies both APKs with `aapt2 dump badging` (package id, label, version, min/target SDK).
4. Computes SHA-256 checksums and publishes a GitHub Release with:
   - `khatiyan-release-<tag>.apk`
   - `khatiyan-debug-<tag>.apk`
   - `SHA256SUMS.txt`

Every push to `main` and every PR runs the same build/test job as a normal CI check
(`.github/workflows/ci.yml`) and uploads the APKs as workflow artifacts.

## Signing policy (important)

Release signing in `app/build.gradle.kts` is **conditional by design**:

- If a `keystore.properties` file exists at the repository root **on the build machine**, its
  `storeFile / storePassword / keyAlias / keyPassword` are used for the release signing config.
- In GitHub Actions, the workflow creates that file from two repository secrets before invoking
  Gradle:
  - `KHATIYAN_KEYSTORE_BASE64` — the base64-encoded `.jks` keystore.
  - `KHATIYAN_KEYSTORE_PROPERTIES` — the properties file content; use the placeholder
    `storeFile=__KEYSTORE_PATH__` and the workflow replaces it with the decoded keystore path.
- **If those secrets are not configured, the release variant falls back to the debug signing
  config** so CI always produces an *installable* artifact for testing — never a broken or
  unsigned-junk build. Such an APK is debug-signed: it is functionally identical (same code, same
  release build type, minify off in v1.0.0) but **must not** be treated as the final production
  signing identity, and updating an install from a debug-signed build to a production-signed build
  requires an uninstall first.

This project never stores private keys in git. `keystore.properties` and `*.jks`/`*.keystore`
are git-ignored.

### Generating the production keystore (once)

```bash
keytool -genkeypair -v -keystore khatiyan-release.jks -alias khatiyan \
        -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 khatiyan-release.jks   # → repository secret KHATIYAN_KEYSTORE_BASE64
```

`KHATIYAN_KEYSTORE_PROPERTIES` example:

```properties
storeFile=__KEYSTORE_PATH__
storePassword=<password>
keyAlias=khatiyan
keyPassword=<password>
```

## Versioning

Semver `MAJOR.MINOR.PATCH`; `versionName` and the git tag must match (`v1.2.3` ↔ `1.2.3`).
`versionCode` is monotonic and stored in `app/build.gradle.kts`; every F-Droid/Play upload needs
a strictly larger `versionCode`.

## v1.0.0 notes

- Release build is **not minified** yet (`isMinifyEnabled = false`) — R8 keep rules for the
  kotlinx-serialization backup path need instrumented verification; intentionally deferred.
- APK requires Android 8.0+ (API 26), targets API 35.
- The manifest declares no `INTERNET` permission — a quick way to re-verify privacy on any build:
  `aapt2 dump badging app-release.apk | grep uses-permission`

## Verifying a download

```bash
sha256sum khatiyan-release-v1.0.0.apk      # must match SHA256SUMS.txt in the release
```

### v1.0.1

UI/UX + Bengali copy refinement on top of v1.0.0 — no schema or logic changes (versionCode 2). Release pipeline unchanged; debug-sign fallback applies unless `KHATIYAN_KEYSTORE_BASE64` is configured.
