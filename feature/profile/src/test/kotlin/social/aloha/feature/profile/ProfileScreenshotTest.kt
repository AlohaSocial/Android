// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import android.app.Application
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
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AccountField
import social.aloha.core.model.InstanceRule
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.navigation.PeopleKind
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowCache
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/** The profile's states, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = RichTextCache()

    private object NoActions : StatusActions, ProfileScreenActions {
        override fun onOpen(statusId: String) = Unit
        override fun onProfile(accountId: String) = Unit
        override fun onLink(target: RichLinkTarget) = Unit
        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onReply(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
        override fun onBack() = Unit
        override fun onRefresh() = Unit
        override fun onTab(tab: ProfileTab) = Unit
        override fun onNearEnd() = Unit
        override fun onFillGap(gapId: String) = Unit
        override fun onChange(change: RelationshipChange) = Unit
        override fun onBlockDomain(block: Boolean) = Unit
        override fun onLists() = Unit
        override fun onListed(listId: String, add: Boolean) = Unit
        override fun onEditProfile() = Unit
        override fun onReport() = Unit
        override fun onPeople(followers: Boolean) = Unit
        override fun onOpenInBrowser(url: String) = Unit
    }

    @Composable
    private fun profile(relation: Relation = Relation(followedBy = true), highlights: Boolean = true): ProfileUiState {
        val colors = RichTextColors.fromTheme()
        val bob = StatusSamples.bob.copy(
            note = "<p>Surfs at dawn, codes by noon. " +
                "<a href=\"https://cloud.example/tags/aloha\" class=\"hashtag\">#aloha</a></p>",
            locked = true,
            statusesCount = 1_204,
            followingCount = 87,
            followersCount = 1,
            fields = listOf(
                AccountField(
                    "Website",
                    "<a href=\"https://bob.example\">bob.example</a>",
                    verifiedAt = StatusSamples.NOW,
                ),
                AccountField("Board", "9'2\" longboard"),
            ),
        )
        val rows = StatusRowCache(cache).apply { use(colors) }
        return ProfileUiState(
            header = ProfilePresentation.header(bob, cache, colors, isSelf = false),
            relation = relation,
            highlights = ProfileHighlights(
                available = true,
                weeks = listOf(0, 1, 2, 1, 0, 3, 2, 1, 4, 5, 6, 7),
            ).takeIf {
                highlights
            },
            items = listOf(StatusSamples.post(), StatusSamples.gallery).map {
                ProfileItem.Post(rows.rowFor(it, "1", null))
            },
            loading = false,
            now = StatusSamples.NOW,
        )
    }

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
    fun followsYou() = capture("profile-follows-you") { ProfileScreen(profile(), NoActions, NoActions) }

    @Test
    fun followingDark() = capture("profile-following-dark", ThemeSettings(mode = ThemeMode.Dark)) {
        ProfileScreen(profile(Relation(following = true), highlights = false), NoActions, NoActions)
    }

    @Test
    @Config(fontScale = 2f)
    fun requestedLargeFont() = capture("profile-requested-font200") {
        ProfileScreen(profile(Relation(requested = true)), NoActions, NoActions)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun tablet() = capture("profile-tablet") { ProfileScreen(profile(), NoActions, NoActions) }

    @Test
    fun gone() = capture("profile-gone") {
        ProfileScreen(ProfileUiState(loading = false, gone = true), NoActions, NoActions)
    }

    @Test
    fun followers() = capture("profile-followers") {
        val mapper = StatusRowMapper(cache, RichTextColors.fromTheme())
        val people = listOf(StatusSamples.alice, StatusSamples.bob).map {
            PeopleUiState.Person(mapper.author(it), it.emojis)
        }
        PeopleScreen(PeopleKind.Followers, PeopleUiState(people, loading = false, reachedEnd = true), {}, {}, {}, {})
    }

    @Test
    fun editProfile() = capture("profile-edit") {
        val form = ProfileForm(
            displayName = "Alice Example",
            note = "Surfs at dawn, writes at dusk.",
            fields = listOf(AccountField("Pronouns", "she/her"), AccountField("Site", "alice.example")) +
                List(2) { AccountField("", "") },
            locked = true,
        )
        EditProfileScreen(EditProfileUiState(form = form, original = form.copy(note = "")), {}, {}, {}, {})
    }

    @Test
    fun report() = capture("profile-report") {
        val rules = listOf(InstanceRule("1", "Be kind"), InstanceRule("2", "No spam or ads"))
        val state = ReportUiState(category = ReportCategory.Violation, rules = rules, broken = setOf("2"))
        ReportScreen("@bob@remote.example", remote = true, state, NoReport, onDone = {})
    }

    private object NoReport : ReportActions {
        override fun onCategory(category: ReportCategory) = Unit
        override fun onRule(id: String, broken: Boolean) = Unit
        override fun onComment(comment: String) = Unit
        override fun onForward(forward: Boolean) = Unit
        override fun onSend() = Unit
        override fun onMute() = Unit
        override fun onBlock() = Unit
    }
}
