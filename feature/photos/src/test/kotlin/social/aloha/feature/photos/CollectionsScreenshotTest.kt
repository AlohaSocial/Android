// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.photos.PhotoExplore
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.Tag
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CollectionsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val collections = listOf(
        MediaCollection("1", "Beach", "Summer", postCount = 12),
        MediaCollection("2", "Mountains", postCount = 1),
        MediaCollection("3", "", postCount = 0),
    )

    private val none = CollectionsScreenActions({}, {}, {}, {}, {}, {})

    private fun capture(
        name: String,
        settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        content: @Composable () -> Unit,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(settings) { content() } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun ownCollections() = capture("collections-own") {
        CollectionsScreen(CollectionsUiState(collections, own = true, loading = false), none, SnackbarHostState())
    }

    @Test
    @Config(fontScale = 2f)
    fun othersCollectionsLargeFont() = capture("collections-font200", ThemeSettings(mode = ThemeMode.Dark)) {
        CollectionsScreen(CollectionsUiState(collections, own = false, loading = false), none, SnackbarHostState())
    }

    @Test
    fun noCollections() = capture("collections-none") {
        CollectionsScreen(CollectionsUiState(own = true, loading = false), none, SnackbarHostState())
    }

    @Test
    fun collection() = capture("collection") {
        val posts = listOf(StatusSamples.gallery, StatusSamples.sensitive, StatusSamples.gallery.copy(id = "g2"))
        CollectionScreen(CollectionUiState(posts, loading = false), "Beach", {}, {}, {}, {}, SnackbarHostState())
    }

    @Test
    fun explore() = capture("photos-explore") {
        val photos = listOf(StatusSamples.gallery, StatusSamples.sensitive, StatusSamples.gallery.copy(id = "g2"))
        PhotoExploreScreen(
            PhotoExploreUiState(
                PhotoExplore(
                    trending = photos,
                    tags = listOf(Tag("beach"), Tag("sunset"), Tag("filmphotography")),
                    people = listOf(StatusSamples.alice, StatusSamples.bob),
                    network = photos.take(2).map { it.copy(id = "n" + it.id) },
                ),
                loading = false,
            ),
            {},
            {},
            {},
            {},
            {},
        )
    }

    @Test
    fun addToCollection() = capture("add-to-collection") {
        AddToCollectionScreen(
            AddToCollectionUiState(collections, added = setOf("2"), loading = false),
            {},
            {},
            {},
            {},
            SnackbarHostState(),
        )
    }
}
