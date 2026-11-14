# CustomTabsRouter

A minimal, headless Android Default Browser router and standalone Chrome blocker written in Kotlin.

## Overview & Motivation

Android's built-in `WebView` fails modern OAuth / Google Advanced Protection / Passkey (WebAuthn) checks, whereas full `com.android.chrome` provides an unrestricted omnibox/address bar and tabbed browser UI.

`CustomTabsRouter` solves both problems:
1. **Default Browser Link Router ([`LinkRouterActivity`](app/src/main/java/com/nlevidev/customtabsrouter/LinkRouterActivity.kt)):** Registers as the system's Default Browser (`ACTION_VIEW` for `http`/`https`) with `Theme.NoDisplay` and no launcher icon. Incoming links are checked against a centralized domain blocklist and immediately opened inside a Chrome Custom Tab (`org.chromium.chrome.browser.customtabs.CustomTabActivity`) bound explicitly to `com.android.chrome`.
2. **Domain Blocklist & Escape-Hatch Prevention ([`CustomTabHelper`](app/src/main/java/com/nlevidev/customtabsrouter/CustomTabHelper.kt)):**
   - Blocks distracting domains (`reddit.com`, `twitter.com`, `x.com`, `facebook.com`, `instagram.com`, `tiktok.com`, `youtube.com`, `youtu.be`, `ycombinator.com`, `news.google.com`), subdomains, and redirect wrappers (`?q=`, `?url=`, `?u=`) before launching Chrome.
   - Calls `customTabsIntent.intent.setPackage("com.android.chrome")` to avoid infinite intent resolution loops when set as the default browser.
   - Keeps the Custom Tab URL bar visible (`setUrlBarHidingEnabled(false)`), disables the share menu (`SHARE_STATE_OFF`), and hides the "Open in Chrome browser" menu item (`OPEN_IN_BROWSER_STATE_OFF`) via `androidx.browser:browser:1.10.0`.
3. **Standalone Chrome & In-Tab Domain Blocker ([`ChromeBlockerService`](app/src/main/java/com/nlevidev/customtabsrouter/ChromeBlockerService.kt)):**
   - On Android 14/15+ (non-root), `adb shell` cannot disable individual activities (`com.google.android.apps.chrome.Main`) inside `com.android.chrome` without disabling the entire Chrome package (which would break Custom Tabs and installed WebAPKs).
   - `ChromeBlockerService` is a lightweight `AccessibilityService` scoped exclusively to `com.android.chrome` that watches for:
     - **Standalone Chrome activities** (`com.google.android.apps.chrome.Main`, `ChromeTabbedActivity`, `ChromeTabbedActivity2`, `IncognitoTabLauncher`, `SearchActivity`) or standalone tabbed Chrome UI elements (`tab_switcher_button`, `home_button`, `search_box_text`).
     - **Blocked domains inside a Custom Tab** (`com.android.chrome:id/url_bar`), preventing in-tab navigation to `youtube.com`, `reddit.com`, etc. via Google Search results or internal page links.
   - Whenever standalone Chrome is opened or a Custom Tab navigates to a blocked domain, `ChromeBlockerService` immediately pops back (`GLOBAL_ACTION_BACK`) and returns to the home screen (`GLOBAL_ACTION_HOME`), while leaving allowed `CustomTabActivity` pages and `SameTaskWebApkActivity` (installed Chrome WebAPKs like Antigravity) untouched.
4. **Optional Antigravity Shortcut ([`AntigravityLauncherActivity`](app/src/main/java/com/nlevidev/customtabsrouter/AntigravityLauncherActivity.kt)):**
   - Included in the codebase for launching `https://antigravity.google.com/` in a Custom Tab, but set to `android:enabled="false"` in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) when the Chrome WebAPK for Antigravity is already installed on the device.

---

## Project Structure

- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — Declares package visibility `<queries>` for `com.android.chrome`, `LinkRouterActivity`, `AntigravityLauncherActivity` (disabled by default), and `ChromeBlockerService`.
- [`app/src/main/java/com/nlevidev/customtabsrouter/CustomTabHelper.kt`](app/src/main/java/com/nlevidev/customtabsrouter/CustomTabHelper.kt) — Domain blocklist and `CustomTabsIntent` builder targeting `com.android.chrome`.
- [`app/src/main/java/com/nlevidev/customtabsrouter/LinkRouterActivity.kt`](app/src/main/java/com/nlevidev/customtabsrouter/LinkRouterActivity.kt) — Invisible `Theme.NoDisplay` activity handling `http`/`https` intents.
- [`app/src/main/java/com/nlevidev/customtabsrouter/ChromeBlockerService.kt`](app/src/main/java/com/nlevidev/customtabsrouter/ChromeBlockerService.kt) — Accessibility service that blocks standalone tabbed Chrome while permitting Custom Tabs and WebAPKs.
- [`app/src/main/java/com/nlevidev/customtabsrouter/AntigravityLauncherActivity.kt`](app/src/main/java/com/nlevidev/customtabsrouter/AntigravityLauncherActivity.kt) — Optional homescreen shortcut activity for Antigravity.
- [`app/src/main/res/xml/accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml) — Accessibility configuration scoped to `com.android.chrome`.

---

## Building & Installing on a New Device (via ADB)

### 1. Build the Debug APK

```bash
cd CustomTabsRouter
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Install the APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Ensure `com.android.chrome` is Enabled

Chrome must remain enabled at the package level so its `CustomTabsService`, `CustomTabActivity`, WebAuthn/passkey credentials, and installed WebAPKs function:

```bash
adb shell pm enable com.android.chrome
```

### 4. Set `CustomTabsRouter` as the System Default Browser

```bash
adb shell cmd role add-role-holder android.app.role.BROWSER com.nlevidev.customtabsrouter
# Verify:
adb shell cmd role get-role-holders android.app.role.BROWSER
```

### 5. Enable `ChromeBlockerService` (Accessibility Service)

```bash
adb shell settings put secure enabled_accessibility_services com.nlevidev.customtabsrouter/com.nlevidev.customtabsrouter.ChromeBlockerService
adb shell settings put secure accessibility_enabled 1
```

*(Note: If you already have other accessibility services enabled on the device, append `:com.nlevidev.customtabsrouter/com.nlevidev.customtabsrouter.ChromeBlockerService` to the existing value of `adb shell settings get secure enabled_accessibility_services`.)*
