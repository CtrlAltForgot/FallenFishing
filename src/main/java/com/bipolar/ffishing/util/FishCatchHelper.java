package com.bipolar.ffishing.util;

import org.bson.BsonDouble;
import org.bson.BsonString;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class FishCatchHelper {
    private static final Random RANDOM = new Random();
    private static final String METADATA_PREFIX = "FFishing_";
    private static final Map<String, SizeProfile> SIZE_PROFILES = new HashMap<>();
    private static final Map<String, String> DISPLAY_NAMES = new HashMap<>();

    static {
        register("Bluegill", 10.0, 35.0, 0.08, 1.2, "Bluegill");
        register("Catfish", 25.0, 120.0, 0.6, 25.0, "Catfish");
        register("Minnow", 3.0, 12.0, 0.01, 0.08, "Minnow");
        register("Tang_Blue", 10.0, 30.0, 0.08, 0.8, "Blue Tang");
        register("Tang_Chevron", 10.0, 28.0, 0.08, 0.75, "Chevron Tang");
        register("Tang_Lemon_Peel", 8.0, 22.0, 0.06, 0.55, "Lemon Peel Tang");
        register("Tang_Sailfin", 12.0, 40.0, 0.1, 1.5, "Sailfin Tang");
        register("Clownfish", 5.0, 16.0, 0.02, 0.25, "Clownfish");
        register("Pufferfish", 7.0, 30.0, 0.05, 2.0, "Pufferfish");
        register("Trout_Rainbow", 15.0, 90.0, 0.2, 12.0, "Rainbow Trout");
        register("Salmon", 25.0, 150.0, 1.0, 32.0, "Salmon");
        register("Jellyfish_Blue", 10.0, 45.0, 0.05, 2.5, "Blue Jellyfish");
        register("Jellyfish_Cyan", 10.0, 45.0, 0.05, 2.5, "Cyan Jellyfish");
        register("Jellyfish_Green", 10.0, 45.0, 0.05, 2.5, "Green Jellyfish");
        register("Jellyfish_Red", 10.0, 45.0, 0.05, 2.5, "Red Jellyfish");
        register("Jellyfish_Yellow", 10.0, 45.0, 0.05, 2.5, "Yellow Jellyfish");
        register("Jellyfish_Man_Of_War", 15.0, 60.0, 0.15, 5.0, "Man Of War Jellyfish");
        register("Crab", 8.0, 40.0, 0.1, 3.0, "Crab");
        register("Crocodile", 90.0, 420.0, 8.0, 320.0, "Crocodile");
        register("Eel_Moray", 40.0, 180.0, 0.5, 20.0, "Moray Eel");
        register("Fen_Stalker", 55.0, 240.0, 2.2, 85.0, "Fen Stalker");
        register("Frostgill", 20.0, 70.0, 0.25, 6.0, "Frostgill");
        register("Lobster", 18.0, 60.0, 0.3, 5.5, "Lobster");
        register("Pike", 25.0, 140.0, 0.7, 25.0, "Pike");
        register("Piranha_Black", 10.0, 35.0, 0.1, 2.5, "Black Piranha");
        register("Piranha", 10.0, 35.0, 0.1, 2.5, "Piranha");
        register("Shark_Hammerhead", 80.0, 350.0, 10.0, 180.0, "Hammerhead Shark");
        register("Shellfish_Lava", 12.0, 40.0, 0.15, 3.2, "Lava Shellfish");
        register("Snapjaw", 20.0, 100.0, 0.4, 10.0, "Snapjaw");
        register("Trilobite_Black", 8.0, 24.0, 0.04, 0.6, "Black Trilobite");
        register("Trilobite", 8.0, 24.0, 0.04, 0.6, "Trilobite");
        register("Whale_Humpback", 350.0, 1600.0, 600.0, 30000.0, "Humpback Whale");
    }

    private FishCatchHelper() {}

    public static FishCatchInfo createCatchInfo(String itemId) {
        return createCatchInfo(itemId, "Common", 0.05D, FishingHabitat.PLAINS_FRESHWATER, 0.40D);
    }

    public static FishCatchInfo createCatchInfo(String itemId, String rarityName, double encounterChance, FishingHabitat habitat, double bandBias) {
        SizeProfile profile = SIZE_PROFILES.getOrDefault(itemId, SizeProfile.DEFAULT);
        String rarity = FishSpeciesProfile.capitalize(rarityName);
        double rarityBias = defaultBandBias(rarity);
        double baseBias = clamp((rarityBias * 0.78D) + (bandBias * 0.22D) + randomRange(-0.05D, 0.05D), 0.0D, 1.0D);
        double lengthT = clamp(baseBias + randomRange(-0.06D, 0.06D), 0.0D, 1.0D);
        double weightT = clamp(Math.pow(baseBias, 1.08D) + randomRange(-0.05D, 0.05D), 0.0D, 1.0D);
        double lengthCm = round2(lerp(profile.minLengthCm, profile.maxLengthCm, lengthT));
        double weightKg = round2(lerp(profile.minWeightKg, profile.maxWeightKg, weightT));
        String finalItemId = getRaritySpecificItemId(itemId, rarity);
        String speciesName = toDisplayName(finalItemId);
        double score = computeHiddenScore(finalItemId, rarity, lengthCm, weightKg, encounterChance);
        return new FishCatchInfo(finalItemId, speciesName, rarity, lengthCm, weightKg, score, encounterChance, habitat);
    }

    public static String getRaritySpecificItemId(String itemId, String rarityName) {
        String speciesKey = extractSpeciesKey(itemId);
        String rarityKey = rarityName == null || rarityName.isBlank() ? "Common" : FishSpeciesProfile.capitalize(rarityName);
        return "FFishing_" + speciesKey + "_" + rarityKey + "_Item";
    }

    public static double computeHiddenScore(String itemId, String rarityName, double lengthCm, double weightKg) {
        return computeHiddenScore(itemId, rarityName, lengthCm, weightKg, defaultEncounterChance(itemId, rarityName));
    }

    public static double computeHiddenScore(String itemId, String rarityName, double lengthCm, double weightKg, double encounterChance) {
        SizeProfile profile = SIZE_PROFILES.getOrDefault(itemId, SizeProfile.DEFAULT);
        double lengthScore = normalize(lengthCm, profile.minLengthCm, profile.maxLengthCm);
        double weightScore = normalize(weightKg, profile.minWeightKg, profile.maxWeightKg);
        double rarityScore = getRarityScore(rarityName);
        double sizeScore = (weightScore * 0.60D) + (lengthScore * 0.40D);
        double chance = encounterChance > 0.0D ? encounterChance : defaultEncounterChance(itemId, rarityName);
        double rarityOddsScore = chanceToRarityScore(chance);
        double combined = ((rarityScore * 0.55D) + (sizeScore * 0.45D)) * 0.90D;
        combined += rarityOddsScore * 0.10D;
        return round6(clamp(combined, 0.0D, 1.0D));
    }

    public static String toDisplayName(String itemId) {
        String mapped = DISPLAY_NAMES.get(itemId);
        if (mapped != null) return mapped;
        String speciesKey = extractSpeciesKey(itemId);
        return DISPLAY_NAMES.getOrDefault("FFishing_" + speciesKey + "_Common_Item", speciesKey.replace('_', ' '));
    }

    public static String formatLengthCm(double lengthCm) { return String.format(Locale.US, "%.2f cm", lengthCm); }
    public static String formatWeightKg(double weightKg) { return String.format(Locale.US, "%.2f kg", weightKg); }
    public static String getMetadataKeyLengthCm() { return METADATA_PREFIX + "LengthCm"; }
    public static String getMetadataKeyWeightKg() { return METADATA_PREFIX + "WeightKg"; }
    public static String getMetadataKeyRarity() { return METADATA_PREFIX + "Rarity"; }
    public static String getMetadataKeySpeciesName() { return METADATA_PREFIX + "SpeciesName"; }
    public static org.bson.BsonValue lengthValue(double lengthCm) { return new BsonDouble(lengthCm); }
    public static org.bson.BsonValue weightValue(double weightKg) { return new BsonDouble(weightKg); }
    public static org.bson.BsonValue rarityValue(String rarity) { return new BsonString(rarity); }
    public static org.bson.BsonValue speciesValue(String speciesName) { return new BsonString(speciesName); }

    public static double defaultEncounterChance(String itemId, String rarityName) {
        double speciesBias = switch (extractSpeciesKey(itemId)) {
            case "Whale_Humpback" -> 0.00025D;
            case "Shark_Hammerhead", "Crocodile" -> 0.0010D;
            case "Jellyfish_Man_Of_War", "Snapjaw", "Shellfish_Lava", "Trilobite_Black", "Fen_Stalker" -> 0.0030D;
            case "Eel_Moray", "Piranha_Black", "Piranha", "Pike", "Frostgill" -> 0.0100D;
            case "Trilobite", "Lobster", "Crab", "Pufferfish" -> 0.0250D;
            default -> 0.0800D;
        };
        double rarityFactor = switch (rarityName == null ? "common" : rarityName.toLowerCase(Locale.ROOT)) {
            case "legendary" -> 0.04D;
            case "epic" -> 0.10D;
            case "rare" -> 0.22D;
            case "uncommon" -> 0.40D;
            default -> 1.0D;
        };
        return speciesBias * rarityFactor;
    }

    private static double getRarityScore(String rarityName) {
        if (rarityName == null) return 0.10D;
        return switch (rarityName.toLowerCase(Locale.ROOT)) {
            case "legendary" -> 1.00D;
            case "epic" -> 0.82D;
            case "rare" -> 0.58D;
            case "uncommon" -> 0.32D;
            default -> 0.10D;
        };
    }

    private static double chanceToRarityScore(double encounterChance) {
        double safeChance = Math.max(0.000001D, Math.min(1.0D, encounterChance));
        double score = -Math.log10(safeChance) / 6.0D;
        return clamp(score, 0.0D, 1.0D);
    }

    private static double defaultBandBias(String rarityName) {
        return switch (rarityName == null ? "common" : rarityName.toLowerCase(Locale.ROOT)) {
            case "legendary" -> 0.96D;
            case "epic" -> 0.84D;
            case "rare" -> 0.66D;
            case "uncommon" -> 0.46D;
            default -> 0.24D;
        };
    }

    private static String extractSpeciesKey(String itemId) {
        String name = itemId == null ? "" : itemId;
        if (name.startsWith("FFishing_")) name = name.substring("FFishing_".length());
        if (name.startsWith("Fish_")) name = name.substring("Fish_".length());
        if (name.endsWith("_Item")) name = name.substring(0, name.length() - "_Item".length());
        for (String rarity : new String[]{"_Common", "_Uncommon", "_Rare", "_Epic", "_Legendary"}) {
            if (name.endsWith(rarity)) {
                name = name.substring(0, name.length() - rarity.length());
                break;
            }
        }
        return name;
    }

    private static double randomRange(double min, double max) { return min + (RANDOM.nextDouble() * (max - min)); }
    private static double lerp(double min, double max, double t) { return min + ((max - min) * t); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static double normalize(double value, double min, double max) { if (max <= min) return 0.0D; return clamp((value - min) / (max - min), 0.0D, 1.0D); }
    private static double round2(double value) { return Math.round(value * 100.0D) / 100.0D; }
    private static double round6(double value) { return Math.round(value * 1_000_000.0D) / 1_000_000.0D; }

    private static void register(String speciesKey, double minLengthCm, double maxLengthCm, double minWeightKg, double maxWeightKg, String displayName) {
        SizeProfile profile = new SizeProfile(minLengthCm, maxLengthCm, minWeightKg, maxWeightKg);
        SIZE_PROFILES.put("Fish_" + speciesKey + "_Item", profile);
        DISPLAY_NAMES.put("Fish_" + speciesKey + "_Item", displayName);
        SIZE_PROFILES.put("FFishing_" + speciesKey + "_Item", profile);
        DISPLAY_NAMES.put("FFishing_" + speciesKey + "_Item", displayName);
        for (String rarity : new String[]{"Common", "Uncommon", "Rare", "Epic", "Legendary"}) {
            String id = "FFishing_" + speciesKey + "_" + rarity + "_Item";
            SIZE_PROFILES.put(id, profile);
            DISPLAY_NAMES.put(id, displayName);
        }
    }

    private record SizeProfile(double minLengthCm, double maxLengthCm, double minWeightKg, double maxWeightKg) {
        private static final SizeProfile DEFAULT = new SizeProfile(10.0D, 80.0D, 0.10D, 8.0D);
    }
}
