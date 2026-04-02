package com.example.stickynotes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all sticky-note state and handles JSON save/load.
 * Saved to: <game-dir>/config/stickynotes.json
 */
public class NoteData {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoteData.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path savePath;

    // Size constraints
    public static final int MIN_W   = 120;
    public static final int MIN_H   = 80;
    public static final int TITLE_H = 15;

    // Persistent state
    public static boolean      visible          = true;
    public static boolean      minimized        = false;
    public static int          posX             = -1;
    public static int          posY             = -1;
    public static int          width            = 180;
    public static int          height           = 130;
    public static List<String> notes            = new ArrayList<>();
    public static int          currentNoteIndex = 0;

    public static void init() {
        savePath = Minecraft.getInstance()
                .gameDirectory.toPath()
                .resolve("config")
                .resolve("stickynotes.json");
        load();
        if (notes.isEmpty()) {
            notes.add("Your first sticky note!\nEdit with M.\nResize by dragging edges.");
        }
    }

    public static int contentHeight() {
        return Math.max(MIN_H - TITLE_H, height - TITLE_H);
    }

    public static String getCurrentNote() {
        if (notes.isEmpty()) notes.add("");
        currentNoteIndex = Math.max(0, Math.min(currentNoteIndex, notes.size() - 1));
        return notes.get(currentNoteIndex);
    }

    public static void setCurrentNote(String text) {
        if (notes.isEmpty()) notes.add(text);
        else notes.set(currentNoteIndex, text);
        save();
    }

    public static void addNote() {
        notes.add("New note " + (notes.size() + 1));
        currentNoteIndex = notes.size() - 1;
        save();
    }

    public static void removeCurrentNote() {
        if (notes.size() <= 1) {
            notes.set(0, "");
        } else {
            notes.remove(currentNoteIndex);
            currentNoteIndex = Math.max(0, currentNoteIndex - 1);
        }
        save();
    }

    public static void navigateNote(int delta) {
        if (notes.isEmpty()) return;
        currentNoteIndex = Math.floorMod(currentNoteIndex + delta, notes.size());
        save();
    }

    public static void save() {
        if (savePath == null) return;
        try {
            Files.createDirectories(savePath.getParent());
            SaveData d       = new SaveData();
            d.visible        = visible;
            d.minimized      = minimized;
            d.posX           = posX;
            d.posY           = posY;
            d.width          = width;
            d.height         = height;
            d.notes          = new ArrayList<>(notes);
            d.currentNoteIndex = currentNoteIndex;
            try (Writer w = Files.newBufferedWriter(savePath)) {
                GSON.toJson(d, w);
            }
        } catch (IOException e) {
            LOGGER.error("[StickyNotes] Failed to save notes", e);
        }
    }

    public static void load() {
        if (savePath == null || !Files.exists(savePath)) return;
        try (Reader r = Files.newBufferedReader(savePath)) {
            SaveData d = GSON.fromJson(r, SaveData.class);
            if (d == null) return;
            visible          = d.visible;
            minimized        = d.minimized;
            posX             = d.posX;
            posY             = d.posY;
            width            = Math.max(MIN_W, d.width);
            height           = Math.max(MIN_H, d.height);
            notes            = d.notes != null ? d.notes : new ArrayList<>();
            currentNoteIndex = Math.max(0, Math.min(d.currentNoteIndex,
                                   Math.max(0, notes.size() - 1)));
        } catch (IOException e) {
            LOGGER.error("[StickyNotes] Failed to load notes", e);
        }
    }

    @SuppressWarnings("unused")
    private static class SaveData {
        boolean      visible          = true;
        boolean      minimized        = false;
        int          posX             = -1;
        int          posY             = -1;
        int          width            = 180;
        int          height           = 130;
        List<String> notes            = new ArrayList<>();
        int          currentNoteIndex = 0;
    }
}
