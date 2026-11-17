# ClickGUI and custom fonts

The native 0.2.4 interface follows the supplied `bytephase-clickgui-new.zip` design:
centered translucent indigo glass, sidebar navigation, a two-column card grid,
toggle switches and scrollable module settings dialogs. It uses the supplied
logo and native icon textures. The layout scales to the game window and GUI scale.

## Controls

- Right Shift opens/closes the screen.
- Select categories in the sidebar. Visuals contains render modules; Other
  contains miscellaneous modules. World and Explore remain available.
- Left-click a card to toggle a live module; right-click opens its settings.
- Use the wheel in the cards, Themes, Configs, Friends or a settings dialog.
- Drag sliders; click mode values to cycle forward, right-click for backward.
- Module Keybind accepts a keyboard key. Action-key settings also accept a
  mouse button. Escape, Backspace, Delete or right-click clears a binding.
- Ctrl+F focuses search. Standard clipboard and editing shortcuts work.
- Arrow keys select cards. Enter/Space toggles; Shift+Enter configures.
- Tab and Shift+Tab cycle text fields.

## Themes and fonts

The default theme is black, silver, gray and white. **Themes** offers custom
**Accent**, **Text** and **Background color** controls. Click a color to open
the full picker: drag the saturation/brightness square and hue bar, adjust
R/G/B/alpha, or enter `#RRGGBB` / `#AARRGGBB`. The preview is live; **Save**
keeps it and **Cancel** restores the previous value. The logo tile and
Bytephase wordmark follow the accent; the logo mark and button text switch
between dark and light for contrast.

### Image background

In **Settings**, click **Background image → Choose image…**, then select a
local PNG, JPG, GIF or BMP. A GIF uses its first frame. **Use background image**
turns it on or off; **Image opacity** changes visibility. **Image fit** offers:

- **Cover:** fill the panel, cropping edges while preserving the image's proportions.
- **Contain:** show the entire image with space around it.
- **Stretch:** fill the panel without preserving proportions.

**Reload background image** refreshes a file changed on disk.
**Remove background image** returns to the glass background.
**Image path** also accepts a pasted local path. Images load asynchronously
and are cached; the file path and appearance controls are saved in configs.
PanelOpacity and BackgroundBlur still control the panel tint and glass backdrop.

Settings → UIFont and HUDFont separately offer:

| Font | Style |
| --- | --- |
| Inter | Default sans serif |
| Outfit | Rounded geometric sans serif |
| JetBrains Mono | Monospace |
| Minecraft | Standard game font |

UIFont applies to the ClickGUI and profile picker. HUDFont applies to
Bytephase's HUD, including ArrayList, Coordinates, Watermark, target labels
and imported combat overlays. Text widths and slider trimming use the
selected font. ArrayList refreshes its width cache when HUDFont changes.
The settings persist in `config/bytephase.json`; profiles include them in
Appearance's settings.

### Text rendering

The bundled faces use signed distance fields generated from 8× font outlines.
A dedicated shader calculates edge coverage in final framebuffer pixels, so
fractional ClickGUI scaling and HUD scaling use the same rendering path.
Glyphs are generated on first drawing, cached, and released on font reload.
Ordinary Minecraft fallback glyphs use separate atlases. Inter and Outfit use static Medium faces
(weight 500); JetBrains Mono uses Regular (400). Bold styles use separate
Semibold (600) faces, with matching font measurements, instead of offsetting
and redrawing the regular glyph. Colors and mixed text styles are preserved.
Small keybind labels, theme captions and dialog hints are also larger.
The sidebar displays the installed build version; this build shows `0.2.4`.

Version 0.2.7 aligns row labels, icons, toggles, buttons and slider handles to
shared vertical centers. Label placement uses each bundled face's cap height
and Minecraft's baseline, including scaled captions and semibold headers.
The StreamProof status has its own second line. Rounded borders use matching
outer and inset silhouettes without overlapping corner pixels or disconnected
arcs. Repeated controls share radius values; the extra straight highlight below
their top border has been removed.

The original variable fonts are retained in `tools/font-sources/`. Regenerate
the six bundled faces with `uv run --with fonttools tools/generate_fonts.py`.
The selected font names and saved font preferences remain the same.

### Icons

Icons sample their complete 48×48 source images at the displayed size. The
logo similarly samples its complete 256×256 image. Their texture metadata
enables linear filtering and clamps texture edges. Module cards use their
own category's icon, including search results containing several categories.

## StreamProof

