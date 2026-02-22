# Architecture

## Multiloader Structure (Architectury)

```
Architects_Toolbox_Architectury/
  build.gradle          # Root: architectury-plugin 3.4, loom 1.11-SNAPSHOT, shadow 8.3.6
  gradle.properties     # Shared properties (versions, mod metadata)
  common/
    build.gradle        # architectury { common(...) }, depends on architectury API + fabric-loader (annotations only)
    src/main/java/      # ALL game logic, rendering, GUI, mixins
    src/main/resources/
      architects_toolbox.mixins.json    # Mixin config (client-only: WorldRendererMixin)
      assets/architects_toolbox/lang/   # Translation files
  fabric/
    build.gradle        # architectury { fabric() }, shadow bundles common, ModMenu dep
    src/main/java/      # ArchitectsToolboxFabricClient, ModMenuIntegration
    src/main/resources/
      fabric.mod.json   # Entry points, dependencies, mixin reference
  neoforge/
    build.gradle        # architectury { neoForge() }, shadow bundles common
    src/main/java/      # ArchitectsToolboxNeoForge
    src/main/resources/
      META-INF/neoforge.mods.toml  # Mod metadata, dependencies
```

## Architectury API Usage

This mod uses Architectury to avoid platform-specific code in the common module:

| Feature | Architectury API | Import |
|---------|-----------------|--------|
| Keybind registration | `KeyMappingRegistry.register()` | `dev.architectury.registry.client.keymappings` |
| Client tick event | `ClientTickEvent.CLIENT_POST.register()` | `dev.architectury.event.events.client` |
| HUD render event | `ClientGuiEvent.RENDER_HUD.register()` | `dev.architectury.event.events.client` |
| Config directory | `Platform.getConfigFolder()` | `dev.architectury.platform` |

**No ServiceLoader pattern needed** - Architectury's `Platform` class replaces the manual `IPlatformHelper`/`Services` pattern used in vanilla multiloader setups.

## Platform Entry Points

### Fabric (`ArchitectsToolboxFabricClient implements ClientModInitializer`)
```java
public void onInitializeClient() {
    ArchitectsToolbox.init();
    ArchitectsToolboxClient.initClient();
}
```

### NeoForge (`@Mod(ArchitectsToolbox.MOD_ID)`)
```java
public ArchitectsToolboxNeoForge(IEventBus modEventBus) {
    ArchitectsToolbox.init();
    if (FMLEnvironment.dist == Dist.CLIENT) {
        modEventBus.addListener(this::onClientSetup);  // -> ArchitectsToolboxClient.initClient()
    }
}
```

### ModMenu (Fabric only)
`ModMenuIntegration implements ModMenuApi` returns `ConfigScreen::new` as the config screen factory.

## Mixin

Single mixin in common module: `WorldRendererMixin` injects at `@At("TAIL")` of `LevelRenderer.renderLevel()` to call `MeasurementRenderer.getInstance().render(camera, positionMatrix)`.

The mixin config (`architects_toolbox.mixins.json`) is declared in common resources and referenced by both platform mod descriptors.

## Build Pipeline

Both platform modules use Shadow plugin to bundle common code:
```groovy
common(project(path: ':common', configuration: 'namedElements')) { transitive = false }
shadowBundle project(path: ':common', configuration: 'transformProduction{Fabric|NeoForge}')
```

Mojang official mappings are used (`loom.officialMojangMappings()`).
