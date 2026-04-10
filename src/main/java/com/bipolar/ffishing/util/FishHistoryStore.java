package com.bipolar.ffishing.util;

import com.bipolar.ffishing.FallenFishingPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class FishHistoryStore {
    private static final String FILE_NAME = "ffishing_catches.tsv";
    private static final int MAX_HISTORY_ENTRIES = 50;
    private static final int MAX_LEADERBOARD_ENTRIES = 50;
    private static final String HEADER = "timestampMs\tplayer\titemId\tspecies\trarity\tlengthCm\tweightKg\tscore\tencounterChance\n";

    private FishHistoryStore() {}

    public static synchronized void appendCatch(String playerName, FishCatchInfo info) {
        if (playerName == null || playerName.isBlank() || info == null) return;
        try {
            Path file = getFile();
            ensureFile(file);
            String line = System.currentTimeMillis() + "\t"
                    + safe(playerName) + "\t"
                    + safe(info.getItemId()) + "\t"
                    + safe(info.getSpeciesName()) + "\t"
                    + safe(info.getRarityName()) + "\t"
                    + info.getLengthCm() + "\t"
                    + info.getWeightKg() + "\t"
                    + info.getScore() + "\t"
                    + info.getEncounterChance() + "\n";
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            FallenFishingPlugin.LOGGER.atWarning().withCause(e).log("Failed writing FFishing catch history");
        }
    }

    public static synchronized List<Entry> getPlayerHistory(String playerName) {
        return loadEntries().stream()
                .filter(e -> e.playerName.equalsIgnoreCase(playerName))
                .sorted(Comparator.comparingLong(Entry::getTimestampMs).reversed())
                .limit(MAX_HISTORY_ENTRIES)
                .collect(Collectors.toList());
    }

    public static synchronized List<Entry> getLeaderboard() {
        return loadEntries().stream()
                .sorted(Comparator.comparingDouble(Entry::getEffectiveScore).reversed()
                        .thenComparing(Comparator.comparingDouble(Entry::getWeightKg).reversed())
                        .thenComparing(Comparator.comparingDouble(Entry::getLengthCm).reversed())
                        .thenComparing(Comparator.comparingLong(Entry::getTimestampMs).reversed()))
                .limit(MAX_LEADERBOARD_ENTRIES)
                .collect(Collectors.toList());
    }


	public static synchronized List<SpeciesRecordEntry> getSpeciesRecords() {
		Map<String, SpeciesRecordAccumulator> accumulators = new LinkedHashMap<>();
		for (SpeciesDefinition definition : KNOWN_SPECIES) {
			accumulators.put(definition.recordKey, new SpeciesRecordAccumulator(definition));
		}

		for (Entry entry : loadEntries()) {
			String key = normalizeSpeciesKey(entry.getSpeciesName());
			SpeciesRecordAccumulator accumulator = accumulators.get(key);
			if (accumulator == null) {
				SpeciesDefinition dynamicDefinition = new SpeciesDefinition(key, entry.getSpeciesName());
				accumulator = new SpeciesRecordAccumulator(dynamicDefinition);
				accumulators.put(key, accumulator);
			}
			accumulator.accept(entry);
		}

		return accumulators.values().stream()
				.map(SpeciesRecordAccumulator::toEntry)
				.collect(Collectors.toList());
	}

    private static List<Entry> loadEntries() {
        List<Entry> entries = new ArrayList<>();
        try {
            Path file = getFile();
            ensureFile(file);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank() || line.startsWith("timestampMs\t")) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 8) continue;
                try {
                    double storedScore = Double.parseDouble(parts[7]);
                    double encounterChance = parts.length >= 9 ? Double.parseDouble(parts[8]) : -1.0D;
                    entries.add(new Entry(
                            Long.parseLong(parts[0]),
                            parts[1],
                            parts[2],
                            parts[3],
                            parts[4],
                            Double.parseDouble(parts[5]),
                            Double.parseDouble(parts[6]),
                            storedScore,
                            encounterChance
                    ));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            FallenFishingPlugin.LOGGER.atWarning().withCause(e).log("Failed reading FFishing catch history");
        }
        return entries;
    }

    private static Path getFile() throws IOException {
        Path dataDir = FallenFishingPlugin.get() != null ? FallenFishingPlugin.get().getDataDirectory() : Path.of(".");
        Files.createDirectories(dataDir);
        return dataDir.resolve(FILE_NAME);
    }

    private static void ensureFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, HEADER, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    public static final class Entry {
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        private final long timestampMs;
        private final String playerName;
        private final String itemId;
        private final String speciesName;
        private final String rarityName;
        private final double lengthCm;
        private final double weightKg;
        private final double score;
        private final double encounterChance;

        public Entry(long timestampMs, String playerName, String itemId, String speciesName, String rarityName, double lengthCm, double weightKg, double score, double encounterChance) {
            this.timestampMs = timestampMs;
            this.playerName = playerName == null ? "Unknown" : playerName;
            this.itemId = itemId == null ? "" : itemId;
            this.speciesName = speciesName == null ? "Unknown Fish" : speciesName;
            this.rarityName = rarityName == null ? "Common" : rarityName;
            this.lengthCm = lengthCm;
            this.weightKg = weightKg;
            this.score = score;
            this.encounterChance = encounterChance;
        }

        public long getTimestampMs() { return timestampMs; }
        public String getPlayerName() { return playerName; }
        public String getItemId() { return itemId; }
        public String getSpeciesName() { return speciesName; }
        public String getRarityName() { return rarityName; }
        public double getLengthCm() { return lengthCm; }
        public double getWeightKg() { return weightKg; }
        public double getScore() { return score; }
        public double getEncounterChance() { return encounterChance; }
        public double getEffectiveScore() {
            return FishCatchHelper.computeHiddenScore(itemId, rarityName, lengthCm, weightKg, encounterChance);
        }

        public String getFormattedLengthCm() { return String.format(Locale.US, "%.2f cm", lengthCm); }
        public String getFormattedWeightKg() { return String.format(Locale.US, "%.2f kg", weightKg); }
        public String getFormattedDate() { return DATE_FORMAT.format(new Date(timestampMs)); }
    }

	private static String normalizeSpeciesKey(String speciesName) {
		return speciesName == null ? "unknown_fish" : speciesName.toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	private static final List<SpeciesDefinition> KNOWN_SPECIES = List.of(
			new SpeciesDefinition("bluegill", "Bluegill"),
			new SpeciesDefinition("catfish", "Catfish"),
			new SpeciesDefinition("minnow", "Minnow"),
			new SpeciesDefinition("rainbow_trout", "Rainbow Trout"),
			new SpeciesDefinition("salmon", "Salmon"),
			new SpeciesDefinition("pike", "Pike"),
			new SpeciesDefinition("frostgill", "Frostgill"),
			new SpeciesDefinition("piranha", "Piranha"),
			new SpeciesDefinition("black_piranha", "Black Piranha"),
			new SpeciesDefinition("snapjaw", "Snapjaw"),
			new SpeciesDefinition("crocodile", "Crocodile"),
			new SpeciesDefinition("fen_stalker", "Fen Stalker"),
			new SpeciesDefinition("clownfish", "Clownfish"),
			new SpeciesDefinition("blue_tang", "Blue Tang"),
			new SpeciesDefinition("chevron_tang", "Chevron Tang"),
			new SpeciesDefinition("lemon_peel_tang", "Lemon Peel Tang"),
			new SpeciesDefinition("sailfin_tang", "Sailfin Tang"),
			new SpeciesDefinition("pufferfish", "Pufferfish"),
			new SpeciesDefinition("crab", "Crab"),
			new SpeciesDefinition("lobster", "Lobster"),
			new SpeciesDefinition("moray_eel", "Moray Eel"),
			new SpeciesDefinition("blue_jellyfish", "Blue Jellyfish"),
			new SpeciesDefinition("cyan_jellyfish", "Cyan Jellyfish"),
			new SpeciesDefinition("green_jellyfish", "Green Jellyfish"),
			new SpeciesDefinition("red_jellyfish", "Red Jellyfish"),
			new SpeciesDefinition("yellow_jellyfish", "Yellow Jellyfish"),
			new SpeciesDefinition("man_of_war_jellyfish", "Man Of War Jellyfish"),
			new SpeciesDefinition("hammerhead_shark", "Hammerhead Shark"),
			new SpeciesDefinition("humpback_whale", "Humpback Whale"),
			new SpeciesDefinition("lava_shellfish", "Lava Shellfish"),
			new SpeciesDefinition("trilobite", "Trilobite"),
			new SpeciesDefinition("black_trilobite", "Black Trilobite")
	);

	private record SpeciesDefinition(String recordKey, String displayName) {
	}

	private static final class SpeciesRecordAccumulator {
		private final SpeciesDefinition definition;
		private Entry longest;
		private Entry heaviest;

		private SpeciesRecordAccumulator(SpeciesDefinition definition) {
			this.definition = definition;
		}

		private void accept(Entry entry) {
			if (entry == null) return;
			if (longest == null
					|| entry.getLengthCm() > longest.getLengthCm() + 0.000001D
					|| (Math.abs(entry.getLengthCm() - longest.getLengthCm()) <= 0.000001D && entry.getTimestampMs() < longest.getTimestampMs())) {
				longest = entry;
			}
			if (heaviest == null
					|| entry.getWeightKg() > heaviest.getWeightKg() + 0.000001D
					|| (Math.abs(entry.getWeightKg() - heaviest.getWeightKg()) <= 0.000001D && entry.getTimestampMs() < heaviest.getTimestampMs())) {
				heaviest = entry;
			}
		}

		private SpeciesRecordEntry toEntry() {
			boolean discovered = longest != null || heaviest != null;
			String itemId = "";
			if (longest != null && longest.getItemId() != null && !longest.getItemId().isBlank()) {
				itemId = longest.getItemId();
			} else if (heaviest != null && heaviest.getItemId() != null && !heaviest.getItemId().isBlank()) {
				itemId = heaviest.getItemId();
			}
			return new SpeciesRecordEntry(definition.displayName, itemId, discovered, longest, heaviest);
		}
	}

	public static final class SpeciesRecordEntry {
		private final String speciesName;
		private final String itemId;
		private final boolean discovered;
		private final Entry longest;
		private final Entry heaviest;

		public SpeciesRecordEntry(String speciesName, String itemId, boolean discovered, Entry longest, Entry heaviest) {
			this.speciesName = speciesName;
			this.itemId = itemId == null ? "" : itemId;
			this.discovered = discovered;
			this.longest = longest;
			this.heaviest = heaviest;
		}

		public String getSpeciesName() { return speciesName; }
		public String getItemId() { return itemId; }
		public boolean isDiscovered() { return discovered; }
		public Entry getLongest() { return longest; }
		public Entry getHeaviest() { return heaviest; }
	}

}
