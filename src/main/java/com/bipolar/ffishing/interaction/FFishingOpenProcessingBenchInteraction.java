package com.bipolar.ffishing.interaction;

import com.hypixel.hytale.builtin.crafting.interaction.OpenProcessingBenchInteraction;

/**
 * Provides a concrete interaction asset instance that can be loaded into the interaction asset store
 * and attached as the default root interaction for custom processing benches.
 */
public class FFishingOpenProcessingBenchInteraction extends OpenProcessingBenchInteraction {
    public FFishingOpenProcessingBenchInteraction(String id) {
        super();
        this.id = id;
    }
}
