// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AmpStories
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.AmpStories
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The only way features reach an icon. Outlined for the resting state, filled
 * for the selected one, so state is carried by shape as well as colour.
 *
 * ponytail: backed by the frozen Material Icons set for now; swap the bodies for
 * Material Symbols vectors (Apache-2.0) without touching a caller.
 */
public object AlohaIcons {
    public val Home: ImageVector = Icons.Outlined.Home
    public val HomeSelected: ImageVector = Icons.Filled.Home
    public val Photos: ImageVector = Icons.Outlined.PhotoLibrary
    public val PhotosSelected: ImageVector = Icons.Filled.PhotoLibrary
    public val Video: ImageVector = Icons.Outlined.VideoLibrary
    public val VideoSelected: ImageVector = Icons.Filled.VideoLibrary

    // stacked portrait cards, as on Apple; Slideshow looks the same filled and outlined
    public val Shorts: ImageVector = Icons.Outlined.AmpStories
    public val ShortsSelected: ImageVector = Icons.Filled.AmpStories
    public val News: ImageVector = Icons.Outlined.Newspaper
    public val NewsSelected: ImageVector = Icons.Filled.Newspaper
    public val Audio: ImageVector = Icons.Outlined.Headphones
    public val AudioSelected: ImageVector = Icons.Filled.Headphones
    public val Notifications: ImageVector = Icons.Outlined.Notifications
    public val NotificationsSelected: ImageVector = Icons.Filled.Notifications
    public val Profile: ImageVector = Icons.Outlined.AccountCircle
    public val ProfileSelected: ImageVector = Icons.Filled.AccountCircle

    // statuses: every toggled action draws a different glyph when on, not only another colour
    public val Reply: ImageVector = Icons.AutoMirrored.Outlined.Reply
    public val Boost: ImageVector = Icons.Outlined.Repeat
    public val Boosted: ImageVector = Icons.Filled.RepeatOn
    public val Favourite: ImageVector = Icons.Outlined.StarBorder
    public val Favourited: ImageVector = Icons.Filled.Star
    public val Bookmark: ImageVector = Icons.Outlined.BookmarkBorder
    public val Bookmarked: ImageVector = Icons.Filled.Bookmark
    public val More: ImageVector = Icons.Outlined.MoreVert
    public val Reorder: ImageVector = Icons.Outlined.DragHandle
    public val Pinned: ImageVector = Icons.Outlined.PushPin
    public val VisibilityPrivate: ImageVector = Icons.Outlined.Lock
    public val VisibilityDirect: ImageVector = Icons.Outlined.Mail
    public val VisibilityUnlisted: ImageVector = Icons.Outlined.NightsStay
    public val Edited: ImageVector = Icons.Outlined.Edit
    public val ContentWarning: ImageVector = Icons.Outlined.WarningAmber
    public val ExpandMore: ImageVector = Icons.Outlined.ExpandMore
    public val ExpandLess: ImageVector = Icons.Outlined.ExpandLess
    public val Place: ImageVector = Icons.Outlined.Place
    public val Archived: ImageVector = Icons.Outlined.Inventory2
    public val Dislike: ImageVector = Icons.Outlined.ThumbDown
    public val Play: ImageVector = Icons.Filled.PlayArrow
    public val Pause: ImageVector = Icons.Filled.Pause
    public val Bot: ImageVector = Icons.Outlined.SmartToy
    public val Filtered: ImageVector = Icons.Outlined.FilterAlt
    public val Share: ImageVector = Icons.Outlined.Share
    public val CopyLink: ImageVector = Icons.Outlined.Link
    public val OpenInBrowser: ImageVector = Icons.Outlined.OpenInBrowser
    public val NewWindow: ImageVector = Icons.AutoMirrored.Outlined.OpenInNew
    public val Translate: ImageVector = Icons.Outlined.Translate
    public val MuteConversation: ImageVector = Icons.Outlined.NotificationsOff
    public val PauseNotifications: ImageVector = Icons.Outlined.NotificationsPaused
    public val Report: ImageVector = Icons.Outlined.Flag
    public val Delete: ImageVector = Icons.Outlined.Delete
    public val Redraft: ImageVector = Icons.Outlined.EditNote
    public val Add: ImageVector = Icons.Outlined.Add

