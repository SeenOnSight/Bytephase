package dev.bytephase.render.font;

/** Marks only Bytephase's bundled TrueType providers for distance-field baking. */
public interface DistanceFieldSource {
    void bytephase$enableDistanceFields();
}
