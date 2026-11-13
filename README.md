# DepthLive

DepthLive is an Android live wallpaper prototype. It stores a selected photo
locally, analyzes it on-device, and renders the layers with OpenGL ES. Manual
rain and snow overlays and 12 offline motion presets are included. Microphone
reactivity requires explicit permission in settings.

## Current limitations

- MediaPipe DeepLab identifies foreground objects locally. Sky, water, terrain
  and depth are still heuristic estimates, not model-derived semantic layers
  or a depth model. If MediaPipe fails, diagnostics identify the heuristic
  fallback.
- The Compose preview and wallpaper use the same OpenGL renderer in the
  `render-engine` Android library. Preview and wallpaper run in separate GL
  contexts; behavior still needs verification on devices.
- Gemini can select one of the 12 shader presets from a text prompt after
  the user explicitly enables it. Invalid, unavailable or timed-out replies
  fall back to offline rules. Some requested effects, including zoom, are not
  implemented.
- Automatic Open-Meteo weather is disabled: a weather request conflicts with
  the requirement that only motion-prompt LLM calls use the network. Rain and
  snow can be selected manually.
- The renderer adjusts between 60, 30, and 15 fps based on measured frame
  cadence, subject to the selected maximum and a 30 fps cap below 20% battery.
  The under-3%-per-day battery target and frame rates require device testing.
- The wallpaper suspends rendering and sensors when hidden or in Doze, but
  lifecycle behavior, unlock animation, and audio need verification on devices.

## Build

The project requires Android SDK 36.1 and a JDK supported by Android Gradle
Plugin 9.1.1. Run `./gradlew testDebugUnitTest` with a configured Android SDK;
the wrapper downloads Gradle 9.3.1. The AI Studio signing
configuration also expects the keystore files described in
`app/build.gradle.kts` when building installable packages.

The first build downloads Google's MediaPipe DeepLab v3 model from
`storage.googleapis.com` and verifies its SHA-256 against the official
MediaPipe sample. This happens at build time, not while analyzing photos.
Subsequent builds can use the verified model in
`app/build/generated/mediapipeAssets/deeplabv3.tflite`, including with
`--offline`. The model recognizes the 20 Pascal VOC object classes, not
sky or water. Review the model's applicable distribution terms before release.

To enable Gemini, register application ID `com.aistudio.depthlive.vwpqzx` in a
Firebase project, enable Firebase AI Logic with the Gemini Developer API, and
place that project's `google-services.json` in `app/`. Configure App Check for
the release application as appropriate. Never put a Gemini API key in the APK.
The optional Gemini switch is off by default; only the entered text and the
built-in preset descriptions are sent when a user explicitly requests it.
Without Firebase configuration or connectivity, the prompt uses offline rules.
