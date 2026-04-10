package com.bipolar.ffishing.util;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class FishSpeciesProfile {
    public static final String[] RARITIES = {"Common", "Uncommon", "Rare", "Epic", "Legendary"};

    private final String speciesKey;
    private final String displayName;
    private final EnumMap<FishingHabitat, Double> habitatWeights = new EnumMap<>(FishingHabitat.class);
    private final double[] rarityWeights = new double[RARITIES.length];
    private final double baseDifficulty;

    private FishSpeciesProfile(String speciesKey, String displayName, double baseDifficulty) {
        this.speciesKey = speciesKey;
        this.displayName = displayName;
        this.baseDifficulty = baseDifficulty;
    }

    public static FishSpeciesProfile create(String speciesKey, String displayName, double baseDifficulty) {
        return new FishSpeciesProfile(speciesKey, displayName, baseDifficulty);
    }

    public FishSpeciesProfile habitat(FishingHabitat habitat, double weight) {
        if (weight > 0.0D) {
            habitatWeights.put(habitat, weight);
        }
        return this;
    }

    public FishSpeciesProfile rarityWeights(double common, double uncommon, double rare, double epic, double legendary) {
        rarityWeights[0] = Math.max(0.0D, common);
        rarityWeights[1] = Math.max(0.0D, uncommon);
        rarityWeights[2] = Math.max(0.0D, rare);
        rarityWeights[3] = Math.max(0.0D, epic);
        rarityWeights[4] = Math.max(0.0D, legendary);
        return this;
    }

    public String getSpeciesKey() {
        return speciesKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getBaseDifficulty() {
        return baseDifficulty;
    }

    public double getHabitatWeight(FishingHabitat habitat) {
        return habitatWeights.getOrDefault(habitat, 0.0D);
    }

    public Map<FishingHabitat, Double> getHabitatWeights() {
        return habitatWeights;
    }

    public double getRarityWeight(String rarityName) {
        int index = rarityIndex(rarityName);
        return index >= 0 ? rarityWeights[index] : 0.0D;
    }

    public double getRarityTotalWeight() {
        double total = 0.0D;
        for (double weight : rarityWeights) total += weight;
        return total;
    }

    public boolean supportsRarity(String rarityName) {
        return getRarityWeight(rarityName) > 0.0D;
    }

    public double getRarityProbability(String rarityName) {
        double total = getRarityTotalWeight();
        if (total <= 0.0D) return 0.0D;
        return getRarityWeight(rarityName) / total;
    }

    public String rollRarity(double roll) {
        double total = getRarityTotalWeight();
        if (total <= 0.0D) {
            return "Common";
        }
        double remaining = roll * total;
        for (int i = 0; i < rarityWeights.length; i++) {
            remaining -= rarityWeights[i];
            if (remaining <= 0.0D && rarityWeights[i] > 0.0D) {
                return RARITIES[i];
            }
        }
        for (int i = rarityWeights.length - 1; i >= 0; i--) {
            if (rarityWeights[i] > 0.0D) {
                return RARITIES[i];
            }
        }
        return "Common";
    }

    public double getRarityBandBias(String rarityName) {
        return switch (normalizeRarity(rarityName)) {
            case "legendary" -> 0.96D;
            case "epic" -> 0.84D;
            case "rare" -> 0.68D;
            case "uncommon" -> 0.48D;
            default -> 0.28D;
        };
    }

    public int computeHookWindowTicks(String rarityName) {
        double rarityMultiplier = switch (normalizeRarity(rarityName)) {
            case "legendary" -> 0.28D;
            case "epic" -> 0.42D;
            case "rare" -> 0.60D;
            case "uncommon" -> 0.82D;
            default -> 1.0D;
        };
        int ticks = (int) Math.round(110.0D * rarityMultiplier * Math.max(0.18D, 1.0D - (baseDifficulty * 0.35D)));
        return Math.max(12, Math.min(110, ticks));
    }

    public String getItemIdForRarity(String rarityName) {
        return "FFishing_" + speciesKey + "_" + capitalize(rarityName) + "_Item";
    }

    public static int rarityIndex(String rarityName) {
        String normalized = normalizeRarity(rarityName);
        return switch (normalized) {
            case "common" -> 0;
            case "uncommon" -> 1;
            case "rare" -> 2;
            case "epic" -> 3;
            case "legendary" -> 4;
            default -> -1;
        };
    }

    public static String capitalize(String value) {
        if (value == null || value.isBlank()) return "Common";
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String normalizeRarity(String rarityName) {
        return rarityName == null ? "common" : rarityName.toLowerCase(Locale.ROOT);
    }
}
