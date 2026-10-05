// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.Account
import social.aloha.core.model.Digest
import social.aloha.core.model.ModerationWarning
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.NotificationPolicy
import social.aloha.core.model.NotificationRequest
import social.aloha.core.model.PolicyDecision
import social.aloha.core.model.PollFrequency
import social.aloha.core.model.QuietHours
import social.aloha.core.model.ReadingStyle
import social.aloha.core.model.SeveranceEvent
import social.aloha.core.model.Status
import social.aloha.core.sync.Distributor
import social.aloha.core.ui.LocalReadingStyle

/** The notifications screens, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NotificationsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-09-30T12:00:00Z")

    init {
        // the card asking for notifications has its own image; the others show the list as it is once allowed
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun capture(
        name: String,
        theme: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        content: @Composable () -> Unit,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(theme) { content() } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun row(kind: NotificationKind, key: String, others: Int = 0, unread: Boolean = false, minutes: Long = 5) =
        NotificationRowUi(
            key = key,
            kind = kind,
            name = "Alice Example",
            others = others,
            people = List(minOf(others + 1, 4)) { NotificationRowUi.Person("$it", "Person $it", null) },
            preview = "Surf report for the north shore: waist high and glassy, going out at seven.",
            statusId = "s1",
            accountId = "1",
            groupKey = key.takeIf { others > 0 },
            unread = unread,
            at = now.minusSeconds(minutes * 60),
        )

    private val list = NotificationsUiState(
        rows = listOf(
            row(NotificationKind.Favourite, "favourite-s1", others = 34, unread = true),
            row(NotificationKind.Mention, "m1", unread = true, minutes = 20),
            row(NotificationKind.Reblog, "reblog-s1", others = 1, minutes = 90),
            row(NotificationKind.Follow, "follow", others = 2, minutes = 600).copy(preview = null),
            row(NotificationKind.Poll, "poll-1", minutes = 3_000),
        ),
        loadedOnce = true,
        filtering = true,
        pendingRequests = 4,
        now = now,
    )

    private val actions = object : NotificationsActions {
        override fun onRefresh() = Unit

        override fun onKind(kind: NotificationKind) = Unit

        override fun onAllKinds() = Unit

        override fun onNearEnd() = Unit

        override fun onOpen(row: NotificationRowUi) = Unit

        override fun onOthers(groupKey: String) = Unit

        override fun onMuteConversation(row: NotificationRowUi) = Unit

        override fun onNoticeShown() = Unit

        override fun onPolicy() = Unit

        override fun onRequests() = Unit

        override fun onAskedForPermission() = Unit
        override fun onProfile(accountId: String) = Unit
        override fun onFollowRequest(row: NotificationRowUi, accept: Boolean) = Unit
        override fun onMarkAllRead() = Unit
        override fun onLearnMore(url: String) = Unit
    }

    @Test
    fun notifications() = capture("notifications") { NotificationsScreen(list, actions, navigationIcon = {}) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun notificationsTablet() = capture("notifications-tablet") {
        NotificationsScreen(list, actions, navigationIcon = {})
    }

    @Test
    fun overflowHoldsTheActionsOnAPhone() {
        var marked = false
        val marking = object : NotificationsActions by actions {
            override fun onMarkAllRead() {
                marked = true
            }
        }
        compose.setContent { AlohaTheme { NotificationsScreen(list, marking, navigationIcon = {}) } }
        compose.onNodeWithContentDescription("Mark all as read").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Mark all as read").performClick()
        assertTrue(marked)
    }

    @Test
    fun notificationsDark() = capture("notifications-dark", ThemeSettings(mode = ThemeMode.Dark)) {
        NotificationsScreen(list, actions, navigationIcon = {})
    }

    @Test
    @Config(fontScale = 2f)
    fun notificationsLargeFont() = capture("notifications-font200") {
        NotificationsScreen(list.copy(kinds = setOf(NotificationKind.Mention)), actions, navigationIcon = {})
    }

    @Test
    fun notices() = capture("notifications-notices") {
        val rows = listOf(
            row(NotificationKind.FollowRequest, "request", minutes = 2).copy(preview = null),
            row(NotificationKind.SeveredRelationships, "severed", minutes = 60).copy(
                preview = null,
                severance = SeveranceEvent("domain_block", "spam.example"),
            ),
            row(NotificationKind.ModerationWarning, "warning", minutes = 120).copy(
                preview = null,
                warning = ModerationWarning("9", "silence", "Please keep spoilers behind a content warning."),
            ),
        )
        NotificationsScreen(list.copy(rows = rows, origin = "https://social.example"), actions, navigationIcon = {})
    }

    @Test
    fun permission() = capture("notifications-permission") { PermissionCard(onTurnOn = {}) }

    @Test
    fun push() = capture("notifications-push") {
        Column {
            PushRows(
                PushUi(listOf(Distributor("io.heckel.ntfy", "ntfy")), chosen = "io.heckel.ntfy"),
                onDistributor = {},
                onFind = {},
            )
            PushRows(PushUi(), onDistributor = {}, onFind = {})
        }
    }

    @Test
    fun empty() = capture("notifications-empty") {
        NotificationsScreen(NotificationsUiState(loadedOnce = true, now = now), actions, navigationIcon = {})
    }

    @Test
    fun policy() = capture("notifications-policy") {
        PolicyScreen(
            PolicyUiState(
                policy = NotificationPolicy(
                    forNewAccounts = PolicyDecision.Filter,
                    forLimitedAccounts = PolicyDecision.Drop,
                ),
                dropFilters = true,
            ),
            onDecision = { _, _ -> },
            onBack = {},
        )
    }

    @Test
    fun requests() = capture("notifications-requests") {
        val sender = Account("7", "surfer", "surfer@waves.example", displayName = "Wave Rider")
        RequestsScreen(
            RequestsUiState(
                listOf(
                    NotificationRequest(
                        "r1",
                        sender,
                        notificationsCount = 3,
                        lastStatus = Status("s9", sender, content = "<p>Anyone out at dawn?</p>"),
                    ),
                    NotificationRequest(
                        "r2",
                        sender.copy(id = "8", displayName = "Tide Watcher"),
                        notificationsCount = 1,
                    ),
                ),
            ),
            onAccept = {},
            onDismiss = {},
            onOpenProfile = {},
            onBack = {},
        )
    }

    @Test
    fun numbersOff() = capture("notifications-numbers-off") {
        CompositionLocalProvider(LocalReadingStyle provides ReadingStyle(showCounts = false)) {
            NotificationsScreen(list, actions, navigationIcon = {})
        }
    }

    @Test
    @Config(fontScale = 2f)
    fun syncSettingsLargeFont() = capture("notifications-settings-font200") {
        SyncRows(
            SyncSettingsUi(
                PollFrequency.BatterySaver,
                wifiOnly = true,
                quiet = QuietHours(fromHour = 22, untilHour = 7),
            ),
            onFrequency = {},
            onWifiOnly = {},
            onQuietHours = {},
            onDigest = {},
            onKinds = {},
        )
    }

    @Test
    @Config(fontScale = 2f)
    fun syncSettingsDigestLargeFont() = capture("notifications-settings-digest-font200") {
        SyncRows(
            SyncSettingsUi(digest = Digest(hours = listOf(8, 18))),
            onFrequency = {},
            onWifiOnly = {},
            onQuietHours = {},
            onDigest = {},
            onKinds = {},
        )
    }

    @Test
    fun syncSettingsDigest() = capture("notifications-settings-digest") {
        SyncRows(
            SyncSettingsUi(digest = Digest(hours = listOf(8, 18))),
            onFrequency = {},
            onWifiOnly = {},
            onQuietHours = {},
            onDigest = {},
            onKinds = {},
        )
    }
}
