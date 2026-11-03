package com.ArchitectsToolbox.client;

import com.ArchitectsToolbox.command.ToolboxCommands;
import com.ArchitectsToolbox.gui.GuiCanvas;
import com.ArchitectsToolbox.gui.Wheel;
import com.ArchitectsToolbox.measure.Measurement;
import com.ArchitectsToolbox.measure.MeasurementStore;
import com.ArchitectsToolbox.measure.ViewSettings;
import com.ArchitectsToolbox.render.MeasurementScene;
import com.ArchitectsToolbox.render.WorldRenderLayer;
import com.ArchitectsToolbox.render.WorldScene;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The client side of the mod: owns the settings, the measurements and their views, and decides what is drawn.
 * Made once by each loader's entry point, which calls {@link #tick}, {@link #drawHud} and {@link #commands} from
 * the client thread. Measurements live as long as the client, as in 1.0.0: they are not saved with a world.
 */
public final class ToolboxClient {
    private final ToolboxConfig config;
    private final MeasurementStore store = new MeasurementStore();
    private final ViewSettings views = new ViewSettings();
    private final Operations operations = new Operations(store, views);
    private final Wheel wheel = new Wheel(operations);
    private final MeasureInput input = new MeasureInput(operations, wheel);
    private final SceneWorker scenes = new SceneWorker();
    private final BlockCounter counter = new BlockCounter();
    private @Nullable ClientLevel level;
    private @Nullable Component lastWarning;

    public ToolboxClient(Path configFile) {
        this.config = ToolboxConfig.load(configFile);
        BlockChanges.listen(counter::blockChanged);
    }

    public ToolboxConfig config() {
        return config;
    }

    public void tick(Minecraft minecraft) {
        level = minecraft.level;
        if (level == null) {
            WorldRenderLayer.show(WorldScene.EMPTY);
            return;
        }
        input.tick(minecraft);
        operations.keepLayerInside();

        Measurement active = store.active();
        BlockPos target = ClientCompat.screen(minecraft) == null ? MeasureInput.target(minecraft) : null;
        List<Measurement> shown = active.isEmpty() ? List.of() : List.of(active);
        scenes.request(new MeasurementScene.Input(shown, views.hollow(), views.layer() && !active.isEmpty() ? views.layerY() : null,
                MeasurementScene.preview(active, target)));
        MeasurementScene.Result result = scenes.take();
        if (result != null) {
            WorldRenderLayer.show(result.scene());
            if (result.warning() != null && !Objects.equals(result.warning(), lastWarning)) {
                ClientCompat.actionBar(minecraft, result.warning());
            }
            lastWarning = result.warning();
        }

        counter.track(views.count() ? active : null);
        counter.tick(level);
    }

    public void drawHud(GuiCanvas canvas) {
        if (views.count() && level != null) {
            CountOverlay.draw(canvas, counter);
        }
    }

    /** The {@code /atb} command for the loader's client command source. */
    public <S> LiteralArgumentBuilder<S> commands() {
        return ToolboxCommands.create(operations, () -> {
            BlockCounter.Count count = views.count() ? counter.count() : null;
            return count != null && count.progress() >= 1 ? count.total() : -1;
        });
    }
}
