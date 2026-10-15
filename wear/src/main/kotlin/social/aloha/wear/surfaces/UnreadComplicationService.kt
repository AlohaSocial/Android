// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.surfaces

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import social.aloha.wear.R
import social.aloha.wear.WearActivity
import social.aloha.wear.social.WatchSocial

/**
 * How many notifications are unread, on the watch face. The watch face asks on its own schedule (the
 * update period in the manifest); between asks the watch asks the server nothing.
 */
@AndroidEntryPoint
class UnreadComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var social: WatchSocial

    // signed out there is no count to show; a server that does not answer keeps the last one (null)
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? = when {
        !social.signedIn() -> NoDataComplicationData()
        else -> social.unreadCount()?.let { data(request.complicationType, it) }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, PREVIEW_COUNT)

    private fun data(type: ComplicationType, count: Int): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(count.toString()).build(),
            PlainComplicationText.Builder(resources.getQuantityString(R.plurals.wear_unread, count, count)).build(),
        ).setTapAction(open).build()
    }

    private companion object {
        const val PREVIEW_COUNT = 3
    }
}
