# Bytephase audit — bug, dead code and bloat sweep

Date: 2026-10-29
Scope: the existing client, at 113 registered modules. This pass does **not**
add new modules; the missing-module port is tracked separately.

Method: read the core infrastructure end to end, then read the module set,
shared utilities, input handling and mixins, and check the whole project with
mechanical passes for the bug classes that are easy to miss by eye
(unregistered classes, unregistered mixins, missing translation keys, unused
imports, unused setting fields, dead alias branches).

Everything below was verified against `./gradlew build` and
`./gradlew runClientGameTest`.

---

## 1. Fixes applied

### 1.1 Missing key-binding translations (user-visible)

`Keybinds.init()` creates one vanilla `KeyBinding` per module with the
translation key `key.bytephase.<lowercaseName>`. Three modules had no entry, so
their binds rendered as the raw key string in **Options → Controls →
Bytephase**.

| Module | Missing key |
| --- | --- |
| `HideArmor` | `key.bytephase.hidearmor` |
| `MiningProgress` | `key.bytephase.miningprogress` |
| `PearlPhase` | `key.bytephase.pearlphase` |

Added to `assets/bytephase/lang/en_us.json`. The file now has exactly one
`key.bytephase.*` entry per registered module plus `clickgui`, and no stale
entries (verified by script).

### 1.2 Unused imports (bloat)

| File | Imports removed |
| --- | --- |
| `module/combat/AutoWeapon` | `dev.bytephase.BytePhase` |
| `module/world/PacketMine` | `net.minecraft.util.hit.BlockHitResult`, `net.minecraft.util.hit.HitResult` |
| `module/player/ChestStealer` | `net.minecraft.screen.GenericContainerScreenHandler` |
| `render/shader/FullscreenQuad` | `org.lwjgl.opengl.GL20` |

### 1.3 Unguarded `BytePhase.mc` dereference

The convention in this codebase is `var mc = BytePhase.mc; if (mc == null || …)`.
Six modules dereferenced the field directly. `BytePhase.mc` is assigned in
`onInitializeClient` before any tick, so this is not reachable in normal play,
but it is a latent NPE and an inconsistency that makes the rest of the
null-checking look optional. Normalised to the project convention:

- `module/combat/AimAssist.onTick`
- `module/combat/AutoClicker.onTick`
- `module/player/AutoFish.onTick`
- `module/render/Trajectories.onTick`
- `module/render/LogoutSpots.onTick`
- `module/player/RotationLock.onTick`

### 1.4 `RotationLock` fought the GUI

`RotationLock.onTick` rewrote yaw/pitch unconditionally. It had no
`currentScreen` check, so it also locked the camera while chat, the ClickGUI or
any container was open. Added `mc.currentScreen != null` to the guard. Every
other movement/rotation module already had this check.

### 1.5 `Scaffold` cooldown ran negative

`if (cooldown-- > 0) return;` decremented the field on every non-placing tick
with no floor, so after a few seconds of idle the counter was a large negative
number that had to climb back above zero following a placement. The effective
behaviour was correct, but the counter was unbounded garbage. Rewritten as an
explicit decrement inside the guard so it saturates at 0.

---

## 2. Suspected issues left in place

These were investigated and deliberately *not* changed, because fixing them
would mean inventing behaviour rather than correcting a defect. They are listed
so they can be decided on.

### 2.1 `ReverseStep` — resolved

> Rewritten: it now looks one tick ahead for a ledge (empty space just below the
> next position) with solid ground within `FallDistance` beneath it. Previously
> it tested the ground under the current position, which is always solid, so it
> forced a -3 velocity every tick on flat ground and `FallDistance` did nothing.

Original notes:

