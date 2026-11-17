# Bytephase debugging and performance notes

## Current 0.2.4 — screen initialization crash repair

The reported Fabric Screen API crash ticked vanilla ConfirmScreen
(`net.minecraft.class_410`) before its screen events were initialized.
The crash stack identifies the invalid screen, but does not identify the
code that requested it.

MinecraftClient.setScreen is now wrapped before its HEAD hooks. Worker-thread
requests are queued onto the client thread, so currentScreen publication,
screen removal, imported-screen adaptation and initialization run together
there. The first 16 off-thread requests log their caller stacks for diagnosis.
A startup `Bytephase screenfix 2026-11-16` line identifies this build even while
the displayed mod version remains 0.2.4.

StreamProof no longer assigns currentScreen to null and restores a saved
screen reference. Render-only filters in GameRenderer and InGameHud omit the
private screen during the clean pass. This keeps the actual screen and its
Fabric lifecycle intact and avoids restoring a stale screen after a transition.

Production compilation/remapping are the checks for this update. The friend's
confirmation-dialog crash has not been reproduced in the Minecraft client.

## Current 0.2.4 — inventory totem input and Freelook perspective repair

Auto Inventory Totem opens a dedicated inventory screen that consumes keyboard,
click, drag and scroll input. Number keys pressed to select combat items cannot
swap a hovered inventory stack into the hotbar while the automatic refill is
running. Escape cancels and closes that screen. The module tracks the exact
screen it opened when deciding whether to close it.

Hotbar Refill fills only an empty configured slot. It cannot move anchors,
glowstone, weapons or another occupied stack into the inventory. Before sending
the automatic click, the source is rechecked as a totem and the destination as
empty; a missing menu slot does not count as a successful refill.

Freelook saves the current perspective on activation, switches to third-person
back, and restores the saved perspective on disable or shutdown. Camera angles
are initialized again if the player instance changes while Freelook is active.

Gameplay confirmation remains pending.

## Current 0.2.4 — UI customization, 2026-11-16

Adds a local image background with native file selection, path entry,
Cover/Contain/Stretch, opacity, reload and removal controls. Image decoding and
pixel conversion run on a daemon worker; uploads run on the client thread.
Input size is bounded, large images are subsampled before texture upload, and
the texture is cached. Request generations discard superseded loads and release
their native images. Normal image UVs are kept separate from framebuffer preview
UVs. UI image resources are released at shutdown.

All native color controls open an HSV/RGBA picker with hex input, live preview,
Save and Cancel. The default UI palette is black/silver/gray/white. Config v10
migrates untouched old purple/lavender appearance defaults once; other saved
colors remain configurable. The logo uses separate background and mark masks:
the background/wordmark follow the accent and the mark uses a contrasting color.
Existing presets are replaced by custom color controls.

Production compilation has passed. Native chooser interaction, visual behavior
and gameplay have not been exercised in the Minecraft client.

## Current 0.2.4 — Combat update and persistence audit, 2026-11-16

The 25 combat modules in the combat source archive replace their matching Bytephase
implementations. Other combat modules remain registered, including AutoArmor,
AutoClicker, AutoWeapon, SprintReset, MarlowOptimizer, SwordObsidian and the
additional engine combat utilities. The import uses Bytephase's existing
settings, keybinds, friend list, native HUD and event infrastructure. Source
hashes are recorded in [COMBAT_SOURCE_MAP.json](COMBAT_SOURCE_MAP.json). The component
license text is included in the nested PvP JAR.

GlowESP is a separate Visuals module. Its silhouette, stroke, aura,
fill, sheen, breathing, distance and entity-limit controls are retained.
Theme colors use Bytephase's accent. The render hook supplies the real frustum
and runs once at the world render stage. With Iris shaders active,
through-wall box outlines are used. Clean capture skips and clears pending
overlays. Disabled world handlers avoid the render dispatch entirely.

The imported SpearSwap keeps the staged equip, charge wait, consumed action
key and original-slot restoration repairs. The old frame-based Triggerbot
callback and CrystalMacro placement hook were removed because the supplied
combat implementations use different control paths.

BlockOverlay replaces the previous native Highlight implementation.
The existing Highlight name and toggle shortcut are retained. Smooth movement,
fade, theme/gradient/solid/rainbow colors, outline glow, face-only selection and
mining-progress fill use the current implementation. Vanilla's outline is hidden
only while the replacement is enabled, and remains available during clean
capture. Misses, air, unloaded chunks and empty shapes are rejected.
Outline segments and face geometry are cached; animated colors continue to
update each frame without rebuilding segment arrays or boxing color values.

