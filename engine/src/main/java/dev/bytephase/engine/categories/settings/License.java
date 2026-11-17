package dev.bytephase.engine.categories.settings;
import ai.auth.Module;
import dev.bytephase.engine.gui.ButtonBase;
import dev.bytephase.engine.utils.IconsPngType;
/** Shows the combined client's version and source-license information. */
public final class License extends Module {
    public License() {
        super("Client Info", IconsPngType.SETTING);
        addSetting(new ButtonBase("Version and licenses", "Information about Bytephase and the imported components", "Open", () ->
            mc.setScreen(new dev.bytephase.ui.ClientInfoScreen(mc.currentScreen))));
    }
    @Override public boolean isHiddenModule() { return true; }
}
