package com.example.stickynotes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Renders the sticky-note overlay and handles:
 *   - Title-bar dragging  (move)
 *   - Edge / corner dragging  (resize, like an OS window)
 *   - Button clicks  (minimize, add note, close)
 *   - Content click  (open editor)
 *   - Scroll wheel  (switch notes)
 *
 * Resize handles (6 px wide) are drawn at:
 *   right edge, bottom edge, bottom-right corner
 *
 * NOTE: mouse interaction requires a visible cursor, i.e. any screen must be
 * open (inventory, our editor, etc.). During raw gameplay use the keybinds.
 */
public class StickyNotesOverlay {

    // ── Resize handle size ────────────────────────────────────────────────────
    private static final int HANDLE = 6;

    // ── Minimised tab ─────────────────────────────────────────────────────────
    private static final int MINI_W = 22;
    private static final int MINI_H = 70;

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final int COL_BG       = 0xEEFFEE66;
    private static final int COL_TITLE    = 0xFF997700;
    private static final int COL_BORDER   = 0xFF775500;
    private static final int COL_SHADOW   = 0x55000000;
    private static final int COL_TEXT     = 0xFF332200;
    private static final int COL_DIM      = 0xFF998822;
    private static final int COL_BTN      = 0xBB553300;
    private static final int COL_BTN_HOV  = 0xFFCC7700;
    private static final int COL_HANDLE   = 0x88FFDD00;   // yellow tint on resize zones
    private static final int COL_WHITE    = 0xFFFFFFFF;

    // ── Interaction state ─────────────────────────────────────────────────────

    private enum Action { NONE, MOVE, RESIZE_R, RESIZE_B, RESIZE_BR }

    private static Action action      = Action.NONE;
    private static int    anchorMouseX, anchorMouseY;
    private static int    anchorPosX,  anchorPosY;
    private static int    anchorW,     anchorH;

