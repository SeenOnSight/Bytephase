# HUD positions, sizes and new overlays — 0.2.4

Open Right Shift → Settings → **Move HUD**. Drag an overlay with the left
mouse button. Right-click it, then scroll or drag vertically to change its size
from 0.25× to 3×. Press R to reset the selected overlay; Reset all restores the
whole layout. Done or Escape saves the layout and returns to the GUI.

Positions follow the viewport dimensions and are kept on screen when the
window or Minecraft GUI scale changes. Layout data is stored with Appearance,
so both the ordinary config and named configs retain positions and sizes.

Enabled Bytephase screen overlays, native imported HUD panels and the FakeLag
queue widget use the editor. Vanilla potion icons, hotbar, crosshair, health,
armor, hunger, air bubbles, chat, boss bars, scoreboard, action bar and titles
also have independent positions. Conditional widgets appear when their content
is visible. ArmorHUD, Effects and TargetHUD retain editable placeholders.

## New Visuals modules

- **Keystrokes:** movement inputs, an optional jump row and mouse buttons.
  Movement labels follow the actual key bindings.
- **Compass:** a heading strip with cardinal/intercardinal directions and an
  optional degree readout.
- **InventoryHUD:** a live inventory grid without opening a container.
  Horizontal and vertical layouts, hotbar inclusion, stack overlays, empty
  slots, title and background opacity are configurable.

InventoryHUD uses the inventory-grid idea shown by
[InventoryHUD+](https://modrinth.com/mod/inventoryhudplus); its implementation
uses Bytephase's renderer and Minecraft's live inventory data.

## ViewModel

The existing Scale and OffsetX/Y/Z values control the main hand. Disable
**MirrorOffhand** to reveal independent OffhandScale, OffhandOffsetX/Y/Z and
OffhandRotationX/Y/Z settings. RotationX/Y/Z controls the main hand.
Mirror mode preserves the previous linked offsets and scale.

## PerfectWindCharge

- **Look Down Speed (deg/tick):** 1–180; higher values turn the server pose faster.
- **Aim Ticks:** minimum preparation time, 1–20 ticks.
- **Wind Charge Delay (ms):** 0–2,000 ms after the downward aim becomes ready.
- **Switch Back:** return to the previous hotbar item, or stay on the wind charge.
- **Switch Back Delay (ms):** 0–2,000 ms after a successful throw.

The downward pose is sent to the server and used for the local third-person
model. The first-person camera retains its own pitch and mouse control. Item
use receives the same downward aim, including across the PvP integration.
Manual slot changes, menus and player changes cancel a pending action.

## Shield Breaker delays

| Setting | Default | Range |
| --- | ---: | ---: |
| Swap Delay (ms) | 0 | 0–2,000 |
| Attack Delay (ms), Auto mode | 0 | 0–2,000 |
| Switch Back Delay (ms) | 100 | 0–2,000 |
| Repeat Delay (ms), Auto mode | 500 | 0–5,000 |

Passive mode waits before swapping for a manual hit. Auto mode waits for the
swap, then the attack. Pending actions recheck the same target, raised shield,
reach, hotbar ownership and held axe. Switch-back does not undo a manual item
change. All millisecond waits are evaluated on game ticks.

## Build status

Production compilation and remapping are the packaging checks. In-game
interaction, visuals, multiplayer pose replication and FPS measurements remain
unverified.
