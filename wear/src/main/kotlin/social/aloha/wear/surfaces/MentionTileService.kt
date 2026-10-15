// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.surfaces

import android.content.ComponentName
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import social.aloha.core.model.Visibility
import social.aloha.wear.R
import social.aloha.wear.WearActivity
import social.aloha.wear.social.WatchNotice
import social.aloha.wear.social.WatchPost
import social.aloha.wear.social.WatchSocial

/** The newest mention, a swipe from the watch face; a tap opens the app. */
@AndroidEntryPoint
class MentionTileService : TileService() {
    @Inject lateinit var social: WatchSocial

    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val shown = if (social.signedIn()) {
                social.latestMention()?.let(::shown) ?: getString(R.string.wear_tile_no_mention)
            } else {
                getString(R.string.wear_tile_signed_out)
            }
            val open = ModifiersBuilders.Clickable.Builder()
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(packageName)
                                .setClassName(
                                    ComponentName(this@MentionTileService, WearActivity::class.java).className,
                                )
                                .build(),
                        ).build(),
                ).build()
            val layout = materialScope(this@MentionTileService, request.deviceConfiguration) {
                primaryLayout(
                    titleSlot = { text(getString(R.string.wear_tile_title).layoutString) },
                    mainSlot = { text(shown.layoutString, maxLines = MAIN_LINES) },
                    onClick = open,
                )
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                // the system asks again after this; the watch never asks on its own
                .setFreshnessIntervalMillis(TimeUnit.MINUTES.toMillis(FRESH_MINUTES))
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    /** A private message says who sent it and no more: a tile shows next to the watch face. */
    private fun shown(mention: WatchNotice): String = when (mention.post?.visibility) {
        Visibility.Direct -> getString(R.string.wear_tile_private, mention.who)
        else -> getString(R.string.wear_tile_mention, mention.who, mention.post?.let(::shownText).orEmpty())
    }

    /** A post behind a content warning shows the warning, as everywhere else. */
    private fun shownText(post: WatchPost): String =
        if (post.warning.isBlank()) post.text else getString(R.string.wear_content_warning, post.warning)

    override fun onTileResourcesRequest(
        request: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture { ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build() }

    private companion object {
        const val RESOURCES = "1"
        const val FRESH_MINUTES = 30L
        const val MAIN_LINES = 3
    }
}
