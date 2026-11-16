// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.interests

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.ActivityLearning
import social.aloha.core.model.InterestsState
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.InterestEndpoints
import social.aloha.core.network.endpoints.RecommendationEndpoints

/**
 * The hashtags Aloha Social has learnt the reader cares about, from what they read and the
 * hashtags they follow: seen, or all forgotten; and whether it learns at all. The server alone
 * decides which they are. A server without interests answers 404, which the screen takes as nothing
 * to show. Every change answers with the whole state as it then is.
 */
@Singleton
public class Interests @Inject constructor(private val clients: ClientFactory) {
    public suspend fun state(reader: SignedInAccount): Answer<InterestsState> =
        clients.answer(reader, InterestEndpoints.state())

    /** Forgets everything learnt. */
    public suspend fun reset(reader: SignedInAccount): Answer<InterestsState> =
        clients.answer(reader, InterestEndpoints.reset())

    public suspend fun learn(reader: SignedInAccount, learning: Boolean): Answer<InterestsState> =
        clients.answer(reader, InterestEndpoints.settings(learning = learning))

    public suspend fun pause(reader: SignedInAccount, paused: Boolean): Answer<InterestsState> =
        clients.answer(reader, InterestEndpoints.settings(paused = paused))

    /** The For You activity-learning consent; 404 where the server's newer recommendations are off. */
    public suspend fun activityLearning(reader: SignedInAccount): Answer<ActivityLearning> =
        clients.answer(reader, RecommendationEndpoints.preferences())

    /** Changes it as of [current]; a change made elsewhere meanwhile is refused with a 409. */
    public suspend fun learnActivity(
        reader: SignedInAccount,
        current: ActivityLearning,
        learning: Boolean,
    ): Answer<ActivityLearning> = clients.answer(reader, RecommendationEndpoints.learning(current.revision, learning))
}
