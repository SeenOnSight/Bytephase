package dev.bytephase.ui;

import dev.bytephase.BytePhase;
import dev.bytephase.config.ProfileManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** A bounded profile picker using Minecraft's normal keyboard-accessible widgets. */
public final class ProfileScreen extends Screen {
    private static final int MAX_ROWS = 5;
    private static final int ROW_HEIGHT = 22;
    private final Screen parent;
    private final Map<String, ButtonWidget> profileButtons = new LinkedHashMap<>();
    private TextFieldWidget name;
    private ButtonWidget save;
    private ButtonWidget load;
    private ButtonWidget delete;
    private List<String> profiles = List.of();
    private String status = "";
    private int statusColor = Theme.TEXT;
    private int page;
    private int rows = MAX_ROWS;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int footerY;
    private String editName = "";
    private String deleteConfirmation;
    private boolean listAvailable;
    private boolean compact;

    public ProfileScreen(Screen parent) {
        super(Text.literal("Bytephase Profiles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        readProfiles();
        profileButtons.clear();
        save = null;
        load = null;
        delete = null;
        compact = width < 270 || height < 196;
        panelWidth = Math.max(1, Math.min(360, width - 16));
        panelX = (width - panelWidth) / 2;
        if (compact) {
            panelHeight = Math.max(1, Math.min(110, height - 8));
            panelY = (height - panelHeight) / 2;
            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                    .dimensions(panelX, Math.max(0, Math.min(height - 20, panelY + panelHeight - 24)),
                            panelWidth, Math.min(20, Math.max(1, height))).build());
            return;
        }

        // Reduce visible rows on smaller windows; controls and status keep their own space.
        rows = Math.max(1, Math.min(MAX_ROWS, (height - 174) / ROW_HEIGHT));
        panelHeight = 166 + rows * ROW_HEIGHT;
        panelY = (height - panelHeight) / 2;
        page = Math.max(0, Math.min(page, pageCount() - 1));
        int x = panelX + 12;
        int contentWidth = panelWidth - 24;
        name = addDrawableChild(new TextFieldWidget(textRenderer, x, panelY + 46, contentWidth, 20,
                Text.literal("Profile name")));
        name.setMaxLength(32);
        name.setTextPredicate(value -> value.matches("[A-Za-z0-9_-]{0,32}"));
        name.setPlaceholder(Text.literal("Choose or enter a name"));
        name.setTooltip(Tooltip.of(Text.literal("1–32 letters, digits, underscores or dashes.")));
        name.setText(editName);
        name.setChangedListener(value -> {
            boolean changed = !editName.equals(value);
            editName = value;
            cancelDeletion();
            if (changed) status = "";
            updateActions();
        });
        setInitialFocus(name);

        int gap = 6;
        int buttonWidth = (contentWidth - 2 * gap) / 3;
        save = addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveProfile())
                .dimensions(x, panelY + 72, buttonWidth, 20).build());
        load = addDrawableChild(ButtonWidget.builder(Text.literal("Load"), button -> loadProfile())
                .tooltip(Tooltip.of(Text.literal("Apply the saved module settings and enabled states.")))
                .dimensions(x + buttonWidth + gap, panelY + 72, buttonWidth, 20).build());
        delete = addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> deleteProfile())
                .dimensions(x + 2 * (buttonWidth + gap), panelY + 72,
                        contentWidth - 2 * (buttonWidth + gap), 20).build());

        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= profiles.size()) break;
            String profile = profiles.get(index);
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(profile),
                    selected -> {
                        name.setText(profile);
                        name.setCursorToStart(false);
                        cancelDeletion();
                        updateActions();
                    }).dimensions(x, panelY + 110 + row * ROW_HEIGHT, contentWidth, 20).build());
            profileButtons.put(profile, button);
        }
        footerY = panelY + 114 + rows * ROW_HEIGHT;
        int navWidth = Math.min(72, (contentWidth - 64) / 3);
        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), button -> {
            cancelDeletion();
            page--;
            refresh();
        }).dimensions(x, footerY, navWidth, 20).build());
        previous.active = page > 0;
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(
                Text.literal(listAvailable ? "Next" : "Retry"), button -> {
            cancelDeletion();
            if (listAvailable) page++;
            refresh();
        }).dimensions(x + navWidth + gap, footerY, navWidth, 20).build());
        next.active = !listAvailable || page + 1 < pageCount();
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(x + contentWidth - navWidth, footerY, navWidth, 20).build());
        updateActions();
    }

    private int pageCount() {
        return Math.max(1, (profiles.size() + rows - 1) / rows);
    }

    private void readProfiles() {
        try {
            profiles = BytePhase.profiles.list();
            listAvailable = true;
        } catch (Exception error) {
            profiles = List.of();
            listAvailable = false;
            status("Could not list profiles. Check storage and profile count.", Theme.ACCENT_TEXT);
        }
    }

    private void refresh() {
        clearChildren();
        init();
    }

    private void cancelDeletion() {
        if (deleteConfirmation != null) status = "";
        deleteConfirmation = null;
    }

    private void updateActions() {
        if (save == null || load == null || delete == null) return;
        boolean valid = editName.matches("[A-Za-z0-9_-]{1,32}");
        boolean exists = profiles.contains(editName);
        save.active = listAvailable && valid && (exists || profiles.size() < 128);
        save.setMessage(Text.literal(exists ? "Overwrite" : "Save"));
        save.setTooltip(Tooltip.of(Text.literal(exists
                ? "Replace this saved profile with the current settings and states."
                : "Save the current module settings and enabled states.")));
        load.active = listAvailable && valid && exists;
        delete.active = listAvailable && valid && exists;
        boolean confirming = editName.equals(deleteConfirmation);
        delete.setMessage(Text.literal(confirming ? "Delete now" : "Delete"));
        delete.setTooltip(Tooltip.of(Text.literal(confirming
                ? "Permanently remove this saved profile."
                : "Remove this saved profile. Click again to confirm.")));
        for (var entry : profileButtons.entrySet()) {
            entry.getValue().setMessage(Text.literal((entry.getKey().equals(editName) ? "> " : "")
                    + entry.getKey()));
        }
    }

    private void saveProfile() {
        cancelDeletion();
        String wanted = editName;
        boolean overwrite = profiles.contains(wanted);
        try {
            BytePhase.profiles.save(wanted);
            status((overwrite ? "Overwrote " : "Saved ") + wanted + ".", Theme.TEXT);
            refresh();
        } catch (Exception error) {
            status("Could not save profile. Check storage and settings.", Theme.ACCENT_TEXT);
            updateActions();
        }
    }

    private void loadProfile() {
        cancelDeletion();
        try {
            BytePhase.profiles.load(editName);
            status("Loaded " + editName + ".", Theme.TEXT);
        } catch (ProfileManager.LoadException error) {
            status(error.getMessage(), Theme.ACCENT_TEXT);
        } catch (Exception error) {
            status("Could not load profile. Check its name and contents.", Theme.ACCENT_TEXT);
        }
        updateActions();
    }

    private void deleteProfile() {
        String wanted = editName;
        if (!wanted.equals(deleteConfirmation)) {
            deleteConfirmation = wanted;
            status("Click Delete now to remove " + wanted + ".", Theme.ACCENT_TEXT);
            updateActions();
            return;
        }
        cancelDeletion();
        try {
            boolean removed = BytePhase.profiles.delete(wanted);
            status(removed ? "Deleted " + wanted + "." : "That profile is already gone.", Theme.TEXT);
            refresh();
        } catch (Exception error) {
            status("Could not delete profile. Check storage access.", Theme.ACCENT_TEXT);
            updateActions();
        }
    }

    private void status(String message, int color) {
        status = message;
        statusColor = color;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE6111119);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.PANEL);
        centered(context, title.getString(), panelY + 10, Theme.ACCENT);
        if (compact) {
            if (panelHeight >= 72) {
                centered(context, "Enlarge the window or lower GUI scale.", panelY + 30, Theme.MUTED);
            }
        } else {
            centered(context, "Module settings and states only.", panelY + 25, Theme.MUTED);
            context.drawTextWithShadow(textRenderer, "Saved profiles", panelX + 12, panelY + 98, Theme.MUTED);
            String count = profiles.size() + " / 128";
            context.drawTextWithShadow(textRenderer, count,
                    panelX + panelWidth - 12 - textRenderer.getWidth(count), panelY + 98, Theme.MUTED);
            if (profiles.isEmpty()) {
                centered(context, listAvailable ? "No saved profiles. Enter a name to save."
                        : "Profiles unavailable.", panelY + 116, Theme.MUTED);
            }
            int navWidth = Math.min(72, (panelWidth - 88) / 3);
            int counterX = panelX + 12 + 2 * (navWidth + 6);
            int counterWidth = panelWidth - 24 - 3 * navWidth - 12;
            String counter = (page + 1) + "/" + pageCount();
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(counter, counterWidth),
                    counterX, footerY + 6, Theme.MUTED);
            // Two bounded lines keep feedback separate from the navigation and visible inside the panel.
            String first = textRenderer.trimToWidth(status, panelWidth - 24);
            String rest = status.substring(first.length()).stripLeading();
            centered(context, first, footerY + 26, statusColor);
            centered(context, rest, footerY + 37, statusColor);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void centered(DrawContext context, String text, int y, int color) {
        if (y >= 0 && y + textRenderer.fontHeight <= height) {
            context.drawCenteredTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(text, Math.max(1, panelWidth - 24)), width / 2, y, color);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
