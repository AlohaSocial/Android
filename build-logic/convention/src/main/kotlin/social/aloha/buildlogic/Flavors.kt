// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.buildlogic

import com.android.build.api.dsl.CommonExtension

/** Distribution flavors: `generic` for F-Droid (no Google libraries), `gplay` for Google Play. */
const val DISTRIBUTION = "distribution"

fun configureFlavors(extension: CommonExtension) {
    extension.flavorDimensions += DISTRIBUTION
    extension.productFlavors.create("generic") { dimension = DISTRIBUTION }
    extension.productFlavors.create("gplay") { dimension = DISTRIBUTION }
}
