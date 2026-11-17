package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.bytephase.render.ui.NativePreviewScene;

public final class PreviewSetting extends Setting<Boolean> {
    public final NativePreviewScene scene;
    public boolean failed;
    public PreviewSetting(String name, NativePreviewScene scene) { super(name, false); this.scene = scene; }
    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }
    @Override public void fromJson(JsonElement value) { }
}
