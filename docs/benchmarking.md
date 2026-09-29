# Benchmarking

The budgets are in the table below. They are gated on a physical
device; a CI emulator run (`.github/workflows/ui.yml`) is a smoke test only.

## Device-lab run

A Pixel 6a class device, charged, screen on, developer options with "Stay awake".

```sh
# 1. baseline and startup profiles (writes app/src/<flavor>/generated/baselineProfiles)
./gradlew :app:generateGenericReleaseBaselineProfile
# 2. startup: TTID and TTFD, cold, with the profile installed
./gradlew :benchmark:connectedGenericBenchmarkReleaseAndroidTest
```

The results land in
`benchmark/build/outputs/connected_android_test_additional_output/`. Copy the
medians into `config/benchmark-baseline.json` (created by the first run) in the
same pull request that changes the startup path; CI compares its emulator smoke
against that file with a 20 % tolerance.

## What is measured

| Benchmark | Metric | Budget |
|---|---|---|
| `StartupBenchmark.coldStartup` | TTID, TTFD (`reportFullyDrawn()` after the first timeline row, from Phase 2) | TTID ≤ 800 ms P50, TTFD ≤ 1.2 s P50 |
| timeline scroll (Phase 2) | `FrameTimingMetric` `frameOverrunMs` | P90 ≤ 0 ms, P99 ≤ 8 ms |
