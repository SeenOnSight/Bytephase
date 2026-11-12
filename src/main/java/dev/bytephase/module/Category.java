package dev.bytephase.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    WORLD("World"),
    MISC("Misc"),
    EXPLORE("Explore"),
    UI("UI");

    public final String display;

    Category(String display) {
        this.display = display;
    }
}
