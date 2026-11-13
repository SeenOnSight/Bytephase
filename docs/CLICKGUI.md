# ClickGUI and custom fonts

The native 0.2.3 interface follows the supplied `bytephase-clickgui.zip` design:
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

Themes includes Indigo, Mint, Ocean, Violet, Rose, Amber and Crimson presets.
Accent and Text also have RGB sliders. Settings contains PanelOpacity and
BackgroundBlur, which control the glass background.

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
The sidebar displays the installed build version; this build shows `0.2.3`.

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
