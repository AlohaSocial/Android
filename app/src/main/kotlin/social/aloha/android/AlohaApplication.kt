// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Nothing else starts here: every other component initialises on first use, off the startup path. */
@HiltAndroidApp
class AlohaApplication : Application()
