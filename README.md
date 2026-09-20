# TipTop

A small Android accessibility utility for returning to the start of a list. Enable its accessibility service, then tap the blue bar near the top of another app. The bar's size, position, distance from the screen top, and opacity can be adjusted in TipTop. The bar can also be turned off without disabling the service.

TipTop first requests the list's first position through Android's accessibility API. Where that action is unavailable, it requests repeated backward scrolls, stopping when the app declines or after 40 steps. Apps that do not expose scroll actions may not respond. The app has no internet permission and does not store screen content.

## Build

Open this folder in Android Studio and build the `app` module. Android SDK 34 and JDK 17 are required. The project uses Android Gradle Plugin 8.4.2. The minimum supported Android version is 8.0 (API 26).

## Use

1. Install and open TipTop.
2. Select **Open accessibility settings**, find TipTop, and enable the service.
3. Open a list in another app and tap the blue bar.

At zero offset TipTop positions its overlay window at the physical screen edge. Android may still intercept taps or obscure part of the bar in the status bar or camera cutout on some devices.