Config writes retain unknown settings and modules, including controls from
earlier implementations. Range controls restore both bounds together:
loading a minimum before a higher maximum no longer loses the new minimum
against the old maximum. Named-profile rollback also restores paired ranges.
Explicit saves from worker threads are handed to the client thread.

FakeLag retains session cleanup, binds each delayed packet to its connection,
player and world, and drains the queue at its 1024-packet threshold. Replayed
packets use a thread-local bypass. The aim-cone check uses a dot product rather
than computing an inverse cosine for every candidate.

The prior structured-profile, autosave deadline, cached dispatch, preview
cleanup and asynchronous schematic repairs are retained. The source was
backed up before the import. Production compilation and remapping have passed;
gameplay confirmation and FPS measurements have not been run.

## Current 0.2.4 — SpearSwap action repair

The action now consumes its configured key or mouse press during gameplay so
it cannot also select a vanilla hotbar slot, attack, use or drop an item.
Release events remain available to clear vanilla input state. The original
hotbar slot is captured at the action press and passed explicitly to the
restoration helper. Spear selection searches for the actual Lunge-enchanted
item; it does not use the action key as an inventory index.

Equipping a spear resets attack charge. Standard spears require a full charge
lasting approximately 13–23 ticks, exceeding the earlier 10-tick prepared-action
timeout. The windup deadline now follows the equipped weapon's minimum charge
and attack speed, with a bounded grace period. Selection is sent before waiting.
The action stabs once when ready and restores the original slot on the next
eligible tick when Switch Back is enabled. Manual slot changes cancel it.

Production compilation/remapping are the build checks; another in-game lunge
has not been verified.

## Current 0.2.4 — config, combat timing and HUD update

Profiles now accept bounded strict JSON arrays and null values used by imported
feature data and HUD layouts. Typed scalar settings retain their individual
validation. The depth limit supports nested feature editors. Bloodhound's data
loader replaces the previous saved data instead of merging it into a profile.

Autosave retains the one-second quiet period and adds a five-second deadline
for continuous changes. Requests from worker threads are handed to the client
thread. Combat settings changed by their own features now request persistence.

SpearSwap stages selection before stabbing, rechecks the actual held spear and
Lunge enchantment, and waits for vanilla to update equipped-item state. Shared
slot restoration tracks the player instance so respawns/world changes cannot
restore another player's previous slot.

Imported dispatch caches the classes that implement each handler, retaining
registration order and enabled checks. Toggle reflection is cached at startup.
HUD text bounds are measured periodically during gameplay and every frame while
editing. Pending GUI previews are released when their screen is removed.
Schematic loads use a generation per request, preventing older jobs from
overwriting newer selections; folder reads return immutable snapshots.
Discord handshake readers are daemon threads that are shut down after use;
Unix domain sockets use channel streams.

Adds the HUD editor, Keystrokes, Compass and InventoryHUD for a 210-module
catalog. ViewModel has independent hand transforms. PerfectWindCharge has
silent downward aim and timing/return controls; Shield Breaker has four delay
settings. See [HUD and control instructions](HUD_EDITOR.md).

Highlight requires a BLOCK hit, a loaded chunk, a non-air state and a nonempty
outline. It renders and caches the actual outline boxes instead of a full cube,
so missed rays and empty parts of non-cube blocks do not create cube highlights.

Gameplay checks and FPS measurements have not been run. A successful production
build establishes compilation and packaging, not runtime behavior.

## Current 0.2.4 — startup catalog repair

The reported client-entrypoint failure requested engine-features.json while
the delivered JAR contained bytephase-features.json. The catalog now has the
same engine-features.json name in source, processed resources and the loader.
The resources task declares the inventory as an input and checks that it is
present in its output. A clean production build and remapping passed.
The repaired JAR's inventory path and listed feature classes were inspected;
another game launch remains unverified.

## Current 0.2.4 — branding and FPS update

Renames client branding, source namespaces, helper classes, shader identifiers
and logo textures to Bytephase. Required legal attribution remains in bundled
notices. Prior saved engine files, identifier values, module names and shortcut
keys have migration/compatibility handling.

Engine settings mirrors use a change revision and periodic poll, with forced
synchronization before saving. Empty notification layers skip GPU setup. The UI
caches module searches and font measurements, reuses pixel-snap scratch storage,
uses four cached 30 Hz blur passes and limits previews to cached 30 Hz frames at
960×640. Dragging/zooming bypasses the preview cache. Gameplay FPS measurements
have not been run.

