# TapTop

**One tap. Back to the top.**

Tap your chosen area to scroll to the top. Touch the screen to stop.

Version **0.5.1** · Android **8.0+**

[<img src="docs/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522com.taptop.app%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252Ftitandrive%252Ftaptop%2522%252C%2522author%2522%253A%2522TitanDrive%2522%252C%2522name%2522%253A%2522TapTop%2522%257D)
[<img src="docs/assets/badge_github.png" alt="Get APK from GitHub" height="80">](https://github.com/titandrive/taptop/releases/latest)

## Get started

1. Install and open TapTop.
2. Open **Accessibility settings** and enable TapTop’s service.
3. Turn TapTop on, then tap the tap zone while using another app.

## Make it yours

- **Five speeds:** Slowest, Slow, Medium, Fast, Fastest.
- **Adjustable tap zone:** change its width, height, position, and top offset.
- **Optional visible bar:** choose a color and opacity, or hide it. The hidden area still works; a dotted outline helps you adjust it inside TapTop.
- **Light and dark themes:** Catppuccin Latte and Macchiato. Tap the sun/moon button to switch; hold it to follow the system.
- **Optional haptics:** feedback when tapping the area or the enable/disable button.

## App filtering

- **All:** TapTop works in every app.
- **Blacklist:** TapTop works everywhere except the apps you select.
- **Whitelist:** TapTop works only in the apps you select. An empty list disables it everywhere.

Tap **Choose blocked apps** or **Choose allowed apps** to edit the list. Search by app name; selected apps appear in their own section at the top. **Select all** selects every app, including those hidden by search. **Clear** deselects everything. Tap **Done** to save or **Cancel** to discard changes.

Both lists are saved separately. Excluded apps have no tap zone and cannot be scrolled by shortcuts or automation. Newly installed apps appear when you return to TapTop; select them if you want to add them to a list.

## Shortcuts

- **Quick Settings:** add the TapTop tile to toggle it on or off.
- **App shortcuts:** long-press the app icon for **Toggle TapTop** and **Scroll to top**. Compatible launchers and gesture apps can use them too.
- **Automation:** trigger either action from Tasker or MacroDroid.

The app’s **ⓘ button** includes a usage guide and copyable automation fields.

<details>
<summary>Tasker / MacroDroid setup</summary>

Use **Send Intent**. Set the target to **Broadcast Receiver** in Tasker or **Broadcast** in MacroDroid.

| Field | Value |
| --- | --- |
| Toggle action | `com.taptop.app.action.TOGGLE` |
| Scroll action | `com.taptop.app.action.SCROLL_TO_TOP` |
| Package | `com.taptop.app` |
| Class | `com.taptop.app.AutomationReceiver` |

Choose one action and leave the remaining fields empty. Scrolling requires TapTop and its accessibility service to be enabled.

</details>

## Privacy & compatibility

TapTop uses accessibility access to scroll the current app. It has no internet permission and does not save screen content. Scrolling behavior depends on the app; some apps may not respond.

<details>
<summary>Build from source</summary>

Use Android Studio, or JDK 17 with Android SDK 34:

```sh
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

For signed releases, see [release build instructions](docs/RELEASING.md).

</details>

## AI Disclaimer

TapTop was vibecoded using Codex. Use at your own discretion, and [report issues](https://github.com/titandrive/taptop/issues) when something breaks.

## License

[MIT](LICENSE) © 2026 TitanDrive. See [third-party notices](THIRD_PARTY_NOTICES.md) for included assets.
