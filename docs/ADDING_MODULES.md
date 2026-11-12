# Adding modules and customizations

Notes on how BytePhase is put together, how to add a module or setting, and how
to port something from the Meteor Client source (`~/Downloads/meteor-client-1.21.11`,
which targets the same Minecraft 1.21.11 + Yarn mappings) or from Anubis
(`~/Downloads/anubis-src`, same Minecraft version but Mojang mappings).

Everything here is intentionally small: a module is one class plus at most one
line in `ModuleManager`, and the ClickGUI picks up its name, description,
category and settings automatically.

---

## 1. The moving parts

| Piece | Location | Responsibility |
| --- | --- | --- |
| `Module` | `dev.bytephase.module.Module` | Base class: name, description, category, settings, enabled state, hooks |
| `Category` | `dev.bytephase.module.Category` | GUI categories (`COMBAT`, `MOVEMENT`, `PLAYER`, `RENDER`, `WORLD`, `MISC`) |
| `ModuleManager` | `dev.bytephase.module.ModuleManager` | Owns instances, dispatches ticks/renders/packets, isolates exceptions |
| `Setting` types | `dev.bytephase.setting.*` | `BooleanSetting`, `NumberSetting`, `ModeSetting`, `ColorSetting` |
| `ConfigManager` | `dev.bytephase.config.ConfigManager` | JSON persistence in `config/bytephase.json` |
| `Keybinds` | `dev.bytephase.input.Keybinds` | One vanilla key binding per module, stored in `options.txt` |
| `ClickGuiScreen` | `dev.bytephase.ui.ClickGuiScreen` | Draws everything; no per-module GUI code |
| `Render2D` | `dev.bytephase.render.Render2D` | Rounded rects, shadows, colour lerps used by the GUI |
| `RenderBox` / `RenderProjection` | `dev.bytephase.render.*` | 3D boxes/lines, world→screen projection |
| `EspRenderer` | `dev.bytephase.render.EspRenderer` | Shared ESP target modes, styles and colour helpers |
| `ClarityEspRenderer` | `dev.bytephase.render.esp.ClarityEspRenderer` | Raw-GL ESP mask/downsample/blur/composite adapter for the supplied Clarity shaders |
| `ShaderCrosshairRenderer` | `dev.bytephase.render.ShaderCrosshairRenderer` | Renders the supplied crosshair shader into an FBO and bridges its texture into the GUI |
| `ClaritySkyRenderer` | `dev.bytephase.render.sky.ClaritySkyRenderer` | Compiles the supplied sky presets, builds the noise texture, and renders the low-res upscale path |
| `ClarityTargetRenderer` | `dev.bytephase.render.ClarityTargetRenderer` | Builds the supplied `worldfx/target` billboard/cylinder geometry and draws it with the supplied shader |
| `dev.bytephase.render.shader.*` | `GlShader`, `GlTarget`, `GlState`, `GlSamplers`, `FullscreenQuad`, `GlTextures` | Small raw-GL helpers shared by the shader adapters |

`BytePhase` (the static holder) exposes `BytePhase.mc`, `BytePhase.modules` and
`BytePhase.config`. Inside mixins always null-check them: mixins are applied
before Fabric calls `onInitializeClient`.

---

## 2. Adding a module

1. Create the class in the right package (`movement`, `player`, `combat`,
   `render`, `world`, `misc`).
2. Extend `Module`, call `super(name, category, description)`.
3. Declare settings with `add(new ...)` as fields.
4. Implement the hooks you need.
5. Register it once in `ModuleManager.init()`.
6. Build and run; the GUI and config pick it up automatically.

Minimal example:

```java
package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;

public final class BunnyHop extends Module {
    private final NumberSetting boost = add(new NumberSetting("Boost", 1.2, 1.0, 2.0, 0.05));
    private final BooleanSetting onlySprinting = add(new BooleanSetting("OnlySprinting", true));

    public BunnyHop() {
        super("BunnyHop", Category.MOVEMENT, "Adds a small speed boost while hopping");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || !mc.player.isOnGround()) {
            return;
        }
        if (onlySprinting.getValue() && !mc.player.isSprinting()) {
            return;
        }
        var velocity = mc.player.getVelocity();
        mc.player.setVelocityClient(velocity.multiply(boost.getValue(), 1.0, boost.getValue()));
    }
}
```

