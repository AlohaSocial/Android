# 10 — Intelligence

Both builds translate posts and draft alt text on the device; the Play build also rewrites a draft and summarises a thread there. Written from the code.

## Principles

- **Opt-in, one feature at a time.** Settings, Intelligence has a switch per feature, all off; there is no master switch. With them off, the app shows nothing of them.
- **Never automatic.** Each runs only on a tap on what it acts on. Nothing is posted, rewritten or replaced without the writer seeing it first.
- **Marked as generated.** What a model wrote is labelled where it is shown for review, and alt text left as drafted carries its mark to the server; once the writer edits it, the words are theirs and the mark goes.
- **On the device.** The text, pictures and threads these features read are not sent anywhere for them. Translation is the exception the reader chooses: it goes to their server where the server translates.

## Builds and availability

`:core:intelligence` holds what the features share: `Intelligence`, the availability (`ModelAvailability`: Available, NotReady, NotEligible), `EntityShield`, the alt-text assembly, the prompts, and the open picture reader every build uses (`OnDevicePictures`):

- **What is in a picture:** EfficientNet-Lite0 (int8, Apache-2.0, bundled in `core/intelligence/src/main/assets/` with its 1,000 ImageNet labels; source and checksum in [ACKNOWLEDGEMENTS.md](../ACKNOWLEDGEMENTS.md)) on LiteRT, Google's open-source TensorFlow Lite runtime. Labels it is at least 35 % sure of, at most six.
- **How many faces:** Android's own `android.media.FaceDetector`. A count, never whose.

The rest are optional Hilt bindings (`LanguageModel`, `PrivacyNotice`), each build binding what it has:

| | `generic` (F-Droid) | `gplay` (Google Play) |
|---|---|---|
| What is in a picture, faces | Open reader | Open reader |
| Rewrite, summaries | Not offered: they need a language model, and the only one on Android devices is Google's | Gemini Nano through ML Kit's Prompt API, run by Android's AICore service (`GeminiNano`) |
| Google libraries | None apart from the open-source LiteRT runtime | The same, plus ML Kit's Prompt API and what it brings |
| Privacy statement | Its own paragraph (`app/src/generic/res/values/strings.xml`) | Its own paragraph (`app/src/gplay/res/values/strings.xml`) |

Alt text is offered in every build. Rewriting and summaries need the model: on a device that cannot run it their switches say so, and while it is still fetching it they say that, turning one on starting the download. Nothing asks the device about its model before a switch that needs it is on: the Intelligence settings list the switches without asking, and rewriting and summaries ask whether the model is ready only once theirs is on, and again each time the composer or thread is back on screen (`Intelligence.ready`). Under the switches, Settings names the models in use, each only once its feature is on: the picture classifier with Draft alt text, and Gemini Nano, with the name AICore gives it (`Intelligence.modelName`), with rewriting or summaries. ML Kit does not start with the app (its start-up provider is removed from the Play manifest): it starts the first time it is asked or reads a picture (`MlKitStart`).

## Alt-text drafts

While a picture with a file on the device has no description, its card in the composer carries a sparkle that drafts one in place, and the media editor offers "Draft a description"; either reads the picture (`Intelligence.describe`, `AltText.assemble`): up to three things the classifier recognised and the number of faces, never anything about whom. Words written in a picture are not read: no open text recognition can be had from the build's repositories, and the Play build leaves out ML Kit's, whose native code would add some 39 MB. No language model sees the picture. The draft is at most 400 characters and ends with " (AI generated)"; the field shows the mark as a fixed suffix and counts it in the 1,500 characters, so a description left as drafted reaches the server with it. The writer's first edit drops the mark: the text has then been through human review and editorial control, and the person posting holds the editorial responsibility, which is where the AI Act asks for no disclosure ([EU guidance on labelling AI-generated content](https://digital-strategy.ec.europa.eu/en/policies/eu-icons-labelling-ai-generated-content), Article 50(4)). Keeping the mark on an unedited draft goes further than that minimum, so that a description nobody rewrote says where it came from. "Generated, please check" stands over a fresh draft until the writer changes it.

The warning about media without descriptions offers "Draft descriptions": every undescribed picture in turn, with progress said as it goes, and Stop (`AltTextDrafts`). A description typed meanwhile wins. A description carrying the mark that the writer has not looked at in its editor since the composer opened is unchecked; that follows from the description itself, so a draft kept and opened again, or one the app was closed over, is unchecked again. When a batch ends, and before posting, the composer says how many are unchecked; "Check them" opens each in turn, under "Generated, please check".

## Rewrite

