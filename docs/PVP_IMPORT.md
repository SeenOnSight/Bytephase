# PvP import — version 0.2.3

Status: **0.2.3 source update; gameplay testing pending**. The production JAR was built
locally. No game launch or gameplay tests were performed by the agent.

The first user launch exposed a mixin package declaration covering the entire
combat component. It blocked normal API classes from loading. The corrected
build limits that declaration to `dev.bytephase.pvp.mixin` and moves the mouse
accessor into that same package. The corrected JAR awaits another user launch.

## Mace and crystal fixes

The current 0.2.3 build includes fixes for the reported combat failures:

- Mace compares each candidate weapon's own attack damage. Delayed smash waits
  one tick instead of repeatedly postponing an attack throughout the fall.
- Mace rearms when a new fall begins, including Wind Burst launches and a
  confirmed smash resetting fall distance, instead of requiring a landing.
- Combat sprint pauses now survive the utility Sprint module's end-of-tick
  update. Assisted attacks send vanilla's sprint state update before attacking.
- An attack blocked by the rotation gate no longer counts as a sent attack.
- Crystal placement reports success only when the interaction succeeds, so
  failed placements do not start a spawn confirmation wait.
- Selecting an already selected CrystalAura weapon preserves its settling timer.

Compilation and JAR remapping completed successfully. Gameplay confirmation is
pending. For Mace auto attacks, enable **Smash Assist**, aim at an enemy player,
and fall at least the configured **Min Fall** distance (3 blocks by default).
Enable **Mobs** when trying it against mobs.

## AimAssist, SwordObsidian and tracers

AimAssist continues steering with a stationary mouse. It retains its Range,
FOV, Speed, Weapons Only and Vertical controls. Manual movement away from the
target pauses the corresponding assisted axis for that frame.

Enable **Combat → SwordObsidian**, hold a sword, and right-click the top face
of the ground with obsidian in your hotbar. The module selects obsidian before
vanilla reads the held item, allowing the same click to place it. It leaves
obsidian selected by default. Enable **Switch Back** to restore the sword after
the action. Existing hotbar ownership and busy-hand checks still apply.

Tracers unprojects the crosshair through the complete world transform to keep
its line origin centered during camera rotation, view bob and hurt tilt.

The build also includes the particle/item limits and render work reductions
described in [Performance controls](PERFORMANCE.md).

## Triggerbot timing

**Instant** defaults to enabled. Triggerbot checks the current crosshair target
after mouse look and AimAssist on every focused frame, including frames with
a stationary mouse. Tick checks remain active too.

**Cooldown (%)** ranges from **0 to 100**, defaulting to **90**. Lower it to
attack earlier in the weapon's cooldown; lower charge also reduces damage.
Disable Instant to use **Reaction** and, with Smart crits, **Crit Wait (ticks)**.
Smart crits adds no wait with Instant enabled. Crits Only still requires an
actual critical hit opportunity. Busy hands, reach, walls, shields and another
combat module's ownership can defer an attack.

## FakeLag visualization

FakeLag shows an orange box at the last movement position sent to the server
while packets are queued. **Show Tether** connects that position to your local
position, and **Show World Label** displays their separation in metres.
The position is an estimate from outgoing movement packets.

**Show Queue HUD** displays the oldest queued packet's age, packet count,
position offset and a bar relative to the configured delay. With an empty
queue, it displays “ready”. Configure **Position Color**, **Fill Opacity (%)**
and **Through Walls** in FakeLag's settings. **Show Server Position** controls
the box, tether and world label together; the queue HUD has its own toggle.
Visualization state resets when the player, world or connection changes.

## Place CPS

CrystalMacro and AnchorMacro each expose **Place CPS**, from **5 to 20** in
steps of 1, defaulting to **20**. Right-click the module in the Combat category to
change it. The value caps placement rate; breaking, charging, and detonation
are not slowed by this setting.

Placement pacing retains fractional tick intervals for intermediate values.
Only successful placements advance the timer, and waits for the server do not
accumulate a burst of extra placements. Anchor replacement in Double mode uses
the same timer as the first placement. Server response time, occupied bases,
and the anchor's charge/detonate cycle can reduce the achieved rate.

## CrystalMacro placement assistant

Hold the normal place/use button with crystals in either usable hand and aim at
an obsidian or bedrock base. CrystalMacro removes the normal repeat delay as
soon as that base is available and the Place CPS timer is ready, so holding the button repeatedly places crystals
without having to click again. It records
successful placements from either hand, matches the crystal's actual spawn to
the placed base block, and attacks from the spawn handler as soon as the server
adds the crystal to the client world. It does not predict entity IDs.

