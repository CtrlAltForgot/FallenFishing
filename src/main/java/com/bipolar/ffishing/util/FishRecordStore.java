package com.bipolar.ffishing.util;

import com.bipolar.ffishing.FallenFishingPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FishRecordStore {
	private static final String FILE_NAME = "ffishing_records.tsv";
	private static final Map<String, GlobalFishRecord> GLOBAL_RECORDS = new HashMap<>();
	private static boolean loaded = false;

	private FishRecordStore() {
	}

	public static synchronized RecordUpdate updateGlobalRecord(FishCatchInfo catchInfo, String username) {
		ensureLoaded();
		String recordKey = normalizeSpeciesKey(catchInfo.getSpeciesName());
		GlobalFishRecord current = GLOBAL_RECORDS.get(recordKey);
		boolean isNewRecord = current == null || catchInfo.getScore() > current.score + 0.000001D;
		if (!isNewRecord) {
			return new RecordUpdate(false, current);
		}

		GlobalFishRecord updated = new GlobalFishRecord(
				recordKey,
				catchInfo.getItemId(),
				catchInfo.getSpeciesName(),
				username,
				catchInfo.getLengthCm(),
				catchInfo.getWeightKg(),
				catchInfo.getRarityName(),
				catchInfo.getScore()
		);
		GLOBAL_RECORDS.put(recordKey, updated);
		save();
		return new RecordUpdate(true, updated);
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = getRecordFilePath();
		if (!Files.exists(path)) {
			return;
		}

		try {
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				if (line.isBlank() || line.startsWith("#")) continue;
				String[] parts = line.split("\t");
				if (parts.length < 7) continue;
				try {
					GlobalFishRecord record;
					if (parts.length >= 8) {
						record = new GlobalFishRecord(
								parts[0],
								parts[1],
								parts[2],
								parts[3],
								Double.parseDouble(parts[4]),
								Double.parseDouble(parts[5]),
								parts[6],
								Double.parseDouble(parts[7])
						);
					} else {
						String speciesName = parts[1];
						record = new GlobalFishRecord(
								normalizeSpeciesKey(speciesName),
								parts[0],
								speciesName,
								parts[2],
								Double.parseDouble(parts[3]),
								Double.parseDouble(parts[4]),
								parts[5],
								Double.parseDouble(parts[6])
						);
					}
					GLOBAL_RECORDS.put(record.recordKey, record);
				} catch (Exception ignored) {
				}
			}
		} catch (Exception exception) {
			FallenFishingPlugin.LOGGER.atWarning().withCause(exception).log("Failed to load FFishing records file");
		}
	}

	private static void save() {
		Path path = getRecordFilePath();
		try {
			Files.createDirectories(path.getParent());
			List<String> lines = new ArrayList<>();
			lines.add("# recordKey\titemId\tspeciesName\tusername\tlengthCm\tweightKg\trarity\tscore");
			for (GlobalFishRecord record : GLOBAL_RECORDS.values()) {
				lines.add(String.join("\t",
						record.recordKey,
						record.itemId,
						record.speciesName,
						record.username,
						String.format(Locale.US, "%.2f", record.lengthCm),
						String.format(Locale.US, "%.2f", record.weightKg),
						record.rarity,
						String.format(Locale.US, "%.6f", record.score)
				));
			}
			Files.write(path, lines, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			FallenFishingPlugin.LOGGER.atWarning().withCause(exception).log("Failed to save FFishing records file");
		}
	}

	private static Path getRecordFilePath() {
		Path dataDirectory = FallenFishingPlugin.get().getDataDirectory();
		return dataDirectory.resolve(FILE_NAME);
	}

	private static String normalizeSpeciesKey(String speciesName) {
		return speciesName == null ? "unknown_fish" : speciesName.toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	public record GlobalFishRecord(String recordKey, String itemId, String speciesName, String username, double lengthCm, double weightKg,
	                              String rarity, double score) {
	}

	public record RecordUpdate(boolean isNewRecord, GlobalFishRecord record) {
	}
}
