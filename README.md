# Text File Reader

A simple, lightweight Android app that lets you open and read plain text files on your phone.

Designed especially for devices (like Xiaomi / HyperOS) that lack a built-in text file viewer.

## Features

- **Open from file manager**: When you tap a `.txt`, `.log`, `.md`, `.json`, `.xml`, `.csv`, etc. file, this app appears in the “Open with” list.
- **In-app file picker**: Use the **Open File** button to browse any text file.
- **Monospace display** with selectable text.
- **Line & character count**.
- **Share** the content with other apps.
- Works with both `content://` and `file://` URIs.
- Supports UTF-8 (and BOM detection).

## Supported file types

| Extension | MIME type          |
|-----------|--------------------|
| `.txt`    | text/plain         |
| `.log`    | text/*             |
| `.md`     | text/markdown      |
| `.csv`    | text/csv           |
| `.json`   | application/json   |
| `.xml`    | application/xml    |
| `.html`   | text/html          |
| `.css`    | text/css           |
| `.js`     | application/javascript |
| `.kt/.java/.py/.sh/.yaml/.yml/.ini/.conf` | various |

## How to build & install

### Option 1 – Android Studio (recommended)

1. Clone or download this repository.
2. Open the folder in **Android Studio** (Hedgehog or newer recommended).
3. Let Gradle sync.
4. Connect your Xiaomi 13T (or any Android 7+ device) with USB debugging enabled.
5. Click **Run ▶**.

### Option 2 – Command line

```bash
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

Install with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option 3 – GitHub Actions

Every push to `main`/`master` (and manual runs) builds both **debug** and **release** APKs.

1. Go to the **Actions** tab of the repository.
2. Download the artifact named `TextFileReader-debug`.
3. Transfer the APK to your phone and install it (you may need to allow “Install from unknown sources”).

## Permissions

The app uses the **Storage Access Framework** (`OpenDocument`).  
It does **not** request broad storage permissions – only the specific file you choose is accessible.

## Minimum requirements

- Android 7.0 (API 24) or higher
- Works on Xiaomi HyperOS / MIUI, stock Android, etc.

## Project structure

```
TextFileReader/
├── app/
│   ├── src/main/
│   │   ├── java/com/textfilereader/MainActivity.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/build.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## License

MIT – free to use and modify.
