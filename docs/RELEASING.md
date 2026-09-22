# Release builds

Public APKs use a dedicated release key. Debug builds use Android’s debug key.

## Signing

The maintainer’s signing keystore is stored in `.signing/` and configured by `keystore.properties`. Both are ignored by Git. Back up both securely: future updates must use the same key.

For a separate build, create your own signing key and a root `keystore.properties` file:

```properties
storeFile=/path/to/release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Without this file, Gradle produces an unsigned release APK. Never publish that APK or commit signing secrets.

## Build and publish

1. Update `versionName` and increase `versionCode` in `app/build.gradle`.
2. Run `./gradlew assembleRelease testDebugUnitTest lintRelease`.
3. Verify `app/build/outputs/apk/release/app-release.apk` with Android SDK `apksigner verify`.
4. Attach the verified APK as **TapTop.apk** to the version’s GitHub release. The README download button points to the latest release page.

A release APK cannot update an existing debug installation because their signatures differ. Switching from a debug build requires uninstalling it first, which clears its settings. Normal release-to-release updates preserve settings.

Version 0.6.0 changes the application ID to `com.taptop.app`. It installs separately from 0.5.x and does not migrate settings; the normal update behavior above applies to releases sharing the same application ID.
