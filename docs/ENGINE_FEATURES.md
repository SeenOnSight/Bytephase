# Bytephase feature migration

Bytephase 0.2.4 adds **83** feature classes from the supplied feature archive.
The existing 124 modules remain registered, for **207 modules**.
**62** source features match existing modules, settings, commands or UI and are skipped.

Version 0.2.5 defers imported font/graphics initialization until Minecraft has
created its window. Keybinding names remain registered before GameOptions, then
attach to the late-created feature instances before the config is loaded.

## Controls and storage

Use the existing ClickGUI search or category tabs. Bots are in World, server-specific
tools are in Explore, network and client settings are in Other, and visual and movement
features use their corresponding tabs. Left-click toggles; right-click configures.
Text values open a native Save/Cancel editor. Imported editor requests use Bytephase controls; action buttons retain their feature callbacks.
Item, radio, schematic and trade-rule editors are available through Configure/Advanced settings.

Ordinary settings, enabled states and structured feature data are stored by Bytephase's
config/profile system. Action buttons never execute when a config or profile is loaded.
The friend list is shared with the existing PvP component. Imported schematics, radio lists,
presets and bot data use the bytephase/engine subdirectory of the relevant game/config directory.
Source activation/account infrastructure is not initialized; ClientInfo shows combined version
and source-license information. Panic disables the whole native and imported catalog.

## Added features

| Source feature | Bytephase name | Category |
| --- | --- | --- |
| Anchor Hunter | AnchorHunter | bots |
| Auto Area Miner | AutoAreaMiner | bots |
| AutoAuctionFlipper | AutoAuctionFlipper | bots |
| Auto Builder | AutoBuilder | bots |
| Auto Elytra Finder | AutoElytraFinder | bots |
| Auto Elytra Pilot | AutoElytraPilot | bots |
| Auto TPA Here | AutoTpaHere | bots |
| RTP Base Alert | RtpBaseAlert | bots |
| SpawnerProtect | SpawnerProtect | bots |
| Auto Villager Trade | VillagerTradeBot | bots |
| Auto Aggro Pearl | AutoAggroPearl | combat |
| AutoCart | AutoCart | combat |
| Criticals | Criticals | combat |
| Hit Boxes | HitBoxes | combat |
| Hit Select | HitSelect | combat |
| Obsidian Optimizer | ObsidianOptimizer | combat |
| Perfect Wind Charge | PerfectWindCharge | combat |
| Reach | Reach | combat |
| Silent Aim | SilentAim | combat |
| Trident Assist | TridentAssist | combat |
| AutoMine | AutoMine | donutsmp |
| AutoSell | AutoSell | donutsmp |
| Auto Sell Chest | AutoSellChest | donutsmp |
| Bloodhound | Bloodhound | donutsmp |
| BoneDropper | BoneDropper | donutsmp |
| ChunkFinder | ChunkFinder | donutsmp |
| ChunkFinderV2 | ChunkFinderV2 | donutsmp |
| Deep Player Finder | DeepPlayerFinder | donutsmp |
| FakePay | FakePay | donutsmp |
| FakeStats | FakeStats | donutsmp |
| GamblingRigger | GamblingRigger | donutsmp |
| Netherite Finder | NetheriteFinder | donutsmp |
| RegionMap | RegionMap | donutsmp |
| Relog Method | Relog | donutsmp |
| SeedAnalyzer | SeedAnalyzer | donutsmp |
| Silent Home | SilentHome | donutsmp |
| SpecialItemBuyer | SpecialItemBuyer | donutsmp |
| Staff List | StaffList | donutsmp |
| TraderFinder | WanderingTraderFinder | donutsmp |
| ACAlert | ACAlert | misc |
| Auto Pot | AutoPot | misc |
| Auto Promo | AutoPromo | misc |
| Boatfly | BoatFly | misc |
| Capes Library | Capes | misc |
| Bytephase Radio | BytephaseRadio | misc |
| Creamy Keys | CreamyKeys | misc |
| CustomSkin | CustomSkin | misc |
| Fake Coordinates | FakeCoordinates | misc |
| Fast Place | FastPlace | misc |
| Fastbreak | Fastbreak | misc |
| Potion Refill | PotionRefill | misc |
| SkinProtect | SkinProtect | misc |
| WeatherChanger | WeatherChanger | misc |
| WeatherNotifier | WeatherNotifier | misc |
| Auto Spear | AutoSpear | movement |
| Flight | Flight | movement |
| Freecam | Freecam | movement |
| KeepSprint | KeepSprint | movement |
| Pearl Catch Assist | PearlCatchAssist | movement |
| Scaffold | Scaffold | movement |
| SpeedHack | SpeedHack | movement |
| Back Track | BackTrack | network |
| Blink | Blink | network |
| Client Sounds | ClientSounds | settings |
| Client Info | ClientInfo | settings |
| Quick Friend | MiddleClickFriend | settings |
| Panic | Panic | settings |
| Self Destruct | SelfDestruct | settings |
| Atmospheric Particles | AtmosphericParticles | visuals |
| Custom Flame | CustomFlame | visuals |
| CustomWorld | CustomWorld | visuals |
| FireFly | FireFly | visuals |
| Freelook | Freelook | visuals |
| HandShader | HandShader | visuals |
| Heat Color | HeatColor | visuals |
| Jump Circles | JumpCircles | visuals |
| Motion Blur | MotionBlur | visuals |
| Particles | Particles | visuals |
| Pearl Catch | PearlCatch | visuals |
| Realistic Water | RealisticWater | visuals |
| Spotify HUD | SpotifyHUD | visuals |
| Sword Inspect | SwordInspect | visuals |
| Totem Counter | TotemCounter | visuals |

