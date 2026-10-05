# Acknowledgements

## Code included in this repository

| What | Where | Licence | Source |
|---|---|---|---|
| `DynamicScheme.toColorScheme()` and the tonal-spot seeding approach | `core/designsystem/.../SchemeConversion.kt`, `AlohaColorSchemes.kt` | MIT | [nextcloud/android-common](https://github.com/nextcloud/android-common) (`MaterialSchemes`, `SchemeExtensions.kt`) |
| Gradle wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Apache-2.0 | [Gradle](https://gradle.org) |
| EfficientNet-Lite0 image classifier (int8) and its ImageNet labels, drafting alt text | `core/intelligence/src/main/assets/` | Apache-2.0 | [TensorFlow](https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/image_classification/android/efficientnet_lite0_int8_2.tflite), SHA-256 `aa03e4017f54fd5c743bd397b0a3e136fab0942f9c7cd715064a31ca3e4fc996` |

## Libraries

Every runtime and build dependency is declared in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) and verified against
[`gradle/verification-metadata.xml`](gradle/verification-metadata.xml). The colour
schemes come from [MaterialKolor](https://github.com/jordond/materialkolor)
(MIT), a Kotlin port of Google's Material Color Utilities. The open-source licences screen is generated at build time by
[AboutLibraries](https://github.com/mikepenz/AboutLibraries) (Apache-2.0). The
Material Icons used until the switch to Material Symbols are Apache-2.0
(Google).

## Debts of design

- [Aloha Social for Apple platforms](https://github.com/AlohaSocial/Apple): the
  product specification, and the platform-free logic this app ports.
- [Ice Cubes](https://github.com/Dimillian/IceCubesApp) by Thomas Ricouard, the
  architectural debt the Apple app acknowledges; no code is shared.
- [Now in Android](https://github.com/android/nowinandroid): the module and
  convention-plugin layout.
