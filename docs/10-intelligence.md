# 10 — Intelligence

Of the Apple app's on-device AI features, the Android app has translation, and nothing else yet. Written from the code.

## Translation

A post's menu offers Translate when the post names a language the reader does not read (the app's language, else the system's languages) and someone can translate it.

1. **The server first.** `POST /api/v1/statuses/{id}/translate` with the reader's language (`TranslationRepository`, `StatusEndpoints`), offered where the server's capabilities say it translates (`ServerCapabilities.offersTranslation`) and, where it lists its language pairs, only for a pair it can do. The translation takes the post's place in every screen until "Show original"; the line under it names the service when the server does.
2. **Then the device.** When the server cannot (no translation service, or it answers 503), and the device has a system translation service, the text is translated on the device instead (`DeviceTranslator`, `app/.../DeviceTranslator.kt`). It uses the platform's `android.view.translation` API from Android 12, which a Pixel serves through Android System Intelligence; no library is involved, so `generic` and `gplay` behave the same. Nothing leaves the device.

`TranslationsViewModel` decides between the two. A translation is never stored ([04-data-model.md](04-data-model.md)). A server translation is made by the server or the service it uses; the developer receives nothing ([12-store.md](12-store.md)).

## Not built

Rewriting a draft, drafting alt text for a picture, and summarising a thread are not built. On Android they would need an on-device model, which today means Google's (Gemini Nano through AICore or ML Kit's generative APIs), and those are Google libraries: they would go in the `gplay` flavour only, with `generic` staying free of them, and the Play Data safety answers would be revisited. Whatever is built stays opt-in, and a generated text is an editable draft, marked as generated, never posted unreviewed.