**Break CPS** controls attack pacing. Automatic obsidian placement and a
self-damage threshold are not provided by this module. Old settings for those
behaviors are ignored. **Switch Back** controls restoring your slot if a weapon swap is
needed to break the crystal under Weakness.

It waits for an outstanding placement at the same base to spawn before sending
another placement there. An occupied base must clear before it can be reused;
the crystal optimizer can clear a successfully attacked crystal locally. The
spawn must arrive before the attack can be sent. An existing entity attack,
a busy hand, another module owning the hotbar, or an obstructed/out-of-range
crystal can defer the break. The pending placement expires after a short
confirmation window and is cleared on disconnect, world change, or disable.

## AnchorMacro held repeat

With anchors and glowstone in the hotbar, hold the normal place/use button to
repeat the place, charge, and detonate cycle. Held cycles respect Delay and
the individual stage delays. They reselect an anchor after detonation, even when Switch Back is off.
Releasing the button prevents the next cycle from starting.

The macro reads the physical button state, so suppressing vanilla use during a
cycle does not lose the held input. Failed interactions are retried without
advancing the cycle. Double mode and repeated cycles wait for the detonated
anchor to disappear before placing its replacement. The existing Safe Anchor
and Protect Loot settings still apply.

CrystalMacro and AnchorMacro each expose separate **Place CPS** and **Break
CPS** sliders, from 0.5 to 20 in 0.5 increments. A setting of 0.5 spaces
successive actions two seconds apart at 20 game ticks per second. The first
action is immediately eligible; subsequent actions follow the selected rate.
CrystalMacro paces attacks on its confirmed crystals. AnchorMacro paces
detonations, including the second anchor in Double mode. Placement speed applies
to new crystals and anchors, including replacement anchors. Charging and guard
placement have their own additional delay controls.

Held repeat uses the same limits. Slow settings retain pending crystal attacks
and allow the anchor's detonation step to wait for its rate limit. Rate state
resets when the module is toggled or the player/world session changes.

### Anchor delay controls

The original **Delay** setting now ranges from 0 to 100 game ticks and adds
that wait to each scheduled step, including held cycles. Its saved setting
name is preserved. At 20 game ticks per second, 100 ticks is five seconds.

Each new slider ranges from **0 to 5000 milliseconds**, in **50 ms** increments,
and defaults to zero:

| Setting | Applies before |
| --- | --- |
| Place Delay (ms) | The initial anchor placement and replacement placements |
| Charge Delay (ms) | Charging the anchor with glowstone |
| Detonate Delay (ms) | Each detonation, including already-charged anchors |
| Guard Delay (ms) | Placing/checking the blast guard; visible with Safe Anchor |
| Replace Delay (ms) | The replacement anchor in Double mode; adds to Place Delay |
| Repeat Delay (ms) | Starting the next cycle after the previous cycle finishes |
| Switch Back Delay (ms) | Restoring the previous hotbar slot; visible with Switch Back |

Stage delays add to Delay. CPS limits can extend the wait further. Millisecond
values are converted to game ticks, so actual elapsed time increases when the
game runs below 20 ticks per second. Action-confirmation timeouts start after
the configured stage wait. Delayed initial placement checks that the same
target is still under the crosshair before placing.

The supplied archive contains 25 combat modules. All 25 are imported, together
with their combat helpers and the required input, inventory, event and packet
hooks. Their settings are exposed in Bytephase's existing Combat panel.

AimAssist, AutoTotem, HoverTotem, JumpReset and WTap replace their previous
registered implementations. The other 20 are new catalog entries. The additional
MarlowOptimizer, SwordObsidian and ThrowableTrajectory modules bring Bytephase to **124 modules**.
Existing utilities and compact render controls
remain available. The original PvP algorithms are retained; UI drawing, module
registration, settings persistence, friend storage and input/event integration
are adapted to Bytephase.

## Install this build

Replace the instance's previous Bytephase JAR with the local
`bytephase-0.2.3.jar`. The PvP component is embedded in that JAR; it requires no
separate combat JAR or native GUI library. The loader will list both Bytephase
and its embedded Bytephase PvP component.

Use Minecraft 1.21.11, Fabric API 0.141.6+1.21.11, Fabric Loader 0.20.1 or newer,
and Java 21 or newer. Compile and remap from source with Java 25:

```sh
./gradlew remapJar
```

Output: `build/libs/bytephase-0.2.3.jar`.

## Controls

