# HRHostClone

`HRHostClone` is an Android host application built with Jetpack Compose. It receives live image frames over UDP or TCP, runs local detection with ONNX Runtime or an optional NCNN native core, renders an overlay preview, and communicates with a USB host device for runtime input control.

This repository currently contains the Android client project used in Android Studio.

## Highlights

- Jetpack Compose based Android UI
- UDP and TCP MJPEG frame receiving
- ONNX Runtime inference on Android
- Optional NCNN inference path through native library loading
- Android 8.1+ NNAPI acceleration path with automatic CPU fallback
- ONNX NNAPI toggle in the monitor UI with recent runtime logs and Logcat output
- Monitor status cards now show the actual active inference backend instead of fixed placeholder badges
- Overlay preview with bounding boxes, labels, confidence display, and aim-range visualization
- Lightweight multi-target tracking pipeline
- USB host / serial communication flow for external device control
- Import/export of runtime configuration as JSON
- Model file import into the app's private storage

## Project Status

The project is in an active prototype / integration stage. Based on the local handoff notes in [`API_HANDOFF.md`](./API_HANDOFF.md), the latest validated state includes:

- Stable device connection flow
- Working draw-pattern actions
- Mouse button detection and long-press handling fixes
- Runtime wiring for hotkeys, PD parameters, and tracking configuration

Main implementation is currently concentrated in:

- [`app/src/main/java/com/example/hrhostclone/MainActivity.kt`](./app/src/main/java/com/example/hrhostclone/MainActivity.kt)

## Tech Stack

- Kotlin
- Android Gradle Plugin `9.0.1`
- Kotlin Compose plugin `2.0.21`
- Gradle `9.2.1`
- Jetpack Compose + Material 3
- ONNX Runtime Android `1.22.0`
- `usb-serial-for-android` `3.9.0`

Version catalog:

- [`gradle/libs.versions.toml`](./gradle/libs.versions.toml)

## Requirements

- Android Studio with Android SDK installed
- JDK 11 compatible toolchain
- Android device with USB host support
- `minSdk 24`
- `targetSdk 36`
- `arm64-v8a` target device for the current app build configuration

## Build

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

Optional compile-only check:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

The latest local handoff notes recorded both commands as passing at the time of the handoff.

## Runtime Overview

The application flow is roughly:

1. Receive the latest frame from UDP or TCP.
2. Decode the frame into a `Bitmap`.
3. Run detection through ONNX Runtime or the optional NCNN path.
4. Filter and track targets.
5. Render the preview surface and overlay elements.
6. Send movement / control output to the connected USB device when the configured runtime conditions are met.

Core manifest and app configuration files:

- [`app/src/main/AndroidManifest.xml`](./app/src/main/AndroidManifest.xml)
- [`app/build.gradle.kts`](./app/build.gradle.kts)
- [`settings.gradle.kts`](./settings.gradle.kts)

## Models

The app supports importing model files through the UI into its private app storage.

- ONNX models are loaded directly with ONNX Runtime.
- On Android 8.1 and above, the ONNX path now attempts to register the NNAPI execution provider first. On Android 10 and above, it first tries an accelerator-preferred NNAPI mode before falling back to regular NNAPI, and finally to CPU if the device or model cannot use NNAPI.
- The monitor page exposes an `NNAPI` acceleration toggle. Turning it off forces CPU execution for ONNX models; changing it reloads the selected ONNX model with the new backend preference.
- The monitor status card shows the active model/runtime combination, for example `ONNX + NNAPI`, `ONNX + CPU`, or `NCNN + NATIVE`.
- Recent ONNX initialization / inference messages are shown in the monitor UI and are also emitted to Logcat with the tag `HXHostOnnx`.
- Actual NPU offload still depends on the target device's NNAPI drivers and whether the model operators are supported by that driver. Some models may run partly on accelerator hardware and partly on CPU.
- NCNN models require paired `.param` and `.bin` files.
- The NCNN path also expects the native library `hxhost_core` to be available at runtime.

Model files are not stored in this repository by default.

## Repository Layout

```text
HRHostClone/
|- app/
|  |- src/main/java/com/example/hrhostclone/
|  |  |- MainActivity.kt
|  |  \- ui/theme/
|  |- src/main/res/
|  \- build.gradle.kts
|- gradle/
|- API_HANDOFF.md
|- build.gradle.kts
|- gradle.properties
|- gradlew
|- gradlew.bat
|- settings.gradle.kts
\- README.md
```

## Notes For GitHub Publishing

- `local.properties`, IDE metadata, and build output directories should stay ignored.
- The generated `app/build` directory is not intended to be committed.
- Imported model files, local SDK paths, and private runtime data are not part of the repository.

## Next Cleanup Opportunities

- Split the large `MainActivity.kt` into UI, runtime, networking, model, and USB modules
- Remove duplicated Compose dependency declarations in [`app/build.gradle.kts`](./app/build.gradle.kts)
- Add automated tests around frame parsing, tracker behavior, and USB protocol helpers
- Document the native NCNN build / packaging workflow if that path will remain supported

## License

This project is licensed under the GNU General Public License v3.0.

- License file: [`LICENSE`](./LICENSE)
- SPDX identifier: `GPL-3.0-only`
