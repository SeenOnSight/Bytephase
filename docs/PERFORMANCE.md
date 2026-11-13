# Performance controls

Enable **Render → Performance** and expand its settings.

| Setting | Default | Effect |
| --- | --- | --- |
| LimitParticles | On | Applies the particle count and distance limits. |
| ParticlesPerTick | 128 | Caps newly queued particles during each client tick; adjustable from 16 to 512. |
| ParticleDistance | 48 | Drops new particles beyond this distance from the camera entity; adjustable from 8 to 128 blocks. |
| CullDistantItems | On | Skips dropped-item render states beyond ItemRenderDistance. |
| ItemRenderDistance | 48 | Distance limit for dropped-item rendering; adjustable from 16 to 128 blocks. |
| ChunkUpdates | Balanced | Uses the existing balanced chunk update policy. Threaded and Unchanged remain available. |
| StreamingFpsLimit | Off | Enables the existing configurable recording FPS cap. |

Particle limits act at spawn time. Existing particles finish their lifetimes.
Lower particle budgets and distances reduce the visible effects in crowded
scenes. Item culling affects dropped-item models.

## Automatic render work reductions

- Combat modules request world projection matrices only if their class has
  rendering handlers or an overlay.
- The enabled-module HUD checks changing labels on ticks; render frames reuse
  its cached rows and widths. Module toggles still refresh the rows immediately.
- Combat HUD overlay providers are cached when modules toggle. No overlay
  canvas is allocated when no overlay is active.
- Combat label projection passes coordinates directly to the shared projection
  helper, avoiding a temporary position object for every label.

The local JAR is built for user testing. Gameplay compatibility and FPS gains
have not been measured by the agent.