The clean build and remapping passed. The delivered 0.2.4 JAR has no previous
client branding in entry paths or runtime content outside required license
notices. It replaced 0.2.10 in the Prism instance, with source and JAR backups.


## Version 0.2.10 — 2026-11-13

Imported screen requests are replaced at Minecraft's screen boundary with the
native Bytephase interface. A tail guard also catches another hook replacing
the requested screen later. The old screen's widgets and layout are never
initialized or rendered. Data controllers retain a client reference for their
callbacks. The inventory input-lock screen now lives in Bytephase's namespace
and preserves Minecraft's inventory rendering.

Item lists and radio choices have searchable native editors; configurations,
friends and palettes open Bytephase's existing pages. Nested feature data uses
native forms with Apply, add/remove entries and child navigation. Text entry,
crafter presets, material details, trip summaries and client information use
the same liquid-glass UI. Preview controls export data-only descriptors, and
knife/schematic previews supply model textures to Bytephase's rounded image
pipeline, with drag rotation and scroll zoom. No imported GUI paint is used.

The continuous panel/dialog shadow fix from 0.2.9 is retained.
Production compilation and remapping passed; 0.2.10 replaced the installed
Prism JAR after backing up 0.2.9.
Runtime visual and interaction confirmation remains pending.

## Version 0.2.9 — 2026-11-13

The offset panel and dialog shadows used the hollow accent-glow mask. Its
interior cutout extended below the visible panel, leaving a clear band before
the shadow's outer halo began. Offset shadows now retain their filled core;
the panel covers it and the shadow remains continuous beneath the bottom edge.
Accent halos keep their original hollow mask.

## Version 0.2.8 — 2026-11-13

Implements bytephase-clickgui-new.zip and its IMPLEMENTATION.md using a native
GUI pipeline. SDF surfaces replace rectangle-span corners; the shader computes
coverage and inset stroke in physical pixels. Vertices, text origins and hit
regions share framebuffer snapping. Labels reserve keybind width and ellipsize.
Reference spacing, scoped sidebar clipping, smooth scroll and control motion
are used. A cached half-resolution scene copy and six dual-Kawase passes supply
a rounded, saturated and tinted glass backdrop. Turning BackgroundBlur off
skips the blur. Existing module/config/profile/friend controls remain connected.

Production compilation/remapping passed. No in-game visual checks were completed:
the user closed Minecraft and requested installation so they could run the mod.
The original 0.2.7 JAR was backed up before replacing it in the Prism instance.

## Version 0.2.7 — 2026-11-13

The user's 0.2.6 log shows successful world entry followed by a later
ArrayIndexOutOfBoundsException in KnifeHold.getAmounts2. getAmountsForInt
composed two matrices by calling getAmounts, but Java selected its fixed-arity
vector cross-product overload instead of the matrix varargs overload. The
three-component result was then passed to the affine matrix inverse.
Vector normalization and cross products now have distinct method names, and
getAmountsForInt explicitly multiplies its two 4x4 matrices.

The ClickGUI uses shared corner-radius values and row-center placement for
labels, icons, toggles, buttons, numeric values and slider handles. Bundled font
cap-height metrics set label origins relative to Minecraft's y+7 baseline.
Rounded outlines subtract the inset silhouette from the filled silhouette;
corner arcs are connected and each outline pixel is drawn once. The additional
straight top highlight was removed. StreamProof status occupies a second line
within a taller settings row.

Production compilation and remapping succeeded with ./gradlew remapJar.
Gameplay and visual confirmation of these changes remain pending.

## Version 0.2.6 — 2026-11-13

The reported macOS SIGBUS crash occurs inside Apple's texture upload code,
called by BytephaseSkyRenderer.ensureNoise. That raw upload inherited the current
OpenGL pixel-unpack state, including atlas row lengths and offsets. The crash
report identifies the upload site; it does not record the unpack state.

Sky noise uploads, post-processing texture allocations and private overlay
texture allocations now reset unpack alignment, row/image lengths, skips,
byte order and pixel-unpack buffer binding in a scoped guard. All prior values
are restored when the upload ends, including on Java exceptions. A null upload
pointer therefore allocates storage without reading from a previously bound PBO.
The sky source buffer remains alive until renderer disposal.

Production compilation and remapping succeeded with ./gradlew remapJar.
The user's following launch entered the world and ran for several minutes;
the later knife animation exception is documented under version 0.2.7.

## Version 0.2.5 — 2026-11-13

The reported macOS launch stalled inside GLFW initialization without producing
an exception or crash report. A thread dump from that running Prism instance
showed the render thread in GLFW.glfwInit and an already-created Java2D disposer
thread. The imported font catalog created Java2D graphics during Fabric client
initialization, before Minecraft's GLFW backend and window existed.

