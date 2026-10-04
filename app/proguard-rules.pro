# SPDX-FileCopyrightText: 2026 Aloha Social contributors
# SPDX-License-Identifier: MIT
#
# kotlinx.serialization, Hilt and Compose ship their own consumer rules. The app
# keeps the Navigation 3 keys' serializers, which restore the back stack after
# process death, removes Timber's verbose and debug calls with their arguments, and
# keeps the names of exceptions and API errors, which log lines carry.
-keep,includedescriptorclasses class social.aloha.core.navigation.**$$serializer { *; }
-keepclassmembers class social.aloha.core.navigation.** {
    *** Companion;
}
-keepnames class * extends java.lang.Throwable
-keepnames class social.aloha.core.network.ApiError$*
-assumenosideeffects class timber.log.Timber$Forest {
    public void v(...);
    public void d(...);
}
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
}
