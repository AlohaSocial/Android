// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The preview set every screen ships: light, dark, 200 % font, right to
 * left and a tablet. Screenshot tests render the same configurations.
 */
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Font 200 %", fontScale = 2f)
@Preview(name = "Right to left", locale = "ar")
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240")
public annotation class AlohaPreviews