Register it:

```java
// ModuleManager.init(), inside the Movement block
add(new BunnyHop());
```

That is the whole integration. Specifically, you do **not** need to:

* add a GUI entry (rows are generated from `ModuleManager.byCategory`),
* add a config entry (settings are serialised by name),
* add a key binding (a vanilla `KeyBinding` is created for every module at
  startup; the ClickGUI's expanded `Bind` row edits it),
* handle "enabled" persistence (`ConfigManager` stores it).

### Naming rules

* `Module` names are unique and used as config keys. Renaming a module loses
  its saved state; that is acceptable but worth knowing.
* Setting names are config keys too and are shown verbatim in the GUI. Keep
  them short, no spaces (`"EntityRange"`, not `"Entity Range"`).
* The description appears under the module name in the GUI. One sentence,
  lowercase is fine.

---

## 3. Available hooks

All of these are no-ops in `Module`; override only what you need.

| Hook | When |
| --- | --- |
| `onEnable()` / `onDisable()` | Toggled on/off, and `onDisable` again on client shutdown (`shutdown()`) |
| `onTick()` | Every client tick while enabled |
| `onTickAlways()` | Every client tick, enabled or not (rarely useful) |
| `onRender2D(DrawContext, float)` | HUD frame while enabled |
| `onRender3D(MatrixStack, VertexConsumerProvider, float)` | World pass while enabled |
| `onBeforeAttack(PlayerEntity self, Entity target)` | Before the attack packet is sent |
| `onAttack(PlayerEntity self, Entity target)` | Local player attacks |
| `onPacketSend(Packet<?>)` | Outgoing packet; return `true` to cancel |
| `onPacketReceive(Packet<?>)` | Incoming packet, before the listener; return `true` to drop |
| `usesEntitySnapshot()` | Return `true` if the module iterates `EntitySnapshot` |

`onEnable`/`onDisable` run inside a try/catch: an exception logs once and
disables the module instead of crashing the game. Do not swallow errors inside
your own hooks; let `ModuleManager` report them.

### Adding a new hook

If a feature needs a new event, add it in three places:

1. A method on `Module` (default no-op or `return false`).
2. A dispatch method on `ModuleManager` that iterates enabled modules inside a
   `try/catch` and calls `report(module, "<phase>", e)` on failure.
3. One mixin injection that calls the dispatcher.

The inbound-packet hook is a good template:

```java
// Module
public boolean onPacketReceive(Packet<?> packet) { return false; }

// ModuleManager dispatches from an immutable enabled-module list. When adding
// a new hook, keep the same pattern: iterate only enabled capable modules.
public boolean packetReceive(Packet<?> packet) {
    List<Module> active = activePacketReceivers;
    if (active.isEmpty()) return false;
    for (Module module : active) {
        try {
            if (module.onPacketReceive(packet)) return true;
        } catch (Exception e) {
            report(module, "incoming packet", e);
        }
    }
    return false;
}
```

```java
// ClientConnectionMixin
@Inject(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V",
                 shift = At.Shift.BEFORE),
        cancellable = true)
private void bytephase$receive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
    if (BytePhase.modules != null && BytePhase.modules.packetReceive(packet)) ci.cancel();
}
```

---

## 4. Settings

```java
new BooleanSetting("Name", true);

// value, min, max, step. step < 1 makes it a float slider.
new NumberSetting("Range", 4.5, 3.0, 8.0, 0.05);

// modes, default mode
new ModeSetting("Mode", new String[]{"Vanilla", "Timer", "None"}, "Vanilla");

// ARGB
new ColorSetting("Color", 0xFF8B5CF6);
```

Reading values:

```java
boolean on = boolSetting.getValue();
double value = numberSetting.getValue();   // also asInt() / asFloat()
String mode = modeSetting.getValue();      // compare with .equals("...")
int argb = colorSetting.getValue();        // red()/green()/blue()/alpha() helpers
```

The ClickGUI renders them as a switch, a draggable slider, a cycling value and
a colour row with R/G/B sliders. No extra work per module.

For entity/block ESPs, use `EspRenderer.TARGETS`, `EspRenderer.STYLES` and
`EspRenderer.drawBoxes(...)` instead of hard-coded colours or outline/fill pairs.
Passing a `Shader*` style queues the boxes for `ClarityEspRenderer`; the
manager flushes that queue once after `RenderBox.endFrame()`, so a module does
not need its own render pass or shader setup. That keeps target filtering, RGB
editing and batching consistent across modules.

When adding another supplied shader, keep the original GLSL byte-for-byte
under `src/main/resources/assets/bytephase/clarity_shaders/` and write only
the uniform/geometry adapter. `GlShader` reads that folder, `GlState` captures
and restores the raw GL state, and `GlTarget` provides RGBA8/RGBA16F
framebuffers. The ESP, crosshair, sky and target adapters are the current
examples of that split.

**Gaps to know about:** there is no `List<Item>` / block-list setting, no
per-setting visibility (`visibleWhen`), and no setting groups. Modes and
booleans cover most ports; for the rest, either add a small setting type
(`Setting<T>` needs `toJson`/`fromJson`) and add a branch to
`ClickGuiScreen.drawSetting` + `mouseClicked`, or hard-code the subset you
actually use.

---

## 5. Mixins

Mixins live in `dev.bytephase.mixin` and **must** be listed in
`src/main/resources/bytephase.mixins.json` under `client`. With
`"defaultRequire": 1`, a target that does not exist crashes startup; that is
deliberate, so mapping changes surface immediately.

Rules used in this codebase:

* Guard every handler with `BytePhase.modules == null ? null : BytePhase.modules.get(...)`
  and bail out when the module is null/disabled. For the hottest engine methods
  (bounding boxes, step height, slipperiness, FOV, HUD labels, packet callbacks)
  use the direct cached accessors such as `BytePhase.modules.hitBoxes()` or
  `BytePhase.modules.noRender()`; those return `null` unless the module is
  enabled and avoid a per-call map lookup.
* Prefer the MixinExtras helpers (`@ModifyReturnValue`,
  `@ModifyExpressionValue`, `@WrapOperation`) over `@Redirect`; they survive
  ordinal changes far better.
* Only touch the client player where it matters (`BytePhase.mc.player == (Object) this`
  or an entity comparison) so the integrated server is unaffected.
* Accessors go in their own class (`FishingBobberAccessor`) rather than being
  mixed into a module.

Useful injection points already in use:

| Target | Purpose |
| --- | --- |
| `ClientPlayerEntity.applyMovementSpeedFactors` | item-use slow (`NoSlow`) |
| `ClientPlayerEntity.tickMovement` | input spoofing (`SafeWalk`, `WTap`) |
| `LivingEntity.getHandSwingDuration` | swing speed (`SlowSwing`) |
| `HandledScreen.focusedSlot` (accessor) | hovered container slot (`HoverTotem`) |
| `PlayerEntity.getBlockBreakingSpeed` | mining speed (`FastMine`) |
| `PlayerEntity.getBlockInteractionRange` | reach |
| `ClientPlayNetworkHandler.onPlayerPositionLook` | rotation lock (`NoRotate`) |
| `GameRenderer.getFov` / `showFloatingItem` / `tiltViewWhenHurt` | smooth zoom, custom FOV, totem animation, hurt camera |
| `ClientConnection.send` / `channelRead0` / `exceptionCaught` | packet hooks, anti-kick |
| `Block.getSlipperiness` | `Slippy` |
| `CobwebBlock` / `SweetBerryBushBlock` / `SlimeBlock` | `NoSlow` |

The Meteor tree is the quickest way to find the right target for 1.21.11: its
mixin classes use the same Yarn names. Copy the injection point, then rewrite
the handler in BytePhase style.

---

## 6. Porting a Meteor module

Meteor has its own event bus, settings groups and utility classes; BytePhase
does not. The mechanical translation:

| Meteor | BytePhase |
| --- | --- |
| `categories.get(Categories.X)` in constructor | `Category.X` |
| `onActivate()` / `onDeactivate()` | `onEnable()` / `onDisable()` |
| `@EventHandler onTick(TickEvent.Pre)` | `onTick()` (runs at the end of the client tick) |
| `TickEvent.Post` | usually `onTick()`; use a mixin when a more precise phase matters |
| `PacketEvent.Send` | `onPacketSend`, return `true` to cancel |
| `PacketEvent.Receive` | `onPacketReceive`, return `true` to cancel |
| `Modules.get().get(X.class)` | `BytePhase.modules.get(X.class)` |
| `Setting<Double>` + `DoubleSetting.Builder` | `NumberSetting` |
| `BoolSetting` | `BooleanSetting` |
| `EnumSetting` | `ModeSetting` + `String` comparisons |
| `ColorSetting` | `ColorSetting` (ARGB value with editable RGB channels) |
| `ItemListSetting` / `BlockListSetting` | not supported; hard-code or add a setting type |
| `InvUtils.swap/move`, `FindItemResult` | `clickSlot(...)` with `SlotActionType.SWAP` (see `Offhand`, `ChestSwap`) |
| `Utils.canUpdate()` | `mc.player != null && mc.world != null` |
| `isActive()` | `isEnabled()` |
| `warning(...)` / `info(...)` | `BytePhase.LOGGER.warn/info` |

### What has been ported so far

| Module | Category | Needs |
| --- | --- | --- |
| `NoSlow` | Movement | `ClientPlayerEntity`, `CobwebBlock`, `SweetBerryBushBlock`, `SlimeBlock` mixins |
| `Blink` | Movement | `onPacketSend` + delayed flush |
| `AutoJump` | Movement | `onTick` |
| `ReverseStep` | Movement | `onTick` |
| `Parkour` | Movement | `onTick` |
| `Slippy` | Movement | `Block.getSlipperiness` mixin |
| `ChestSwap` | Player | one-shot on enable, inventory `SWAP` click |
| `NoRotate` | Player | `ClientPlayNetworkHandler.onPlayerPositionLook` |
| `FastMine` | Player | `PlayerEntity.getBlockBreakingSpeed` |
| `Offhand` | Player | inventory `SWAP` click (button 40) |
| `AutoWeapon` | Combat | `onAttack`, attack-damage attribute |
| `TimeChanger` | Render | `onPacketReceive` + level properties |
| `NoRender` | Render | `InGameOverlayRenderer`, `ClientWorld`, `GameRenderer` |
| `AntiPacketKick` | Misc | `ClientConnection.exceptionCaught`, `PacketByteBuf.readNbt` |
| `Spider` | Movement | `onTick` + `horizontalCollision` (Anubis) |
| `Sneak` | Movement | forced sneak key; 1.21.11 has no sneak command packet (Anubis) |
| `JumpReset` | Combat | `onPacketReceive` (`EntityVelocityUpdateS2CPacket`) (Anubis) |
| `WTap` | Combat | `onAttack` + `tickMovement` input mask (Anubis) |
| `HoverTotem` | Combat | `HandledScreen` accessor + inventory `SWAP` (Anubis) |
| `FastUse` | Misc | `MinecraftClient.itemUseCooldown` (Anubis) |
| `SlowSwing` | Misc | `LivingEntity.getHandSwingDuration` (Anubis) |
| `AntiTrap` | Misc | `onTick` entity sweep (Anubis) |
| `AutoReplenish` | Player | inventory `PICKUP` merge (Anubis) |
| `CustomFov` | Render | `GameRenderer.getFov` (Anubis) |
| `ItemESP` | Render | `RenderBox` + `RenderProjection` (Anubis) |
| `BlockESP` | Render | bounded local scan + `RenderBox` (Anubis) |
| `LowHealth` | Render | `onRender2D` + heartbeat sound (Anubis) |

Existing modules refined against Anubis behaviour: `Speed` (Vanilla/Strafe
modes and liquid/sneak gating), `Fly` (Velocity/Abilities, vertical match,
anti-kick drop), `Zoom` (time-based log-space easing), `AutoTotem`
(Always/Health modes, hotbar sources, latency-aware retry), `KillAura` (target
sorting, degrees-per-tick rotation, cooldown percentage), `Nametags` (rounded
background, health bar, distance), `Blink` (bounded packet buffer).

Deliberately **not** ported: anything that needs Meteor's

* baritone/pathfinding (`HighwayBuilder`, `Excavator`),
* fake players / account manager (`FakePlayer`, `Swarm`),
* list settings (`Xray`, `SoundBlocker`),
* Starscript HUD or macro system.

### Porting an Anubis module

Anubis uses the same Minecraft version, so its logic usually transfers as-is;
only the names differ. The mechanical translation (all verified against
1.21.11 Yarn):

| Anubis (Mojang mappings) | BytePhase (Yarn) |
| --- | --- |
| `LocalPlayer` | `ClientPlayerEntity` |
| `Minecraft.getInstance()` / `mc.screen` | `BytePhase.mc` / `mc.currentScreen` |
| `mc.gameMode` | `mc.interactionManager` |
| `player.getDeltaMovement()` / `setDeltaMovement(v)` | `getVelocity()` / `setVelocityClient(v)` |
| `player.onGround()` / `horizontalCollision` | `isOnGround()` / `horizontalCollision` |
| `player.containerMenu` | `player.currentScreenHandler` |
| `ClickType` | `SlotActionType` |
| `getBoundingBox()` | `getBoundingBox()` (same) |
| `level.entitiesForRendering()` | `mc.world.getEntities()` |
| `ItemStack.is(Items.X)` | `ItemStack.isOf(Items.X)` |
| `mc.getConnection()` | `mc.getNetworkHandler()` |
| `ClientboundSetEntityMotionPacket` | `EntityVelocityUpdateS2CPacket` |
| own event bus (`TickEvent`, `PacketSendEvent`, ...) | `onTick`, `onPacketSend`, `onPacketReceive`, mixins |
| `super(id, name, description, category)` | `super(name, category, description)` |

Not ported from Anubis, and why:

* shader/HUD passes (`MotionBlur`, `ColorGrading`, `Atmosphere`, `ShaderESP`)
  need the Anubis shader assets and a per-frame GPU hook,
* character/cosmetic rendering and the Peep sky need large asset packs,
* the donut-macro family (`AutoTpaHere`, AH bots, order macros) is specific to
  one server's chat protocol and UI,
* `BlockESP`/`Xray` colour lists are Anubis list settings; BytePhase has no list
  setting, so `BlockESP` ships preset groups (`Spawner`, `Portal`, `Ore`,
  `Storage`) instead.

---

## 7. The ClickGUI

`ClickGuiScreen` uses floating panels for each module category:

* each panel has a category icon, title and collapse control,
* drag a panel header to move it; right-click a header to collapse it,
* left-click a module row to toggle it and click its chevron to show settings,
* expanded rows expose the keybind and each setting inline,
* scroll inside a panel to move through its modules; overlapping panels follow
  their stacking order for clicks and scroll input.

Implementation detail worth remembering: the screen records absolute `Hit`
rectangles while drawing (`render` → `hits`), and input handlers only walk that
list. When you add a new control, register a `Hit` where you draw it and give
it a `Kind`; clipped hits are ignored outside the panel's scroll area. This
keeps hitboxes aligned while panels scroll or expand without duplicating
layout math.

`dev.bytephase.ui.Theme` holds the palette and `Icon` maps the small PNG
category and chevron textures in
`assets/bytephase/textures/gui/icons`; `Icon.draw` tints the white source
images at draw time.

`Render2D.roundedRect` / `shadow` / `roundedOutline` build rounded shapes out of
a body plus corner spans, which keeps the number of GUI render-state entries
low. Use them instead of `context.fill` when you want the modern look.

---

## 8. Verification checklist

1. `./gradlew build` (Java 25 required for Loom; bytecode targets 21).
2. Launch the dev client with `./gradlew runClient` (or the installed jar).
3. While it boots, watch for:
   * `Mixin apply ... failed` — the injection target changed,
   * `Module X failed during ...` — an exception in a hook,
   * `Illegal option value` — something is writing an invalid vanilla option.
4. Open the GUI with Right Shift, toggle the module, change each setting.
5. Quit normally: the config is saved on close and on `CLIENT_STOPPING`.