With Rewrite on and the model ready, the composer's toolbar offers, while there is text: Proofread, Shorten (to the characters the post has left), Rephrase, Make friendlier, Make more formal, Make more concise (`RewriteStyle`). `EntityShield` swaps each link, mention and hashtag for a token before the model sees the text and puts them back after; a proposal that loses, doubles or invents a token, or writes out a mention, hashtag or link of its own, is turned down, because a rewrite that loses a mention sends the post to the wrong person and one that adds one sends it to someone new. A draft with brackets like the tokens' is not rewritten at all. One that would not fit, counted as the composer counts, or that the model cut off, is turned down too. The sheet shows the draft above the proposal, what changed marked word by word in each (struck through, and bold and underlined), and reads a screen reader the proposal and then what changed in words; with Replace, Copy and Cancel (`RewriteSheet`). A refusal is said plainly and not retried.

## Summaries

With Summarise on, the thread's menu offers "Summarise this thread" (`SummaryViewModel`). The model reads the posts as shown, in order, each with its author's handle; a post behind a content warning is read as its warning alone, and one the reader's filters fold away is left out. A thread longer than 6,000 characters is summarised from its start, and the sheet says how many of its posts it covers. The summary appears in a sheet labelled as generated, with "Read the full thread"; it is never kept and never shown in the thread.

## Translation

A post's menu offers Translate when the post names a language the reader does not read (the app's language, else the system's) and someone can translate it.

1. **The server first.** `POST /api/v1/statuses/{id}/translate` with the reader's language (`TranslationRepository`, `StatusEndpoints`), offered where the server's capabilities say it translates (`ServerCapabilities.offersTranslation`) and, where it lists its language pairs, only for a pair it can do. The translation takes the post's place in every screen until "Show original"; the line under it names the service when the server does.
2. **Then the device.** When the server cannot (no translation service, or it answers 503), and the device has a system translation service, the text is translated on the device instead (`DeviceTranslator`, `app/.../DeviceTranslator.kt`). It uses the platform's `android.view.translation` API from Android 12, which a Pixel serves through Android System Intelligence; no library is involved, so `generic` and `gplay` behave the same. Nothing leaves the device.

The thread's menu offers "Translate thread" while a post in it is foreign to the reader (`rememberThreadTranslation`). One tap translates each such post the same way, and the replies that arrive while the thread is open; a post turned back with Show original stays so, and "Show originals" ends it. Nothing is translated without the tap, and there is no automatic translation per account.

`TranslationsViewModel` decides between server and device. A translation is never stored ([04-data-model.md](04-data-model.md)). A server translation is made by the server or the service it uses; the developer receives nothing ([12-store.md](12-store.md)).

## Telemetry

The app has no analytics and no crash reporting of its own ([12-store.md](12-store.md)). What the on-device features could report, build by build:

| Part | Build | Runs in | Would report | Identifier | What the app does | Left to the reader |
|---|---|---|---|---|---|---|
| LiteRT with EfficientNet-Lite0 | both | the app | nothing: no reporting code, no address but its licences' and documentation's in the library | none | nothing needed | nothing |
| `android.media.FaceDetector` | both | the app | nothing: part of Android | none | nothing needed | nothing |
| Platform translation (`android.view.translation`) | both | Android's system translation service | nothing from the app | none | nothing needed | Android's own settings |
| ML Kit Prompt API (client) | `gplay` | the app | ML Kit's diagnostics and usage analytics: device maker, model, Android version and build, the app's package and version, latency, image format and size, feature version, events, error codes ([Google's disclosure](https://developers.google.com/ml-kit/android-data-disclosure)) | one per installation | removes the transport backend (`CctBackendFactory`) from the Play manifest: the uploader then has no destination and deletes what was queued, unsent ("Unknown backend …, deleting event batch", `transport-runtime` 3.1.9) | nothing |
| Gemini Nano in AICore | `gplay` | Android's AICore service | whatever AICore reports; it is part of Android, not of the app | AICore's | nothing it can do | Android's usage and diagnostics settings |

Until a feature is on, none of the Play build's ML Kit parts run, so none has anything to report. How this was checked: the merged release manifests (no Google component in `generic`; no `MlKitInitProvider` and no `CctBackendFactory` in `gplay`), `generic`'s runtime classpath (no ML Kit, Play services, Firebase or data transport), LiteRT's and the bundled models' binaries for network addresses, and the transport uploader's bytecode for what it does without a backend. The removal is not a setting Google offers: every ML Kit update is checked again the same way before it is merged.

## Deliberately absent

No generated posts, replies or messages; no ranking or re-ordering of feeds; no sentiment analysis, scoring or automated moderation; no generated images; no "explain this post".
