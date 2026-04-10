package com.bipolar.ffishing.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.bipolar.ffishing.ui.FishLogbookPage;

import java.util.concurrent.CompletableFuture;

public class FishCommand extends AbstractPlayerCommand {
    public FishCommand(String name, String description) {
        super(name, description);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> senderRef, PlayerRef playerRef, World world) {
        Player player = (Player) context.senderAs(Player.class);
        if (player == null) {
            return;
        }
        CompletableFuture.runAsync(() -> player.getPageManager().openCustomPage(senderRef, store,
                new FishLogbookPage(playerRef, CustomPageLifetime.CanDismiss)), world);
    }
}
