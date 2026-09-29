# SPDX-FileCopyrightText: 2026 Aloha Social contributors
# SPDX-License-Identifier: MIT
#
# kotlinx.serialization, Hilt and Compose ship their own consumer rules. The only
# app rule keeps the Navigation 3 keys' serializers, which restore the back stack
# after process death.
-keep,includedescriptorclasses class social.aloha.core.navigation.**$$serializer { *; }
-keepclassmembers class social.aloha.core.navigation.** {
    *** Companion;
}
