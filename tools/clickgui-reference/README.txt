bytephase ClickGUI
==================
Files
  index.html   page markup
  style.css    liquid glass indigo theme (accent colour is --acc, panel opacity is --panel)
  script.js    modules, settings, configs, themes
  assets/logo.svg  bytephase logo (scale to any size, convert to PNG for in-game use)

Preview: open index.html in a browser.

Using it in your mod
  This is a web UI, so it needs an embedded browser in the client, e.g. MCEF (Chromium for Minecraft)
  or JCEF. Load index.html as a local page, render it as an overlay, and keep the page background
  transparent (already set). If you would rather draw the GUI natively (Java/Kotlin screen), use
  these files as the visual reference: colours, spacing, and layout are all in style.css.

Hooking up real modules
  1. Edit the MODULES object at the top of script.js with your real module names.
  2. Toggle logic: in script.js, the card click handler (body.onclick) flips state[key]. Call your
     client's module toggle from there. With MCEF you can expose a Java bridge function and call it.
  3. Per-module settings: add entries to CUSTOM in script.js, e.g.
       CUSTOM["Movement.Speed"]=[{k:"speed",t:"slider",l:"Speed",min:1,max:5,step:.1,d:1.5}]
     Types: slider, select, toggle. Values are in mset[moduleKey][settingKey].
  4. Configs: configs are stored in the browser's localStorage by default. To store them as files
     in your mod's folder, replace the store.get / store.set helpers with calls to your Java bridge.
  5. Menu key: Right Shift toggles the panel in the page. If the game owns key input, forward the key
     from your mod and toggle the #gui element's "hide" class instead.

Fonts (Settings page: UIFont / HUDFont)
  Choices: Inter, Outfit, JetBrains Mono, Minecraft. index.html loads Inter, Outfit and JetBrains Mono
  from Google Fonts, and uses Pixelify Sans as the stand-in pixel face for "Minecraft".
  For offline use inside the mod, put .ttf files in a fonts/ folder, add @font-face rules in style.css
  with the same family names (use "Minecraft" for the game's own font), and remove the <link> in index.html.
  The font maps live in FONTS at the top of the Settings section of script.js.
