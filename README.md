# TipTop

A small Android accessibility utility for returning to the start of a list. Enable its accessibility service, then tap the blue bar near the top of another app. The scroll speed and the bar's size, position, distance from the screen top, and opacity can be adjusted in TipTop. Scroll speed ranges from Slowest to Maximum; Maximum is the default and preserves the previously tested behavior. Lower settings use a continuous held drag at 180, 360, 720, or 1440 dp per second instead of slowing down by spacing native requests apart. The drag switches between briefly overlapping pointers to reset its screen position without releasing into a fling. App compatibility and perceived smoothness still need device validation. Changes apply on the next bar tap. The last-resort fling uses a fixed 90 ms gesture. The tap area’s size and placement work independently of its visibility. Show tap bar hides only the visual marker; the invisible area still responds to taps. Enable TipTop is the master switch that removes the tap area and stops further scrolling requests.

At Maximum speed, lists that support AndroidX granular accessibility scrolling, or platform granular scrolling on Android 15 and newer, receive a single request to scroll to the beginning. Compatible RecyclerViews handle this with their native smooth animation; its speed is set by the target app, and touching the list can interrupt it. At Maximum, lists without that support use their scroll-to-position action, which may jump to the top. Lower speeds use the held-drag path when backward scrolling is available; lists exposing only a position action still jump to the top. Other lists use backward-scroll actions synchronized to display frames, capped at roughly 60 requests per second, renewing the app's animation before it settles. Generic semantic views exposed as `android.view.View` receive requests no faster than roughly every 100 ms so each animation can advance before another request replaces it. RecyclerView retains the faster cadence. Missed frames are skipped rather than followed by a burst of catch-up requests. This avoids injecting finger-down events between swipes. The controller retains the original list, updates its metadata from scroll events instead of forcing periodic node refreshes, and stops when the list becomes invisible or the backward action is unavailable or rejected. It also stops after 1.5 seconds without scroll events or 60 seconds overall. During Maximum native scrolling, touching the screen stops further requests; this first touch is consumed, and the app's current animation may finish. Lower speeds leave the list touchable. The bar watches for physical touches outside its window and immediately cancels the drag; touching the bar also cancels immediately. TipTop ignores its own injected movement, suppresses the cancellation contact on its bar, and prevents late gesture callbacks from restarting scrolling. Automatic stopping releases a held pointer after the current segment (at most 800 ms), with a short stationary hold to avoid a fling. If the initial native request fails, the optional fling fallback sends one gesture and may stop short of the top on long lists. Native animation behavior still depends on the target app; sustained motion and perceived smoothness need device validation. An optional haptic click confirms the bar tap; the Haptic feedback toggle is enabled by default. Apps that do not expose scroll actions may not respond. The app has no internet permission and does not store screen content.

## Appearance

Settings use the [Catppuccin palette](https://catppuccin.com/palette/): Latte for light mode and Macchiato for dark mode. Choose System, Light, or Dark; System follows the phone’s appearance. Grouped controls include a fixed, solid tap-bar color example, persistent theme and haptic preferences, and a matching launcher icon. While TipTop is in the foreground and the bar is hidden, a dotted outline in the theme highlight marks the actual tap area. Show tap bar replaces the outline with the selected color fill. Leaving TipTop removes the outline and follows the saved visibility setting. The color example keeps a fixed size and position at full opacity, even when the actual bar is hidden. The geometry controls remain available; the color and opacity controls collapse. The color picker above opacity uses an Android-style swatch palette. Tapping a color applies it immediately; Default restores the automatic theme highlight. Reset all restores width, height, offset, and opacity without changing visibility or position. Existing scrolling and bar preferences are preserved.

## Quick Settings

Add the **TipTop** tile using the Quick Settings edit screen. It toggles the same Enable TipTop setting as the app and reflects changes made in either place. Turning it off removes the tap area and stops further scrolling requests; turning it back on preserves size, position, visibility, and color. Accessibility access must already be enabled for scrolling to work.

## Gesture shortcuts

**Toggle TipTop** and **Scroll to top** are available as Android app shortcuts, including launchers’ app-icon menus and gesture shortcut pickers. Legacy Create shortcut pickers are also supported. Toggle mirrors the master switch and Quick Settings tile. Scroll to top acts on the current app without opening TipTop, using the existing speed and scrolling behavior; TipTop and its accessibility service must be enabled. Choosing a shortcut in a picker only configures it, without executing it.

## Tasker / MacroDroid intents

Tap the info button at the top of TipTop for setup instructions and copyable intent fields.

Use **Send Intent**, with target **Broadcast Receiver** (Tasker) or **Broadcast** (MacroDroid):

| Field | Value |
| --- | --- |
| Action: toggle on/off | `com.tiptop.app.action.TOGGLE` |
| Action: scroll to top | `com.tiptop.app.action.SCROLL_TO_TOP` |
| Package | `com.tiptop.app` |
| Class | `com.tiptop.app.AutomationReceiver` |

Choose one action per intent. Leave data, MIME type, categories, and extras empty. These explicit broadcasts work without opening TipTop. Toggle changes the same master setting; scrolling respects that setting and requires accessibility access. The receiver accepts only these two actions.

For example:

```sh
adb shell am broadcast -a com.tiptop.app.action.TOGGLE -n com.tiptop.app/.AutomationReceiver
adb shell am broadcast -a com.tiptop.app.action.SCROLL_TO_TOP -n com.tiptop.app/.AutomationReceiver
```

## Build

Open this folder in Android Studio and build the `app` module. Android SDK 34 and JDK 17 are required. The project uses Android Gradle Plugin 8.4.2. The minimum supported Android version is 8.0 (API 26).

## Use

1. Install and open TipTop.
2. Select **Open accessibility settings**, find TipTop, and enable the service.
3. Open a list in another app and tap the blue bar.

At zero offset TipTop positions its overlay window at the physical screen edge. Android may still intercept taps or obscure part of the bar in the status bar or camera cutout on some devices.
