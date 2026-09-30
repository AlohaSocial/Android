// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

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

    @Provides
    @Singleton
    fun appPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("app") })

    // an unreadable file starts over from the defaults rather than failing every screen that reads it
    @Provides
    @Singleton
    fun accountSettings(@ApplicationContext context: Context): AccountSettingsStore = AccountSettingsStore(
        DataStoreFactory.create(
            AccountSettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { AccountSettingsSerializer.defaultValue },
        ) { File(context.filesDir, "datastore/account_settings.json") },
    )

    // mentions can be private: kept where no backup reaches, and gone with the data if the file breaks
    @Provides
    @Singleton
    fun widgetFeed(@ApplicationContext context: Context): WidgetFeedStore = WidgetFeedStore(
        DataStoreFactory.create(
            WidgetFeedSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { WidgetFeedSerializer.defaultValue },
        ) { File(context.noBackupFilesDir, "widget_feed.json") },
    )
}
