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
   - Whenever standalone Chrome is opened or a Custom Tab navigates to a blocked domain, `ChromeBlockerService` immediately pops back (`GLOBAL_ACTION_BACK`), returns to the home screen (`GLOBAL_ACTION_HOME`), and displays a 3-second accessibility overlay toast explaining why it closed (`"Blocked domain: <domain>"` or `"Closed: Standalone Chrome is disabled"`), while leaving allowed `CustomTabActivity` pages and `SameTaskWebApkActivity` (installed Chrome WebAPKs like Antigravity) untouched.
4. **Optional Antigravity Shortcut ([`AntigravityLauncherActivity`](app/src/main/java/com/nlevidev/customtabsrouter/AntigravityLauncherActivity.kt)):**
   - Included in the codebase for launching `https://antigravity.google.com/` in a Custom Tab, but set to `android:enabled="false"` in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) when the Chrome WebAPK for Antigravity is already installed on the device.

---

## Project Structure

- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — Declares package visibility `<queries>` for `com.android.chrome`, `LinkRouterActivity`, `AntigravityLauncherActivity` (disabled by default), and `ChromeBlockerService`.
- [`app/src/main/java/com/nlevidev/customtabsrouter/CustomTabHelper.kt`](app/src/main/java/com/nlevidev/customtabsrouter/CustomTabHelper.kt) — Domain blocklist and `CustomTabsIntent` builder targeting `com.android.chrome`.
- [`app/src/main/java/com/nlevidev/customtabsrouter/LinkRouterActivity.kt`](app/src/main/java/com/nlevidev/customtabsrouter/LinkRouterActivity.kt) — Invisible `Theme.NoDisplay` activity handling `http`/`https` intents.
- [`app/src/main/java/com/nlevidev/customtabsrouter/ChromeBlockerService.kt`](app/src/main/java/com/nlevidev/customtabsrouter/ChromeBlockerService.kt) — Accessibility service that blocks standalone tabbed Chrome and blocked in-tab domains while permitting Custom Tabs and WebAPKs.
- [`app/src/main/java/com/nlevidev/customtabsrouter/AntigravityLauncherActivity.kt`](app/src/main/java/com/nlevidev/customtabsrouter/AntigravityLauncherActivity.kt) — Optional homescreen shortcut activity for Antigravity.
- [`app/src/main/res/xml/accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml) — Accessibility configuration scoped to `com.android.chrome`.

---

## Building & Installing on Another Android Phone

### Prerequisites

1. **On the computer:**
   - JDK 17+ (`brew install openjdk@17` on macOS)
   - Android SDK (via Android Studio or `sdkmanager` with `platforms;android-36` and `build-tools;35.0.0+`)
   - `adb` (`brew install android-platform-tools` on macOS)
2. **On the target Android phone:**
   - **Google Chrome** (`com.android.chrome`) installed and signed in.
   - *(Optional)* Any Progressive Web Apps / WebAPKs you want on your homescreen (e.g., **Add to Home screen / Install app** from Chrome) should be installed **before** enabling `ChromeBlockerService` (or you can temporarily toggle `ChromeBlockerService` off while installing a new WebAPK).
   - **Developer Options & USB Debugging enabled:**
     1. Open **Settings → About phone** and tap **Build number** 7 times.
     2. Open **Settings → System → Developer options** and turn on **USB debugging**.
     3. Connect the phone via USB, run `adb devices`, and tap **Allow** on the phone's *"Allow USB debugging?"* prompt.

---

### Step 1: Clone & Configure Android SDK Path

```bash
git clone https://github.com/joebobfisher/CustomTabsRouter.git
cd CustomTabsRouter
```

Set `ANDROID_HOME` (or create `local.properties` pointing to your Android SDK):

```bash
# macOS default Android SDK path:
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

### Step 2: (Optional) Customize the Domain Blocklist

To add or remove blocked domains before building, edit `BLOCKED_HOSTS` in [`app/src/main/java/com/nlevidev/customtabsrouter/CustomTabHelper.kt`](app/src/main/java/com/nlevidev/customtabsrouter/CustomTabHelper.kt).

*(If you want the built-in `AntigravityLauncherActivity` homescreen icon instead of a Chrome WebAPK, change `android:enabled="false"` to `android:enabled="true"` in [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml).)*

### Step 3: Build the Debug APK

```bash
# If JDK 17 is installed via Homebrew on Apple Silicon macOS:
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Or if JAVA_HOME is already configured on your system:
./gradlew assembleDebug
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Step 4: Install the APK via ADB

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Ensure `com.android.chrome` is Enabled

Chrome must remain enabled at the package level so its `CustomTabsService`, `CustomTabActivity`, WebAuthn/passkey credentials, and installed WebAPKs function:

```bash
adb shell pm enable com.android.chrome
```

### Step 6: Set `CustomTabsRouter` as the System Default Browser

```bash
adb shell cmd role add-role-holder android.app.role.BROWSER com.nlevidev.customtabsrouter

# Verify it returns "com.nlevidev.customtabsrouter":
adb shell cmd role get-role-holders android.app.role.BROWSER
```

*(Alternatively on the phone: **Settings → Apps → Default apps → Browser app → CustomTabsRouter**.)*

### Step 7: Enable `ChromeBlockerService` (Accessibility Service)

#### Option A: Enable automatically via `adb` (Recommended — bypasses Android's Restricted Settings dialog)

```bash
# 1. Check if the phone already has other accessibility services enabled:
EXISTING=$(adb shell settings get secure enabled_accessibility_services)
SERVICE="com.nlevidev.customtabsrouter/com.nlevidev.customtabsrouter.ChromeBlockerService"

# 2. Enable ChromeBlockerService (preserving any existing services):
if [ -z "$EXISTING" ] || [ "$EXISTING" = "null" ]; then
  adb shell settings put secure enabled_accessibility_services "$SERVICE"
else
  adb shell settings put secure enabled_accessibility_services "${EXISTING}:${SERVICE}"
fi
adb shell settings put secure accessibility_enabled 1
```

#### Option B: Enable manually on the phone

If you ever toggle it on manually in **Settings → Accessibility → Downloaded apps → CustomTabsRouter** on Android 13+, Android may show a **"Restricted setting"** prompt because the APK was installed via ADB rather than Google Play. To unlock the toggle:
1. Open **Settings → Apps → See all apps → CustomTabsRouter**.
2. Tap the **three-dot menu (⋮)** in the top-right corner and tap **Allow restricted settings**.
3. Return to **Settings → Accessibility → CustomTabsRouter** and turn it on.