The imported font, module and graphics catalog now initializes in CLIENT_STARTED,
before loading the native config. Imported keybinding names are still registered
in the initial Fabric phase: Fabric explicitly rejects keybinding registration
after GameOptions is initialized. The existing bindings are attached to the real
feature instances after the window is created. Event hooks stay registered early
and graphics callbacks wait for imported initialization. The 207-module catalog
is retained. Production compilation/remapping are the build checks; no new game
launch or gameplay tests were run for this fix.

## Version 0.2.4 — 2026-11-13

Adds 83 missing Bytephase feature classes, for a 207-module catalog, and skips 62
existing module, command and UI matches. The recovered source targets the same
Minecraft version. Engine hook selectors are converted to current Yarn names
and remapped with the main JAR. The standalone activation/bootstrap path is
replaced by Bytephase lifecycle, catalog, settings, config, profile, friend and
keybind integration. Source data and specialized editors are preserved.

The final JAR is built through production compilation and remapping. No game
launch or gameplay tests were run. See [the migration inventory](ENGINE_FEATURES.md)
and [the source notice](../engine/THIRD_PARTY_NOTICES.txt).

## Version 0.2.3 — 2026-11-13

The committed 0.2.3 source consolidates the local development builds through
0.2.5. This includes stationary AimAssist, sword obsidian placement, throwable
trajectories, macro pacing controls, the glass ClickGUI, distance-field text,
corrected icon sampling, configurable Triggerbot cooldown and FakeLag's
server-position and packet-queue overlays. The catalog now contains 124 modules.

The supplied Marlow optimizer 2.0.0 JAR is also integrated as MarlowOptimizer,
with gated hooks, KeepRender, retained-crystal cleanup and the source's server
opt-out protocol. See [module details](MARLOW_OPTIMIZER.md). It is mutually
exclusive with the previous CrystalOptimizer implementation.

The first user test failed during initialization: the combat mixin configuration
declared `dev.bytephase.pvp` as its package, reserving the API and implementation
classes as mixins. The declaration is now limited to `dev.bytephase.pvp.mixin`;
all 19 mixins, including the relocated mouse accessor, reside in that package.
The rebuilt JAR awaits another user launch.

The supplied archive's 25 combat modules and their required helpers/hooks are
imported into a separate combat component. It is remapped and embedded in the
main JAR. Five previous combat registrations are replaced and 20 new ones are
added, followed by MarlowOptimizer, for 122 modules. The existing GUI and config/profile system own their
controls; action binds, conditional settings and friend commands are included.

The required combat mixins are remapped to the runtime namespace. The component
uses native HUD drawing and the shared label budget. Utility weapon, sprint and
inventory helpers yield to the matching imported combat owners.

The production JAR builds locally. No game launch or gameplay tests were run
for these changes. See [the PvP guide](PVP_IMPORT.md).

## Version 0.2.2 — 2026-11-12

Fixed the reported initialization crash in the shared discovery overlay. Its
style default now references the supported `Box` mode through the same constant
used in the ESP style catalog. The invalid `Outline` name threw from
ModeSetting while MotionScanner was being constructed; PrimeChunkFinder and
SpawnerFinder inherited the same invalid default.

Production compilation and JAR remapping succeeded. The game was not launched
and runtime tests were not run for this hotfix. The module catalog remains 101.

## Version 0.2.1 — 2026-11-12

The catalog now contains 101 modules. This migration adds 13 utility and
render/discovery modules; see [the module guide](MODULE_GUIDE.md#added-in-021)
and [the complete source feature inventory](FEATURE_MIGRATION.md).

- Render controls are grouped and collapsed by default; existing module settings
  and names persist. Search reveals matching groups.
- The enabled-module HUD groups render modules and has a configurable row cap.
- World labels share a bounded overlap budget with reusable rectangle storage.
- New discovery caches are bounded and use loaded chunks. Light hooks execute
  after packet application on the client thread, inside the module error policy.
- Discovery alerts use bounded queues and a one-second interval per scanner.
- AutoGap reuses AutoEat's key ownership and selection restoration. AutoEat
  yields while healing is needed. HoverTotem yields to AutoTotem.
- SmoothCamera restores the captured setting. Performance overrides policies
  when used, without changing saved video preferences.

Validation: production compilation and JAR remapping completed. Gameplay and
client regression tests were not run for this release. No RAM or FPS improvement
is claimed without a new runtime measurement. The migration remains incomplete.

## Version 0.2.0 — 2026-11-12

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
