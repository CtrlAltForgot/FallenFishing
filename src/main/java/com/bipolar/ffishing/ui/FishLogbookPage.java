package com.bipolar.ffishing.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.bipolar.ffishing.util.FishHistoryStore;

import java.util.List;
import java.util.Locale;

public class FishLogbookPage extends InteractiveCustomUIPage<FishLogbookPage.Data> {
    private final PlayerRef playerRef;
    private String currentTab = "logbook";

    public FishLogbookPage(PlayerRef playerRef, CustomPageLifetime lifetime) {
        super(playerRef, lifetime, Data.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder builder, UIEventBuilder eventBuilder, Store<EntityStore> store) {
        builder.append("FFishingLogbook.ui");
        builder.set("#Title.Text", getTitle());
        builder.set("#Subtitle.Text", getSubtitle());
        builder.set("#LogbookTab.Text", currentTab.equals("logbook") ? "[ LOGBOOK ]" : "LOGBOOK");
        builder.set("#LeaderboardTab.Text", currentTab.equals("leaderboard") ? "[ LEADERBOARD ]" : "LEADERBOARD");
        builder.set("#RecordsTab.Text", currentTab.equals("records") ? "[ RECORDS ]" : "RECORDS");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LogbookTab", EventData.of("Action", "logbook"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeaderboardTab", EventData.of("Action", "leaderboard"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecordsTab", EventData.of("Action", "records"), false);

        fillList(builder);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        super.handleDataEvent(ref, store, data);
        if (data == null || data.action == null || data.action.isBlank()) {
            return;
        }
        String action = data.action.toLowerCase(Locale.ROOT);
        if (action.equals("logbook") || action.equals("leaderboard") || action.equals("records")) {
            currentTab = action;
            rebuild();
        }
    }

    private void fillList(UICommandBuilder builder) {
        if (currentTab.equals("records")) {
            List<FishHistoryStore.SpeciesRecordEntry> records = FishHistoryStore.getSpeciesRecords();
            if (records.isEmpty()) {
                appendEmpty(builder, "#MainPanel", "No fish species registered.");
                return;
            }
            int rowIndex = 0;
            for (FishHistoryStore.SpeciesRecordEntry entry : records) {
                appendRecordRow(builder, "#MainPanel", rowIndex, entry);
                rowIndex++;
            }
            return;
        }

        List<FishHistoryStore.Entry> entries = getActiveEntries();
        if (entries.isEmpty()) {
            appendEmpty(builder, "#MainPanel", currentTab.equals("leaderboard") ? "No leaderboard catches yet." : "You haven't caught any fish yet.");
            return;
        }

        int rowIndex = 0;
        for (FishHistoryStore.Entry entry : entries) {
            appendRow(builder, "#MainPanel", rowIndex, entry, rowIndex + 1);
            rowIndex++;
        }
    }

    private List<FishHistoryStore.Entry> getActiveEntries() {
        if (currentTab.equals("leaderboard")) {
            return FishHistoryStore.getLeaderboard();
        }
        return FishHistoryStore.getPlayerHistory(playerRef != null ? playerRef.getUsername() : "");
    }


    private String getTitle() {
        return switch (currentTab) {
            case "leaderboard" -> "Fishing Leaderboard";
            case "records" -> "Fish Records";
            default -> "Your Fishing Logbook";
        };
    }

    private String getSubtitle() {
        return switch (currentTab) {
            case "leaderboard" -> "Top catches on this server, scored by true rarity, length, and weight.";
            case "records" -> "Server records for every catchable fish. Uncaught species stay hidden as ???.";
            default -> "Your catch history, newest first.";
        };
    }

    private void appendEmpty(UICommandBuilder builder, String parent, String message) {
        String inline = "Group { Anchor: (Width: 726, Height: 90); LayoutMode: Center; Label { Style: (FontSize: 16, TextColor: #ffffff(0.80), HorizontalAlignment: Center, VerticalAlignment: Center); Text: \"" + escape(message) + "\"; } }";
        builder.appendInline(parent, inline);
    }

    private void appendRow(UICommandBuilder builder, String parent, int localIndex, FishHistoryStore.Entry entry, int rank) {
        boolean leaderboard = currentTab.equals("leaderboard");
        String template = leaderboard ? getLeaderboardTemplate(rank) : "FFishingLogbookRow.ui";
        builder.append(parent, template);
        String rowPath = parent + "[" + localIndex + "]";
        builder.set(rowPath + " #Icon.ItemId", entry.getItemId());
        builder.set(rowPath + " #Name.Text", entry.getSpeciesName());
        builder.set(rowPath + " #Rarity.Text", entry.getRarityName());
        builder.set(rowPath + " #Length.Text", "Length: " + entry.getFormattedLengthCm());
        builder.set(rowPath + " #Weight.Text", "Weight: " + entry.getFormattedWeightKg());

        if (leaderboard) {
            builder.set(rowPath + " #CaughtBy.Text", "Caught By: " + entry.getPlayerName());
            builder.set(rowPath + " #Date.Text", "Date: " + entry.getFormattedDate());
            builder.set(rowPath + " #Rank.Text", "#" + rank);
        } else {
            builder.set(rowPath + " #Date.Text", "Date: " + entry.getFormattedDate());
        }
    }


    private void appendRecordRow(UICommandBuilder builder, String parent, int localIndex, FishHistoryStore.SpeciesRecordEntry entry) {
        builder.append(parent, "FFishingRecordRow.ui");
        String rowPath = parent + "[" + localIndex + "]";

        if (entry.isDiscovered() && entry.getItemId() != null && !entry.getItemId().isBlank()) {
            builder.set(rowPath + " #Icon.ItemId", entry.getItemId());
        } else {
            builder.set(rowPath + " #Icon.ItemId", "");
        }

        builder.set(rowPath + " #Name.Text", entry.isDiscovered() ? entry.getSpeciesName() : "???");

        FishHistoryStore.Entry longest = entry.getLongest();
        FishHistoryStore.Entry heaviest = entry.getHeaviest();

        builder.set(rowPath + " #LongestLength.Text", "Longest Length: " + (longest != null ? longest.getFormattedLengthCm() : "???"));
        builder.set(rowPath + " #LongestCaughtBy.Text", "Caught By: " + (longest != null ? longest.getPlayerName() : "???"));
        builder.set(rowPath + " #LongestDate.Text", "Date: " + (longest != null ? longest.getFormattedDate() : "???"));

        builder.set(rowPath + " #HeaviestWeight.Text", "Heaviest Weight: " + (heaviest != null ? heaviest.getFormattedWeightKg() : "???"));
        builder.set(rowPath + " #HeaviestCaughtBy.Text", "Caught By: " + (heaviest != null ? heaviest.getPlayerName() : "???"));
        builder.set(rowPath + " #HeaviestDate.Text", "Date: " + (heaviest != null ? heaviest.getFormattedDate() : "???"));
    }

    private String getLeaderboardTemplate(int rank) {
        return switch (rank) {
            case 1 -> "FFishingLeaderboardRowGold.ui";
            case 2 -> "FFishingLeaderboardRowSilver.ui";
            case 3 -> "FFishingLeaderboardRowBronze.ui";
            default -> "FFishingLeaderboardRowPlain.ui";
        };
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .documentation("UI action type")
                .add()
                .build();

        private String action = "";
    }
}
