<div align="center">
  <img src="src/main/resources/assets/bytephase/bytephase-logo.png" alt="Bytephase binary monogram and wordmark" width="560">
  <h1>Bytephase</h1>
  <p><strong>Explore farther. Keep your bearings. Make the everyday easier.</strong></p>
  <p>A Fabric utility client for Minecraft, built for personal play on 2b2t.</p>
  <p><strong>0.2.3</strong> &nbsp;·&nbsp; Minecraft <strong>1.21.11</strong> &nbsp;·&nbsp; Fabric</p>
  <p>
    <a href="#features">Features</a> &nbsp;·&nbsp;
    <a href="#install">Install</a> &nbsp;·&nbsp;
    <a href="#build">Build</a> &nbsp;·&nbsp;
    <a href="docs/EXPLORATION.md">Exploration guide</a> &nbsp;·&nbsp;
    <a href="docs/MODULE_GUIDE.md">Module guide</a>
  </p>
</div>

---

## Features

A compact module system, a searchable ClickGUI and the matching binary `[01]`
HUD mark. Gameplay and exploration utilities sit alongside the existing ESP,
model effects, shaders and general visuals. The 0.2.3 catalog contains
**124 modules**, including 25 imported PvP modules. Gameplay testing remains
pending. See [PvP modules and controls](docs/PVP_IMPORT.md).

| For | Tools | What they do |
| --- | --- | --- |
| **Discovery** | `BaseFinder` · `SusChunks` · `MotionScanner` · `PrimeChunkFinder` · `SpawnerFinder` | Scan loaded chunks for storage, spawners, weighted amethyst and opt-in activity signals |
| **Navigation** | `Navigator` · `CoordSnapper` | Track saved waypoints and copy your position or a looked-at block |
| **Mining** | `TunnelMiner` · `MiningProgress` | Work a level tunnel with visible aim and view breaking progress |
| **Inventory & trade** | `HotbarArrange` · `TradeAssist` | Maintain configured hotbar roles and manually batch a selected merchant offer |
| **Profiles** | `/bytephase profile` | Save and load named module settings and enabled states |
| **Visuals & HUD** | ESP · Tracers · Nametags · StorageESP · SkyShaders | Entity and block overlays, model styles, sky presets and configurable HUD elements |
| **Input assistance** | AimAssist · AutoClicker · AutoTotem · AutoWalk · SafeWalk | Assist normal aiming, item handling and movement inputs |

Scanners use loaded terrain and report heuristics; findings are historical local
records. `HotbarArrange` defaults all nine roles to **Keep**. `TradeAssist`
starts from its bind with an offer selected in an open merchant screen.
Trading stops if explicit server inventory confirmation is unavailable.
See the [exploration guide](docs/EXPLORATION.md) for defaults and workflows.

## Install

The published release is **0.2.2**. Build **0.2.3** from this source tree for
the new PvP and UI changes; see the [PvP guide](docs/PVP_IMPORT.md).

1. Create a **Minecraft 1.21.11** installation with **Fabric Loader 0.20.1 or newer**.
2. Download [`bytephase-0.2.2.jar`](https://github.com/SeenOnSight/Bytephase/releases/download/v0.2.2/bytephase-0.2.2.jar)
   from [GitHub Releases](https://github.com/SeenOnSight/Bytephase/releases).
3. Put the Bytephase mod JAR and **Fabric API 0.141.6+1.21.11** in that
   installation's `mods/` directory.
4. Launch the Fabric profile with **Java 21 or newer** and press **Right Shift**.

Use the active launcher's game directory. To build the JAR from source,
use the instructions below.

## Build

Use **Java 25** for the configured Loom build. The mod itself targets
**Java 21** bytecode.

```sh
./gradlew build
```

Output for this working tree: `build/libs/bytephase-0.2.3.jar`.

<details>
<summary><strong>Build versions</strong></summary>

| Component | Configured version |
| --- | --- |
| Bytephase working tree | `0.2.3` |
| Minecraft | `1.21.11` |
| Yarn mappings | `1.21.11+build.6` |
| Fabric Loader | `0.20.1` |
| Fabric API | `0.141.6+1.21.11` |
| Fabric Loom | `1.19.2` |
| Gradle wrapper | `9.8.1` |
| Mod Java target | `21` |

Version sources: [gradle.properties](gradle.properties),
[build.gradle](build.gradle) and
[gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties).

</details>

## Controls

| Action | Control |
| --- | --- |
| Open / close ClickGUI | **Right Shift**, or the configured ClickGUI key |
| Toggle a module | Left-click its row |
| Open module settings and bind | Right-click its card |
| Assign / clear a bind | Click **Bind**, then a key / **Escape** |
| Choose a category | Click the sidebar |
| Find a module | Type in the top-right search field; Ctrl+F focuses it |
| Choose UI and HUD fonts | Settings → UIFont / HUDFont |
| Open profiles | `/bytephase profile` |
| Show command help | `/bytephase` |

Sliders support dragging; mode values cycle with left/right click. Module binds
also appear in vanilla **Controls → Bytephase**. TradeAssist's bind works while
an open merchant screen has focus. More UI details and module-specific behavior
are in the [module guide](docs/MODULE_GUIDE.md).

## PvP component

Version 0.2.3 adds all 25 combat modules from the supplied archive, stationary
AimAssist, configurable Triggerbot timing, FakeLag visualization, sword obsidian
placement and throwable trajectories. It also includes the glass ClickGUI,
distance-field fonts and corrected icon rendering.
See [the PvP guide](docs/PVP_IMPORT.md) for modules, action binds and friends.

## Compact overlays

The ClickGUI uses the supplied glass design with sidebar navigation, rounded
module cards and modal settings. **Configs**, **Friends**, **Themes** and
**Settings** connect to the live client. Settings offers Inter, Outfit,
JetBrains Mono and Minecraft,
with separate font selections for the UI and HUD.
See [ClickGUI controls and fonts](docs/CLICKGUI.md).

`ArrayList → CompactRender` combines enabled render modules into group counts;
`MaxRows` caps its height. `Appearance → WorldLabelLimit` and
`AvoidLabelOverlap` apply one shared budget to nametags, item labels,
logout spots, discovery labels and damage markers.

The current feature additions and remaining migration work are recorded in the
[module guide](docs/MODULE_GUIDE.md#added-in-021) and
[feature migration inventory](docs/FEATURE_MIGRATION.md).

## Your settings & places

| Path in the active game directory | Stores |
| --- | --- |
| `config/bytephase.json` | Module settings/states and ClickGUI layout |
| `config/bytephase/profiles/` | Named module snapshots |
| `config/bytephase/locations.json` | Server/dimension-scoped waypoints and findings |
| `options.txt` | Vanilla-managed keybinds |

Profiles contain module settings and states. Coordinates live in the location
store. See [local data](docs/EXPLORATION.md#local-data) for scope, limits and
backup details.

## Documentation

- [Exploration & everyday workflows](docs/EXPLORATION.md) — defaults, commands,
  discovery, navigation, mining, inventory, trading and profiles.
- [Module guide](docs/MODULE_GUIDE.md) — detailed controls and retained combat,
  movement, ESP, shader and HUD behavior notes.
- [Adding modules](docs/ADDING_MODULES.md) — module, setting and mixin structure.
- [Debugging & performance notes](docs/AUDIT.md).

## License

[MIT](LICENSE) · Copyright © 2026 Bytephase contributors.