`module/movement/ReverseStep.onTick` builds a box from the player's bounding
box offset down by `FallDistance + 0.01` and returns early when
`world.isSpaceEmpty(box)` is true, i.e. it pulls the player down only when
there *is* solid geometry within 1.2–3.0 blocks below. That reads as the
inverse of the doc comment ("Pulls you down quickly when the block below has
room for a step"), but it is also a coherent heuristic from the `isOnGround()`
gate: don't yank the player into the void, only snap down onto a ledge that
exists. The offset geometry makes the standing-on-ground case a no-op either
way, so the module cannot be evaluated by reading alone. Needs an in-game
check before either direction is "fixed".

### 2.2 `Zoom.getFov()` reports a stale value after disabling

`onDisable` clears `active`, and `getFov()` returns `active ? current : start`,
so after disable it reports the FOV that was live when zoom *started*, not the
last applied value. The doc comment says this accessor is only used by tests
and the GUI readout, so it has no gameplay effect.

### 2.3 `NumberSetting` validates its bounds after `super()`

The constructor is `super(name, clamp(value, min, max))` and only then checks
`min > max` / non-finite bounds. Because Java requires the `super` call first,
`clamp` runs against possibly-invalid bounds (it returns `min`), and the
constructor then throws. No observable defect — the throw always wins — but a
static `initial(...)` helper that validates first would be clearer.

### 2.4 `SpeedMine` has three names

The class is `SpeedMine`, the module name is `FastMine`, and `ModuleManager.add`
also registers the legacy lookup key `"speedmine"`. The alias is load-bearing
(old configs used it) and `docs/ADDING_MODULES.md` still calls the module
`SpeedMine`. Worth one pass to settle on one name; the alias branch cannot be
removed without dropping old configs.

### 2.5 `ModeSetting.getModes()` clones on every call

`getModes()` returns `modes.clone()`. Its only caller is
`ClickGuiScreen.mouseClicked` (once per click), so this is not a hot path — but
it is an easy place for a future render-loop caller to allocate per frame.

### 2.6 Duplicated ESP bookkeeping

`PlayerEsp`, `Tracers`, `ItemEsp`, `HoleEsp`, `SusChunks` and `StorageEsp` each
keep their own `List<Box>`/target-list scaffolding with near-identical
clear-populate-draw shape. The shared parts (`EspRenderer.TARGETS`, `STYLES`,
`drawBoxes`) are already factored out, so this is a consolidation opportunity
rather than a bug report.

---

## 3. Areas checked and found sound

Recorded so this list does not have to be re-derived next time.

**Core infrastructure.** `Module` (enable/disable error containment, shutdown
restoration, no-op hooks), `ModuleManager` (immutable enabled-list snapshots
rebuilt only on toggle, so there is no data race between the Netty packet
thread and the client thread; per-hook capability sets computed by reflection
once at startup; `try/catch` + reported-once error isolation per module per
phase; direct cached accessors for the hot engine mixins),
`ConfigManager` (atomic temp-file write, refuses to overwrite a newer or
unparseable config, migrations pinned to fixed version boundaries rather than
`CONFIG_VERSION`, per-setting error recovery), `Keybinds` (presses queued while
a screen is open are drained rather than toggling modules on close),
`BytePhaseClient` (config load deferred to `CLIENT_STARTED` because game
options do not exist during `onInitializeClient`).

**Settings.** `Setting`, `NumberSetting`, `BooleanSetting`, `ModeSetting`,
`ColorSetting` — all JSON round-trip, all clamp or reject invalid input.
No module declares a setting field it never reads (checked by script).

**Registration hygiene.** Every `Module` subclass is registered exactly once;
no registered module lacks a class; every class in `dev.bytephase.mixin` is
listed in `bytephase.mixins.json` and vice versa.

**Mixins read.** `ClientPlayerInteractionManagerMixin` (PacketMine packet
sequencing, AttackLimiter marking, Criticals), `MinecraftClientMixin`
(FastPlace/FastUse cooldown, MultiTask wrapping only the first `isUsingItem`
branch so the stop-using path stays intact),
`ClientPlayerEntityMixin` (NoFall ground spoof restored in `finally`, NoSlow
item factor, SafeWalk+WTap input rewrite in one injection without relying on
mixin order), `EntityMixin` (HitBoxes expands the targeting margin, not the
AABB, so it cannot trap the player), `LivingEntityMixin` (step height,
jump delay, swing duration, HighJump/LongJump all scoped to the local player),
`GameRendererMixin` (Zoom outranks CustomFov, render-resource disposal),
`ClientConnectionMixin` (both packet directions, `PacketSendBypass` re-entry
guard, FastException keeps `Error` and `TimeoutException` fatal).

**Modules read in full (96 of 113):** all of `combat` except `AutoCrystal`;
`movement/Blink, ElytraFly, FakeLag, Fly, HighJump, Jesus, LongJump, NoFall,
NoJumpDelay, NoSlow, Parkour, ReverseStep, SafeWalk, Sneak, Speed, Spider,
Sprint, Step`; all of `player`, `world` and `misc`; `render/ArmorHud,
ArrayListView, BlockHighlight, Coordinates, CustomFov, Fps, Fullbright,
HideArmor, Hitmarker, HoleEsp, ItemEsp, LogoutSpots, LowHealthWarning,
MiningProgress, Nametags, NoBob, NoRender, Ping, PlayerEsp, StatusEffects,
TargetFx, TimeChanger, Tracers, Trajectories, Watermark, Zoom`; all five
shared utilities; `input/KeyOverride`; `ClickGuiScreen`.

**Not read line by line (17 modules + 26 mixins + the raw-GL adapters):**
`combat/AutoCrystal` (620 lines), `render/BlockEsp`, `render/Crosshair`,
`render/SkyShaders`, `render/StorageEsp`, `render/SusChunks`,
`movement/AntiVoid, AutoJump, AutoWalk, ElytraBounce, FastFall, FastLadder,
FastSwim, FastWeb, PearlPhase, Slippy, Strafe`, and the shader adapters under
`render/shader` and `render/sky`. They compile, are exercised by the
regression test where the test covers them, and no mechanical check flagged
them.

---

## 4. Verification

```
./gradlew build                 # BUILD SUCCESSFUL
./gradlew runClientGameTest     # BUILD SUCCESSFUL
```

The game test drives a real client: config-corruption preservation, AutoEat,
KeyPearl, AutoReconnect suppression, Strafe/Speed interaction, Velocity,
ElytraFly, module keybinds, Surround key activation, ClickGUI slider drag +
search + escape handling, TimeChanger, Nuker target selection, ElytraBounce,
PearlPhase + AutoCrystal range, AutoMine, multi-crystal place/break batching
and self-damage budgeting, MiningProgress source resolution, and the ESP
renderers.

Mechanical checks re-run after the edits:

- translation key ↔ module name: 0 missing, 0 stale;
- module registration: 0 unregistered, 0 dangling;
- mixin package ↔ `bytephase.mixins.json`: 0 mismatches;
- unused imports: 5 → 0;
- unused setting fields: 0.

## 5. Compatibility follow-up — 2026-11-10

The movement-buffer correction/world-change fixes, conservative combat controls,
single-scan crystal placement batching, bounded target ranking, shared single-cycle
self-damage budgeting, and live nametag health-text fix are documented in
`SERVER_COMPATIBILITY.md`. Regression fixtures now explicitly select non-Focus
retry behavior and reset movement before screenshot comparisons.

Both `./gradlew build` and `./gradlew runClientGameTest` pass locally. No live
2b2t anti-cheat bypass was tested or verified.

### Native-packet follow-up

`GRIM_SOURCE_AUDIT.md` records the pinned upstream revision and the optional
`VanillaPackets` combat path. It uses camera-following aim, waits for outgoing
look/tick-end evidence, avoids extra movement packets, and commits actual
ray-hit placement faces. Native AutoCrystal takes priority over native KillAura.
The game-test-only packet tracer checks real aura attacks and an elevated-base
crystal placement, plus cancellation/evidence boundaries. The final
`./gradlew build runClientGameTest` passes. This is not a live Grim bypass test.
