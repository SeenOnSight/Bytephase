# Server compatibility and debugging

Verified locally on 2026-11-10: `./gradlew build` and
`./gradlew runClientGameTest` both completed successfully.

This pass adds optional conservative behavior, not a verified anti-cheat bypass.
It has not been tested against 2b2t's live anti-cheat. Client-side acceptance,
attack attempts and explosion matches are not proof that a server accepted an
action. No setting guarantees avoiding corrections or bans.

`GRIM_SOURCE_AUDIT.md` records the subsequent upstream Grim review, the yaw
continuity fix, and unresolved extra-packet/order/placement conflicts. The
Conservative controls do not yet form a complete Grim-compatible profile.

### Optional native-packet combat

`AutoCrystal.VanillaPackets` and `KillAura.VanillaPackets` select camera-following
aim carried by vanilla movement packets, rather than silent aim with immediate
extra look packets. The mode applies Conservative constraints, requires a swing,
waits for transmitted-look evidence, and runs actions before vanilla movement.
It pauses during item use, mining, Blink, FakeLag or Criticals. AutoCrystal uses
actual ray-hit placement faces and leaves the crystal slot selected instead of
immediately swapping back. If both use this mode, AutoCrystal takes priority.

This visibly moves the camera and may act more slowly. Defaults and saved
settings are preserved. Do not mix it with legacy silent combat or assume it
fixes unrelated Speed/NoSlow/Velocity/NoFall behavior. It is not a verified Grim
bypass; see `GRIM_SOURCE_AUDIT.md` for the boundaries and packet-order tests.

## Combat settings

`AutoCrystal` and `KillAura` now expose **Conservative**, off by default so
existing configurations retain their settings. It uses the player's
server-provided interaction-range attributes directly, even with `Reach` on.
These constraints do not rewrite your saved range, rotation or delay values.

For `AutoCrystal`, Conservative:

- requires rotation and visible interactions, regardless of `Rotate` or
  `ThroughWalls`;
- caps cycles at 20/second, disables extra frame cycles, and allows at most one
  attack or placement per cycle; successful attacks take priority over placing;
- applies at least one cooldown cycle between actions of each kind;
- disables repeated Focus placements and waits for pending reservations;
- does not assume an attacked crystal has disappeared before the client sees it;
- uses a latency-based retry window of 250–2000 ms rather than 120–250 ms;
- clears pending state and pauses for ten client ticks after a position correction.

For `KillAura`, Conservative requires rotation, line of sight and full attack
cooldown, tightens the aiming ray, and pauses for ten ticks after a correction.
`RequireAttack` is a new independent option: only act while your attack key is
held. AutoCrystal already had this option.

For a less automated setup, enable `RequireAttack` and `Conservative`, leave
movement/packet exploits and reach modifications disabled, and choose only the
utilities allowed by the server. This is **not** a global legit mode: automated
combat may still violate server rules even with conservative settings.

## Movement buffering fixes

- `Blink` and `FakeLag` discard pre-correction movement before vanilla sends its
  teleport acknowledgement. They stop buffering for `CorrectionPause` client
  ticks (default 10, range 1–40), so that acknowledgement is not trapped.
- Buffered movement is scoped to both the connection and the client world.
  A dimension change or disconnect discards it instead of replaying stale positions.
- FakeLag now flushes at, not after, `MaxPackets`. `MaxDelay` (default 20 ticks,
  range 1–100) also bounds buffer age when stationary movement produces few packets.
- Buffer flushes still use the interceptor re-entry guard, so one buffering
  module cannot capture another's flush. Running both together is nevertheless
  unnecessary and can produce surprising delays.

## Correctness and performance

- AutoCrystal's non-Focus placement batch ranks bases once rather than rerunning
  the block scan and damage raycasts after every placement. Each commit rechecks
  the remaining self-damage budget and overlap with earlier reservations. The
  bounded candidate list can yield fewer attempts when its best bases overlap.
- Target ranking keeps only the best eight candidates instead of sorting a full
  list or allocating a candidate record for every mob. Damage profiles still
  cover only the selected eight. Ties preserve entity query order.
- Break and place requests share a self-damage budget even at `CrystalsPerTick=1`.
  The budget is per cycle, not a guarantee against delayed explosions across cycles.
- AutoCrystal clears stale aim when paused by a screen, item use, health or the
  attack-key requirement.
- Nametag health text now notices health/absorption updates between ticks and
  only reformats on change, keeping the text consistent with its live health bar.
  Disabling Nametags also releases its entity references.

## Checks

`./gradlew runClientGameTest` includes targeted correction-acknowledgement,
world-discard, buffering-resume, FakeLag age/count and re-entry checks, plus
attribute reach, frame-cycle suppression, attack-key gating, overlapping batch
reservations, combined single-crystal break/place budgeting and conservative
Focus retry checks. They run in the isolated
singleplayer test world; they are not anti-cheat tests.

The existing placement-reservation scenario now explicitly selects non-Focus
mode. Screenshot scenarios clear movement input before spawning the target so
the camera cannot walk past it while the armor comparison is captured.

Before using a new configuration on a live server, check it in a local test
world. Change one module at a time and watch corrections and confirmed actions.
For crystal selection problems, enable `AutoCrystal.Debug`; use its rejection
reasons rather than increasing speed or range blindly.
