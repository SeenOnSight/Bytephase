# Grim upstream source audit

Date: 2026-11-10. Reviewed the public `GrimAnticheat/Grim` repository, branch
`2.0`, at commit [`e457d556692333748f9d85a095b268287d81e244`](https://github.com/GrimAnticheat/Grim/commit/e457d556692333748f9d85a095b268287d81e244),
dated 2026-11-08. This identifies the source examined, **not** the version,
configuration, forks or extra checks deployed on 2b2t or another server.

Final local verification: `./gradlew build runClientGameTest` passed, including
the native-combat packet traces and cancelled-packet evidence checks.

No live Grim server was tested. No verified anti-cheat bypass was found or
implemented in this pass. A subsequent optional native-packet path addresses
some findings below; see section 6. The useful findings are differences between
Bytephase's actions and the protocol/physics Grim checks.

## 1. Yaw continuity — fixed locally

[`AimModulo360`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/aim/AimModulo360.java#L21-L38)
examines raw yaw changes, including large jumps into a normalized yaw range.
`AimSmoother.advance` used wrapped angle differences when turning, but returned
the raw target angle in both snapping branches.

Examples of the old behavior:

- `359.99 -> 0`: a tiny correction became a raw jump of about -360 degrees.
- `359 -> 1`: a two-degree turn completed by returning 1 instead of 361.

Both branches now return the equivalent angle on the current revolution. Pitch
remains bounded normally. Regression checks cover both directions, both snapping
branches, multiple revolutions and unchanged pitch limiting.

This fixes a real local discontinuity, not the whole rotation pipeline. Ending
silent aim or switching rotation owners can still change from a continuous
server-facing yaw to the camera's raw yaw. That handoff needs a separate audit.

## 2. Extra movement/look packets — still unresolved

[`TickTimer`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/timer/TickTimer.java#L24-L43)
checks clients that support tick-end packets and flags multiple non-teleport
movement packets without the expected tick end.
[`AimDuplicateLook`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/aim/AimDuplicateLook.java#L17-L31)
checks unchanged yaw/pitch in a rotation update, with explicit exemptions.

`SilentRotation.flush()` sends an extra `LookAndOnGround` packet on demand,
independently of vanilla's movement packet. AutoCrystal calls it while aiming;
KillAura calls it before attacking. Repeated calls can send unchanged angles.
`Criticals` sends a pair of additional position packets. Blink/FakeLag can release
many movement packets while letting vanilla tick-end packets pass separately.

These are **not** Grim-compatible merely because their rotations are smoothed
or their bursts are bounded. A proper correction would coordinate a single
normal movement update, tick boundaries and subsequent actions instead of
inserting extra movement packets or fake tick-end markers. It requires outgoing
packet-sequence tests and a local Grim integration test; it was not patched
blindly here.

## 3. Action ordering — still unresolved

[`PacketOrderO`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/packetorder/PacketOrderO.java#L23-L51)
checks synchronous packets sent after movement before tick end on applicable
clients. [`PacketOrderE`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/packetorder/PacketOrderE.java)
checks slot changes during conflicting actions; its metadata marks it
experimental. [`PacketOrderI`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/packetorder/PacketOrderI.java)
checks conflicting attack/use/release/dig sequences and is also experimental.

Bytephase's immediate look flushes, auto-swaps, independent enabled combat
modules and optional swap-back can create these conflicts. A per-module delay
does not coordinate all of them. Exact behavior depends on where vanilla tick
end lands relative to the module callbacks and which checks the server enables.

[`PacketOrderB`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/packetorder/PacketOrderB.java)
expects the version-appropriate attack/swing sequence. Turning `Swing` off is
not a safe general combat setting for Grim. Full cooldown alone does not satisfy
packet ordering.

## 4. Crystal reach and placement geometry

[`Reach`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/combat/Reach.java#L258-L371)
explicitly includes end crystals and ray-intersects a compensated bounding box.
It uses entity-range attributes or, when applicable, server/client-supported
item attack-range components. Crystal attacks are **not** generally exempt.
Conservative's attribute cap is stricter than some legitimate item-component
ranges; it is not an implementation of Grim's full reach model.

Block-place packets are dispatched to checks in
[`CheckManagerListener`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/events/packets/CheckManagerListener.java#L534-L566).
Relevant checks include
[`FarPlace`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/scaffolding/FarPlace.java),
[`RotationPlace`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/scaffolding/RotationPlace.java),
[`PositionPlace`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/scaffolding/PositionPlace.java),
and cursor validity/bounds checks. They test reach, an intersecting aim ray and
whether the clicked face can be seen from the player's possible eye positions.

AutoCrystal currently uses a fixed top-center hit vector and always sends
`Direction.UP`. Its visibility helper only asks whether a ray to a selected
point reaches the target block; that does not establish that UP is the face the
player could click. Its angle tolerance also does not prove a ray intersects a
particular face. Elevated bases and side-visible bases need real ray-hit
selection and validation, independent of damage scoring.

[`MultiPlace`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/scaffolding/MultiPlace.java)
is experimental and compares successive face/cursor/position values within a
tick. Normal-mode distinct-base batching can conflict with it. Conservative's
single action per cycle reduces that specific risk but does not fix extra
movement packets or clashes with other placement modules.

## 5. Movement is simulated, not just rate-limited

[`PredictionRunner`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/movement/PredictionRunner.java),
[`NoSlow`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/movement/NoSlow.java),
and velocity handlers compare movements with compensated vanilla possibilities.
[`Timer`](https://github.com/GrimAnticheat/Grim/blob/e457d556692333748f9d85a095b268287d81e244/common/src/main/java/ac/grim/grimac/checks/impl/timer/Timer.java)
also ties timing to transaction-confirmed clock information.

Bytephase's arbitrary velocity scaling, removal of item-use slowdown, knockback
cancellation and spoofed ground flags remain potential conflicts. Adding tiny
offsets, jitter or delays does not establish valid physics. No general Speed,
Fly, NoSlow, Velocity or NoFall bypass was established from this review.

## 6. Subsequent native-packet path

`AutoCrystal` and `KillAura` now have `VanillaPackets`, off by default. It also
applies Conservative's constraints without rewriting the saved settings.

- Aim visibly steers the camera before vanilla movement/input runs. This avoids
  sending a different movement yaw to the server while retaining a camera yaw
  for local movement physics. This is deliberately **not silent aiming**.
- The modules do not call `SilentRotation.flush()` in this mode. Vanilla carries
  the rotation; actions wait for evidence of an outgoing look and completed tick.
- Actions run in the start-of-tick phase, not between movement and tick end.
- The evidence tracker is updated only after the module interceptors accept a
  packet. It resets on connection/world/player changes and server corrections.
  It is not a server acknowledgement or a simulation of latency compensation.
- Native combat pauses during item use, normal mining, Blink, FakeLag or
  Criticals. It does not disable or bypass those modules.
- A swing is required. AutoCrystal skips immediate swap-back to avoid changing
  slots after placing in the same action sequence.
- Placement candidates must have a reachable visible ray hit. At commit time
  the previously transmitted look is raycast again, and the actual hit position
  and face are used instead of a fabricated UP face.
- Only the best eligible placement is aimed at per cycle, avoiding repeated
  camera turns toward several candidates while waiting for a transmitted look.
- If both modules use native mode, AutoCrystal owns the camera and has priority;
  native KillAura waits. Mixing native and legacy aiming remains unsupported.

The client regression tests capture actual outgoing packets for a native aura
attack and a crystal placement against an elevated base. They check that actions
have transmitted-look evidence, movement packets are not duplicated within a
tick, no unchanged look update is inserted, and the elevated placement uses the
visible WEST face rather than UP. The packet observer is game-test-only.
Additional evidence checks ensure that cancelled look packets do not advance
the tracked facing, unfinished/unclean ticks do not permit an action, and Blink
is recognized as a conflict.

This does not make arbitrary movement modifications or every module combination
Grim-compatible. Legacy silent aim/flush behavior remains unchanged by default.
Damage scoring is unchanged; native mode improves which geometrically legal
placement candidates can be attempted, not the explosion-damage model.

## Next validation work

1. Capture real outgoing sequences for one enabled module at a time.
2. Audit legacy silent aim, owner/camera handoff and other automated modules.
3. Extend native packet-order tests to more movement/slot/use combinations.
4. Test against a locally controlled server with the intended Grim revision,
   Minecraft version and check configuration, then inspect specific flag logs.

The `Conservative` and `VanillaPackets` settings are **not a verified Grim
bypass**. Source inspection and passing client game tests cannot establish
that a server's deployed anti-cheat accepts an action.
