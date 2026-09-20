# Performance-test build

This opt-in build adds an automated client/server benchmark. It is intentionally not enabled in
the normal mod JAR.

## Build

```powershell
.\gradlew.bat performanceTestJar
```

Install `build/libs/create_echo_radars-1.0.0-performance-test.jar` on both the client and server in
place of the normal Create Echo Radars JAR. Keep the same Create: Radars and Create dependencies as
the normal mod.

## Run

Join as an operator and run:

```text
/echo_radars_perf start
```

The command moves the player to the bundled void dimension and runs:

1. a no-display baseline;
2. synthetic monitor rendering for every sonar mode at 16, 64, 144, and 256 displays;
3. real `SonarVoxelDda` ray traversal, doubling the rays from 256 until average traversal time is
   at least 50 ms per server tick, p95 is at least 75 ms, or client frame telemetry detects visible
   slowdown (with a safety limit of 262,144 rays).

Display snapshots are generated directly and contain no sonar block and no world tracing. This
keeps the display benchmark separate from the ray benchmark.

### Ray-only tests

`/echo_radars_perf rays` skips displays and runs the raw voxel DDA benchmark. This measures
traversal arithmetic only, with no block reads or Sable physics; it does not measure the benefit
of skipping known-water sections.

`/echo_radars_perf water` skips displays and compares the classic and optimized traversal on
identical immutable Minecraft block snapshots: a water volume surrounded by stone. CSV phases
are `water_reference` and `water_optimized`. Hit checksums must match. After the reference reaches
the lag threshold, only the optimized path continues at higher ray counts. Neither path runs
Sable physics or edits the world to create the water volume. This is a controlled snapshot
benchmark, not a measurement of total in-game sonar cost or densely mixed terrain.

Both commands use the same temporary dimension, chat/console/CSV reporting and player restoration.

### Correctness and standalone timing

```powershell
.\gradlew.bat test traceTest -PtraceBenchmark=true
```

The tests compare exact voxel hits, water/air/unknown boundaries, palette snapshot immutability,
and sparse Sable snapshots with translated, rotated and scaled poses. The optional timing output
is diagnostic; timing is not used as a pass/fail assertion. `build` runs correctness tests too.

Progress and results are written to player chat, the server console, and a CSV file under:

```text
<world>/create_echo_radars-performance/performance-YYYYMMDD-HHMMSS.csv
```

Use `/echo_radars_perf status` for the current stage or `/echo_radars_perf stop` to stop early. On
completion or manual stop, the test blocks are restored and the player is returned to the original
dimension, position, and game mode.
