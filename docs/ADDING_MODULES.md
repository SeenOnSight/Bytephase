# Adding modules and customizations

[Home](../README.md) · [Module guide](MODULE_GUIDE.md)

Bytephase uses a small module class, typed settings and a shared dispatcher.
New modules should use normal input and interaction APIs, preserve vanilla
movement/reach limits, and keep discovery work bounded to loaded chunks.

## Structure

| Piece | Location | Responsibility |
| --- | --- | --- |
| `Module` / `Category` | `dev.bytephase.module` | State, settings, hooks and GUI grouping |
| `ModuleManager` | `dev.bytephase.module` | Registered catalog and immutable enabled/capability lists |
| Settings | `dev.bytephase.setting` | Boolean, number, mode and colour values |
| `ConfigManager` / `ProfileManager` | `dev.bytephase.config` | Settings persistence and named snapshots |
| `LocationStore` | `dev.bytephase.data` | Server/dimension-scoped coordinates |
| `Keybinds` / `KeyOverride` | `dev.bytephase.input` | Bind registration and shared temporary input ownership |
| `ClickGuiScreen` | `dev.bytephase.ui` | Panels, settings controls, search and binds |
| Render helpers | `dev.bytephase.render` | Batched boxes, projections, entity snapshots and shader adapters |
| `DiscoverySignals` | `dev.bytephase.util` | Testable growth and block-placement heuristics |

`BytePhase.mc`, `BytePhase.modules`, `BytePhase.config` and
`BytePhase.locations` expose shared services. Null-check client/world/player
state before using it; mixins can run before client initialization completes.

## Add a module

1. Create a class extending `Module` in the appropriate module package.
2. Call `super(name, category, description)` and declare settings with `add(...)`.
3. Override only the hooks needed by the module.
4. Register one instance in `ModuleManager.init()`.
5. Add `key.bytephase.<lowercase module name>` to `assets/bytephase/lang/en_us.json`.
6. Add regression coverage and update the module guide.

```java
public final class Example extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 4, 1, 40, 1));

    public Example() {
        super("Example", Category.MISC, "Describe the player-facing behavior");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) return;
        // Bounded client-thread work.
    }
}
```

Only registered instances can be enabled. Old config/profile entries for
removed modules are ignored; they cannot restore retired functionality.

## Hooks and lifecycle

- `onEnable` / `onDisable`: initialize or release state. Release input and
  references to old players/worlds on disable and world changes.
- `onTick`: bounded work on the client thread. Immutable active lists are
  refreshed on toggles, and dispatch also skips modules disabled mid-callback.
- `onTickAlways`: session work that must run while disabled; use sparingly.
- `onFrame`, `onRender2D`, `onRender3D`: reuse tick-built lists where possible.
  Renderers already interpolate entity positions.
- `onBeforeAttack` / `onAttack`: assist normal player actions. Do not send
  synthetic movement, broaden reach or change vanilla damage/movement rules.
- `onPacketSend` / `onPacketReceive`: callbacks may run on Netty. Queue bounded
  notices; read world/entity state and modify modules on the client thread.
- `onServerCorrection`: release pending input and invalidate the affected state.

Declare `usesEntitySnapshot()` when reading shared entity snapshots, and
`usesProjection()` when HUD work needs world matrices. This lets inactive
features avoid entity walks and rendering setup.

## Settings, input and storage

Number settings clamp normal config values; named profiles validate their
known setting types and bounds before applying changes. Call
`BytePhase.config.requestSave()` after a user edit so changes share a debounced
write. Failed writes retain pending data and retry.

Use `KeyOverride.set(this, key, pressed)` and `releaseAll(this)` for temporary
input. Claims from multiple modules share ownership; a false claim suppresses
conflicting true claims. Release claims when a screen opens or focus changes.

Coordinate data belongs in `LocationStore`; profile snapshots must not include
server identifiers or coordinates. Respect its file and entry limits.

## Rendering and scanners

Use `EspRenderer`, `RenderBox` and `RenderProjection` for shared batched
geometry. Keep camera-relative calculations in double precision until vertex
submission. Plain box styles do not require shader compilation.

Scanners should skip unloaded chunks and empty/uninteresting section palettes,
check a time budget, and preserve a cursor when pausing. Mutable lookup
positions can be reused, but retained results must be immutable. Validate
chunk identity and revision before publishing results. Keep alerts, findings
and render batches bounded.

## Verification

Build with Java 25; release bytecode targets Java 21.

```sh
./gradlew clean build runClientGameTest
```

`UtilityRegressionTest` exercises the current catalog, discovery, storage
recovery, scanner budgets/cache invalidation, keybinds, eating, GUI and rendering
in an isolated temporary game directory. Add a focused check beside it when
introducing a new behavior.