    // ── Render ────────────────────────────────────────────────────────────────

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!NoteData.visible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && !(mc.screen instanceof StickyNotesScreen)) return;

        GuiGraphics gfx = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Default position: top-right
        if (NoteData.posX < 0) NoteData.posX = sw - NoteData.width - 10;
        if (NoteData.posY < 0) NoteData.posY = 10;

        if (NoteData.minimized) renderMinimised(gfx, sw, sh, mc);
        else                    renderExpanded(gfx, sw, sh, mc);
    }

    private static void renderExpanded(GuiGraphics gfx, int sw, int sh, Minecraft mc) {
        clampExpanded(sw, sh);
        int x = NoteData.posX, y = NoteData.posY;
        int w = NoteData.width,  h = NoteData.height;
        double mx = guiMX(mc), my = guiMY(mc);

        // Drop shadow
        gfx.fill(x+4, y+4, x+w+4, y+h+4, COL_SHADOW);

        // Border
        gfx.fill(x-1, y-1, x+w+1, y+h+1, COL_BORDER);

        // Title bar
        gfx.fill(x, y, x+w, y+NoteData.TITLE_H, COL_TITLE);

        // Content background
        gfx.fill(x, y+NoteData.TITLE_H, x+w, y+h, COL_BG);

        // ── Resize handle highlights ────────────────────────────────────────
        // Right edge
        boolean hoverR  = isIn(mx,my, x+w-HANDLE, y+NoteData.TITLE_H, HANDLE, h-NoteData.TITLE_H-HANDLE);
        // Bottom edge
        boolean hoverB  = isIn(mx,my, x, y+h-HANDLE, w-HANDLE, HANDLE);
        // Bottom-right corner
        boolean hoverBR = isIn(mx,my, x+w-HANDLE, y+h-HANDLE, HANDLE, HANDLE);

        if (hoverR || action == Action.RESIZE_R)
            gfx.fill(x+w-HANDLE, y+NoteData.TITLE_H, x+w, y+h-HANDLE, COL_HANDLE);
        if (hoverB || action == Action.RESIZE_B)
            gfx.fill(x, y+h-HANDLE, x+w-HANDLE, y+h, COL_HANDLE);
        if (hoverBR || action == Action.RESIZE_BR)
            gfx.fill(x+w-HANDLE, y+h-HANDLE, x+w, y+h, 0xCCFFCC00);

        // Corner resize icon (small triangle hint)
        gfx.fill(x+w-4, y+h-1, x+w,   y+h,   COL_BORDER);
        gfx.fill(x+w-2, y+h-3, x+w,   y+h-1, COL_BORDER);
        gfx.fill(x+w-1, y+h-5, x+w,   y+h-3, COL_BORDER);

        // ── Title bar contents ──────────────────────────────────────────────
        gfx.drawString(mc.font, "§6✎ Sticky Notes", x+4, y+4, COL_WHITE, false);

        if (NoteData.notes.size() > 1) {
            String idx = (NoteData.currentNoteIndex+1) + "/" + NoteData.notes.size();
            int iw = mc.font.width(idx);
            gfx.drawString(mc.font, idx, x+w-iw-38, y+4, 0xFFFFDD88, false);
        }

        // Buttons: [–] [+] [×]
        drawBtn(gfx, mc, x+w-33, y+3, 9, 9, "–", isIn(mx,my, x+w-33,y+3,9,9));
        drawBtn(gfx, mc, x+w-22, y+3, 9, 9, "+", isIn(mx,my, x+w-22,y+3,9,9));
        drawBtn(gfx, mc, x+w-11, y+3, 9, 9, "×", isIn(mx,my, x+w-11,y+3,9,9));

        // ── Note content ────────────────────────────────────────────────────
        String content = NoteData.getCurrentNote();
        int pad  = 5;
        int maxW = w - pad*2 - HANDLE;   // leave room for right handle
        int lineH = mc.font.lineHeight + 2;
        int maxLines = (NoteData.contentHeight() - pad*2 - mc.font.lineHeight - 4) / lineH;

        if (content.isEmpty()) {
            gfx.drawString(mc.font, "§7Press §fM §7to edit…", x+pad, y+NoteData.TITLE_H+pad, COL_DIM, false);
        } else {
            String[] lines = content.split("\n", -1);
            int ty = y + NoteData.TITLE_H + pad;
            for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
                String line = mc.font.plainSubstrByWidth(lines[i], maxW);
                if (!line.equals(lines[i])) line += "…";
                gfx.drawString(mc.font, line, x+pad, ty, COL_TEXT, false);
                ty += lineH;
            }
            if (lines.length > maxLines) {
                gfx.drawString(mc.font, "§8▼ " + (lines.length-maxLines) + " more…",
                        x+pad, y+h-mc.font.lineHeight-HANDLE-2, COL_DIM, false);
            }
        }

        // Bottom hint
        gfx.drawString(mc.font, "§8[M] edit · [,] min · drag corner to resize",
                x+pad, y+h-mc.font.lineHeight-2, COL_DIM, false);
    }

    private static void renderMinimised(GuiGraphics gfx, int sw, int sh, Minecraft mc) {
        int x = sw - MINI_W - 2;
        int y = clamp(NoteData.posY, 0, sh - MINI_H);

        gfx.fill(x+2, y+2, x+MINI_W+2, y+MINI_H+2, COL_SHADOW);
        gfx.fill(x-1, y-1, x+MINI_W+1, y+MINI_H+1, COL_BORDER);
        gfx.fill(x,   y,   x+MINI_W,   y+MINI_H,   COL_BG);
        gfx.fill(x,   y,   x+MINI_W,   y+NoteData.TITLE_H, COL_TITLE);

        gfx.drawString(mc.font, "§f«", x+5, y+4, COL_WHITE, false);

        int ty = y + NoteData.TITLE_H + 6;
        for (char c : "Stick".toCharArray()) {
            gfx.drawString(mc.font, "§6" + c, x+6, ty, COL_TEXT, false);
            ty += 10;
        }
        if (NoteData.notes.size() > 1)
            gfx.drawString(mc.font, "§e" + NoteData.notes.size(), x+6, y+MINI_H-12, COL_WHITE, false);
    }

    // ── Mouse button ──────────────────────────────────────────────────────────

    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!NoteData.visible) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;          // cursor not available in raw gameplay

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double mx = guiMX(mc), my = guiMY(mc);

        // ── Minimised tab ───────────────────────────────────────────────────
        if (NoteData.minimized) {
            int tx = sw - MINI_W - 2;
            int ty = clamp(NoteData.posY, 0, sh - MINI_H);
            if (isIn(mx,my, tx,ty, MINI_W, MINI_H) && event.getAction() == GLFW.GLFW_PRESS) {
                NoteData.minimized = false;
                NoteData.save();
                event.setCanceled(true);
            }
            return;
        }

        // ── Expanded ────────────────────────────────────────────────────────
        clampExpanded(sw, sh);
        int x = NoteData.posX, y = NoteData.posY;
        int w = NoteData.width, h = NoteData.height;

        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (action != Action.NONE) {
                action = Action.NONE;
                NoteData.save();
                event.setCanceled(true);
            }
            return;
        }

        // PRESS — determine what was hit
        if (!isIn(mx, my, x-1, y-1, w+2, h+2)) return;

        // Title-bar buttons (checked before drag zones)
        if (isIn(mx,my, x+w-33, y+3, 9, 9)) {
            NoteData.minimized = true; NoteData.save(); event.setCanceled(true); return;
        }
        if (isIn(mx,my, x+w-22, y+3, 9, 9)) {
            NoteData.addNote(); event.setCanceled(true); return;
        }
        if (isIn(mx,my, x+w-11, y+3, 9, 9)) {
            NoteData.visible = false; NoteData.save(); event.setCanceled(true); return;
        }

        // Snapshot anchor for drag/resize
        anchorMouseX = (int) mx; anchorMouseY = (int) my;
        anchorPosX   = x;        anchorPosY   = y;
        anchorW      = w;        anchorH      = h;

        // Bottom-right corner  (check before edges)
        if (isIn(mx,my, x+w-HANDLE, y+h-HANDLE, HANDLE, HANDLE)) {
            action = Action.RESIZE_BR; event.setCanceled(true); return;
        }
        // Right edge
        if (isIn(mx,my, x+w-HANDLE, y+NoteData.TITLE_H, HANDLE, h-NoteData.TITLE_H-HANDLE)) {
            action = Action.RESIZE_R; event.setCanceled(true); return;
        }
        // Bottom edge
        if (isIn(mx,my, x, y+h-HANDLE, w-HANDLE, HANDLE)) {
            action = Action.RESIZE_B; event.setCanceled(true); return;
        }
        // Title bar — move
        if (isIn(mx,my, x, y, w, NoteData.TITLE_H)) {
            action = Action.MOVE; event.setCanceled(true); return;
        }
        // Content area — open editor
        if (isIn(mx,my, x, y+NoteData.TITLE_H, w, h-NoteData.TITLE_H)) {
            if (!(mc.screen instanceof StickyNotesScreen)) {
                mc.setScreen(new StickyNotesScreen());
            }
            event.setCanceled(true);
        }
    }

    // ── Scroll: switch notes ──────────────────────────────────────────────────

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!NoteData.visible || NoteData.minimized || NoteData.notes.size() <= 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        clampExpanded(sw, sh);
        double mx = guiMX(mc), my = guiMY(mc);
        if (!isIn(mx,my, NoteData.posX, NoteData.posY, NoteData.width, NoteData.height)) return;

        NoteData.navigateNote(event.getScrollDeltaY() > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    // ── Tick: apply move / resize ─────────────────────────────────────────────

    public static void onTick() {
        if (action == Action.NONE) return;

        Minecraft mc = Minecraft.getInstance();

        // Release if button no longer held
        if (GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
            action = Action.NONE;
            NoteData.save();
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int dx = (int) guiMX(mc) - anchorMouseX;
        int dy = (int) guiMY(mc) - anchorMouseY;

        switch (action) {
            case MOVE -> {
                NoteData.posX = clamp(anchorPosX + dx, 0, sw - NoteData.width);
                NoteData.posY = clamp(anchorPosY + dy, 0, sh - NoteData.height);
            }
            case RESIZE_R -> {
                NoteData.width  = clamp(anchorW + dx, NoteData.MIN_W, sw - anchorPosX);
            }
            case RESIZE_B -> {
                NoteData.height = clamp(anchorH + dy, NoteData.MIN_H, sh - anchorPosY);
            }
            case RESIZE_BR -> {
                NoteData.width  = clamp(anchorW + dx, NoteData.MIN_W, sw - anchorPosX);
                NoteData.height = clamp(anchorH + dy, NoteData.MIN_H, sh - anchorPosY);
            }
            default -> {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void clampExpanded(int sw, int sh) {
        NoteData.posX = clamp(NoteData.posX, 0, Math.max(0, sw - NoteData.width));
        NoteData.posY = clamp(NoteData.posY, 0, Math.max(0, sh - NoteData.height));
    }

    private static void drawBtn(GuiGraphics gfx, Minecraft mc,
                                int x, int y, int w, int h, String lbl, boolean hov) {
        gfx.fill(x, y, x+w, y+h, hov ? COL_BTN_HOV : COL_BTN);
        gfx.fill(x, y, x+w, y+1, 0x66FFFFFF);
        int lw = mc.font.width(lbl);
        gfx.drawString(mc.font, lbl, x+(w-lw)/2, y+1, COL_WHITE, false);
    }

    private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double guiMX(Minecraft mc) {
        return mc.mouseHandler.xpos()
                * mc.getWindow().getGuiScaledWidth()
                / mc.getWindow().getScreenWidth();
    }

    private static double guiMY(Minecraft mc) {
        return mc.mouseHandler.ypos()
                * mc.getWindow().getGuiScaledHeight()
                / mc.getWindow().getScreenHeight();
    }
}
