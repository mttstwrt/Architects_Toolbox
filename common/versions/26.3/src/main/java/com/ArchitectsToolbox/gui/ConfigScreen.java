package com.ArchitectsToolbox.gui;

import com.ArchitectsToolbox.client.ClientCompat;
import com.ArchitectsToolbox.client.MeasurementColours;
import com.ArchitectsToolbox.client.ToolboxClient;
import com.ArchitectsToolbox.client.ToolboxConfig;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The settings screen: ghost settings, creative placement, measurement palette and limit. Toggle and slider changes
 * save immediately; the validated text fields save on Done. Opened from NeoForge's mod list or with {@code /atb config}; each version has its own copy for its screen
 * API. 26.3.
 */
public final class ConfigScreen extends Screen {
    private static final int WIDTH = 200;
    private final @Nullable Screen parent;
    private final ToolboxClient client;
    private @Nullable EditBox paletteField;
    private @Nullable EditBox limitField;
    private @Nullable Button done;
    private int controlsY;

    public ConfigScreen(@Nullable Screen parent, ToolboxClient client) {
        super(Component.translatable("architects_toolbox.config.title"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int x = width / 2 - WIDTH / 2;
        int y = Math.max(32, (height - 200) / 2);
        controlsY = y;
        ToolboxConfig config = client.config();
        // Keep unfinished text when the window is resized and init runs again.
        String oldPalette = paletteField == null ? config.palette().stream().map(MeasurementColours::hex).collect(Collectors.joining(", "))
                : paletteField.getValue();
        String oldLimit = limitField == null ? Integer.toString(config.measurementLimit()) : limitField.getValue();
        addRenderableWidget(CycleButton.builder(ConfigScreen::styleName, config.ghostStyle())
                .withValues(ToolboxConfig.GhostStyle.values())
                .create(x, y, WIDTH, 20, Component.translatable("architects_toolbox.config.ghost_style"),
                        (button, style) -> client.setConfig(client.config().withGhostStyle(style))));
        addRenderableWidget(new OpacitySlider(x, y + 24, config.ghostOpacity()));
        addRenderableWidget(CycleButton.onOffBuilder(config.placeReplaces())
                .create(x, y + 48, WIDTH, 20, Component.translatable("architects_toolbox.config.place_replaces"),
                        (button, on) -> client.setConfig(client.config().withPlaceReplaces(on))));
        paletteField = addRenderableWidget(new EditBox(font, x, y + 84, WIDTH, 20,
                Component.translatable("architects_toolbox.config.palette")));
        paletteField.setMaxLength(ToolboxConfig.MAX_PALETTE_SIZE * 16);
        paletteField.setValue(oldPalette);
        paletteField.setResponder(value -> updateValidation());
        limitField = addRenderableWidget(new EditBox(font, x, y + 120, WIDTH, 20,
                Component.translatable("architects_toolbox.config.measurement_limit")));
        limitField.setMaxLength(3);
        limitField.setValue(oldLimit);
        limitField.setResponder(value -> updateValidation());
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x, y + 160, WIDTH, 20).build());
        updateValidation();
    }

    private static Component styleName(ToolboxConfig.GhostStyle style) {
        return Component.translatable("architects_toolbox.config.ghost_style." + style.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("architects_toolbox.config.palette"), width / 2 - WIDTH / 2, controlsY + 73, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("architects_toolbox.config.measurement_limit"), width / 2 - WIDTH / 2, controlsY + 109, 0xFFFFFFFF);
        Component error = validationError();
        if (error != null) {
            graphics.text(font, error, width / 2 - WIDTH / 2, controlsY + 145, 0xFFFF5555);
        }
    }

    private @Nullable List<Integer> palette() {
        if (paletteField == null) {
            return null;
        }
        return ToolboxConfig.parsePalette(Arrays.asList(paletteField.getValue().split(",", -1)));
    }

    private int limit() {
        if (limitField == null) {
            return -1;
        }
        try {
            int value = Integer.parseInt(limitField.getValue());
            return value >= 1 && value <= ToolboxConfig.MAX_MEASUREMENTS ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private @Nullable Component validationError() {
        if (palette() == null) {
            return Component.translatable("architects_toolbox.config.invalid_palette", ToolboxConfig.MAX_PALETTE_SIZE);
        }
        if (limit() < 0) {
            return Component.translatable("architects_toolbox.config.invalid_limit", ToolboxConfig.MAX_MEASUREMENTS);
        }
        return null;
    }

    private void updateValidation() {
        if (done != null) {
            done.active = validationError() == null;
        }
    }

    @Override
    public void onClose() {
        List<Integer> colours = palette();
        int max = limit();
        if (colours == null || max < 0) {
            return;
        }
        client.setConfig(client.config().withPalette(colours).withMeasurementLimit(max));
        ClientCompat.setScreen(minecraft, parent);
    }

    /** Ghost opacity, 0 to 100%. */
    private final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, float opacity) {
            super(x, y, WIDTH, 20, Component.empty(), opacity);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("architects_toolbox.config.ghost_opacity", Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            client.setConfig(client.config().withGhostOpacity((float) value));
        }
    }
}
