# DepthLive

DepthLive is an Android live wallpaper prototype. It stores a selected photo
locally, creates approximate layers on-device, and renders them with OpenGL ES.
The settings screen offers manual rain and snow overlays and offline motion
presets. Microphone reactivity requires explicit permission in settings.

## Current limitations

- Segmentation uses color and position heuristics, not MediaPipe; the depth
  image is an approximation, not a model-derived depth map.
- The Compose preview and wallpaper use the same OpenGL renderer in the
  `render-engine` Android library. Preview and wallpaper run in separate GL
  contexts; behavior still needs verification on devices.
- Motion prompts use offline keyword rules, not an LLM. Some requested effects
  such as zoom are not implemented.
- Automatic Open-Meteo weather is disabled: a weather request conflicts with
  the requirement that only motion-prompt LLM calls use the network. Rain and
  snow can be selected manually.
- Frame rate can be configured at 60, 30, or 15 fps and is capped at 30 below
  20% battery. Automatic performance-based degradation is not implemented.
  The under-3%-per-day battery target and frame rates require device testing.
- The wallpaper suspends rendering and sensors when hidden or in Doze, but
  lifecycle behavior, unlock animation, and audio need verification on devices.

## Build

The project requires Android SDK 36.1 and a JDK supported by Android Gradle
Plugin 9.1.1. Run `./gradlew testDebugUnitTest` with a configured Android SDK;
the wrapper downloads Gradle 9.3.1. The AI Studio signing
configuration also expects the keystore files described in
`app/build.gradle.kts` when building installable packages.
