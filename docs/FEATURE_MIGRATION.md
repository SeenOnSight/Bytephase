# Feature migration inventory

Status: **in progress**. Version 0.2.1 is the first migration batch; it does not
provide full feature parity with the supplied recovered source.

The supplied module index contains **148 entries**, including **5 framework
entries**. This release adds **13 modules** to Bytephase's existing 88, for a
catalog of **101**. The index below accounts for every entry. Existing
counterparts are not assertions that every source setting has been ported.

The retained scope carries forward the earlier request for legit/semi-legit
modules. Cosmetic engines, custom model assets, shader effects, account/skin
features and additional helpers still require separate integration work.

## Status counts

| Status | Entries |
| --- | ---: |
| Added in 0.2.1 | 13 |
| Existing counterpart | 34 |
| Infrastructure | 5 |
| Outside retained scope | 39 |
| Pending | 57 |

## Inventory

| Feature | Status | Bytephase counterpart | Notes |
| --- | --- | --- | --- |
| AimAssist | Existing counterpart | AimAssist | Related feature already present; complete source setting parity is not claimed. |
| AirPlace | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AirStuck | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AmbientParticles | Pending | — | Not implemented by this release. |
| AnchorTap | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| Animations | Pending | — | Not implemented by this release. |
| AntiKnockback | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AntiTrap | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AstralWeaponTrails | Pending | — | Not implemented by this release. |
| Aura | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AutoAnchor | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AutoArmor | Existing counterpart | AutoArmor | Related feature already present; complete source setting parity is not claimed. |
| AutoCart | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AutoClicker | Existing counterpart | AutoClicker | Related feature already present; complete source setting parity is not claimed. |
| AutoCrystal | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AutoDisconnect | Added in 0.2.1 | AutoDisconnect | Behavior adapted to Bytephase; settings and implementation may differ. |
| AutoDrain | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AutoEat | Existing counterpart | AutoEat | Related feature already present; complete source setting parity is not claimed. |
| AutoFarm | Added in 0.2.1 | AutoFarm | Behavior adapted to Bytephase; settings and implementation may differ. |
| AutoGap | Added in 0.2.1 | AutoGap | Behavior adapted to Bytephase; settings and implementation may differ. |
| AutoMLG | Pending | — | Not implemented by this release. |
| AutoPearlCatch | Pending | — | Not implemented by this release. |
| AutoRespawn | Existing counterpart | AutoRespawn | Related feature already present; complete source setting parity is not claimed. |
| AutoSell | Pending | — | Not implemented by this release. |
| AutoSprint | Existing counterpart | Sprint | Related feature already present; complete source setting parity is not claimed. |
| AutoTool | Existing counterpart | AutoTool | Related feature already present; complete source setting parity is not claimed. |
| AutoTotem | Existing counterpart | AutoTotem | Related feature already present; complete source setting parity is not claimed. |
| AutoTrap | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| AutoTunnel | Existing counterpart | TunnelMiner | Related feature already present; complete source setting parity is not claimed. |
| AutoWeb | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| Backtrack | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| BaseFinder | Existing counterpart | BaseFinder | Related feature already present; complete source setting parity is not claimed. |
| BlockOverlay | Existing counterpart | Highlight | Related feature already present; complete source setting parity is not claimed. |
| BlockPlacementController$Type_k | Infrastructure | — | Source framework entry; not a separately registered user feature. |
| BoatFly | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| Capes | Pending | — | Not implemented by this release. |
| CelestialWeather | Pending | — | Not implemented by this release. |
| Chams | Existing counterpart | ESP model styles | Related feature already present; complete source setting parity is not claimed. |
| ChestStealer | Existing counterpart | ChestStealer | Related feature already present; complete source setting parity is not claimed. |
| ChinaHat | Pending | — | Not implemented by this release. |
| ChunkScanModule | Infrastructure | — | Source framework entry; not a separately registered user feature. |
| ClickPulse | Pending | — | Not implemented by this release. |
| Criticals | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| CryptoPets | Pending | — | Not implemented by this release. |
| CrystalChams | Existing counterpart | ESP FilledCrystals | Related feature already present; complete source setting parity is not claimed. |
| CrystalOptimizer | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| CustomCrosshair | Existing counterpart | Crosshair | Related feature already present; complete source setting parity is not claimed. |
| CustomSky | Existing counterpart | SkyShaders | Related feature already present; complete source setting parity is not claimed. |
| DamageMarkers | Added in 0.2.1 | DamageMarkers | Behavior adapted to Bytephase; settings and implementation may differ. |
| ESP | Existing counterpart | ESP | Related feature already present; complete source setting parity is not claimed. |
| EchoHand | Pending | — | Not implemented by this release. |
| ElytraFly | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| ElytraHelper | Pending | — | Not implemented by this release. |
| ElytraMotion | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| Emotes | Pending | — | Not implemented by this release. |
| EntitySpeed | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| FakeLag | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| FakePay | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| FakeSpawner | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| FastUse | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| FlameHands | Pending | — | Not implemented by this release. |
| Fly | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| Freecam | Pending | — | Not implemented by this release. |
| Freelook | Pending | — | Not implemented by this release. |
| Fullbright | Existing counterpart | Fullbright | Related feature already present; complete source setting parity is not claimed. |
| GUIAnimation | Pending | — | Not implemented by this release. |
| GrimSpeed | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| HUD | Existing counterpart | Watermark / Coordinates / FPS / Ping / ArrayList / ArmorHUD / Effects | Related feature already present; complete source setting parity is not claimed. |
| Halloween | Pending | — | Not implemented by this release. |
| HandChams | Pending | — | Not implemented by this release. |
| HeadlessKorblox | Pending | — | Not implemented by this release. |
| HitFX | Pending | — | Not implemented by this release. |
| HitSounds | Added in 0.2.1 | HitSounds | Behavior adapted to Bytephase; settings and implementation may differ. |
| HolesESP | Existing counterpart | HoleESP | Related feature already present; complete source setting parity is not claimed. |
| HomeMacro | Pending | — | Not implemented by this release. |
| HoverTotem | Added in 0.2.1 | HoverTotem | Behavior adapted to Bytephase; settings and implementation may differ. |
| IgnoreEntity | Pending | — | Not implemented by this release. |
| InvCleaner | Added in 0.2.1 | InventoryCleaner | Behavior adapted to Bytephase; settings and implementation may differ. |
| IslandLyrics | Pending | — | Not implemented by this release. |
| ItemESP | Existing counterpart | ItemESP | Related feature already present; complete source setting parity is not claimed. |
| ItemPhysic | Pending | — | Not implemented by this release. |
| Jesus | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| JumpFX | Pending | — | Not implemented by this release. |
| KillFX | Existing counterpart | KillEffects | Related feature already present; complete source setting parity is not claimed. |
| ClientTheme | Pending | — | Not implemented by this release. |
| LightDebug | Pending | — | Not implemented by this release. |
| LineGlyphs | Pending | — | Not implemented by this release. |
| LungeSwap | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| MCFriend | Pending | — | Not implemented by this release. |
| Mace | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| Module | Infrastructure | — | Source framework entry; not a separately registered user feature. |
| Module3DUI | Pending | — | Not implemented by this release. |
| MotionBlur | Pending | — | Not implemented by this release. |
| MotionScanner | Added in 0.2.1 | MotionScanner | Behavior adapted to Bytephase; settings and implementation may differ. |
| NameChanger | Pending | — | Not implemented by this release. |
| Nametags | Existing counterpart | Nametags | Related feature already present; complete source setting parity is not claimed. |
| NoBlockRotation | Pending | — | Not implemented by this release. |
| NoBreakDelay | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| NoFall | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| NoJumpDelay | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| NoSlowdown | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| NoWeather | Existing counterpart | NoWeather | Related feature already present; complete source setting parity is not claimed. |
| PearlParticles | Pending | — | Not implemented by this release. |
| Performance | Added in 0.2.1 | Performance | Behavior adapted to Bytephase; settings and implementation may differ. |
| Pets | Pending | — | Not implemented by this release. |
| PlayerModel | Pending | — | Not implemented by this release. |
| PlayerTrail | Existing counterpart | BreadCrumbs | Related feature already present; complete source setting parity is not claimed. |
| PrimeChunkFinder | Added in 0.2.1 | PrimeChunkFinder | Behavior adapted to Bytephase; settings and implementation may differ. |
| Reach | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| RealisticWater | Pending | — | Not implemented by this release. |
| Removals | Existing counterpart | NoRender | Related feature already present; complete source setting parity is not claimed. |
| RodSwap | Pending | — | Not implemented by this release. |
| Scaffold | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| SelfVisuals | Pending | — | Not implemented by this release. |
| ShieldBreaker | Pending | — | Not implemented by this release. |
| SkinChanger | Pending | — | Not implemented by this release. |
| SkySettingsModule | Infrastructure | — | Source framework entry; not a separately registered user feature. |
| SkyShader | Existing counterpart | SkyShaders | Related feature already present; complete source setting parity is not claimed. |
| SmoothCamera | Added in 0.2.1 | SmoothCamera | Behavior adapted to Bytephase; settings and implementation may differ. |
| SnowGround | Pending | — | Not implemented by this release. |
| SpawnerFinder | Added in 0.2.1 | SpawnerFinder | Behavior adapted to Bytephase; settings and implementation may differ. |
| Speed | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| SpinBot | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| StaffList | Pending | — | Not implemented by this release. |
| StorageESP | Existing counterpart | StorageESP | Related feature already present; complete source setting parity is not claimed. |
| StretchedRes | Pending | — | Not implemented by this release. |
| SusChunkFinder | Existing counterpart | SusChunks | Related feature already present; complete source setting parity is not claimed. |
| SwordModels | Pending | — | Not implemented by this release. |
| TargetAura | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| TargetHUD | Added in 0.2.1 | TargetHUD | Behavior adapted to Bytephase; settings and implementation may differ. |
| TargetStrafe | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| TimeChanger | Existing counterpart | TimeChanger | Related feature already present; complete source setting parity is not claimed. |
| TotemHit | Outside retained scope | — | Excluded from the retained legit/semi-legit catalog. |
| Tracers | Existing counterpart | Tracers | Related feature already present; complete source setting parity is not claimed. |
| TriggerBot | Pending | — | Not implemented by this release. |
| ViewModel | Existing counterpart | ViewModel | Related feature already present; complete source setting parity is not claimed. |
| WTap | Existing counterpart | WTap | Related feature already present; complete source setting parity is not claimed. |
| WalkStyle | Pending | — | Not implemented by this release. |
| WavingFoliage | Pending | — | Not implemented by this release. |
| WetGround | Pending | — | Not implemented by this release. |
| WindChargeHop | Pending | — | Not implemented by this release. |
| Wings | Pending | — | Not implemented by this release. |
| WorldAutomationModule | Infrastructure | — | Source framework entry; not a separately registered user feature. |
| WorldColor | Pending | — | Not implemented by this release. |
| WorldLyrics | Pending | — | Not implemented by this release. |
| WorldMenu | Pending | — | Not implemented by this release. |
| XRay | Pending | — | Not implemented by this release. |
| Zoom | Existing counterpart | Zoom | Related feature already present; complete source setting parity is not claimed. |