Open the ClickGUI with Right Shift and select Combat in the sidebar. Left-click
cards to toggle modules; right-click a card to open its settings dialog.
Toggle binds use the dialog's Keybind control and vanilla Controls.

Key Throw, Spear Swap and the aura activation settings also have action binds.
Click their key setting, then press a keyboard key or mouse button. Escape or a
right-click on the setting clears it. Conditional settings only appear when
relevant to the selected mode.

Friends are managed locally:

```text
/bytephase friend add PlayerName
/bytephase friend remove PlayerName
/bytephase friend list
```

The list is saved in `config/bytephase/friends.json`. The imported target filters
and FriendGuard read that same list. Combat settings and enabled states use the
existing Bytephase configuration and profiles.

## Imported modules

| Module | Behavior from the supplied source |
| --- | --- |
| AimAssist | Eases your crosshair onto the nearest enemy player, including while the mouse is still. |
| AnchorAura | Places, charges and detonates respawn anchors on nearby enemies. |
| AutoCity | Mines a surround block of the best enemy in a hole, then turns off so Crystal Aura can blow through it. |
| AutoInventoryTotem | Refills the offhand totem after a pop through the inventory or a hotbar swap; Double Hand also keeps one in the main hand while in danger. |
| AutoTotem | Refills an empty offhand with a totem from your inventory, at a human pace you choose with Refill Delay. |
| AutoTrap | Boxes the nearest enemy in with obsidian: sides at feet and head height, then the top. |
| CrystalAura | Places and breaks end crystals for the most damage. |
| CrystalMacro | Rapidly places crystals while use is held, then attacks your new crystals as their spawns arrive. |
| CrystalOptimizer | Removes crystals client-side as soon as your hit breaks them. |
| FakeLag | Holds your outgoing packets for a moment so the server sees you a little behind. Dynamic: only while you walk at the player you aim at. |
| FastXP | Throws experience bottles faster while use is held. |
| FriendGuard | Stops you from hitting players on your friends list. |
| HoverTotem | Swaps a hovered totem from any open container into the offhand or selected hotbar slot. |
| JumpReset | Jumps the tick knockback lands to take less of it. |
| KeyThrow | Throws a pearl or a wind charge with a key and swaps back. |
| KillAura | Attacks nearby targets without moving your camera. |
| KnockbackDelay | Takes some hits' knockback a random moment late: a little in the air, longer on the ground. |
| Mace | Swaps to the strongest mace for smash hits, with an optional smash assist. |
| ShieldBreaker | Swaps to an axe for any hit into a raised shield. Auto also makes the hit. |
| AnchorMacro | Rapidly repeats anchor placement, charging, and detonation while use is held. |
| SpearSwap | Swaps to your Lunge spear and lunges. Reach Poke stabs with a spear past sword reach. |
| SwordObsidian | Selects hotbar obsidian before a ground-use click with a sword; optionally switches back afterward. |
| Triggerbot | Hits the crosshair target at a configurable cooldown threshold, with immediate frame checks. |
| WTap | Resets your sprint right after a sprint hit so the next hit knocks back too. |
| CrystalPvPBot | Crystal-fights the nearest enemy player: steers the Crystal and Anchor Aura, knocks them up, breaks their hole, guards, eats, mends, refills and pearls. |
| SwordPvPBot | Fights the nearest enemy player with a sword or axe: keeps out of their reach while charging, times crits, strafes and eats gapples. |

## Integration details

- The combat component is compiled with the source's Minecraft names and
  remapped to the same runtime namespace as the utility client. A Java-only
  interface connects it to the existing module registry and settings controls.
- Keyboard and mouse action events, native attacks, inventory actions, movement
  shaping and received/applied packets use the required combat hooks.
- Server rotation, delayed packets, slot restoration, macro ownership and bot
  coordination retain the supplied implementation.
- Food ownership uses the existing client state. Offhand management yields to
  the imported totem modules, and HotbarArrange yields to AutoInventoryTotem.
  AutoWeapon yields to Mace, ShieldBreaker and SpearSwap; SprintReset yields
  to the imported WTap implementation.
- Internal bot changes to other modules are reflected in the GUI and HUD.
- Damage labels use the shared world-label budget and native HUD drawing.
- The imported component retains its GPL-3.0 license in `LICENSE_PVP`; the
  existing utility source retains its own license.

Gameplay confirmation remains pending for these changes.

## MarlowOptimizer addition

The local working tree also includes the optimizer from the supplied 2.0.0 JAR
as a separate toggleable module. See [module details](MARLOW_OPTIMIZER.md).
