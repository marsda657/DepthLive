# DepthLive

DepthLive is an Android live wallpaper prototype. It stores a selected photo
locally, analyzes it on-device, and renders the layers with OpenGL ES. Manual
rain and snow overlays and 12 offline motion presets are included. Microphone
reactivity requires explicit permission in settings.

## Current limitations

- MediaPipe DeepLab identifies foreground objects locally. Sky, water and
  terrain use a second on-device ADE20K DeepLab model for semantic layers.
  Unmapped ADE20K classes retain the existing image-based heuristic behavior.
  MiDaS estimates relative inverse depth from the photo; each layer's average
  drives its parallax and depth ordering. It is not metric distance and the
  renderer does not yet displace individual pixels using the depth map.
  Diagnostics identify model failures and heuristic fallbacks.
- The Compose preview and wallpaper use the same OpenGL renderer in the
  `render-engine` Android library. Preview and wallpaper run in separate GL
  contexts; behavior still needs verification on devices.
- Mistral AI (default) or Grok (xAI) can select one of the 12 shader presets from a text prompt after
  the user explicitly enables it. Invalid, unavailable or timed-out replies
  fall back to offline rules. Some requested effects, including zoom, are not
  implemented.
- Optional Open-Meteo weather is enabled only after a city is explicitly saved
  in Ambient settings. No device location is accessed. The city is sent to
  Open-Meteo's geocoding service, which selects the first matching place; its
  coordinates are then sent to the forecast service. The resolved location
  is displayed in settings so ambiguous results can be corrected.
  The last successful forecast is cached locally for offline use; without a
  city or cached forecast, ambient time and battery behavior continue without
  automatic weather overlays. The visible wallpaper checks every 30 minutes
  and reuses a forecast less than 30 minutes old; saving the same city again
  requests an immediate refresh. It does not schedule background work while
  hidden or in Doze. Manual rain and snow remain available. Review
  [Open-Meteo's terms](https://open-meteo.com/en/terms) for commercial use.
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

The build also downloads the ADE20K DeepLabV3 MobileNetV2 model from Sayak
Paul's pinned GitHub release, verifies the archive and extracted model SHA-256
values, then packages it as an on-device MediaPipe asset. Model tensor details,
inference checks, license provenance and the remaining dataset-terms question
are documented below. Landscape-model inference logs and falls back to
heuristics on runtime, native-library or out-of-memory failure.

The build also downloads MiDaS v2.1 small (about 63 MiB) from the pinned
revision of the [MiDaS Android example](https://github.com/shubham0204/MiDaS-Android),
and verifies its SHA-256. It is packaged uncompressed; analysis makes no
network requests. Offline builds require the verified model at
`app/build/generated/mediapipeAssets/midas_v21_small.tflite`. The model's
256x256 input is resized from the photo and produces relative, not physical,
depth; predictions can be wrong, especially on unusual scenes. MiDaS is
distributed under the MIT license (see `app/src/main/assets/licenses/MIDAS.txt`).
The added model increases app size and processing cost; latency, memory and
battery impact need device measurements.

To enable the LLM, open the Motion Prompt card, choose Mistral AI (default) or
Grok (xAI), paste your own API key and switch the option on. Keys are encrypted
with an Android Keystore AES key and are never bundled in the APK. The switch is
off by default; only the entered text and the built-in preset descriptions are
sent, over HTTPS, to the OpenAI-compatible chat endpoint. Replies must be one of
the 12 preset IDs. Without a key, connectivity or a valid reply, the prompt uses
offline rules. Model names (`mistral-small-latest`, `grok-3-mini`) are in
`LlmProvider.kt` and have not been exercised against the live services.

## Landscape model slot

`ImageSegmentationEngine.landscapeSegmenter` defaults to
`MediaPipeLandscapeSegmenter`, which returns 1-based ADE20K-150 labels as a
`LandscapeMask`. `Ade20kLayers` maps sky (3), water (22, 27, 61, 114, 129),
earth/ground (14, 95), terrain, vegetation and buildings to the five
wallpaper layers. Setting the segmenter to `null` retains the heuristic
fallback. Diagnostics report "ADE20K landscape" when model inference succeeds.

### ADE20K model provenance and validation

The [TF Hub archive](https://github.com/tensorflow/tfhub.dev/blob/master/assets/docs/sayakpaul/deeplabv3-mobilenetv2-ade20k/1.md)
identifies Sayak Paul's ADE20K model as Apache-2.0. The author's repository is
also Apache-2.0, with its license added at commit
`98609376ff6052b0f3bc327227ac7fdb03da7f83`. Its
[version 2 documentation](https://github.com/tensorflow/tfhub.dev/blob/master/assets/docs/sayakpaul/deeplabv3-mobilenetv2-ade20k/1/default/lite/2.md)
describes dynamic-range quantization, metadata, and the original TensorFlow
MobileNetV2 ADE20K checkpoint. The GitHub API lists 10 assets in the v0.8.0
release, with only one ADE20K MobileNetV2 asset and no ADE20K float32 fallback.
The
[Kaggle version 2 download](https://www.kaggle.com/api/v1/models/spsayakpaul/deeplabv3-mobilenetv2/tfLite/ade20k/2/download)
could not be accessed in the development environment; its bytes have not been
verified against the author release.

The app uses the author's
[GitHub v0.8.0 release](https://github.com/sayakpaul/Adventures-in-TensorFlow-Lite/releases/tag/v0.8.0)
contains `mnv2_ade20k_tflite.tar.gz` (SHA-256
`08e419abedc31fc0840b815bdd46eebc55aefdb96af1db5ec777367ec01d0c6c`).
Its `deeplabv3_mnv2_ade20k_train_2018_12_03.tflite` file has SHA-256
`0acdf37d5db38e681646ce79557c2af516650ecca5377b15c9cb171f751556cb`.
This release is from the same author and ADE20K checkpoint as Kaggle version 2,
but is not verified to be byte-identical. Its metadata states Apache-2.0; its
151 embedded labels match the ADE20K object list with
`others` at index 0. Its float32 RGB input is `[1, 513, 513, 3]`, normalized
to `[-1, 1]`, and its float32 output is `[1, 513, 513, 151]` logits.
A desktop LiteRT and MediaPipe category-mask inference on the bundled sample
landscape identified sky, water, trees, mountains and ground. A separate
generated-scene smoke test produced ADE20K building and house labels. These
are not quantitative accuracy measurements or Android device tests. The
Python MediaPipe process peaked at approximately 572 MiB RSS, including the
Python runtime and libraries.

The output tensor alone occupies approximately 152 MiB, despite the model file
being about 2.3 MiB. The app combines its category mask with the existing
foreground model and falls back to heuristics if landscape inference encounters
an out-of-memory condition. Peak memory, latency and device compatibility still
need Android device validation.
[ADE20K's terms](https://ade20k.csail.mit.edu/terms/index.html) restrict
use of its images to non-commercial research and education; the effect, if
any, on distributing trained weights requires separate legal review. An
Apache-2.0 model notice is not itself a determination of those dataset terms.
