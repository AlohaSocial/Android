// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The licence list Settings shows is generated into the app at build time, from its real dependencies. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LicencesTest {
    @Test
    fun `the libraries the app ships are listed with their licences`() {
        val libraries = Libs.Builder().withContext(
            ApplicationProvider.getApplicationContext<Context>(),
        ).build().libraries
        val okhttp = libraries.first { it.uniqueId == "com.squareup.okhttp3:okhttp" }
        assertTrue(okhttp.licenses.any { it.spdxId == "Apache-2.0" })
        assertTrue(libraries.any { it.uniqueId.startsWith("androidx.compose.material3:") })
    }
}
