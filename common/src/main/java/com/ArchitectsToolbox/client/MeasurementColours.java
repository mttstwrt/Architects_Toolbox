package com.ArchitectsToolbox.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** The named measurement colours and their shared text representation. Colours are opaque ARGB. */
public final class MeasurementColours {
    private record Named(String name, int colour) {}

    private static final List<Named> NAMED = List.of(
            new Named("red", 0xFFFF4D4D),
            new Named("orange", 0xFFFFA64D),
            new Named("yellow", 0xFFFFFF4D),
            new Named("green", 0xFF55FF55),
            new Named("cyan", 0xFF55FFFF),
            new Named("blue", 0xFF5555FF),
            new Named("purple", 0xFFAA55FF),
            new Named("pink", 0xFFFF55FF));

    public static final List<Integer> DEFAULT_PALETTE = NAMED.stream().map(Named::colour).toList();

    private MeasurementColours() {}

    /** Parses a named colour or exactly six hexadecimal RGB digits preceded by '#'; null means invalid. */
    public static @Nullable Integer parse(String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        for (Named named : NAMED) {
            if (named.name().equals(text)) {
                return named.colour();
            }
        }
        if (text.length() == 7 && text.charAt(0) == '#') {
            int colour = 0;
            for (int i = 1; i < text.length(); i++) {
                int digit = Character.digit(text.charAt(i), 16);
                if (digit < 0 || text.charAt(i) > 127) {
                    return null;
                }
                colour = (colour << 4) | digit;
            }
            return 0xFF000000 | colour;
        }
        return null;
    }

    public static List<String> names() {
        return NAMED.stream().map(Named::name).toList();
    }

    public static String hex(int colour) {
        return String.format(Locale.ROOT, "#%06X", colour & 0xFFFFFF);
    }

    /** A translated name for defaults, or the RGB hex value for a custom colour. */
    public static Component name(int colour) {
        for (Named named : NAMED) {
            if (named.colour() == colour) {
                return Component.translatable("architects_toolbox.colour." + named.name());
            }
        }
        return Component.literal(hex(colour));
    }
}
