package com.example.stickynotes.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static final String CATEGORY = "key.category.stickynotes";

    /** Default: N — toggle the overlay visibility */
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.stickynotes.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY
    );

    /** Default: M — open the note editor screen */
    public static final KeyMapping OPEN_EDIT = new KeyMapping(
            "key.stickynotes.edit",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    );

    /** Default: Comma — minimize / expand the overlay */
    public static final KeyMapping MINIMIZE = new KeyMapping(
            "key.stickynotes.minimize",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            CATEGORY
    );

    /** Default: Left-bracket — previous note */
    public static final KeyMapping PREV_NOTE = new KeyMapping(
            "key.stickynotes.prev",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            CATEGORY
    );

    /** Default: Right-bracket — next note */
    public static final KeyMapping NEXT_NOTE = new KeyMapping(
            "key.stickynotes.next",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(OPEN_EDIT);
        event.register(MINIMIZE);
        event.register(PREV_NOTE);
        event.register(NEXT_NOTE);
    }
}
