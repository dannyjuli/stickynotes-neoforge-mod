package com.example.stickynotes;

import com.example.stickynotes.client.KeyBindings;
import com.example.stickynotes.client.NoteData;
import com.example.stickynotes.client.StickyNotesOverlay;
import com.example.stickynotes.client.StickyNotesScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Sticky Notes — NeoForge 21.1.221 / Minecraft 1.21.1
 *
 * Default keybinds (all rebindable in Controls → Sticky Notes):
 *   N            — Toggle overlay visibility
 *   M            — Open note editor
 *   ,            — Minimize / expand
 *   [            — Previous note
 *   ]            — Next note
 *
 * Drag the title bar (when a screen with a cursor is active) to reposition.
 * Notes are saved to:  .minecraft/config/stickynotes.json
 */
@Mod(StickyNotesMod.MOD_ID)
public class StickyNotesMod {

    public static final String MOD_ID = "stickynotes";

    public StickyNotesMod(IEventBus modEventBus) {
        // This mod is client-only; skip all registration on a dedicated server.
        if (FMLEnvironment.dist != Dist.CLIENT) return;

        // Keybind registration happens on the mod event bus
        modEventBus.addListener(KeyBindings::register);

        // Everything else wires up during FMLClientSetupEvent
        modEventBus.addListener(this::onClientSetup);
    }

    // ── Client setup ──────────────────────────────────────────────────────────

    private void onClientSetup(FMLClientSetupEvent event) {
        // Load persisted notes on the main thread
        event.enqueueWork(NoteData::init);

        // Register game-event listeners on the NeoForge event bus
        NeoForge.EVENT_BUS.addListener(StickyNotesOverlay::onRenderGui);
        NeoForge.EVENT_BUS.addListener(StickyNotesOverlay::onMouseButton);
        NeoForge.EVENT_BUS.addListener(StickyNotesOverlay::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    // ── Client tick: keybind polling + drag update ────────────────────────────

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // ── Toggle visibility ───────────────────────────────────────────────
        while (KeyBindings.TOGGLE.consumeClick()) {
            NoteData.visible = !NoteData.visible;
            NoteData.save();
        }

        // ── Open editor (only when no other screen is active) ───────────────
        while (KeyBindings.OPEN_EDIT.consumeClick()) {
            if (NoteData.visible && mc.screen == null) {
                mc.setScreen(new StickyNotesScreen());
            }
        }

        // ── Minimize / expand ───────────────────────────────────────────────
        while (KeyBindings.MINIMIZE.consumeClick()) {
            if (NoteData.visible) {
                NoteData.minimized = !NoteData.minimized;
                NoteData.save();
            }
        }

        // ── Navigate between notes ──────────────────────────────────────────
        while (KeyBindings.PREV_NOTE.consumeClick()) {
            if (NoteData.visible) NoteData.navigateNote(-1);
        }
        while (KeyBindings.NEXT_NOTE.consumeClick()) {
            if (NoteData.visible) NoteData.navigateNote(1);
        }

        // ── Update drag position every tick ────────────────────────────────
        StickyNotesOverlay.onTick();
    }
}
