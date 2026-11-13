# StreamProof window capture

Enable **Settings → StreamProof** in the ClickGUI. The setting defaults to off.
Bytephase draws the enhanced view into a separate native window that follows
Minecraft and passes mouse input through. Minecraft's original window receives
a second frame with Bytephase render modules, HUD, ClickGUI, chat and debug
overlay hidden. Fullbright, NoBob and TimeChanger use their original settings
for that frame. Input processing and game ticks run once.

Select the original Minecraft window in the recorder. The companion is named
**Bytephase Private View - do not capture**.

| Platform | Capture setup |
| --- | --- |
| Windows | Select Minecraft's original window in OBS or Discord. The companion uses Windows capture exclusion. For Medal, target the game window and check the resulting clip. |
| macOS | In OBS, use macOS Screen Capture with Window Capture selected, then choose Minecraft. In Discord, select Minecraft's individual window. Medal currently does not support macOS recording. |

Application capture can include multiple windows owned by Minecraft, including
the private view. Display capture on macOS includes the private view. The
implementation targets capture of Minecraft's individual window.

StreamProof switches exclusive fullscreen to windowed mode and restores
fullscreen when disabled if it was enabled beforehand. The companion hides
while Minecraft is unfocused or minimized, and resumes when Minecraft is
focused again.

The settings control shows the current status. **Ctrl+Shift+F9** toggles
StreamProof even if the private view fails to open. When its native window
cannot be created or protected, Bytephase keeps rendering the clean frame in
Minecraft and retries the private view after five seconds.

Rendering twice and copying a frame increase GPU work. StreamProof hides
client visuals; recordings still show gameplay, camera movement, inventory
changes and other actions visible in the original game.

## Implementation status

The source compiles and the local JAR is remapped. No gameplay, native window,
OBS, Discord or Medal capture tests were run. Capture compatibility remains
unverified until the resulting recordings are checked on each target platform.
