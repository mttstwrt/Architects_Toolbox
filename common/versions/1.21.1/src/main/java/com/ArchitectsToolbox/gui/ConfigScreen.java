package com.ArchitectsToolbox.gui;

import com.ArchitectsToolbox.client.ClientCompat;
import com.ArchitectsToolbox.client.ToolboxClient;
import com.ArchitectsToolbox.client.ToolboxConfig;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The settings screen (F52): ghost style, ghost opacity and whether placing replaces blocks, each saved as soon as it
 * changes. Opened from NeoForge's mod list or with {@code /atb config}; each version has its own copy for its screen
 * API. 1.21.1.
 */
public final class ConfigScreen extends Screen {
    private static final int WIDTH = 200;
    private final @Nullable Screen parent;
    private final ToolboxClient client;

    public ConfigScreen(@Nullable Screen parent, ToolboxClient client) {
        super(Component.translatable("architects_toolbox.config.title"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int x = width / 2 - WIDTH / 2;
        int y = height / 4;
        ToolboxConfig config = client.config();
        addRenderableWidget(CycleButton.<ToolboxConfig.GhostStyle>builder(ConfigScreen::styleName)
                .withValues(ToolboxConfig.GhostStyle.values())
                .withInitialValue(config.ghostStyle())
                .create(x, y, WIDTH, 20, Component.translatable("architects_toolbox.config.ghost_style"),
                        (button, style) -> client.setConfig(client.config().withGhostStyle(style))));
        addRenderableWidget(new OpacitySlider(x, y + 24, config.ghostOpacity()));
        addRenderableWidget(CycleButton.onOffBuilder(config.placeReplaces())
                .create(x, y + 48, WIDTH, 20, Component.translatable("architects_toolbox.config.place_replaces"),
                        (button, on) -> client.setConfig(client.config().withPlaceReplaces(on))));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x, y + 84, WIDTH, 20).build());
    }

    private static Component styleName(ToolboxConfig.GhostStyle style) {
        return Component.translatable("architects_toolbox.config.ghost_style." + style.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
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
