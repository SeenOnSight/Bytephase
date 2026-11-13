# MarlowOptimizer

The supplied Marlow's Crystal Optimizer **2.0.0 for Minecraft 1.21.11** is
integrated as **Combat → MarlowOptimizer**. It defaults off and uses the existing
module bind, configuration and profile controls.

## Behavior

- Tracks crystals after a damage-capable attack, preserving their server entity
  records while follow-up targeting and crystal placement can look past them.
- Uses the source's **30-tick** visibility window and block-sequence
  acknowledgments to release retained state.
- `KeepRender` defaults off. Turning it on keeps retained crystals visible while
  targeting and crystal placement can still look past them.
- Uses the source's attack-damage, strength/weakness and world-border checks.
- Clears retained state and temporary crosshair changes when disabled or when
  the client world changes. The tick counter stays monotonic across resets.
- Retains the optimizer's server opt-out and challenge/version packet protocol.
  A server opt-out suppresses optimization for that connection.
- Switching between MarlowOptimizer and the previous CrystalOptimizer disables
  the other implementation. Crystal macro, aura and placement helpers skip
  retained crystals rather than treating them as fresh attack targets.

The normal game hooks are gated by this module's state. The implementation is
inside the main Bytephase JAR. If the standalone optimizer is already
installed, the module reports that condition instead of enabling a second copy.

## Build status

The optimizer is included in the 0.2.3 source update; gameplay testing remains pending.
No game launch or gameplay tests were performed by the agent. The corrected
mixin-package declaration is included in this build; all combat mixins reside
under `dev.bytephase.pvp.mixin`.

## Source provenance

Imported from the supplied `marlows-crystal-optimizer-2.0.0+mc1.21.11.jar`.
The crystal helpers and six mixins were recovered from that exact JAR and
mapped to the combat component's compile namespace. Package names, module
lifecycle, render preferences, protocol integration and reset handling were
adapted to Bytephase. The source's MIT license and attribution are included in
`LICENSE_MARLOW` and `NOTICE_MARLOW.txt`.

Input SHA-256: `291077c2e69614ba966144ab904bc0c24ca4224ce5e0a4fb80ad2ba8de0372f3`.
