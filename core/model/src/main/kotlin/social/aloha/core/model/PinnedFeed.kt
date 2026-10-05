// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * A feed the reader keeps on Home, in the order they keep them. [name] and [icon] are what the reader
 * chose in place of the kind's own; null shows the kind's.
 */
public data class PinnedFeed(val kind: Kind, val name: String? = null, val icon: String? = null) {
    /** Where its rows come from, which is also what makes two feeds the same feed. */
    val source: TimelineSource get() = kind.source

    val id: String get() = source.storageKey

    public sealed interface Kind {
        public val source: TimelineSource

        public data object Following : Kind {
            override val source: TimelineSource get() = TimelineSource.Home
        }

        public data object ThisServer : Kind {
            override val source: TimelineSource get() = TimelineSource.Local
        }

        public data object Everyone : Kind {
            override val source: TimelineSource get() = TimelineSource.Federated
        }

        /** The posts of the people whose bell the reader rang on their profile. */
        public data object Notified : Kind {
            override val source: TimelineSource get() = TimelineSource.Notified
        }

        public data object Bookmarks : Kind {
            override val source: TimelineSource get() = TimelineSource.Bookmarks
        }

        public data object Favourites : Kind {
            override val source: TimelineSource get() = TimelineSource.Favourites
        }

        public data class List(val id: String, val title: String) : Kind {
            override val source: TimelineSource get() = TimelineSource.List(id)
        }

        public data class Hashtag(val tags: TimelineSource.Hashtag) : Kind {
            override val source: TimelineSource get() = tags
        }

        /** Another server's own public posts, by its [domain]. */
        public data class Remote(val domain: String) : Kind {
            override val source: TimelineSource get() = TimelineSource.Remote(domain)
        }
    }

    public companion object {
        /**
         * What Home holds before the reader pins anything: Following, then this server and everyone where
         * the server serves them, the feed Home used to open on first.
         */
        public fun defaults(capabilities: ServerCapabilities, opens: TimelineSource): List<PinnedFeed> {
            val served = buildList {
                add(PinnedFeed(Kind.Following))
                if (capabilities.localFeed) add(PinnedFeed(Kind.ThisServer))
                if (capabilities.federatedFeed) add(PinnedFeed(Kind.Everyone))
            }
            return served.sortedByDescending { it.source == opens }
        }
    }
}
