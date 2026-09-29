// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun accountsDatabase(@ApplicationContext context: Context): AccountsDatabase =
        Room.databaseBuilder(context, AccountsDatabase::class.java, AccountsDatabase.FILE_NAME).build()

    @Provides
    fun accountDao(database: AccountsDatabase): AccountDao = database.accountDao()
}
