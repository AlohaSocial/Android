// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import social.aloha.core.html.RichTextCache

/** A scope that lives as long as the process, for state shared across screens. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One parse cache for the process, so a post seen on two screens is parsed once. */
    @Provides
    @Singleton
    fun richTextCache(): RichTextCache = RichTextCache()
}
