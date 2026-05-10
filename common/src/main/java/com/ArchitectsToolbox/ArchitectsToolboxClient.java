package com.ArchitectsToolbox;

import com.ArchitectsToolbox.gui.BlockCountOverlay;
import com.ArchitectsToolbox.gui.RadialMenuRegistry;
import com.ArchitectsToolbox.gui.RadialMenuScreen;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class ArchitectsToolboxClient {
    private static final ArchitectsToolboxClient INSTANCE = new ArchitectsToolboxClient();

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("architects_toolbox", "main")
    );
    private static final String KEY_MEASURE = "key.architects_toolbox.measure";
    public static final KeyMapping KEY_BINDING_MEASURE = new KeyMapping(
        KEY_MEASURE,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        KEY_CATEGORY
    );

    private long measureKeyPressStart = 0;
    private boolean radialMenuOpened = false;
    private static final long HOLD_THRESHOLD_MS = 200;

    private boolean leftArrowWasPressed = false;
    private boolean rightArrowWasPressed = false;
    private boolean upArrowWasPressed = false;
    private boolean downArrowWasPressed = false;
    private int lastPreviewRotation = 0;

    private ArchitectsToolboxClient() {}

    public static ArchitectsToolboxClient getInstance() {
        return INSTANCE;
    }

    public static void initClient() {
        // Register keybinding via Architectury
        KeyMappingRegistry.register(KEY_BINDING_MEASURE);

        // Register client tick event via Architectury
        ClientTickEvent.CLIENT_POST.register(instance -> onClientTick(Minecraft.getInstance()));

        // Register HUD render event via Architectury
        ClientGuiEvent.RENDER_HUD.register((guiGraphics, deltaTracker) -> onHudRender(guiGraphics, deltaTracker));

        // Register client-side commands via Architectury
        ClientCommandRegistrationEvent.EVENT.register(com.ArchitectsToolbox.command.ToolboxCommands::register);

        // Initialize radial menu actions
        RadialMenuRegistry.initialize();
    }

    public static void onClientTick(Minecraft client) {
        INSTANCE.handleInput(client);
    }

    public static void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        BlockCountOverlay.getInstance().render(guiGraphics, deltaTracker);
    }

    private void handleInput(Minecraft client) {
        updatePastePreview(client);
        handleRotationInput(client);

        if (KEY_BINDING_MEASURE.isDown()) {
            if (measureKeyPressStart == 0) {
                measureKeyPressStart = System.currentTimeMillis();
                radialMenuOpened = false;
            } else if (!radialMenuOpened &&
                       System.currentTimeMillis() - measureKeyPressStart > HOLD_THRESHOLD_MS &&
                       client.screen == null) {
                int keyCode = getKeyCode(KEY_BINDING_MEASURE);
                client.setScreen(new RadialMenuScreen(keyCode));
                radialMenuOpened = true;
            }
        } else {
            if (measureKeyPressStart != 0 && !radialMenuOpened) {
                ClipboardManager clipboard = ClipboardManager.getInstance();
                if (clipboard.isPastePreviewActive()) {
                    confirmPaste(client);
                } else {
                    addBlockToSelection(client);
                }
            }
            measureKeyPressStart = 0;
            radialMenuOpened = false;
        }
    }

    private void updatePastePreview(Minecraft client) {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        if (!clipboard.isPastePreviewActive()) return;

        if (client.level == null || client.getCameraEntity() == null) return;

        HitResult hitResult = client.getCameraEntity().pick(512.0, 1.0F, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            clipboard.setPreviewAnchorPos(blockHit.getBlockPos().relative(blockHit.getDirection()));
        }
    }

    private void handleRotationInput(Minecraft client) {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        SelectionManager selection = SelectionManager.getInstance();

        if (client.screen != null) {
            leftArrowWasPressed = false;
            rightArrowWasPressed = false;
            upArrowWasPressed = false;
            downArrowWasPressed = false;
            return;
        }

        if (!clipboard.isPastePreviewActive()) {
            boolean hollowLayerActive = selection.isLayerModeEnabled() && selection.hasSelection();
            boolean pasteLayerActive = clipboard.isLayerViewEnabled() && clipboard.hasLockedPlacements();

            if (hollowLayerActive || pasteLayerActive) {
                handleLayerInput(client, selection, clipboard, hollowLayerActive, pasteLayerActive);
            } else {
                upArrowWasPressed = false;
                downArrowWasPressed = false;
            }
        }

        if (clipboard.isPastePreviewActive()) {
            boolean leftPressed = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT);
            if (leftPressed && !leftArrowWasPressed) {
                clipboard.rotateCounterClockwise();
                showRotationMessage(client, clipboard.getPreviewRotation());
            }
            leftArrowWasPressed = leftPressed;

            boolean rightPressed = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT);
            if (rightPressed && !rightArrowWasPressed) {
                clipboard.rotateClockwise();
                showRotationMessage(client, clipboard.getPreviewRotation());
            }
            rightArrowWasPressed = rightPressed;

            int currentRotation = clipboard.getPreviewRotation();
            if (currentRotation != lastPreviewRotation) {
                lastPreviewRotation = currentRotation;
                com.ArchitectsToolbox.render.MeasurementRenderer.getInstance().invalidateGhostBlockCaches();
            }
        } else {
            leftArrowWasPressed = false;
            rightArrowWasPressed = false;
        }
    }

    private void handleLayerInput(Minecraft client, SelectionManager selection,
                                   ClipboardManager clipboard, boolean hollowActive, boolean pasteActive) {
        boolean upPressed = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_UP);
        if (upPressed && !upArrowWasPressed) {
            if (hollowActive) {
                selection.cycleLayerUp();
                com.ArchitectsToolbox.render.MeasurementRenderer.getInstance().invalidateHollowShapeCache();
            }
            if (pasteActive) {
                clipboard.cycleLayerUp();
            }
            showLayerMessage(client, selection, clipboard, hollowActive, pasteActive);
        }
        upArrowWasPressed = upPressed;

        boolean downPressed = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_DOWN);
        if (downPressed && !downArrowWasPressed) {
            if (hollowActive) {
                selection.cycleLayerDown();
                com.ArchitectsToolbox.render.MeasurementRenderer.getInstance().invalidateHollowShapeCache();
            }
            if (pasteActive) {
                clipboard.cycleLayerDown();
            }
            showLayerMessage(client, selection, clipboard, hollowActive, pasteActive);
        }
        downArrowWasPressed = downPressed;
    }

    private void showLayerMessage(Minecraft client, SelectionManager selection,
                                   ClipboardManager clipboard, boolean hollowActive, boolean pasteActive) {
        if (client.player == null) return;

        if (hollowActive && pasteActive) {
            int hLayer = selection.getCurrentLayer() + 1;
            int hTotal = selection.getLayerCount();
            int pLayer = clipboard.getCurrentViewLayer() + 1;
            int pTotal = clipboard.getLayerCount();
            client.player.displayClientMessage(Component.literal("Layer: " + hLayer + "/" + hTotal + " | Paste: " + pLayer + "/" + pTotal), true);
        } else if (hollowActive) {
            int layer = selection.getCurrentLayer() + 1;
            int total = selection.getLayerCount();
            client.player.displayClientMessage(Component.literal("Layer: " + layer + "/" + total + " (Y=" + selection.getCurrentLayerY() + ")"), true);
        } else if (pasteActive) {
            int layer = clipboard.getCurrentViewLayer() + 1;
            int total = clipboard.getLayerCount();
            client.player.displayClientMessage(Component.literal("Layer: " + layer + "/" + total), true);
        }
    }

    private void showRotationMessage(Minecraft client, int rotation) {
        if (client.player != null) {
            String degrees = switch (rotation) {
                case 0 -> "0\u00b0";
                case 1 -> "90\u00b0";
                case 2 -> "180\u00b0";
                case 3 -> "270\u00b0";
                default -> rotation * 90 + "\u00b0";
            };
            client.player.displayClientMessage(Component.literal("Rotation: " + degrees), true);
        }
    }

    private void confirmPaste(Minecraft client) {
        ClipboardManager clipboard = ClipboardManager.getInstance();

        if (clipboard.getPreviewAnchorPos() != null) {
            clipboard.lockCurrentPlacement();
            com.ArchitectsToolbox.render.MeasurementRenderer.getInstance().invalidateGhostBlockCaches();

            if (client.player != null) {
                client.player.displayClientMessage(Component.literal("Ghost blocks placed"), true);
            }
        }
    }

    private void addBlockToSelection(Minecraft client) {
        if (client.level == null || client.player == null || client.getCameraEntity() == null) return;

        HitResult hitResult = client.getCameraEntity().pick(512.0, 1.0F, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            SelectionManager.getInstance().addBlock(blockHit.getBlockPos());
        }
    }

    private int getKeyCode(KeyMapping keyMapping) {
        String translationKey = keyMapping.saveString();
        if (translationKey.startsWith("key.keyboard.")) {
            String keyName = translationKey.substring("key.keyboard.".length());
            return switch (keyName) {
                case "g" -> GLFW.GLFW_KEY_G;
                case "f" -> GLFW.GLFW_KEY_F;
                default -> GLFW.GLFW_KEY_G;
            };
        }
        return GLFW.GLFW_KEY_G;
    }
}
