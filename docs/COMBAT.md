# Bytephase combat, GlowESP and Highlight

The 0.2.4 combat update includes these modules:

AimAssist, AnchorAura, AutoCity, AutoInvTotem, AutoTotem, AutoTrap, CrystalAura,
CrystalBot, CrystalMacro, CrystalOptimizer, FakeLag, FastXP, FriendGuard,
HoverTotem, JumpReset, KeyThrow, KillAura, KnockbackDelay, Mace, ShieldBreaker,
AnchorMacro, SpearSwap, SwordBot, Triggerbot and WTap.

Additional combat modules remain available. Existing module names and
Minecraft toggle bindings remain stable. Settings shared by both implementations
retain their saved values. Unrecognized old settings stay in the autosave file;
the controls shown in the GUI follow the imported implementation.

## GlowESP

Open Bytephase with Right Shift, select **Visuals**, and find **GlowESP**.
Left-click to enable it and right-click for settings.

- Choose players, passives, dropped items and end crystals.
- Use **Color → Theme** for Bytephase's accent, or **Manual** for individual colors.
- **Mark Target** and the friend color distinguish the current target and friends.
- **Stroke**, **Aura** and **Aura Reach** control the outline and glow.
- **Fill**, **Sheen**, **Sheen Color** and **Breathe** adjust the interior effect.
- **Distance** and **Limit** bound how many entities are processed.

With an Iris shader pack active, the module uses through-wall box
outline fallback. The full silhouette glow is used without a shader pack.

SpearSwap selects a Lunge-enchanted spear, waits for its equipped attack charge,
stabs once and returns to the original slot when **Switch Back** is enabled.
Its action binding is **Lunge Key**; the regular module shortcut toggles the
feature.

## Auto Inventory Totem

The inventory opened for an automatic totem refill blocks manual inventory
input, including number-key hotbar swaps. **Escape** cancels it.
**Hotbar Refill** fills its configured slot only when that slot is empty;
occupied hotbar items stay in place.

## Highlight

**Visuals → Highlight** now uses the block selection effect.
Its existing module toggle shortcut is retained.

- **Style** selects outline, fill or both.
- **Color Mode** selects Theme, Gradient, Solid or Rainbow.
- **Flow**, **Glow**, **Width** and **Fill Opacity** adjust the effect.
- **Smooth** and **Smooth Speed** control movement between selected blocks.
- **Mining Progress** fills the block as it breaks.
- **Face Only** selects only the looked-at face; **See Through** draws through blocks.

The replacement handles partial block shapes and connected double blocks.
