package com.bipolar.ffishing;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.logger.HytaleLogger;
import com.bipolar.ffishing.command.FishCommand;
import com.bipolar.ffishing.component.BobberComponent;
import com.bipolar.ffishing.component.BoundBobberComponent;
import com.bipolar.ffishing.config.FishingConfig;
import com.bipolar.ffishing.interaction.FFishingOpenProcessingBenchInteraction;
import com.bipolar.ffishing.interaction.FishingInteraction;
import com.bipolar.ffishing.interaction.SpawnFishInteraction;
import com.bipolar.ffishing.systems.BobberDespawnSystem;
import com.bipolar.ffishing.systems.BobberSystem;
import com.bipolar.ffishing.util.FishHelper;

public class FallenFishingPlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static FallenFishingPlugin instance;

    private ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, BobberComponent> bobberComponent;
    private ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, BoundBobberComponent> boundBobberComponent;
    private final Config<FishingConfig> config;

    public FallenFishingPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("Initializing FallenFishing Plugin");
        this.config = withConfig("FFishingConfig", FishingConfig.CODEC);
    }

    public static FallenFishingPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Bobber component");
        ComponentRegistryProxy registry = getEntityStoreRegistry();
        this.bobberComponent = registry.registerComponent(BobberComponent.class, BobberComponent::new);
        this.boundBobberComponent = registry.registerComponent(BoundBobberComponent.class, "FFishing_BoundBobber", BoundBobberComponent.CODEC);

        LOGGER.atInfo().log("Registering Fishing Interaction");
        getCodecRegistry(Interaction.CODEC).register("FFishingFish", FishingInteraction.class, FishingInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("FFishing_Spawn_Fish", SpawnFishInteraction.class, SpawnFishInteraction.CODEC);

        LOGGER.atInfo().log("Registering default processing bench interaction for custom processing benches");
        FFishingOpenProcessingBenchInteraction defaultProcessingInteraction =
                new FFishingOpenProcessingBenchInteraction("*FFishing_Processing_Default");
        RootInteraction defaultProcessingRoot =
                new RootInteraction("*FFishing_Processing_Default_Root", defaultProcessingInteraction.getId());
        AssetRegistry.getAssetStore(Interaction.class)
                .loadAssets("FFishing:FallenFishing", java.util.List.of(defaultProcessingInteraction));
        AssetRegistry.getAssetStore(RootInteraction.class)
                .loadAssets("FFishing:FallenFishing", java.util.List.of(defaultProcessingRoot));
        Bench.registerRootInteraction(BenchType.Processing, defaultProcessingRoot);

        LOGGER.atInfo().log("Registering Bobber Systems");
        registry.registerSystem(new BobberSystem());
        registry.registerSystem(new BobberDespawnSystem());

        LOGGER.atInfo().log("Registering Commands (no extra mod-side permission requirement)");
        CommandRegistry commandRegistry = getCommandRegistry();
        commandRegistry.registerCommand(new FishCommand("fish", "Open your fishing logbook and leaderboard"));
    }

    @Override
    protected void start() {
        super.start();
        this.config.save();
        FishingConfig cfg = this.config.get();
        FishHelper.setupFishes(cfg);
    }

    public ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, BobberComponent> getBobberComponent() {
        return bobberComponent;
    }

    public ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, BoundBobberComponent> getBoundBobberComponent() {
        return boundBobberComponent;
    }
}
