# Bytephase Module Guide

[Home](../README.md) · [Exploration & workflows](EXPLORATION.md) ·
[Adding modules](ADDING_MODULES.md)

Detailed controls and behavior notes for Bytephase's combat, movement,
visuals and everyday utilities. The original README's useful module details
are retained here, grouped for easier browsing.

The ClickGUI's large `B` is drawn from `0` and `1` digits; the HUD watermark
uses the matching `[01]` mark.

**Jump to:** [Controls](#controls) · [New utilities](#exploration--workflow-utilities) ·
[Combat](#combat--aiming) · [Movement](#movement--travel) ·
[Visuals](#esp-shaders--hud) · [Everyday tools](#packets-chat--session-tools) ·
[Development](#development--local-tools)

---

## Controls

- `Right Shift` opens the ClickGUI. The configured ClickGUI key also closes it.
- The GUI is a set of floating category panels. Drag a panel header to move it,
  right-click a header to collapse it, and scroll inside a panel to move
  through its modules.
- Left-click a module to toggle it. Click the small chevron on the right of a
  module row to open its settings and keybind row. Modules without settings
  still expose the bind row.
- Inside an expanded module, click `Bind` and press a key to bind it. `Escape`
  clears the bind.
- Click the search bar at the bottom of the ClickGUI and type a module name to
  filter the panels. Enter or Escape leaves the search field; backspace edits it
  and Ctrl+Backspace clears it.
- The UI panel's Appearance entry controls ClickGUI accent and text colours,
  bold headers and text shadows. These settings apply even when the entry is off.
- InventoryMove permits forward, back, sideways, jump and sprint input in
  inventory/container screens; creative inventory and focused text fields are
  excluded. Servers may reject movement while a container is open.
- Sliders are set by clicking or dragging the bar. Click a mode to cycle
  forward, right-click to cycle backwards.
- Colour settings expand into R, G and B sliders. Drag any channel bar to set
  the RGB value; alpha is preserved from the module's default colour.
- Module keybinds are also listed in vanilla Controls under `Bytephase`.

- TradeAssist's bind can start or stop a batch while a focused merchant
  screen is open. Other module binds are drained while screens are open.
- `/bytephase profile` opens the named-profile picker. Its Save/Overwrite,
  Load and two-click Delete controls are documented in the
  [profile workflow](EXPLORATION.md#profiles).

Settings and enabled states are stored in `config/bytephase.json`. Keybinds are
stored by vanilla in `options.txt`.

## Exploration & workflow utilities

| Tool | Panel / entry point | Behavior reference |
| --- | --- | --- |
| `BaseFinder` | Explore | [Storage/utility thresholds, spawners and the soft 0.8 ms scan budget](EXPLORATION.md#basefinder) |
| `SusChunks` | Explore | [Growth/Geode evidence and the soft 1.5 ms scan budget](EXPLORATION.md#suschunks) |
| `Navigator` | Explore | [Selected waypoint distance, direction and arrival](EXPLORATION.md#navigator) |
| `CoordSnapper` | Explore | [One-shot position or looked-at block capture](EXPLORATION.md#coordsnapper) |
| `TunnelMiner` | World | [Locked heading, visible mining and optional AutoWalk](EXPLORATION.md#tunnelminer) |
| `HotbarArrange` | Player | [Continuous roles; all nine slots default to Keep](EXPLORATION.md#hotbararrange) |
| `TradeAssist` | Player | [Bounded manual batches for the selected open merchant offer](EXPLORATION.md#tradeassist) |
| Profiles | `/bytephase profile` | [Module snapshots, validation and recovery](EXPLORATION.md#profiles) |
| Commands & locations | `/bytephase` | [Exact commands](EXPLORATION.md#command-reference) · [Storage paths and limits](EXPLORATION.md#local-data) |

The scanners inspect loaded chunks and record candidates. Historical findings
are server/dimension-scoped local data; live markers are removed when their
loaded evidence becomes invalid. Existing visual styles are covered below.

## Combat & aiming

- `AutoCrystal` and `KillAura` have optional `VanillaPackets` mode: camera-visible
  aim is sent through vanilla movement, with no extra silent-look flush packets.
  Actions wait for transmitted aim, and AutoCrystal uses actual ray-hit placement
  faces. It applies conservative limits; see [GRIM_SOURCE_AUDIT.md](GRIM_SOURCE_AUDIT.md) for
  conflicts and test coverage. This is not a verified Grim bypass.

- `AutoCrystal` and `KillAura` have an optional `Conservative` setting for
  attribute-based reach limits, visible interactions, required rotation and
  correction backoff. `KillAura.RequireAttack` limits automation to when you
  hold attack. These are compatibility controls, not verified anti-cheat
  bypasses or a global legit mode. See [SERVER_COMPATIBILITY.md](SERVER_COMPATIBILITY.md) for details.

- `AutoCrystal` and `KillAura` have `ThroughWalls` settings. AutoCrystal also
  limits obstructed interactions with `PlaceWallsRange` and `BreakWallsRange`;
  damage and self-damage checks still account for block exposure. The server
  can reject attacks or placements through walls regardless of these settings.
  AutoCrystal can place and attack in the same tick, with separate configurable
  delays. `CrystalsPerTick` sets a maximum of 1–8 placement attempts and 1–8
  break attempts per eligible cycle. Non-Focus placements use distinct,
  non-overlapping bases; Focus can retry the same base. `Conservative` disables
  those retries and limits each cycle to one action. Batches share a self-damage budget, so
  fewer may be attempted. Set both delays to 0 for the fastest tick-based rate
  (existing saved settings are preserved). Server limits, lag and anti-cheat
  may reject rapid actions. The ArrayList displays rolling one-second rates:
  `P` accepted client placements, `B` attack attempts, and `X` nearby server
  explosion packets matched to those attacks. They are not guaranteed kills or
  confirmed server placements, and server latency or anti-cheat can limit speed.

- The aiming modules ease their turning instead of stepping at a constant
  speed. `AimAssist` and `BowAimbot` steer the camera every rendered frame, not
  once per tick: the remaining angle is closed by `1 - exp(-rate * dt)` of
  `Smoothing`, which is frame-rate independent and cannot overshoot, and `Speed`
  caps the turn. Your own mouse movement is applied first each frame and is
  never reverted, so the assist adds to it rather than fighting it.
  `AimAssist` aims at the target's neck, or at the chest when the target stands
  a block or more above you, instead of at their eyes. `KillAura`, `AutoCrystal`,
  `AimAssist` and `BowAimbot` all expose `RotationSpeed`/`Speed` and `Smoothing`,
  and `KillAura` and `AimAssist` also have `Humanize`, a small wandering offset
  so the aim is not a perfectly straight line. `KillAura` and `AutoCrystal`
  rotate the server-side facing only: the aim is written into the outgoing
  movement packet (and into your own third-person model) while the camera stays
  exactly where you pointed it. Attacks are sent once the aim has actually
  reached the target, and AutoCrystal waits until it is within a few degrees of
  the base before interacting so the server sees a valid rotation.

- `AutoMine` defaults to `ObsidianOnly`, mining the four blocks beside a
  nearby player's feet. Turn it off for the older broader block selection;
  hold a suitable pickaxe, or use your preferred tool-selection module.

- `Surround` can use `KeyActivate`: enable the module, turn on that setting,
  and assign its `Bind`. Each press attempts one ring at your current feet;
  moving away, losing the required obsidian, or disabling the module cancels it.
  With `KeyActivate` off, the bind toggles the module as usual.

- `PearlPhase` is a one-shot attempt: face a nearby wall with an ender pearl
  in your hotbar, then toggle it. It throws at
  the lower edge and switches back. Free space behind the wall is not required
  client-side; pearl clipping still depends on server physics and is not
  guaranteed, so do not rely on it to escape dangerous situations.

- `Criticals` uses packet-based mini movement before an attack. It is
  intentionally conservative, but can conflict with `FakeLag`.

- The combat set added a self-protection and consumable group. `FeetTrap`
  seals the four cells beside your feet in one burst and re-arms when you move
  to a new block; `SelfTrap` builds a foot ring, a head ring and a roof;
  `SelfFill` places one block next to you, confirms it, and turns itself off.
  The player's own cell cannot be filled — vanilla refuses a block that
  overlaps a living entity — so these build around the feet rather than inside
  them. `HoleFill` closes nearby crystal holes, nearest first, with a bounded
  scan.

- `KeepSprint` and `SprintReset` are opposites and should not both be on:
  `KeepSprint` re-applies sprint for a few ticks after a hit, `SprintReset`
  drops sprint after a hit so the next one lands full knockback. `NoHitDelay`
  clears vanilla's ten-tick cooldown after a missed attack.

- `AutoPot` throws a healing splash potion at the configured health, aiming at
  the ground and restoring your camera pitch afterwards unless `Rotate` is off.
  `ThrowPot` is the one-shot version: it throws one potion, or two below
  `DoubleBelow`, then disables. `AutoSoup` eats mushroom, rabbit, beetroot or
  suspicious stew on a health trigger rather than hunger, for soup-PvP plugins.

## Movement & travel

- `Strafe` redirects your existing horizontal momentum toward your movement
  keys while airborne. `Control` adjusts how quickly it turns (100% is instant).
  It works alongside `Speed`, does not create speed from a standstill, and leaves
  vertical motion unchanged. Servers can reject movement or apply corrections.

- `ElytraBounce` uses a configurable downward `Angle` (default 75 degrees) while
  actively bouncing, and attempts to jump and re-deploy on landing; server corrections,
  anti-cheat, latency, and conflicts with other movement modules can affect it.

- The movement batch covers hunger, effects and mounted travel. `AntiHunger`
  withholds the server-visible sprint start packet, which is the part that adds
  sprint exhaustion; your own sprinting and sprint-jump input still work, and
  toggling the module resynchronises the server. `AntiLevitation` drops the
  levitation effect packet for your player and clears an effect that was
  already active. Both are client-side; the server still owns hunger and
  effect simulation.

- `NoSlow` also removes soul sand's velocity multiplier and the sneak/crawl
  walking slowdown. `IceSpeed` replaces ice-family slipperiness with its
  `Friction` value; lower values accelerate and brake like ground, so ice
  travel gets much faster than vanilla's long slow slide.

- `EntitySpeed` drives the vehicle you are riding at `Speed` along its own yaw
  while a movement key is held. `EntityControl` removes the saddle requirement
  for horses and the carrot/fungus-on-a-stick requirement for pigs and
  striders; the server still simulates the animal's movement.

- `TickShift` banks one tick per tick spent standing still, up to `Charge`, and
  spends that charge as extra ground speed. `FastAccel` raises the per-tick
  acceleration toward `Speed`. `Yaw` holds your yaw at a fixed angle.
  `ClickTP` teleports to the top of the block under the crosshair on a use-key
  press and sends the matching position packet; servers can reject the move.

## ESP, shaders & HUD

- `Fullbright` and `NoBob` restore vanilla options when disabled and when the
  game exits normally. A hard crash can leave the option values changed.

- `MiningProgress` highlights blocks being mined and shows a progress bar and
  name. Your own normal mining and PacketMine are supported. Other players use
  the server's coarse break stages, marked `~`; they are not exact timers and
  only appear when the server supplies a resolvable player ID and progress.

- `HideArmor` hides armor model layers on players, including your own third-person
  model, without changing their equipped items. Other wearables like elytra and
  decorative heads are unaffected. `Nametags` has an `ArmorIcons` option showing
  equipped items using Minecraft's item icons, even while armor is hidden.

- `Nametags` replaces vanilla labels for its selected targets within range.
  `HealthText` shows client-reported HP inside the nametag, with optional
  `Absorption`; servers may not expose accurate health values.

- Entity ESPs (`ESP`, `Tracers`, and `Nametags`) expose a `Targets` mode.
  `PlayerColor`, `MonsterColor` and `OtherColor` select the RGB colour for each
  target group. Block/container ESPs expose per-type colours instead.

- `ESP` also has a `Model Glow` style. It marks the rendered entity model with
  Minecraft's native blurred outline pass, so the highlight follows the model
  instead of its bounding box. Its player, monster and other colours use the
  same colour settings as the other ESP styles.

- `ESP` has a `Model Filled` style that replaces the living model's skin with
  a translucent solid colour. `ModelOpacity` controls transparency and
  `ThroughWalls` controls depth testing. `FilledHeldItems` also colours ordinary
  held-item models (both hands, including your own); `FilledCrystals` colours
  end crystals with `OtherColor`. Special-rendered items such as maps and
  custom models may retain their original appearance. Armour stays normal.

- `ESP` also has a `Model Chams` style for a translucent, tinted model surface
  with the same native model-following outline. The surface uses the selected
  target-group colour; the outline remains crisp and bright.

- `ESP` also has a `Model Wireframe` style that traces the rendered entity
  mesh, including its head and moving limbs, using the selected target colour.

- `ESP` has a `Model Hologram` style that combines a translucent model fill
  and a bright mesh wireframe to match the holographic reference effect.

- `ESP`, `Tracers`, `StorageESP`, `BlockESP`, `ItemESP`, `HoleESP` and
  `SusChunks` have a `ThroughWalls` option. It switches to no-depth overlay
  render layers, so the overlay remains visible behind terrain. Storage ESP
  scans the block-entity maps of loaded chunks, including containers the
  vanilla renderer would not otherwise submit.

- `ArmorHUD` uses 20-pixel slots, compact durability text and optional bars so
  four pieces never overlap. `Scaffold` supports normal bridge mode and a
  jump-based tower mode, selects a block from the hotbar and validates the
  support face and reach before placing.

- The shared `Style` setting offers `Box` (outline), `Filled`, `Highlight`
  (fill + outline) and shader styles. `Shader` and `Shader Glow` use the
  supplied Clarity glow pipeline; `Shader Blur`, `Shader Galaxy`,
  `Shader Prism`, `Shader Neon` and `Shader Scan` select the other supplied
  ESP composites. The shader styles rasterise the same batched boxes into a
  mask, run the supplied downsample/blur/composite shaders, and then draw once
  per style. All GLSL files under `assets/bytephase/clarity_shaders/` are
  shipped unchanged from the approved visual source.

- `Crosshair` includes a Vanilla style that leaves the vanilla crosshair intact.
  Its other styles replace the vanilla crosshair with the supplied Clarity
  crosshair shader. It exposes style, gap, length, thickness, dot, colour
  mode, hit/target colours, outline, glow, rotation, dynamic spread, cooldown
  and hitmarker settings. `HideVanilla` keeps the vanilla crosshair out of the
  way; the module falls back to a small primitive crosshair if the raw shader
  cannot be compiled.

- `SkyShaders` replaces the sky with one of the supplied Clarity presets
  (`Aurora`, `Black Hole`, `Blood Moon`, `Dreamscape`, `Galaxy`, `Nebula`,
  `Prism`, `Synthwave`, `Thunderstorm`). Speed, brightness, intensity, scale,
  star density, celestial speed, horizon haze, colours and a low-resolution
  quality mode are configurable. It renders only in the Overworld unless
  `AllDimensions` is on.

- `TargetFX` draws the supplied `worldfx/target` reticle around the entity
  under the crosshair, with all eight supplied styles, range, scale, colours
  and a through-walls option.

- The render batch covers visuals, HUD limits and perspectives. `BreadCrumbs`
  records a point once you move past `Spacing`, drops entries older than
  `Lifetime`, and clears on a world change. `Skeleton` draws a line skeleton
  from the target's body yaw and stance rather than from model bones, so it
  follows sneaking and turning but costs no extra entity render states.

- `NoWeather` zeroes the client's rain and thunder gradients, which is what
  vanilla drives rain particles, weather fog, sky darkening and lightning
  flashes from; the server's weather and its mechanics are untouched.
  `OldPotions` restores the pre-1.20 bottle colour by blending the effect
  colours (`PotionContentsComponent.mixColors`) instead of using the potion's
  own colour field.

- `ViewClip` exposes the camera clip. `IgnoreBlocks` keeps the third-person
  camera at the requested distance instead of shrinking it out of walls, and
  `Distance` caps it; both resolve through one function and default to vanilla.
  `SmallShield` shrinks the offhand item and `ViewModel` moves and scales both
  first-person hands (the offhand included), both only in your own first-person
  pass; `ViewModel`'s `OffsetX` is mirrored on the left arm so the two hands
  stay symmetric instead of sliding off-centre.

## Packets, chat & session tools

- `Blink` and `FakeLag` discard stale movement on server corrections and world
  changes, and stop buffering for `CorrectionPause` ticks (default 10) so vanilla
  can acknowledge corrections. FakeLag also has a `MaxDelay` age cap (default
  20 ticks), in addition to its packet-count cap.

- `AutoLog` suppresses the next `AutoReconnect` attempt, so an intentional
  combat log is not immediately undone. Rejoin manually, or leave `AutoLog`
  off if you want `AutoReconnect` to handle every disconnect.

- `ExtraTab` raises the tab list's 80-entry cut-off. `InventoryInfo` lists
  shulker contents and stored enchantments from the `CONTAINER` and
  `STORED_ENCHANTMENTS` components, capped by `MaxLines`, so empty containers
  add nothing. `BetterChat` raises the 100-message history and the 256-character
  chat input limit; both are client-side limits.

## Development & local tools

### Build

The configured build uses Fabric Loom 1.19.2 and requires Java 25 for the
build JVM. The mod targets Java 21 bytecode.

```sh
./gradlew build
```

The mod JAR is written to `build/libs/`. See the
[README](../README.md#install) for the configured Minecraft/Fabric versions
and installation instructions.

### Existing regression-test command

`./gradlew runClientGameTest` runs the isolated Fabric client tests in a temporary
singleplayer world. The tests do not use your normal `run/` game directory.

### Fake players

- `/fakeplayer` or `/fakeplayer spawn [name]` creates a client-only player next
  to you (the default name is `FakePlayer`). These entities are useful for
  checking player-targeting visuals and combat selection locally; they are not
  server entities and cannot receive server-side damage.
- `/fakeplayer remove` removes the most recently created fake player, and
  `/fakeplayer clear` removes all of them. Fake players are also removed when
  the world changes, with a limit of 32 at a time.

### Architecture & source notes

- Render, packet and snapshot dispatch is built from immutable enabled-module
  lists, so disabled modules add no per-frame or per-packet work. Empty render
  and packet paths are skipped entirely.

- The earlier module expansion drew from Anubis
  (`~/Downloads/anubis-src`). Anubis-specific shaders and asset packs, list
  settings and server-specific macro protocols were excluded from that
  expansion; [ADDING_MODULES.md](ADDING_MODULES.md) records its mapping and
  exclusions. The supplied Clarity visual pipeline is described in the
  visual notes above. New exploration and workflow utilities use Bytephase's
  own implementations with design inspiration from local Anubis and Corz
  archives; see [EXPLORATION.md](EXPLORATION.md#design-references).

- [ADDING_MODULES.md](ADDING_MODULES.md) documents the module/setting/mixin structure and how
  to port further modules from the Meteor and Anubis sources.

- This client is for personal use. 2b2t permits many client modifications, but
  server rules and anti-cheat behavior can change; use it at your own risk.
