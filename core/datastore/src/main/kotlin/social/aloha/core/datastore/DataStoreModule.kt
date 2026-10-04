// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import social.aloha.core.model.LogArea
import timber.log.Timber

/** The vault's bytes, stored as they are; the content is ciphertext. */
internal object BlobSerializer : Serializer<ByteArray> {
    override val defaultValue: ByteArray = ByteArray(0)

    override suspend fun readFrom(input: InputStream): ByteArray = try {
        input.readBytes()
    } catch (e: java.io.IOException) {
        throw CorruptionException("unreadable vault", e)
    }

    override suspend fun writeTo(t: ByteArray, output: OutputStream) {
        output.write(t)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    // files/vault is excluded from cloud backup and device transfer by the app's backup rules.
    @Provides
    @Singleton
    fun tokenVault(@ApplicationContext context: Context): TokenVault = TokenVault(
        store = DataStoreFactory.create(BlobSerializer) { File(context.filesDir, "vault/secrets.bin") },
        cipher = KeystoreCipher(),
        ioDispatcher = Dispatchers.IO,
    )

    // one store for the file, which both kinds of the device's preferences read
    @Provides
    @Singleton
    @AppStore
    fun appStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("app") }

    @Provides
    @Singleton
    fun appPreferences(@AppStore store: DataStore<Preferences>): AppPreferences = AppPreferences(store)

    @Provides
    @Singleton
    fun modePreferences(@AppStore store: DataStore<Preferences>): ModePreferences = ModePreferences(store)

    @Provides
    @Singleton
    fun readingPreferences(@AppStore store: DataStore<Preferences>): ReadingPreferences = ReadingPreferences(store)

    @Provides
    @Singleton
    fun notificationPreferences(@AppStore store: DataStore<Preferences>): NotificationPreferences =
        NotificationPreferences(store)

    @Provides
    @Singleton
    fun appLockPreferences(@AppStore store: DataStore<Preferences>): AppLockPreferences = AppLockPreferences(store)

    // an unreadable file starts over from the defaults rather than failing every screen that reads it
    @Provides
    @Singleton
    fun accountSettings(@ApplicationContext context: Context): AccountSettingsStore = AccountSettingsStore(
        DataStoreFactory.create(
            AccountSettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { startOver("Account settings", it) },
        ) { File(context.filesDir, "datastore/account_settings.json") },
    )

    // mentions can be private: kept where no backup reaches, and gone with the data if the file breaks
    @Provides
    @Singleton
    fun widgetFeed(@ApplicationContext context: Context): WidgetFeedStore = WidgetFeedStore(
        DataStoreFactory.create(
            WidgetFeedSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { startOver("Widget feed", it) },
        ) { File(context.noBackupFilesDir, "widget_feed.json") },
    )

    private fun <T> startOver(what: String, e: CorruptionException): Map<String, T> {
        Timber.tag(LogArea.App.name).w("%s unreadable, started over: %s", what, e.cause?.javaClass?.simpleName)
        return emptyMap()
    }
}

/** The device's preferences file, which [AppPreferences] and [ModePreferences] share. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AppStore
