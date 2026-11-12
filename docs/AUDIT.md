# Bytephase debugging and performance notes

Date: 2026-11-12 · Version: 0.2.0 · Active modules: 88

## Scope and fixes

- Failed config writes retain pending settings and retry after 20 client ticks.
  Successful writes clear pending state. Temporary files are unique and cleaned
  up after success or failure.
- Reloading a repaired config allows saving again and applies explicit disabled
  module states. Files missing module data cannot change GUI layout.
- HUD, world, attack and packet dispatch skip modules disabled after a dispatch
  list was captured. Shader queues are cleared if shader setup fails.
- BlockESP avoids displaying results from a previous world.
- Every registered module has a keybind translation. Runtime coverage detects
  missing labels.

## Module curation

Removed 77 module implementations that supplied automated combat, reach changes,
movement amplification, packet spoofing/buffering, collision bypasses or
interaction-rule overrides. Removed their engine hooks and unused helpers.
The current catalog has 88 modules, with no hidden registrations for retired
features. Old configs/profiles cannot reactivate removed modules.

The remaining aim assistance requires held attack, visible targets, window
focus and normal interaction reach. AutoClicker requires held attack and the
full vanilla attack cooldown. Movement aids use ordinary input. Inventory,
navigation, discovery and visual tools remain available.

NBT decoding keeps vanilla's size tracker; the unlimited-NBT option and hook
have been removed.

## Discovery additions

SusChunks supports Growth, Geode, WeightedGrowth and Activity modes.
Weighted growth assigns small/medium/large buds and clusters 1/2/3/4 points
when attached to budding amethyst. Activity has opt-in kelp, cave-vine, vine,
bamboo, full bee-nest, mature-cocoa, rotated-deepslate and enclosed-shaft checks.

Scans remain on the client thread, use loaded terrain, validate chunk revisions
and apply bounded queues and time budgets. Disabled activity checks are excluded
from palette filtering. The evidence is heuristic; natural terrain and growth
can produce candidates.

## Performance changes

BlockESP resolves the loaded chunk once per column slice, reuses its lookup
position and clamps vertical traversal to world build limits. Traversal targets
a 0.8 ms budget, checks time every 16 blocks and visits at most 128 columns per
tick. Paused columns resume without skipping blocks. Results remain capped at
1,024 and use a nearest-first spiral.

BaseFinder and SusChunks maintain marker lists on ticks and reuse them between
ticks when packet revisions are unchanged. A new block/chunk revision validates
the cache before the next render. Cache construction and the captured revision
use the same lock.

Measured in a stationary Fabric test world using Spawner mode:

| Horizontal / vertical range | Traversed columns | Scan slices | Previous minimum at 6 columns/tick |
| --- | ---: | ---: | ---: |
| 8 / 16 | 289 | 3 | 49 |
| 64 / 48 | 16,641 | 131 | 2,774 |

These measurements include skipped unloaded columns. Previous values are
calculated from the former cap. Dense terrain, Ore mode and a busy client can
need more slices. Publishing results and other module work are outside the
traversal budget. This is not an FPS or heap memory benchmark.

The code remains Java. Minecraft's world, entity and rendering APIs are Java;
a Rust port would require JNI calls or copied snapshots. Native code should be
considered for an isolated computationally expensive task only after profiling shows a
benefit. Fewer module instances, deleted implementations and reused buffers
address the current unnecessary work without an additional native runtime.

## Verification

Command: `./gradlew clean build runClientGameTest`.

`OptimizationRegressionChecks` covers config write failure/retry, malformed-file
preservation/recovery, disabled-state reloads, GUI preservation, callback
invalidation, paused-column resumption, immutable results, scan caps/ranges,
unchanged render-cache reuse and packet invalidation.

`DiscoveryRegressionChecks` covers signal opt-in, mature cocoa, full bee nests,
deepslate axes, maturity weights, combined activity evidence, evidence removal,
enclosed shafts and broken shaft walls.

`UtilityRegressionTest` also checks the current catalog, rejection of
unregistered modules, eating/input restoration, keybinds, GUI and rendering in
an isolated temporary world. Source/JAR inspection checks that removed classes
and hooks are absent and that registered modules, mixins and translations match.

Final clean build and client tests passed. The release JAR is 709,995 bytes
(about 24% smaller than the prior 930,194-byte release), with 213 class files.
All 77 retired module implementations are absent from the JAR. There are no
missing/duplicate registrations, missing/stale translations or unmatched mixins.
