package dev.bytephase.render.ui;

import com.mojang.blaze3d.textures.GpuTextureView;

/** A data/model renderer with no menu or widget drawing. */
public interface NativePreviewScene {
    GpuTextureView nativeFrame(int width, int height, float delta);
    void nativeDrag(double dx, double dy, int button);
    void nativeZoom(double amount);
    void nativeClose();
}
