package com.bipolar.ffishing.util;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import com.bipolar.ffishing.FallenFishingPlugin;
import com.bipolar.ffishing.config.FishingConfig;
import com.bipolar.ffishing.util.BiomeProbeCache.ProbeSnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class FishHelper {
	private static final Random RANDOM = new Random();
	private static final Map<String, Float> FISHES = new HashMap<>();
	private static final Map<String, FishSpeciesProfile> SPECIES = new HashMap<>();
	private static int minFishingTime = 100;
	private static int maxFishingTime = 600;
	private static boolean canRelease = true;

	static {
		registerDefaults();
	}

	public static Map<String, Float> createDefaultFishTable() {
		Map<String, Float> fishTable = new HashMap<>();
		for (FishSpeciesProfile profile : SPECIES.values()) {
			fishTable.put(profile.getItemIdForRarity("Common"), (float) Math.max(0.001D, profile.getHabitatWeight(FishingHabitat.PLAINS_FRESHWATER)));
		}
		return fishTable;
	}

	public static void setupFishes(FishingConfig config) {
		FISHES.clear();
		FISHES.putAll(config.fishTable);
		LoggerCompat.info(FallenFishingPlugin.LOGGER, "Fish table loaded with %s entries.", FISHES.size());

		minFishingTime = config.minFishingTime;
		maxFishingTime = config.maxFishingTime;
		canRelease = config.canRelease;
	}

	public static RolledCatch rollCatch(World world, Vector3i waterBlock) {
		FishingHabitat habitat = resolveHabitat(world, waterBlock);
		FishSpeciesProfile profile = rollSpeciesForHabitat(habitat);
		if (profile == null) {
			profile = SPECIES.get("Bluegill");
			habitat = FishingHabitat.PLAINS_FRESHWATER;
		}

		double habitatTotal = getHabitatTotalWeight(habitat);
		double speciesChance = habitatTotal <= 0.0D ? 1.0D : profile.getHabitatWeight(habitat) / habitatTotal;
		String rarity = profile.rollRarity(RANDOM.nextDouble());
		double rarityChance = Math.max(0.00001D, profile.getRarityProbability(rarity));
		double encounterChance = Math.max(0.000001D, speciesChance * rarityChance);
		FishCatchInfo catchInfo = FishCatchHelper.createCatchInfo(
				profile.getItemIdForRarity(rarity),
				rarity,
				encounterChance,
				habitat,
				profile.getRarityBandBias(rarity)
		);
		return new RolledCatch(catchInfo, profile.computeHookWindowTicks(rarity), habitat, profile.getSpeciesKey());
	}

	public static ItemStack createRandomFish() {
		String fishId = getRandomFish();
		ItemStack fishStack = ItemStack.EMPTY;
		if (fishId.isEmpty()) {
			return fishStack;
		}
		fishStack = InventoryHelper.createItem(fishId);
		if (fishStack == null) {
			return ItemStack.EMPTY;
		}
		return fishStack;
	}

	public static String getRandomFish() {
		if (FISHES.isEmpty()) return "";
		float totalWeight = 0.0F;
		for (Float w : FISHES.values()) totalWeight += w;
		float r = RANDOM.nextFloat() * totalWeight;
		for (Map.Entry<String, Float> entry : FISHES.entrySet()) {
			r -= entry.getValue();
			if (r <= 0.0F) return entry.getKey();
		}
		return FISHES.keySet().stream().findFirst().orElse("");
	}

	public static int getTimeUntilCatch() {
		return RANDOM.nextInt((maxFishingTime - minFishingTime) + 1) + minFishingTime;
	}

	public static boolean canReleaseFish() {
		return canRelease;
	}

	private static FishSpeciesProfile rollSpeciesForHabitat(FishingHabitat habitat) {
		double total = getHabitatTotalWeight(habitat);
		if (total <= 0.0D) {
			return SPECIES.get("Bluegill");
		}
		double remaining = RANDOM.nextDouble() * total;
		for (FishSpeciesProfile profile : SPECIES.values()) {
			remaining -= profile.getHabitatWeight(habitat);
			if (remaining <= 0.0D && profile.getHabitatWeight(habitat) > 0.0D) {
				return profile;
			}
		}
		return SPECIES.values().stream().findFirst().orElse(null);
	}

	private static double getHabitatTotalWeight(FishingHabitat habitat) {
		double total = 0.0D;
		for (FishSpeciesProfile profile : SPECIES.values()) {
			total += profile.getHabitatWeight(habitat);
		}
		return total;
	}

	public static FishingHabitat resolveHabitat(World world, Vector3i waterBlock) {
		if (world == null || waterBlock == null) {
			return FishingHabitat.PLAINS_FRESHWATER;
		}

		int depth = computeFluidDepth(world, waterBlock, 18);
		int spread = computeFluidSpread(world, waterBlock, 10);
		int y = waterBlock.y;

		BiomeProbeCache.probeAndCache(world, waterBlock.x, waterBlock.z, null);
		ProbeSnapshot probeSnapshot = BiomeProbeCache.getForBlock(world, waterBlock.x, waterBlock.z);
		FishingHabitat probedHabitat = BiomeProbeCache.habitatFromSnapshot(probeSnapshot, y, depth, spread);
		if (probedHabitat != null) {
			return probedHabitat;
		}

		KeywordCounts counts = scanKeywords(world, waterBlock, 6, 2);
		boolean saltwaterLike = counts.reef > 0 || counts.coast > 1 || spread >= 90;

		if (counts.volcanic > 0 || counts.lava > 0) return FishingHabitat.VOLCANIC;
		if (counts.frozen >= 3 || (counts.frozen >= 1 && y >= 100)) return FishingHabitat.FROZEN_WATERS;
		if (counts.ancient > 0) return FishingHabitat.ANCIENT_COAST;
		if (counts.swamp > 1) return FishingHabitat.SWAMP;
		if (counts.reef > 1) return FishingHabitat.TROPICAL_REEF;
		if (saltwaterLike && depth >= 12) return FishingHabitat.DEEP_OCEAN;
		if (saltwaterLike && depth >= 5) return FishingHabitat.OPEN_OCEAN;
		if (y >= 95 || (counts.frozen >= 1 && y >= 88)) return FishingHabitat.MOUNTAIN_COLD;
		return FishingHabitat.PLAINS_FRESHWATER;
	}

	private static KeywordCounts scanKeywords(World world, Vector3i center, int radius, int verticalRadius) {
		KeywordCounts counts = new KeywordCounts();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
					Vector3i sample = new Vector3i(center.x + dx, center.y + dy, center.z + dz);
					WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(sample.x, sample.z));
					if (chunk == null) continue;
					BlockType blockType = chunk.getBlockType(sample);
					if (blockType == null) continue;
					String id = blockType.getId();
					if (id == null) continue;
					String lower = id.toLowerCase(Locale.ROOT);
					if (lower.contains("swamp") || lower.contains("mangrove") || lower.contains("mud") || lower.contains("bog") || lower.contains("fen") || lower.contains("reed") || lower.contains("lily")) counts.swamp++;
					if (lower.contains("ice") || lower.contains("snow") || lower.contains("frost") || lower.contains("glacier")) counts.frozen++;
					if (lower.contains("lava") || lower.contains("basalt") || lower.contains("magma") || lower.contains("ash") || lower.contains("ember") || lower.contains("obsidian")) counts.volcanic++;
					if (lower.contains("lava")) counts.lava++;
					if (lower.contains("coral") || lower.contains("reef") || lower.contains("tropical") || lower.contains("anemone")) counts.reef++;
					if (lower.contains("sand") || lower.contains("beach") || lower.contains("coast") || lower.contains("shore") || lower.contains("kelp") || lower.contains("seaweed") || lower.contains("shell")) counts.coast++;
					if (lower.contains("fossil") || lower.contains("ancient") || lower.contains("bone")) counts.ancient++;
				}
			}
		}
		return counts;
	}

	private static int computeFluidDepth(World world, Vector3i waterBlock, int maxDepth) {
		int depth = 0;
		for (int i = 0; i < maxDepth; i++) {
			int y = waterBlock.y - i;
			WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(waterBlock.x, waterBlock.z));
			if (chunk == null) break;
			if (chunk.getFluidId(waterBlock.x, y, waterBlock.z) == 0) break;
			depth++;
		}
		return depth;
	}

	private static int computeFluidSpread(World world, Vector3i waterBlock, int radius) {
		int spread = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(waterBlock.x + dx, waterBlock.z + dz));
				if (chunk == null) continue;
				if (chunk.getFluidId(waterBlock.x + dx, waterBlock.y, waterBlock.z + dz) != 0) {
					spread++;
				}
			}
		}
		return spread;
	}

	private static void registerDefaults() {
		SPECIES.clear();
		register(FishSpeciesProfile.create("Bluegill", "Bluegill", 0.10D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 1.00D)
				.habitat(FishingHabitat.MOUNTAIN_COLD, 0.15D)
				.habitat(FishingHabitat.SWAMP, 0.10D)
				.rarityWeights(0.64D, 0.25D, 0.09D, 0.02D, 0.00D));
		register(FishSpeciesProfile.create("Catfish", "Catfish", 0.18D)
				.habitat(FishingHabitat.SWAMP, 1.00D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 0.55D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.05D)
				.rarityWeights(0.55D, 0.25D, 0.14D, 0.05D, 0.01D));
		register(FishSpeciesProfile.create("Minnow", "Minnow", 0.06D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 0.90D)
				.habitat(FishingHabitat.MOUNTAIN_COLD, 0.25D)
				.habitat(FishingHabitat.SWAMP, 0.08D)
				.rarityWeights(0.74D, 0.20D, 0.05D, 0.01D, 0.00D));
		register(FishSpeciesProfile.create("Trout_Rainbow", "Rainbow Trout", 0.20D)
				.habitat(FishingHabitat.MOUNTAIN_COLD, 0.95D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 0.40D)
				.habitat(FishingHabitat.FROZEN_WATERS, 0.25D)
				.rarityWeights(0.45D, 0.28D, 0.18D, 0.07D, 0.02D));
		register(FishSpeciesProfile.create("Salmon", "Salmon", 0.26D)
				.habitat(FishingHabitat.MOUNTAIN_COLD, 0.80D)
				.habitat(FishingHabitat.FROZEN_WATERS, 0.70D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 0.22D)
				.rarityWeights(0.30D, 0.28D, 0.22D, 0.14D, 0.06D));
		register(FishSpeciesProfile.create("Pike", "Pike", 0.30D)
				.habitat(FishingHabitat.MOUNTAIN_COLD, 0.55D)
				.habitat(FishingHabitat.FROZEN_WATERS, 0.45D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 0.18D)
				.rarityWeights(0.25D, 0.30D, 0.24D, 0.15D, 0.06D));
		register(FishSpeciesProfile.create("Frostgill", "Frostgill", 0.35D)
				.habitat(FishingHabitat.FROZEN_WATERS, 1.00D)
				.rarityWeights(0.20D, 0.32D, 0.25D, 0.16D, 0.07D));
		register(FishSpeciesProfile.create("Piranha", "Piranha", 0.42D)
				.habitat(FishingHabitat.SWAMP, 0.55D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 0.05D)
				.rarityWeights(0.18D, 0.26D, 0.30D, 0.18D, 0.08D));
		register(FishSpeciesProfile.create("Piranha_Black", "Black Piranha", 0.48D)
				.habitat(FishingHabitat.SWAMP, 0.36D)
				.rarityWeights(0.00D, 0.14D, 0.36D, 0.30D, 0.20D));
		register(FishSpeciesProfile.create("Snapjaw", "Snapjaw", 0.60D)
				.habitat(FishingHabitat.SWAMP, 0.22D)
				.habitat(FishingHabitat.DEEP_OCEAN, 0.05D)
				.rarityWeights(0.00D, 0.10D, 0.38D, 0.32D, 0.20D));
		register(FishSpeciesProfile.create("Crocodile", "Crocodile", 0.78D)
				.habitat(FishingHabitat.SWAMP, 0.16D)
				.habitat(FishingHabitat.PLAINS_FRESHWATER, 0.01D)
				.rarityWeights(0.00D, 0.00D, 0.00D, 0.72D, 0.28D));
		register(FishSpeciesProfile.create("Fen_Stalker", "Fen Stalker", 0.74D)
				.habitat(FishingHabitat.SWAMP, 0.12D)
				.habitat(FishingHabitat.ANCIENT_COAST, 0.04D)
				.rarityWeights(0.00D, 0.05D, 0.30D, 0.42D, 0.23D));

		register(FishSpeciesProfile.create("Clownfish", "Clownfish", 0.12D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.95D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.18D)
				.rarityWeights(0.50D, 0.28D, 0.16D, 0.05D, 0.01D));
		register(FishSpeciesProfile.create("Tang_Blue", "Blue Tang", 0.16D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.80D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.20D)
				.rarityWeights(0.42D, 0.28D, 0.18D, 0.09D, 0.03D));
		register(FishSpeciesProfile.create("Tang_Chevron", "Chevron Tang", 0.18D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.74D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.16D)
				.rarityWeights(0.40D, 0.28D, 0.20D, 0.09D, 0.03D));
		register(FishSpeciesProfile.create("Tang_Lemon_Peel", "Lemon Peel Tang", 0.18D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.72D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.14D)
				.rarityWeights(0.42D, 0.28D, 0.19D, 0.08D, 0.03D));
		register(FishSpeciesProfile.create("Tang_Sailfin", "Sailfin Tang", 0.22D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.62D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.18D)
				.rarityWeights(0.34D, 0.28D, 0.22D, 0.11D, 0.05D));
		register(FishSpeciesProfile.create("Pufferfish", "Pufferfish", 0.32D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.46D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.44D)
				.rarityWeights(0.25D, 0.26D, 0.24D, 0.16D, 0.09D));
		register(FishSpeciesProfile.create("Crab", "Crab", 0.18D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.55D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.30D)
				.habitat(FishingHabitat.SWAMP, 0.22D)
				.rarityWeights(0.48D, 0.24D, 0.16D, 0.08D, 0.04D));
		register(FishSpeciesProfile.create("Lobster", "Lobster", 0.24D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.38D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.42D)
				.rarityWeights(0.22D, 0.30D, 0.24D, 0.15D, 0.09D));
		register(FishSpeciesProfile.create("Eel_Moray", "Moray Eel", 0.58D)
				.habitat(FishingHabitat.DEEP_OCEAN, 0.22D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.15D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.08D)
				.rarityWeights(0.00D, 0.14D, 0.32D, 0.30D, 0.24D));

		register(FishSpeciesProfile.create("Jellyfish_Blue", "Blue Jellyfish", 0.20D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.18D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.28D)
				.habitat(FishingHabitat.FROZEN_WATERS, 0.08D)
				.rarityWeights(0.46D, 0.28D, 0.18D, 0.06D, 0.02D));
		register(FishSpeciesProfile.create("Jellyfish_Cyan", "Cyan Jellyfish", 0.20D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.18D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.26D)
				.rarityWeights(0.46D, 0.28D, 0.18D, 0.06D, 0.02D));
		register(FishSpeciesProfile.create("Jellyfish_Green", "Green Jellyfish", 0.20D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.16D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.24D)
				.rarityWeights(0.46D, 0.28D, 0.18D, 0.06D, 0.02D));
		register(FishSpeciesProfile.create("Jellyfish_Red", "Red Jellyfish", 0.22D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.14D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.24D)
				.habitat(FishingHabitat.VOLCANIC, 0.10D)
				.rarityWeights(0.40D, 0.30D, 0.18D, 0.08D, 0.04D));
		register(FishSpeciesProfile.create("Jellyfish_Yellow", "Yellow Jellyfish", 0.22D)
				.habitat(FishingHabitat.TROPICAL_REEF, 0.14D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.24D)
				.rarityWeights(0.40D, 0.30D, 0.18D, 0.08D, 0.04D));
		register(FishSpeciesProfile.create("Jellyfish_Man_Of_War", "Man Of War Jellyfish", 0.70D)
				.habitat(FishingHabitat.DEEP_OCEAN, 0.08D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.04D)
				.rarityWeights(0.00D, 0.00D, 0.08D, 0.40D, 0.52D));
		register(FishSpeciesProfile.create("Shark_Hammerhead", "Hammerhead Shark", 0.82D)
				.habitat(FishingHabitat.DEEP_OCEAN, 0.10D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.03D)
				.rarityWeights(0.00D, 0.00D, 0.00D, 0.68D, 0.32D));
		register(FishSpeciesProfile.create("Whale_Humpback", "Humpback Whale", 0.95D)
				.habitat(FishingHabitat.DEEP_OCEAN, 0.04D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.01D)
				.rarityWeights(0.00D, 0.00D, 0.00D, 0.12D, 0.88D));
		register(FishSpeciesProfile.create("Shellfish_Lava", "Lava Shellfish", 0.54D)
				.habitat(FishingHabitat.VOLCANIC, 0.22D)
				.rarityWeights(0.00D, 0.08D, 0.30D, 0.34D, 0.28D));
		register(FishSpeciesProfile.create("Trilobite", "Trilobite", 0.30D)
				.habitat(FishingHabitat.ANCIENT_COAST, 0.16D)
				.habitat(FishingHabitat.OPEN_OCEAN, 0.04D)
				.rarityWeights(0.08D, 0.22D, 0.30D, 0.24D, 0.16D));
		register(FishSpeciesProfile.create("Trilobite_Black", "Black Trilobite", 0.48D)
				.habitat(FishingHabitat.ANCIENT_COAST, 0.08D)
				.rarityWeights(0.00D, 0.08D, 0.26D, 0.34D, 0.32D));
	}

	private static void register(FishSpeciesProfile profile) {
		SPECIES.put(profile.getSpeciesKey(), profile);
	}

	private static final class KeywordCounts {
		int swamp;
		int frozen;
		int volcanic;
		int lava;
		int reef;
		int coast;
		int ancient;
	}

	public record RolledCatch(FishCatchInfo catchInfo, int hookWindowTicks, FishingHabitat habitat, String speciesKey) {
	}
}
