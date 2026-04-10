package com.bipolar.ffishing.util;

import java.util.Locale;

public final class FishDurabilityHelper {
	private FishDurabilityHelper() {
	}

	public static int getDurabilityLoss(FishCatchInfo catchInfo) {
		if (catchInfo == null) return 1;
		String rarity = catchInfo.getRarityName();
		if (rarity == null) return 1;
		return switch (rarity.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> 2;
			case "rare" -> 3;
			case "epic" -> 4;
			case "legendary" -> 5;
			default -> 1;
		};
	}
}