    /** Closer to a picture, and back. */
    public val ZoomIn: ImageVector = Icons.Outlined.ZoomIn
    public val ZoomOut: ImageVector = Icons.Outlined.ZoomOut

    /** A video's sound off, and on. */
    public val Muted: ImageVector = Icons.AutoMirrored.Outlined.VolumeOff
    public val Unmuted: ImageVector = Icons.AutoMirrored.Outlined.VolumeUp

    /** An album, Pixelfed's collection of posts with pictures. */
    public val Album: ImageVector = Icons.Outlined.PhotoAlbum

    /** What is trending, and who is popular. */
    public val Explore: ImageVector = Icons.Outlined.Explore
    public val Sensitive: ImageVector = Icons.Outlined.VisibilityOff

    /** A post holding more than one picture, on its square in a grid. */
    public val Stack: ImageVector = Icons.Filled.Collections

    /** Show as a grid of squares; the feed is [Feed]. */
    public val Grid: ImageVector = Icons.Outlined.GridView
    public val Feed: ImageVector = Icons.Outlined.ViewAgenda
    public val Thread: ImageVector = Icons.AutoMirrored.Outlined.Subject
    public val Voted: ImageVector = Icons.Filled.CheckCircle

    /** The chosen one of a set of options. */
    public val Check: ImageVector = Icons.Filled.Check
    public val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    public val Close: ImageVector = Icons.Outlined.Close
    public val Search: ImageVector = Icons.Outlined.Search
    public val Recent: ImageVector = Icons.Outlined.History
    public val Hashtag: ImageVector = Icons.Outlined.Tag
    public val Lists: ImageVector = Icons.AutoMirrored.Outlined.List
    public val Group: ImageVector = Icons.Outlined.Groups
    public val Members: ImageVector = Icons.Outlined.People
    public val Send: ImageVector = Icons.AutoMirrored.Outlined.Send
    public val Story: ImageVector = Icons.Outlined.HistoryToggleOff
    public val Settings: ImageVector = Icons.Outlined.Settings
    public val Appearance: ImageVector = Icons.Outlined.Palette
    public val About: ImageVector = Icons.Outlined.Info
    public val Nextcloud: ImageVector = Icons.Outlined.Cloud
    public val Rules: ImageVector = Icons.AutoMirrored.Outlined.Rule
    public val NewPosts: ImageVector = Icons.Filled.ArrowUpward
    public val ArrowUpward: ImageVector = Icons.Filled.ArrowUpward
    public val MarkAllRead: ImageVector = Icons.Outlined.DoneAll
    public val ArrowDownward: ImageVector = Icons.Filled.ArrowDownward
    public val Timeline: ImageVector = Icons.Outlined.ViewAgenda
    public val AddAccount: ImageVector = Icons.Outlined.PersonAdd
    public val SignOut: ImageVector = Icons.AutoMirrored.Outlined.Logout

    // writing a post
    public val Compose: ImageVector = Icons.Outlined.Create
    public val VisibilityPublic: ImageVector = Icons.Outlined.Public
    public val Language: ImageVector = Icons.Outlined.Language
    public val Emoji: ImageVector = Icons.Outlined.EmojiEmotions
    public val Quote: ImageVector = Icons.Outlined.FormatQuote
    public val AddToThread: ImageVector = Icons.Outlined.AddComment
    public val Remove: ImageVector = Icons.Outlined.RemoveCircleOutline
    public val AddMedia: ImageVector = Icons.Outlined.AddPhotoAlternate
    public val AttachFile: ImageVector = Icons.Outlined.AttachFile
    public val Retry: ImageVector = Icons.Outlined.Refresh
    public val TextCard: ImageVector = Icons.Outlined.TextFields
    public val Trim: ImageVector = Icons.Outlined.ContentCut
    public val Camera: ImageVector = Icons.Outlined.PhotoCamera
    public val AttachMore: ImageVector = Icons.Outlined.AddCircleOutline
    public val Poll: ImageVector = Icons.Outlined.Poll
    public val Schedule: ImageVector = Icons.Outlined.Schedule
    public val AddReaction: ImageVector = Icons.Outlined.AddReaction
}
