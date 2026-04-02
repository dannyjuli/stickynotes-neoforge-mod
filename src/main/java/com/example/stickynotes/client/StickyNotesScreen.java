package com.example.stickynotes.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Editing screen for sticky notes.
 *
 * Features:
 *  • Multi-line text editing
 *  • Previous / Next note navigation  (also scroll-wheel over the panel)
 *  • Add / Delete note buttons
 *  • Auto-saves on close / ESC
 *  • Does NOT pause the game (isPauseScreen = false)
 */
public class StickyNotesScreen extends Screen {

    // ── Panel sizing ──────────────────────────────────────────────────────────

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 240;
    private static final int TITLE_H = 26;

    // ── Colours ───────────────────────────────────────────────────────────────

    private static final int COL_OVERLAY   = 0x88000000;
    private static final int COL_BORDER    = 0xFF775500;
    private static final int COL_TITLE_BG  = 0xFF997700;
    private static final int COL_PANEL_BG  = 0xFFFFEE55;
    private static final int COL_TITLE_TXT = 0xFF111100;

    // ── Widgets ───────────────────────────────────────────────────────────────

    private MultiLineEditBox editBox;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Content before editing started — used by Cancel. */
    private String originalContent;

    public StickyNotesScreen() {
        super(Component.literal("Sticky Note Editor"));
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        originalContent = NoteData.getCurrentNote();

        int px = (width  - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // ── Multi-line text box ─────────────────────────────────────────────
        editBox = addRenderableWidget(new MultiLineEditBox(
                font,
                px + 10,
                py + TITLE_H + 6,
                PANEL_W - 20,
                PANEL_H - TITLE_H - 50,
                Component.empty(),
                Component.literal("Write your note here…")
        ));
        editBox.setValue(originalContent);
        setInitialFocus(editBox);

        // ── Bottom button row ───────────────────────────────────────────────
        int btnY   = py + PANEL_H - 26;
        int center = px + PANEL_W / 2;

        // Previous note
        addRenderableWidget(Button.builder(Component.literal("◄"), b -> {
            saveCurrentText();
            NoteData.navigateNote(-1);
            refreshEditBox();
        }).bounds(px + 4, btnY, 22, 20).build());

        // Next note
        addRenderableWidget(Button.builder(Component.literal("►"), b -> {
            saveCurrentText();
            NoteData.navigateNote(1);
            refreshEditBox();
        }).bounds(px + PANEL_W - 26, btnY, 22, 20).build());

        // Add note
        addRenderableWidget(Button.builder(Component.literal("+ New"), b -> {
            saveCurrentText();
            NoteData.addNote();
            refreshEditBox();
        }).bounds(center - 110, btnY, 50, 20).build());

        // Delete note
        addRenderableWidget(Button.builder(Component.literal("✕ Del"), b -> {
            NoteData.removeCurrentNote();
            refreshEditBox();
        }).bounds(center - 56, btnY, 50, 20).build());

        // Save & close
        addRenderableWidget(Button.builder(Component.literal("✔ Save"), b -> {
            saveCurrentText();
            onClose();
        }).bounds(center + 2, btnY, 54, 20).build());

        // Cancel
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            NoteData.setCurrentNote(originalContent);
            onClose();
        }).bounds(center + 58, btnY, 46, 20).build());
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        // Full-screen dim
        gfx.fill(0, 0, width, height, COL_OVERLAY);

        int px = (width  - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // Shadow
        gfx.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0x55000000);

        // Border
        gfx.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, COL_BORDER);

        // Panel body
        gfx.fill(px, py, px + PANEL_W, py + PANEL_H, COL_PANEL_BG);

        // Title bar
        gfx.fill(px, py, px + PANEL_W, py + TITLE_H, COL_TITLE_BG);

        // Title text
        String title = "✎  Sticky Note  "
                + (NoteData.currentNoteIndex + 1)
                + " / " + NoteData.notes.size();
        gfx.drawCenteredString(font, title, px + PANEL_W / 2, py + 7, COL_TITLE_TXT);

        // Scroll-to-switch hint (shown when multiple notes exist)
        if (NoteData.notes.size() > 1) {
            gfx.drawString(font,
                    "§7Scroll or ◄ ► to switch notes",
                    px + 30, py + TITLE_H + 3, 0xFF886600, false);
        }

        super.render(gfx, mouseX, mouseY, partial);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            saveCurrentText();
            onClose();
            return true;
        }
        // Ctrl+S = quick save (keep screen open)
        if (key == GLFW.GLFW_KEY_S && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            saveCurrentText();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // Scroll within the edit box area → let the box handle it
        int px = (width  - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;
        if (mx >= px && mx < px + PANEL_W && my >= py && my < py + PANEL_H) {
            if (my >= py + TITLE_H && my < py + PANEL_H - 30) {
                // Inside text area — pass to editBox
                return super.mouseScrolled(mx, my, dx, dy);
            }
            // Scroll over title bar / buttons → switch notes
            if (NoteData.notes.size() > 1) {
                saveCurrentText();
                NoteData.navigateNote(dy > 0 ? -1 : 1);
                refreshEditBox();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        saveCurrentText();
        super.onClose();
    }

    /** Does NOT pause the game so the player can keep playing while editing. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveCurrentText() {
        if (editBox != null) {
            NoteData.setCurrentNote(editBox.getValue());
        }
    }

    private void refreshEditBox() {
        if (editBox != null) {
            editBox.setValue(NoteData.getCurrentNote());
            originalContent = NoteData.getCurrentNote();
        }
    }
}