Settings → StreamProof enables a separate, mouse-transparent private view.
Capture Minecraft's original window. Setup and capture limits are documented
in [STREAMPROOF.md](STREAMPROOF.md). Ctrl+Shift+F9 toggles the option if the
private view becomes unavailable.

The three bundled fonts use Minecraft's native TTF providers with fallback
glyphs from its default font. Their Open Font License files are bundled
beside the fonts under `assets/bytephase/font/`.

## Configs and friends

Configs uses the existing native Bytephase profile storage. Save current,
Load, Overwrite, Copy, Delete and Import work with that same profile format.
Copy writes profile JSON to the clipboard; paste it into the import field
and choose a name. Overwriting through Save current or Import requires a
second click. Delete also requires a second click on Confirm.

Profiles store module settings, enabled states and action-key settings.
Ordinary module toggle binds remain in the game's Controls preferences.
Existing profile names and file limits still apply.

Friends reads and writes the combat component's existing
`config/bytephase/friends.json` list, also used by friend commands and target
filters.

The selected sidebar tab and per-tab scroll offsets are stored with the
existing ClickGUI layout. Module settings apply to the live catalog.

Compilation and JAR remapping are the build checks. No gameplay or UI
interaction tests were run by the agent.

## Imported feature settings

Text settings open a native liquid-glass Save/Cancel editor for commands, names,
paths and other strings. Action buttons open Bytephase replacements for imported
editors or perform the named action. Item, schematic, radio and rule data are included in
native configs/profiles; loading a profile never clicks an action button.

## Version 0.2.8 rendering

The layout uses the supplied 1000×690 reference, a 224-wide sidebar, 48-high
module cards with 12-unit gaps and a 360-wide module dialog. Panel/dialog/card/
row/nav/control radii are 32/22/18/14/12/10 before scaling. The sidebar navigation
scrolls within its own clipped region so all eleven entries fit above the footer.

Every surface is a deferred native GUI quad with a signed-distance shader.
Rectangle vertices and text origins snap after transforms to physical pixels;
one physical pixel defines edge antialiasing and inset border thickness.
The same snapped bounds and scissor regions define click targets.

The backdrop is copied once at half resolution, then blurred through two
downsample and two upsample passes. The rounded panel samples it with 1.7
saturation, a 25% dark scrim and the chosen panel tint. BackgroundBlur off skips
these passes. Active cards have an accent halo; edge highlights follow each
rounded surface. Native fonts and live module/profile/friend bindings are kept.

Text uses measured cap-height alignment and real semibold faces. Card name space
is computed after reserving the actual keybind width; missing binds are empty.
Truncated labels use an ellipsis. Motion uses elapsed frame time, with animation
disabled when paused, below 20 FPS or after a long frame.

The build and remapping passed. No native visual or capture checks were completed
for this version; the user requested replacement and will run the mod.

Version 0.2.9 uses filled offset shadows behind the panel and dialog. Their
interior extends beneath the panel edge without the previous transparent band.
Active-card accent glows remain hollow.

## Native imported editors (0.2.10)

Imported Screen requests are replaced before their original widgets or render
methods run. Configs, Friends and palette controls open the existing Bytephase
pages. Item selection has searchable Add/Remove rows; radio selection uses
station names and Select controls. Crafter presets provide nine slot toggles,
Save, Select, Delete and Auto apply in the same interface.

Advanced data is edited through nested fields and entries. Apply commits the
draft; Back leaves unapplied changes out of the feature's stored data. Objects
and lists support child navigation and adding/removing entries. Text and numeric
values use the native text editor with validation. Material details and trip
summaries are shown through native read-only rows.

Preview toolbars are exported as data and rebuilt as Bytephase controls. Knife
and schematic model textures appear in a rounded native preview region; drag
to rotate and scroll to zoom. Their old menus are never painted. The inventory
input-lock wrapper is owned by Bytephase and retains ordinary Minecraft slots.

Compilation and remapping are the checks performed for this update. In-game
visual and interaction confirmation remains pending.

## FPS work in the current 0.2.4 release

The backdrop is reused for up to 33 ms and invalidated on resize. Model previews
use cached 30 Hz frames capped at 960×640 with their aspect ratio retained.
Dragging and zooming invalidate the preview immediately. Search results, labels
and font widths are cached; font changes clear measurements. Pixel snapping
uses direct affine math and reusable render-thread scratch storage.

Engine settings serialization skips unchanged ticks, polls for direct changes
once per second and refreshes before saves/profile operations. Empty engine
notification layers avoid GPU preparation. Actual FPS measurements remain pending.
