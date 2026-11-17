# Implementing the bytephase ClickGUI so it looks right in-game

The HTML/CSS here is the visual reference. Most "it looks bad in Minecraft" problems come from
the same few things: bitmap text, jagged corners, no blur, bad text alignment, and fractional
scaling. Fix these in order.

## 0. Pick a route

**A. Native rendering (recommended).** Draw the GUI in Java with your own quad and text renderer.
You get real blur of the game behind the panel, crisp scaling and no extra dependency.

**B. Embedded browser (MCEF/JCEF).** Fastest way to reuse these files, but the page is rendered
to a texture. CSS `backdrop-filter` can only blur the page itself, not the game behind it, so
set `BackgroundBlur` off (`--blur:0px`), keep the page background transparent, and blur the game
yourself under the browser texture (see section 3). Expect extra input and scaling work.

## 1. Text (biggest visual win)

Minecraft's default bitmap font is built for 8px cells. Scaled to ~12px UI text it looks blurry
and uneven. Use real fonts.

- Bundle Inter, Outfit and JetBrains Mono as `.ttf` in `assets/<modid>/font/`.
- Register each with a font definition using the `ttf` provider:
  `{"providers":[{"type":"ttf","file":"<modid>:inter-medium.ttf","size":11.0,"oversample":8.0,"shift":[0,0]}]}`
  Use `oversample` of 4 to 8. Low oversample is what makes TTF text look soft.
- Keep the "Minecraft" option as the vanilla font, so UIFont and HUDFont can switch between them.
- Use Medium (500) for body, SemiBold (600) for headings and the selected tab. Don't fake bold.
- Turn off TextShadow for the glass look. A shadow on light text over a blurred glass panel just smears it.
- Never draw text at a fractional position. Round x and y to whole framebuffer pixels.

**Vertical centering.** Don't center the font's line box. Center the capital letters:
`baseline = rowCenterY + capHeight / 2`. Do this for card names, nav labels, buttons and the
search box so labels, icons and switches share one center line.

**Truncation.** Measure text with the font, then ellipsize to the space left. Reserve width for the
keybind label first. In your screenshot "Not Ne" is cut off because the label has no reserved width
and no ellipsis. Show nothing, or a short `-`, when there is no bind.

## 2. Rounded rectangles and borders

`fill()` rectangles can't make clean rounded corners. Draw every surface (panel, card, button,
switch, slider track) as one quad with a signed-distance-field fragment shader:

```glsl
float sdRoundRect(vec2 p, vec2 halfSize, float r){
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// fill:   float a = 1.0 - smoothstep(-0.5, 0.5, d);
// border: float edge = smoothstep(-bw-0.5, -bw+0.5, d) * (1.0 - smoothstep(-0.5, 0.5, d));
```

- Compute `d` in framebuffer pixels (multiply GUI units by the GUI scale) so the 1px antialiasing stays 1px.
- Draw the border as a separate inset ring, not by drawing a bigger rect behind. This is the "connected inset border" look from v0.2.7.
- Radii (reference size 1000px wide): panel 32, dialog 22, card 18, row 14, nav pill 12, button 10.
  Scale them with the panel. At your ~640px panel that is about 21 / 14 / 12 / 9 / 8 / 6, matching your docs.

## 3. Glass and blur

1. After the world and hand are rendered, copy the main framebuffer to a texture once per frame.
2. Blur it at half or quarter resolution with a dual-Kawase pass (4 to 6 passes, radius ~16px at 1080p).
3. Draw that blurred texture inside the panel's rounded SDF mask, then the tint on top:
   tint `rgba(26,24,80,PanelOpacity)`, plus a faint dark scrim `rgba(5,5,40,0.25)` for readability.
4. Add the top highlight (1px white at ~45% alpha, fading out over ~12px) and a thin 1px white 14% border.
   These two lines do most of the "liquid glass" feel.
5. Saturate the blurred sample by about 1.7 before tinting. Raw blur looks gray.

If sky or grass behind the panel is bright, text gets hard to read. Raise PanelOpacity or the scrim
before you touch the text color. Skip the blur work whenever `BackgroundBlur` is off.

## 4. Scaling and pixel snapping

- Lay out in a fixed base size (1000x690 here), then use `scale = min(1, windowW*0.96/1000, windowH*0.92/690)` and
  your GUI scale. Recompute layout only on resize or when a setting changes.
- Snap every rect edge and text origin to whole framebuffer pixels after scaling. Half-pixel edges are what make panels look fuzzy.
- Draw at framebuffer resolution, not GUI-scaled resolution, so HiDPI and macOS Retina stay sharp.
- Use one layout pass for both drawing and hit-testing, so clicks always match what is on screen.
  Don't compute rectangles separately in `draw*` and `mouseClicked`.

## 5. Spacing tokens (reference size 1000px wide, multiply by your scale)

| Thing | Value |
|---|---|
| Panel | 1000 x 690, radius 32, 1px border white 14-22% |
| Sidebar | width 224, padding 22/20, divider 1px white 14% |
| Nav button | height ~38, padding 9/14, radius 12, gap 6, icon 20, label 15 SemiBold |
| Content padding | 24 sides, 24 bottom, 22 top |
| Module grid | 2 columns, gap 12, card height 48, card padding 14, radius 18 |
| Card | icon 20, gap 12, name 14, switch 28 x 16 |
| Dialog | 360 wide (88% max), padding 16, radius 22, rows ~42 high |
| Switch | 28 x 16 track, 10px knob, 2px inset |
| Slider | 140 wide, 4px track |
| Hint bar | 12px text, 8 top / 12 bottom padding |

Colors: accent `#6c6ff5`, panel `rgba(26,24,80,.45)`, line `rgba(255,255,255,.14)`,
text `#f0f1ff`, muted `#a6a9d6`. Active card border = accent at 70%, plus a soft accent glow.

## 6. Clipping inside the sidebar and lists

- Nothing may be wider than its container. Pill, hover and text widths come from the sidebar's inner width
  (panel sidebar width minus padding), never from the screen. This is the cause of the selected tab sticking out.
- Clip scrolling lists with a scissor box inset by the corner radius, or with the same SDF mask,
  so cards never poke out of the rounded panel.
- Smooth scrolling: lerp `scrollY` toward the target each frame. Keep a 12px fade at the top and bottom edge.

## 7. Motion

Animate with frame delta time, not fixed steps. About 120 to 160ms ease-out works for all of these:
hover (+6% white), toggle knob slide and color fade, tab pill move, dialog open (fade and 2% scale from 0.98).
Respect no animation when the game is paused or lagging below 20 FPS.

## 8. Quick checklist before shipping

- [ ] TTF fonts with oversample 4+ loaded for Inter, Outfit and JetBrains Mono
- [ ] Text centered by cap height, positions rounded to whole pixels
- [ ] Every surface drawn with the SDF shader (no raw `fill()` corners)
- [ ] Blur masked to the panel and cached once per frame
- [ ] Highlight line and border visible on the panel edge
- [ ] Layout computed once and shared by draw and click code
- [ ] Labels ellipsized, bind label width reserved
- [ ] Looks right at GUI scale 1, 2, 3, 4 and on a Retina or 4K display
- [ ] Readable over bright sky, dark caves and water
