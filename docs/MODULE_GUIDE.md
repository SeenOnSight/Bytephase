# Bytephase module guide

[Home](../README.md) · [Exploration & workflows](EXPLORATION.md) · [Adding modules](ADDING_MODULES.md)

Bytephase 0.2.3 includes 124 modules. Its 25 imported combat
modules include automated PvP and packet delay options. Gameplay testing
remains pending. See the [PvP guide](PVP_IMPORT.md).

## Controls

- `Right Shift` opens the ClickGUI. The configured ClickGUI key also closes it.
- The supplied glass UI is now a centered native screen. Choose a category in
  its sidebar, then scroll the two-column module cards.
- Left-click a module card to toggle it; right-click opens its settings dialog.
  Scroll inside long dialogs. Close or Escape returns to the cards.
- In a module dialog, click `Keybind` and press a key. Escape, Backspace,
  Delete or right-click clears it.
- Search is at the top right and searches all categories. Ctrl+F focuses it;
  standard editing and clipboard shortcuts work. Arrow keys select cards;
  Enter/Space toggles the selection and Shift+Enter opens its settings.
- **Themes** contains accent presets and text colour. **Settings** contains
  panel opacity, background blur and font choices. **UIFont** and **HUDFont** independently offer Inter,
  Outfit, JetBrains Mono and Minecraft. HUDFont applies to the array list,
  coordinates and the other Bytephase HUD labels.
- **Configs** saves, loads, overwrites, copies, imports and deletes native
  profiles. **Friends** edits the existing combat friend list.
  See [ClickGUI and fonts](CLICKGUI.md).
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
| `SusChunks` | Explore | [Growth, weighted growth, geode and activity evidence and the soft 1.5 ms scan budget](EXPLORATION.md#suschunks) |
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

## Input assistance

- **AimAssist** follows enemy players inside its field of view, including while
  the mouse is still, with weapon and vertical controls. Moving the mouse away
  from a target pauses assistance on that axis for that frame. Menus, lost
  focus, item use, mining and another module's rotation/hotbar ownership pause it.
- **SwordObsidian** switches from a held sword to obsidian in the hotbar when
  you use the top face of the ground. The same click performs normal block
  placement. **Switch Back** optionally restores the sword after the action;
  it is off by default. Missing obsidian or an occupied placement cell skips
  the switch.
- **AutoClicker** repeats normal crosshair attacks while attack is held. It
  always waits for the full vanilla attack cooldown and shares the one-attack
  per-tick guard with vanilla input.
- **AutoArmor**, **AutoTotem** and **AutoWeapon** use normal inventory/slot
  interactions. **WTap**, **SprintReset** and **JumpReset** assist timing with
  ordinary movement input.
- **Sprint**, **AutoWalk**, **AutoJump**, **Parkour**, **Sneak**, **SafeWalk**,
  **InventoryMove** and **Yaw** are the remaining movement aids. They do not
  increase movement speed or grant flight. InventoryMove excludes text-entry
  and creative-inventory screens.
- **AutoEat**, **AutoTool**, **AutoFish**, **AutoReplenish**, **ChestStealer**,
  **ChestSwap**, **Offhand**, **AntiAFK**, **AutoRespawn** and **RotationLock**
  provide normal item, inventory and input assistance. Hotbar and merchant
  workflows are described in the exploration guide.

## ESP, shaders & HUD

- `Tracers` starts every line at the crosshair using the full world projection,
  including view bobbing and hurt tilt.

- `Fullbright` and `NoBob` restore vanilla options when disabled and when the
  game exits normally. A hard crash can leave the option values changed.

- `MiningProgress` highlights blocks being mined and shows a progress bar and
  name. Your own normal mining is supported. Other players use
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
  four pieces never overlap.

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

## Chat, session and local tools

ChatFilter and AntiSpam filter displayed messages; NameProtect changes local
labels. PMSound, VisualRange, Notifications, KillEffects and AntiVanish provide
local alerts or visuals. AutoReconnect manages reconnect attempts. PacketLogger
and AntiPacketKick provide diagnostics and bounded decoder protection.

`/bytephase waypoint`, `/bytephase findings` and `/bytephase profile` manage
local navigation and saved settings. `/fakeplayer` creates a local visual-test
player and does not create a server entity.

## Development and verification

Build with Java 25; the mod targets Java 21:

```sh
./gradlew build runClientGameTest
```

The client tests use an isolated temporary world. They exercise the active
catalog, settings recovery, discovery heuristics, scanner budgets, cache
invalidation, keybinds, eating, GUI and rendering. See [audit notes](AUDIT.md)
for measured scan completion and performance limits.

## Added in 0.2.1

| Module | Behavior and defaults |
| --- | --- |
| AutoDisconnect | One-shot multiplayer low-health disconnect; 4 HP threshold, absorption included. Disables itself and suppresses AutoReconnect until you manually reconnect. |
| AutoGap | Uses hotbar golden apples below 14 HP, preferring enchanted apples. Shares AutoEat's input ownership and selection restoration; AutoEat yields while healing is needed. |
| InventoryCleaner | Drops selected junk with at least a two-tick delay. Every junk category starts off; hotbar, named and enchanted stacks are protected. Stops while a screen or cursor stack is active. |
| HoverTotem | Swaps the hovered container totem to offhand. Optional hotbar refill defaults off; AutoTotem has priority. |
| AutoFarm | Harvests mature crops under the actual crosshair within vanilla block reach. Three-tick default delay; pauses in screens and during item use. |
| HitSounds | Local attack feedback using Chime, Pling or Click. This is attack feedback, not a server-confirmed damage signal. |
| DamageMarkers | Floating observed health-loss values for your last attacked target and optionally yourself. Nearby damage defaults off; at most 64 live markers. |
| TargetHUD | Health, distance and held items for the last attacked living entity. Position is configurable; target expires after 80 ticks. |
| SmoothCamera | Vanilla cinematic mouse smoothing, with the previous setting restored on disable. |
| Performance | Configurable particle budget and distance, plus distant dropped-item culling. Defaults: 128 new particles per tick, 48-block particle/item distance. Also controls chunk scheduling and an optional streaming FPS cap. See [performance controls](PERFORMANCE.md). |
| MotionScanner | Groups explicit zero-filled received skylight arrays below sea level. Empty masks are not anomalies. Cluster size defaults to one; discovery processing runs while you are above Y 0. |
| PrimeChunkFinder | Tracks received underground block-light increases and redstone changes. Initial loaded light is a baseline. Ignores nearby player activity, visited underground chunks, and changes near lava or glowing amethyst. |
| SpawnerFinder | Scans two loaded chunks per tick, starting nearby. Shows exact spawner positions and available mob names. Trial spawners default off. |

The three new discovery overlays default to thin outline markers with labels and
tracers off. Chat alerts are rate limited to one per second per scanner. Their
caches are bounded and reset when the world changes or the module is disabled.
They report candidates; natural generation and server lighting policies can
produce similar signals. PrimeChunkFinder currently considers light increases
below Y 0, rather than every received light change.

Appearance settings apply whenever the interface or HUD draws. The selected
sidebar category and its scroll positions are saved in the ClickGUI layout.

## Fixed in 0.2.2

The discovery overlay's default style is now `Box`, the supported outline-only
mode. Version 0.2.1 used the unregistered name `Outline`, causing construction
of MotionScanner to fail before the client finished initializing. This shared
default also applies to PrimeChunkFinder and SpawnerFinder.

## Local PvP changes in 0.2.3

See [the import guide](PVP_IMPORT.md) for the full 25-module list, replacement
modules, action binds, friend commands and local build instructions.
