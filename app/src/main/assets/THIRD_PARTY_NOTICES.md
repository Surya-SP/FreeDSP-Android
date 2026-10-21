# Third-party notices

## FreeDSP Studio

The Gen-1 wire protocol (reads and writes), the RBJ biquad maths, the 2^22 coefficient encoding, the baked-in preamp, the clip/headroom rule and the Equalizer APO text format were independently implemented in Kotlin from FreeDSP Studio's public source. Descriptor-length parsing also uses the HID concepts illustrated in its parser. Attribution is preserved for these adaptations:

- Project: https://github.com/EffectiveEquivalent/FreeDSP-Studio
- Revision: `1b42f63db24f7dc8e94f6897575c1c403f4d4882`
- Copyright (c) 2026 EffectiveEquivalent
- License: MIT; complete license in `docs/FreeDSP-Studio-LICENSE.txt` and bundled in the APK assets.

Upstream's other device families, firmware/preset handling, and treble listening test are not included; auto-fit is ported in its range mode only.

## AutoEq

Auto-fit follows AutoEq's approach as FreeDSP Studio ports it (matching-pursuit start, MSE loss with a sharpness penalty, per-parameter refinement). The bundled target curves (`assets/targets`: Harman in-ear 2019, Harman in-ear 2019 without bass, AutoEq in-ear) are AutoEq's files, unchanged.

- Project: https://github.com/jaakkopasanen/AutoEq
- Revision: `7ae0f56d53074872b028649617a22bbb4232feb7`
- Copyright (c) 2018-2022 Jaakko Pasanen
- License: MIT; complete license in `docs/AutoEq-LICENSE.txt` and bundled in the APK assets.

IEM measurements are not bundled: they belong to the people who measured them, and users import their own.

## Kotlin

Kotlin standard library 2.0.21, Copyright JetBrains s.r.o. and Kotlin Programming Language contributors, Apache License 2.0.
https://github.com/JetBrains/kotlin/tree/v2.0.21/license
https://www.apache.org/licenses/LICENSE-2.0

## Gradle wrapper

Gradle wrapper 8.10.2, Apache License 2.0.
https://github.com/gradle/gradle/blob/v8.10.2/LICENSE

## Test-only dependency

JUnit 4.13.2 (Eclipse Public License 1.0) and Hamcrest Core 1.3 (BSD-3-Clause) are used only by host tests and are not included in the APK.

## AndroidX / Jetpack Compose

This release uses AndroidX Activity, Lifecycle and Compose and their transitive runtime dependencies, provided by the Android Open Source Project under Apache License 2.0. The included `Kotlin-LICENSE.txt` contains that license text. See https://android.googlesource.com/platform/frameworks/support/ for upstream source.
