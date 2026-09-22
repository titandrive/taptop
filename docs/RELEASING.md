# Release builds

Public APKs use a dedicated release key. Debug builds use Android’s debug key.

## Signing

The maintainer’s local signing files are `.signing/tiptop-release.jks` and `keystore.properties`. Both are ignored by Git. Back up both securely: future updates must use the same key.

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
4. Attach the verified APK as **TipTop.apk** to the version’s GitHub release. Keep this filename stable for the README download link.

A release APK cannot update an existing debug installation because their signatures differ. Switching from a debug build requires uninstalling it first, which clears its settings. Normal release-to-release updates preserve settings.
