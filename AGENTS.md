# AGENTS.md

## Project Mission

AndroidTestHelper is a Kotlin Multiplatform / Compose Desktop tool for testing Android tablets and other Android devices through bundled Android platform-tools.

The app is a desktop testing console. It must support Windows, macOS, and Linux from one shared UI codebase, using the matching `adb` binary under the root `platform-tools/` directory.

## Repository Layout

- `desktopApp/`
  - Desktop JVM application entry point.
  - Keep this module thin. It should own the window, app title, initial size, and desktop packaging only.
- `shared/src/commonMain/`
  - Shared Compose UI, screen state, common models, test-module contracts, and expect declarations.
  - Put cross-platform UI and business-facing test flow here.
- `shared/src/jvmMain/`
  - JVM/Desktop actual implementations.
  - Put process execution, ADB path resolution, desktop pointer/cursor behavior, and other JVM-specific code here.
- `shared/src/jvmTest/`
  - JVM tests for parser and desktop-specific logic.
- `platform-tools/`
  - Bundled Android platform-tools for Windows, macOS, and Linux.
  - Do not ignore or delete this folder. The app depends on it at runtime.

## Current App Structure

The main Compose UI is in:

- `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/App.kt`

The UI has three areas:

- Top: test module switcher.
  - Current modules: `数据填充`, `应用`.
- Middle: selected test module content.
  - `数据填充` currently owns storage display and fill controls.
  - `应用` is currently a placeholder module.
- Bottom: shared device connection panel and command log.
  - Device panel and command log are common infrastructure for all test modules.
  - The bottom panel height is draggable and starts at one quarter of the available area.

When adding new test features, add them as test modules instead of hard-coding a one-off page.

## ADB Rules

- Always run commands against an explicit device serial when a device action targets Android:
  - Use `adb -s <serial> ...`.
  - Never rely on implicit single-device behavior.
- Device discovery uses:
  - `adb devices -l`
  - Show serial number and model to users.
- Any command that is executed should be appended to the command log before execution.
- Keep ADB implementation behind the shared `DataFillAdb`-style abstraction:
  - Common API in `commonMain`.
  - Process implementation in `jvmMain`.
- Resolve `adb` from root `platform-tools/` based on current desktop OS.
  - Windows: `platform-tools/platform-tools-latest-windows/platform-tools/adb.exe`
  - macOS: `platform-tools/platform-tools-latest-darwin/platform-tools/adb`
  - Linux: `platform-tools/platform-tools-latest-linux/platform-tools/adb`

## Data Fill Behavior

Data fill currently writes files to:

- `/sdcard/AndroidTestHelperFill`

Storage is read with:

- `adb -s <serial> shell df -k /data`

Fill commands use chunked `dd` writes so progress is visible:

- Current chunk size: `128MB`
- Stopping a fill task must cancel the coroutine and destroy the running ADB process.
- After stopping, clear fill progress and refresh device storage because already-written data remains on the device.

Do not add frontend-only fake storage values. Read actual device state through ADB.

## App List Fetching Behavior

There are two strategies to fetch the installed application list:

1. **ATHPlugin Proxy Mode (Recommended & High Performance)**:
   - Triggered when the helper application `com.floatingmuseum.android.test.helper.plugin` is installed on the target device.
   - PC queries all installed applications by running a single command:
     `adb -s <serial> shell content query --uri content://com.floatingmuseum.android.test.helper.plugin.provider/apps?isSystem=<true|false>`
   - The plugin returns a JSON array containing metadata (packageName, appName, versionName, versionCode, targetSdkVersion, minSdkVersion, compileSdkVersion, isSystem, isEnabled) in one database cell.
   - Icons are loaded lazily. When rendering an icon, PC reads the raw binary stream directly:
     `adb -s <serial> exec-out content read --uri content://com.floatingmuseum.android.test.helper.plugin.provider/icon/<packageName>`
     
2. **Standard ADB Fallback Mode (Slow)**:
   - Used when `ATHPlugin` is not installed or any feature call fails.
   - Combines output of multiple ADB shell calls:
     - `pm list packages -f -U`
     - `pm list packages -3`
     - `pm list packages -s`
     - `pm list packages -d`
     - `dumpsys package`
   - To retrieve icons and app labels, PC pulls each app's base APK into a temp directory and parses `AndroidManifest.xml` and `resources.arsc` locally.
   - Relies on file caching to speed up consecutive loads of system apps.

## UI Guidelines

- Keep controls dense and operational. This is a test tool, not a marketing interface.
- Shared device selection and command log should remain visible across test modules.
- The command log is a diagnostic surface. Do not hide commands that mutate device state.
- Prefer explicit labels in Chinese for user-facing UI, matching the current app.
- Selected devices and selected test modules should be distinguished by color, not by adding noisy text prefixes.
- Avoid placing platform-specific Compose APIs directly in common UI unless they are actually available in common source sets.
  - If needed, use `expect` / `actual`, as with `verticalResizePointerIcon()`.

## Source-Set Rules

- Put pure models, parse helpers, shared UI, and test-module state in `commonMain`.
- Put process execution, file-system OS detection, AWT cursor code, and desktop-specific APIs in `jvmMain`.
- Keep `desktopApp/src/main/.../main.kt` limited to window setup:
  - Current default size: `1280 x 860`.
- Avoid adding Android app module code unless the project target changes. This project controls Android devices; it is not currently an Android app.

## Validation Commands

On Windows PowerShell:

```powershell
.\gradlew.bat :shared:jvmTest
.\gradlew.bat :desktopApp:compileKotlin
```

Useful run command:

```powershell
.\gradlew.bat :desktopApp:run
```

The local Gradle wrapper may need access to the Gradle distribution lock under `D:\softwares\AppData\.gradle`. If a sandboxed run fails with `gradle-*-bin.zip.lck (拒绝访问。)`, rerun the same Gradle command with the required external execution permission.

## Testing Expectations

- Add focused JVM tests for parsers and command-output handling.
- Current examples:
  - `parseAdbDevices`
  - `parseDfStorageInfo`
- Do not require a physical Android device for parser/unit tests.
- Device integration should be verified manually or with future emulator/device automation when available.

## Git / Generated Files

- Build outputs and IDE-local files are ignored.
- Keep Gradle wrapper files tracked.
- Keep `platform-tools/` tracked unless the distribution strategy changes intentionally.
- Do not commit `.gradle/`, `.kotlin/`, `build/`, `.idea/`, or `*.iml`.

## Implementation Posture

- Preserve current package:
  - `com.floatingmuseum.android.test.helper`
- Keep changes scoped. Avoid broad refactors while adding a single test module.
- Prefer small abstractions only when they protect source-set boundaries or avoid repeated ADB process logic.
- When stopping or cancelling long-running ADB tasks, make cancellation observable and clean up child processes.
