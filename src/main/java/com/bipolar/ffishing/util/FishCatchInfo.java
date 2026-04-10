package com.bipolar.ffishing.util;

public class FishCatchInfo {
	private final String itemId;
	private final String speciesName;
	private final String rarityName;
	private final double lengthCm;
	private final double weightKg;
	private final double score;
	private final double encounterChance;
	private final FishingHabitat habitat;

	public FishCatchInfo(String itemId, String speciesName, String rarityName, double lengthCm, double weightKg, double score) {
		this(itemId, speciesName, rarityName, lengthCm, weightKg, score, -1.0D, FishingHabitat.PLAINS_FRESHWATER);
	}

	public FishCatchInfo(String itemId, String speciesName, String rarityName, double lengthCm, double weightKg, double score,
	                    double encounterChance, FishingHabitat habitat) {
		this.itemId = itemId;
		this.speciesName = speciesName;
		this.rarityName = rarityName;
		this.lengthCm = lengthCm;
		this.weightKg = weightKg;
		this.score = score;
		this.encounterChance = encounterChance;
		this.habitat = habitat == null ? FishingHabitat.PLAINS_FRESHWATER : habitat;
	}

	public String getItemId() {
		return itemId;
	}

	public String getSpeciesName() {
		return speciesName;
	}

	public String getRarityName() {
		return rarityName;
	}

	public double getLengthCm() {
		return lengthCm;
	}

	public double getWeightKg() {
		return weightKg;
	}

	public double getScore() {
		return score;
	}

	public double getEncounterChance() {
		return encounterChance;
	}

	public FishingHabitat getHabitat() {
		return habitat;
	}
	
	public String getFormattedLengthCm() {
		return FishCatchHelper.formatLengthCm(this.lengthCm);
	}
	
	public String getFormattedWeightKg() {
		return FishCatchHelper.formatWeightKg(this.weightKg);
	}
	
	public String getCatchSubtitle() {
		return this.rarityName + " • " + getFormattedLengthCm() + " • " + getFormattedWeightKg();
	}
}
