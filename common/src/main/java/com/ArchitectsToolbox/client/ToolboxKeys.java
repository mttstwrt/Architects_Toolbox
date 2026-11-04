package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.ArchitectsToolbox;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?}

/**
 * The mod's key mappings and their category. Each loader's entry point registers them; the names are the ones 1.0.0
 * used, so bindings saved in options.txt carry over.
 */
public final class ToolboxKeys {
    //? if >=26.1 {
    /** Registered by the loader, not through {@code Category.register}, which NeoForge would then refuse as a duplicate. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ArchitectsToolbox.MOD_ID, "main"));
    //?} else {
    /*// Before 1.21.9 a category is a translation key: the one the newer versions derive from their id.
    public static final String CATEGORY = "key.category." + ArchitectsToolbox.MOD_ID + ".main";
    *///?}
    public static final KeyMapping MEASURE = new KeyMapping("key.architects_toolbox.measure", InputConstants.KEY_G, CATEGORY);
    /** Steps the layer view; 1.0.0 had these on the arrow keys, unbindable. */
    public static final KeyMapping LAYER_UP = new KeyMapping("key.architects_toolbox.layer_up", InputConstants.KEY_UP, CATEGORY);
    public static final KeyMapping LAYER_DOWN = new KeyMapping("key.architects_toolbox.layer_down", InputConstants.KEY_DOWN, CATEGORY);
    /** Turn the paste preview; 1.0.0 had these on the arrow keys, unbindable. */
    public static final KeyMapping ROTATE_LEFT = new KeyMapping("key.architects_toolbox.rotate_left", InputConstants.KEY_LEFT, CATEGORY);
    public static final KeyMapping ROTATE_RIGHT = new KeyMapping("key.architects_toolbox.rotate_right", InputConstants.KEY_RIGHT, CATEGORY);
    /** Turns the next-point preview on and off; unbound by default, the wheel's Display page has it too. */
    public static final KeyMapping TOGGLE_PREVIEW = new KeyMapping("key.architects_toolbox.toggle_preview", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final List<KeyMapping> ALL = List.of(MEASURE, LAYER_UP, LAYER_DOWN, ROTATE_LEFT, ROTATE_RIGHT, TOGGLE_PREVIEW);

    private ToolboxKeys() {
    }
}
