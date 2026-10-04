# Benchmarking

The budgets are in the table below. They are gated on a physical
device; a CI emulator run (`.github/workflows/ui.yml`) is a smoke test only
and compares nothing, because emulator timings say nothing about a phone's.

## Device-lab run

A Pixel 6a class device, charged, screen on, developer options with "Stay awake".
Install any build of the app, sign in and let the home timeline load: the scroll
journeys need an account and skip themselves without one. Every build shares the
debug signing key, so the benchmark builds install over it and keep the account.

```sh
# the tasks uninstall the app when they finish unless told otherwise, which would sign it out
KEEP=-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
# 1. baseline and startup profiles (writes app/src/main/generated/baselineProfiles); both flavours run
#    the same code, so the merged profile serves every variant
./gradlew :app:generateBaselineProfile $KEEP
# 2. startup (TTID, TTFD) and timeline scrolling, with the profiles installed
./gradlew :benchmark:connectedGenericBenchmarkReleaseAndroidTest $KEEP
```

With more than one device attached, set `ANDROID_SERIAL` to the device's serial first. The profiles record which code runs, not how fast, so an emulator generates them as well as a phone; the timings below need the phone.
The results land in
`benchmark/build/outputs/connected_android_test_additional_output/`. Record the
medians in `config/benchmark-baseline.json`, with the device, in the pull request
that changes the startup or scrolling path, and compare the next run against it
by hand.

## What is measured

| Benchmark | Metric | Budget |
|---|---|---|
| `StartupBenchmark.coldStartup` | TTID, TTFD (`reportFullyDrawn()` once the first timeline row, or the sign-in screen, is drawn) | TTID ≤ 800 ms P50, TTFD ≤ 1.2 s P50 |
| `TimelineScrollBenchmark.scrollHome` | `FrameTimingMetric` `frameOverrunMs`, 30 swipes down the cached home timeline and back, 5 times | P90 ≤ 0 ms, P99 ≤ 8 ms |

## Last device-lab run

2026-09-30 on a OnePlus CPH2653 (Snapdragon 8 Elite, Android 16), the only phone
available; it is much faster than the Pixel 6a class, so every figure is optimistic.

| Metric | Median |
|---|---|
| TTID | 297 ms |
| TTFD | 535 ms |
| `frameOverrunMs` P90 / P99 | −4.9 ms / −1.4 ms |
| Frames over budget | 0.29 % (5 of 1,716) |
