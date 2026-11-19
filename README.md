# U Type — Offline Sinhala & English Android Keyboard

**U Type** (`com.udara.Utype`) is a native Kotlin Android keyboard (`InputMethodService`) built for fast Sinhala phonetic ("Singlish", Helakuru-style) and English typing, working **100% offline** with **zero `INTERNET` permission**, no ads, and no tracking.

## Features

- **Core Keyboard (Phase 1)**
  - Rounded-key dark emerald palette (`#0A110F` background, `#1E2B27` keys, `#3DD6A5` accent) + Light, System, Midnight Ocean, Amber Dusk, Pitch AMOLED, and Sakura Night themes.
  - 7-icon Tool Strip (Logo/Settings, Incognito Lock, Language Switch, Text Editor, Stylish Fonts, Clipboard, Voice Mic).
  - Top-right digit (`1–0`) and symbol hints with long-press popups, key preview popups, double-tap Caps Lock, hold-to-repeat Backspace, and adaptive tablet / one-handed layouts.
  - **Sinhala Phonetic ("Singlish") Engine** with editable lookup tables in `SinhalaMap.kt` (`ammaa` → `අම්මා`, `sixhala` → `සිංහල`, `shrii` → `ශ්‍රී`, `kya` → `ක්‍ය`, `kra` → `ක්‍ර`, `kru` → `කෘ`).
  - **Offline Word Suggestions & Text Shortcuts** with bundled EN/SI frequency wordlists, on-device learned words, auto-capitalization, and double-space period.
  - **Reliable Emoji Panel** powered by bundled `EmojiCompat` (`androidx.emoji2:emoji2-bundled`), 10 categories, offline emoji search, skin-tone long-press picker, and local Recents.
- **Power Tools (Phase 2)**
  - **Text Editor Panel**: D-pad arrows with hold-to-repeat, Select toggle mode, Home/End, Copy, Cut, Paste, Select All, Undo, Redo, Backspace, and Enter.
  - **Encrypted Clipboard Manager**: Hardware Android Keystore AES-256-GCM encryption in local Room DB, 2-hour auto-delete for unpinned items (configurable 30m–24h), Pin/Unpin with fresh countdown on unpin, max 50 items, and 15-minute `WorkManager` background cleanup.
  - **Stylish Fonts Panel**: 24 Unicode styles in a 4-column pill grid (never alters Sinhala text) + selected-text converter mode.
  - **Voice Typing**: Uses Android `SpeechRecognizer` with Sinhala (`si-LK`) and English (`en-US`) language switching.
- **Onboarding, Settings & Backup (Phase 3)**
  - 3-step guided setup wizard with live enabled/active checks and test field.
  - Sliders for key height, horizontal/vertical spacing, roundness, and bottom gesture padding + One-Handed mode.
  - Offline JSON export to `Downloads` and import restore for settings, shortcuts, learned words, and pinned clipboard items.

## How to Build the APK

### Option 1: Command Line (Linux / macOS)
```bash
chmod +x build_apk.sh
./build_apk.sh
```
The built debug APK is copied to **`apk/LankaKeys.apk`** (overwritten every build).

### Option 2: Command Line (Windows)
```bat
build_apk.bat
```

### Option 3: Gradle Direct
```bash
gradle :app:exportApk
```

### Option 4: GitHub Actions (No Android Studio needed)
Push this repository to GitHub. The workflow in `.github/workflows/build_apk.yml` automatically runs unit tests, builds the APK, and uploads **`apk/LankaKeys.apk`** as a downloadable build artifact under the **Actions** tab.

## How to Install & Enable U Type on Your Phone

1. Transfer **`apk/LankaKeys.apk`** to your Android phone (Android 8.0 / API 26+).
2. Tap the APK file to install it (allow *Install unknown apps* if prompted).
3. Open the **U Type** app from your launcher.
4. Follow the 3-step Setup Wizard:
   - **Step 1**: Tap **Open Keyboard Settings** and switch **U Type Keyboard** ON.
   - **Step 2**: Tap **Choose U Type** and pick **U Type Keyboard** as your active keyboard.
   - **Step 3**: Tap the test field, tap the `සි` key to switch to Singlish, and type `ammaa` → `අම්මා`!

## Running Unit Tests

```bash
gradle :app:testDebugUnitTest
```
Includes:
- `SinhalaTransliteratorTest` (50+ Singlish transliteration & backspace cases)
- `ClipboardExpiryTest` (2-hour expiry, pinning survival, fresh 2-hour countdown after unpinning, Clear unpinned, 50-item cap)
- `StylishFontEngineTest` (Unicode font conversion & Sinhala pass-through verification)
