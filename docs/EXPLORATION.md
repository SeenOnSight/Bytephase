# Exploration & everyday workflows

[Home](../README.md) · [Module guide](MODULE_GUIDE.md) · [Adding modules](ADDING_MODULES.md)

Explore loaded terrain, keep useful coordinates, and make routine inventory work
easier. These utilities share Bytephase's module settings, binds and local storage.
Existing ESP, shaders and HUD modules remain available alongside them.

**On this page:** [Quick start](#quick-start) · [Scanners](#loaded-chunk-scanners) ·
[Navigation](#navigation--coordinate-capture) · [Mining](#tunnelminer) ·
[Inventory](#hotbararrange) · [Trading](#tradeassist) ·
[Profiles](#profiles) · [Commands](#command-reference) · [Local data](#local-data)

---

## Quick start

1. Open the ClickGUI with **Right Shift**. The **Explore** panel contains
   `BaseFinder`, `SusChunks`, `Navigator` and `CoordSnapper`.
2. Enable a scanner, travel normally, then use `/bytephase findings list`.
3. Save a displayed finding with `/bytephase findings save 1 "Return point"`.
   This creates a waypoint and enables `Navigator`.
4. Assign `CoordSnapper` a bind for one-shot clipboard capture.
5. Open `/bytephase profile` to save your current module settings and states.

```text
/bytephase waypoint add "Departure"
/bytephase findings list
/bytephase findings save 1 "Return point"
/bytephase waypoint track "Departure"
```

Finding indices come from the last successful findings listing in the same
server and dimension. Use the index actually displayed in your chat.

## Loaded-chunk scanners

Both scanners inspect chunks already available to the client. Increasing their
range expands the search window; it does not load distant terrain. Findings are
**candidates**: storage, utility blocks and naturally generated amethyst can all
occur without a player base.

| Scanner | Default range | Scan work per tick | Evidence |
| --- | --- | --- | --- |
| `BaseFinder` | 4 chunks | Soft **0.8 ms** budget | Storage counts, utility counts, spawners |
| `SusChunks` | 4 chunks | Soft **1.5 ms** budget | Attached amethyst growth or budding amethyst |

Ranges run from 1 to 8 chunks. The square window contains up to 289 chunk
coordinates at range 8, and nearby rings are queued first. Scans resume across
ticks, skip empty or irrelevant sections, and discard stale work when chunks
change or unload. The budgets limit the scan loop, not total module time;
palette checks, rendering, bookkeeping and storage can add work.

Live markers follow the currently loaded, in-range evidence. Saved findings
remain historical records after a marker disappears.

### BaseFinder

Source: [BaseFinder.java](../src/main/java/dev/bytephase/module/world/BaseFinder.java)

Counts blocks **per chunk**, across its sections:

| Setting | Default | Meaning |
| --- | --- | --- |
| `ChunkRange` | `4` | Chunk radius, from 1 to 8 |
| `MinStorage` | `6` | Storage blocks required for a stash candidate |
| `MinUtilities` | `4` | Utility blocks required for an activity candidate |
| `FindSpawners` | On | Record individual spawner positions |
| `ChatAnnounce` | On | Show local discovery messages |
| `ChunkMarkers` | On | Highlight qualifying storage/utility chunks |
| `Color` | Violet | Chunk-marker colour |

**Storage:** chests, trapped chests, barrels, hoppers and all shulker boxes.
These are block counts; a double chest contributes two blocks. Container
contents are not inspected.

**Utilities:** beacons, enchanting tables, ender chests, respawn anchors,
all anvil damage states and redstone lamps.

A chunk qualifies when either count reaches its threshold. Storage takes
precedence for the finding kind (`stash`); otherwise the kind is `activity`.
The saved coordinate is an evidence block, and the label records both counts.
With `FindSpawners` enabled, up to 32 spawners per scanned chunk are collected
separately as `spawner` findings.

Completed scans produce chunk highlights around the evidence height using the
existing through-walls renderer. The HUD suffix shows the live marked-chunk
count. Changes to chunks, thresholds or world scope invalidate applicable
results. An idle full rescan is scheduled after 200 ticks, and queued chat
announcements are spaced by at least 20 ticks.

### SusChunks

Source: [SusChunks.java](../src/main/java/dev/bytephase/module/render/SusChunks.java)

| Setting | Default | Meaning |
| --- | --- | --- |
| `Range` | `4` | Chunk radius, from 1 to 8 |
| `Signal` | `Growth` | Choose attached growth or geode presence |
| `MinGrowth` | `6` | Attached growth count required in Growth mode |
| `MinBudding` | `6` | Budding-block count required in Geode mode |
| `LogFinds` | On | Save candidates to local findings |
| `ChatAnnounce` | On | Show local discovery messages |
| `Tracers` | Off | Draw lines to live candidate markers |
| `Style` | `Highlight` | Existing shared ESP styles |
| `ThroughWalls` | On | Show markers through terrain |
| `Color` | Violet | Marker colour |

- **Growth** counts small, medium and large amethyst buds and amethyst clusters
  whose facing points back to a budding-amethyst support block. A support
  across a chunk boundary must also be loaded.
- **Geode** counts budding-amethyst blocks, regardless of attached growth.

These signals describe visible block state, not chunk age, player presence or
an observed growth history. Natural geodes can satisfy either threshold.
Both modes save the `geode` finding kind, with a label identifying the signal
and count.

Markers span the evidence's vertical range within each chunk. The HUD suffix
shows the live candidate count. An idle rescan is scheduled after 400 ticks;
markers expire after 120 seconds without a qualifying refresh, or sooner when
evidence becomes invalid. Queued chat announcements are spaced by at least
20 ticks. Turning `LogFinds` off keeps results in the live session instead of
writing them to the location store.

## Navigation & coordinate capture

### Navigator

Source: [Navigator.java](../src/main/java/dev/bytephase/module/world/Navigator.java)

Select a saved waypoint with `/bytephase waypoint track <name>`. Creating a
waypoint also selects it and enables `Navigator`.

The compact HUD readout shows **name · distance · Ahead/Behind/Left/Right · Y**.
Distance is the three-dimensional distance to the saved block's centre;
direction is relative to your current yaw. It provides guidance while you
control movement.

`ArrivalRange` defaults to 8 blocks, with a range of 1–32. With
`ArrivalMessage` on, arrival is announced once; moving beyond twice the
arrival range re-arms the message. A selected waypoint is available only in
its saved server and dimension. Hiding the HUD also hides the readout.

### CoordSnapper

Source: [CoordSnapper.java](../src/main/java/dev/bytephase/module/misc/CoordSnapper.java)

Assign a bind, then press it to copy once. The module disables itself after
the attempt and reports the result in local chat.

| Setting | Default | Meaning |
| --- | --- | --- |
| `Target` | `LookedAt` | Raycast a block, or choose `OwnPosition` |
| `Range` | `128` | Module raycast distance, from 16 to 512 blocks |
| `IncludeDimension` | On | Append the dimension identifier |
| `StopAtFluids` | Off | Let the raycast stop on fluids |

The clipboard format is `x y z [minecraft:overworld]` when dimension text is
enabled. A missed raycast reports that no loaded block is in line of sight.

The command shortcuts have fixed behavior:

- `/bytephase coords` copies your current block position.
- `/bytephase coords look` raycasts up to 256 blocks, without stopping on
  fluids.
- Both commands include the dimension.

Coordinate commands write the clipboard and local chat; they do not send
coordinates to server chat or request a teleport.

## TunnelMiner

Source: [TunnelMiner.java](../src/main/java/dev/bytephase/module/world/TunnelMiner.java)

Found in **World**. Stand at the desired floor level, face the tunnel's
cardinal direction, hold a suitable tool and enable the module.

| Setting | Default | Meaning |
| --- | --- | --- |
| `Width` | `1` | Tunnel width, from 1 to 5 blocks |
| `Height` | `2` | Tunnel height, from 1 to 5 blocks |
| `ForwardDistance` | `3` | Search depth, from 1 to 6; capped by actual reach |
| `AutoWalk` | Off | Walk forward only when the clearance checks pass |

The heading and floor height are captured on the first eligible tick.
Even widths extend one block farther to the right. Mining turns the visible
camera toward a reachable block and requires the crosshair raycast to hit
that block. It uses vanilla breaking progress and can cooperate with enabled
`PacketMine` without restarting its acknowledgement wait.

Mining pauses for screens, lost window focus, item use, another camera,
unloaded corridor cells or a change in floor height. It yields to enabled
`AutoMine` and `Nuker`, and avoids taking over unrelated mining.

Targets exclude block entities, fluids, unbreakable blocks, restricted
blocks and blocks the held item cannot mine. Adjacent fluid or an unloaded
neighbor prevents mining, as does a falling block directly above the target.
World-border, visible-face and reach checks also apply.

`AutoWalk` requires a clear, supported, loaded corridor at least two blocks
high. It checks the swept player footprint, fluids and hazards, and pauses
for incompatible movement input, enabled Movement modules or unsafe motion.
Server corrections release input, clear the heading and pause for 10 ticks
before a new heading can be captured.

## HotbarArrange

Source: [HotbarArrange.java](../src/main/java/dev/bytephase/module/player/HotbarArrange.java)

Found in **Player**. This is a continuous organizer: assign a role to each
`Slot1`–`Slot9`, then enable it. All nine slots default to **Keep**, so
enabling it with untouched settings performs no swaps.

| Available roles | |
| --- | --- |
| Leave in place | `Keep` |
| Tools & weapons | `Sword`, `Pickaxe`, `Axe`, `Bow`, `Mace` |
| Combat & travel | `Totem`, `Pearl`, `Crystal`, `WindCharge` |
| Supplies | `Obsidian`, `Food` |

Matching stacks stay where they are. A missing role is supplied only from
**main-inventory indices 9–35**, choosing the largest matching stack.
The currently held hotbar slot is skipped. Other hotbar slots, armour and
offhand are never used as sources.

Each eligible cycle performs at most one vanilla `SWAP`. `Delay` defaults to
2 ticks and can be set from 0 to 20. Tool roles use item tags; `Food` matches
the food component. It does not rank tools by enchantment or durability.

It pauses while a screen is open, the cursor is occupied, an item is in use,
attack/use is held, a block is being mined or the player is hurt. Active
silent rotation, busy `ElytraMace`, `Blink` and `FakeLag` also pause it.
Inventory and interaction packets create a short quiet period, and server
corrections add backoff.

Pending `AutoReplenish` refills and `AutoTotem` offhand work take priority,
including their cooldown periods. A role with no matching supply is simply
left for a later cycle.

## TradeAssist

Source: [TradeAssist.java](../src/main/java/dev/bytephase/module/player/TradeAssist.java)

Found in **Player**. Configure its settings and bind before opening a
merchant. Open the **MerchantScreen**, select an offer, leave the cursor
empty and press the bind to start a bounded manual batch. Press it again to
stop. The bind is explicitly handled while the merchant screen is open.

| Setting | Default | Meaning |
| --- | --- | --- |
| `Count` | `8` | Batch limit, from 1 to 64 trades |
| `TickDelay` | `4` | Pacing between confirmed steps, from 1 to 40 ticks |
| `MaxEmeraldCost` | `0` | Per-trade emerald-input cap; 0 disables the cap |

It operates on the selected offer through vanilla trade-slot interactions.
Each result uses a separate empty hotbar slot, so available hotbar slots can
limit the batch below `Count`. Trades proceed one result at a time, with server
inventory evidence required before another trade.
A locally predicted transfer alone does not count as a verified trade;
missing confirmation can stop a batch even when the server accepted it.
Confirmation times out after 100 client ticks; an accepted first trade can
therefore finish with zero trades reported as verified.

The batch stops on completion, exhausted offers, missing inputs, inadequate
result space, changed offer/session, another inventory interaction, a server
correction or confirmation timeout. Check the stop message and inventory
before restarting an unverified transfer. Remaining inputs stay in vanilla
trade slots for manual handling.

TradeAssist does **not** navigate to villagers, open their screens or choose
offers for you.

## Profiles

Sources: [ProfileManager.java](../src/main/java/dev/bytephase/config/ProfileManager.java) ·
[ProfileScreen.java](../src/main/java/dev/bytephase/ui/ProfileScreen.java)

Open `/bytephase profile` for the profile picker. Enter a name or choose a
saved row, then use **Save**, **Load** or **Delete**. Existing names show
**Overwrite**; deletion requires a second click on **Delete now**.
The picker uses ordinary keyboard-accessible Minecraft widgets, pagination
and up to five visible profile rows.

Profile names accept 1–32 ASCII letters, digits, underscores or dashes.
Storage is limited to 128 profiles and 1 MiB per file.

Profiles contain module enabled states and Boolean, number, mode and colour
settings. Keybinds, ClickGUI layout, coordinates and server identifiers are
outside the profile snapshot. Loading validates the document before applying
it, preserves omitted settings/modules, and ignores well-formed unknown
modules. Known modes, types and number bounds must be valid.

If applying a profile fails, the manager attempts to restore the previous
configuration. It reports whether recovery completed; incomplete recovery
can leave modules disabled. Loading a profile runs module activation hooks,
including one-shot behavior, so save enabled states intentionally.

Command-based save overwrites the same named profile. Command-based delete
acts immediately; the second-click confirmation belongs to the picker UI.

## Command reference

Source: [BytephaseCommand.java](../src/main/java/dev/bytephase/command/BytephaseCommand.java)

All commands below execute locally. Use `/bytephase` for help.
Quote waypoint names containing spaces; profile names cannot contain spaces.
Lists display 20 entries per page.

| Command | Action |
| --- | --- |
| `/bytephase coords` | Copy your current block coordinates and dimension |
| `/bytephase coords look` | Copy the looked-at block, with a 256-block raycast |
| `/bytephase waypoint add <name>` | Save your position; select it and enable Navigator |
| `/bytephase waypoint add <name> look` | Save the looked-at block using the same fixed raycast |
| `/bytephase waypoint list [page]` | List waypoints in the current server/dimension |
| `/bytephase waypoint track <name>` | Select a waypoint and enable Navigator |
| `/bytephase waypoint remove <name>` | Remove a waypoint in the current scope |
| `/bytephase findings list [page]` | List current-scope findings and snapshot their indices |
| `/bytephase findings save <index> <name>` | Convert a listed finding into a selected waypoint |
| `/bytephase findings clear` | Remove findings in the current server/dimension |
| `/bytephase findings prune <days>` | Remove older findings across all stored scopes |
| `/bytephase profile` | Open the profile picker |
| `/bytephase profile list` | List saved profile names |
| `/bytephase profile save <name>` | Save or overwrite the current module snapshot |
| `/bytephase profile load <name>` | Apply a saved module snapshot |
| `/bytephase profile delete <name>` | Delete the named profile immediately |

`findings prune` accepts 1–3650 days. Both finding cleanup commands retain
manual waypoints. Waypoint names are matched without case sensitivity within
their server and dimension; adding the same name replaces that waypoint.

## Local data

Source: [LocationStore.java](../src/main/java/dev/bytephase/data/LocationStore.java)

Paths below are relative to the active Minecraft game directory with Fabric's
default config directory. A development client uses the corresponding paths
inside `run/`.

| Path | Contents |
| --- | --- |
| `config/bytephase.json` | Active module settings/states and ClickGUI layout |
| `config/bytephase/profiles/<name>.json` | Named module snapshots |
| `config/bytephase/locations.json` | Waypoints, historical findings and selected waypoint |
| `options.txt` | Vanilla-managed module and ClickGUI binds |

Locations are scoped by the normalized multiplayer server address, or the
singleplayer save's absolute directory, plus the dimension identifier.
Moving a singleplayer save or changing the server address can therefore
change the scope used to find saved entries.

The location store holds up to **256 waypoints** and **1,024 findings**
across all scopes, within a 2 MiB file limit. Waypoints do not expire
automatically. Finding capacity/size pressure evicts the oldest findings,
and explicit pruning uses the last recorded timestamp.

Spawner findings deduplicate by exact block coordinate. Stash, activity and
geode findings deduplicate by kind and chunk. Existing findings can refresh
after 60 seconds, while the store reports a new discovery only for a new
entry. The location file contains names, kinds, server/save identifiers,
dimensions, coordinates, timestamps and selection.

Dirty location data is saved after 40 ticks, on a world change or on normal
client shutdown. Writes use a temporary file and replacement, with atomic
replacement where supported. An unreadable location file is preserved and
location edits are blocked until it is restored; failed writes retain
pending changes for a later retry.

Back up the active game's `config/bytephase.json`, `config/bytephase/` and
`options.txt` to preserve settings, profiles, coordinates and binds.

## Architecture

The exploration and everyday gameplay utilities use Bytephase's module,
command and storage structure for chunk signals, coordinate capture, tunnels,
hotbar roles, merchant work and named configurations. Their behavior is
defined by the source links above.

These additions use the existing marker renderer and keep the broader ESP,
model styles, shaders, sky presets, crosshair and HUD workflows available.
The retained behavior notes live in the [module guide](MODULE_GUIDE.md).