## Existing matches

| Source class | Existing Bytephase feature |
| --- | --- |
| CrystalPvpBot | CrystalPvPBot |
| SwordPvpBot | SwordPvPBot |
| AimAssist | AimAssist |
| AnchorMacro | AnchorMacro |
| AutoClicker | AutoClicker |
| AutoHitCrystal | CrystalMacro |
| AutoHotbar | HotbarArrange |
| AutoInventoryTotem | AutoInventoryTotem |
| AutoJumpReset | JumpReset |
| AutoShieldBreaker | ShieldBreaker |
| AutoTotem | AutoTotem |
| AutoTrap | AutoTrap |
| AutoXP | FastXP |
| CrystalAura | CrystalAura |
| CrystalOptimizer | CrystalOptimizer |
| HitSwap | Mace / ShieldBreaker / AutoWeapon |
| KeyPearl | KeyThrow |
| MaceBomber | Mace |
| SafeAnchor | AnchorMacro: Safe Anchor |
| TriggerBot | Triggerbot |
| WTap | WTap |
| Configs | Configs |
| AntiVanish | AntiVanish |
| SusChunkFinder | SusChunks |
| SusChunkFinderV2 | SusChunks |
| AutoTool | AutoTool |
| CustomCrosshair | Crosshair |
| FOVSlider | CustomFov |
| FakePlayer | /fakeplayer command |
| InventoryCleaner | InventoryCleaner |
| NameProtect | NameProtect |
| TimeChanger | TimeChanger |
| Zoom | Zoom |
| AutoWalk | AutoWalk |
| Sprint | Sprint |
| FakeLag | FakeLag |
| KnockbackDelay | KnockbackDelay |
| GUISettings | Appearance / ClickGUI |
| Notifications | Notifications |
| Friends | Friends |
| ThemeCustomizer | Themes |
| BlockESP | BlockESP |
| BlockHighlight | Highlight |
| BlockOverlay | Highlight |
| CustomSky | SkyShaders |
| Fullbright | Fullbright |
| HandCustomer | ViewModel / SlowSwing |
| HoleESP | HoleESP |
| LootESP | ItemESP |
| MobESP | ESP: Mobs |
| NameTags | Nametags |
| NoExplosions | NoRender: Explosions |
| NoFarmLag | Performance |
| NoFlame | NoRender: FireOpacity |
| PearlPrediction | Trajectories / ThrowableTrajectory |
| PlayerESP | ESP |
| ShowHUD | ArrayList / Watermark / ArmorHUD / Effects |
| SpawnerFinder | SpawnerFinder |
| StorageESP | StorageESP |
| TargetESP | TargetFX |
| Trails | BreadCrumbs |
| Waypoints | Navigator |

## Source and integration

The archive's source is preserved under engine/src/main/java and its resources under
engine/src/main/resources. The old registration and activation/bootstrap paths are replaced.
Only missing features enter the native catalog. Existing Bytephase counterparts remain inactive
for shared types and helper code; their ticks, packet handlers and visual behavior are disabled.
Engine hook selectors use current Yarn names and are remapped with the main JAR.
See engine/FEATURE_MAP.json for the complete inventory and original feature-source hashes,
and engine/THIRD_PARTY_NOTICES.txt for source/asset provenance.

Compilation and JAR remapping are the build checks. No gameplay tests or game launch were run.

Version 0.2.10 routes every imported Screen request to Bytephase's UI. The original
GUI widgets and painting are not initialized. Data, callbacks and model renderers
are adapted to native controls and preview textures.
