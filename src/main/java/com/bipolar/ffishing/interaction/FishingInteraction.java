package com.bipolar.ffishing.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.builtin.adventure.memories.MemoriesPlugin;
import com.hypixel.hytale.builtin.adventure.memories.component.PlayerMemories;
import com.hypixel.hytale.builtin.adventure.memories.memories.npc.NPCMemory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.bipolar.ffishing.FallenFishingPlugin;
import com.bipolar.ffishing.api.event.FishCaughtEvent;
import com.bipolar.ffishing.api.event.FishingFailedEvent;
import com.bipolar.ffishing.api.event.FishingStartedEvent;
import com.bipolar.ffishing.component.BobberComponent;
import com.bipolar.ffishing.component.BoundBobberComponent;
import com.bipolar.ffishing.util.FishCatchHelper;
import com.bipolar.ffishing.util.FishCatchInfo;
import com.bipolar.ffishing.util.FishHelper;
import com.bipolar.ffishing.util.FishHistoryStore;
import com.bipolar.ffishing.util.FishRecordStore;
import com.bipolar.ffishing.util.RpgLevelingCompat;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.Locale;
import java.util.UUID;

public class FishingInteraction extends SimpleInstantInteraction {
	private static final String DEFAULT_ROD_ITEM_ID = "FFishing_Fishing_Rod";
	private static final String WAITING_ROD_ITEM_ID = "FFishing_Fishing_Rod_Waiting";

	public static final BuilderCodec<FishingInteraction> CODEC = BuilderCodec.builder(
					FishingInteraction.class, FishingInteraction::new, SimpleInstantInteraction.CODEC
			)
			.documentation("Spawns or reels in a bobber when right-clicked on a block with a fishing rod")
			.build();

	@Override
	protected void firstRun(@NonNullDecl InteractionType type, @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler handler) {
		CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
		World world = commandBuffer.getExternalData().getWorld();
		if (commandBuffer == null) {
			context.getState().state = InteractionState.Failed;
		} else {
			ItemStack itemstack = context.getHeldItem();
			if (itemstack == null) {
				context.getState().state = InteractionState.Failed;
			} else {
				Ref<EntityStore> ref = context.getEntity();
				PlayerRef playerref = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
				Player player = commandBuffer.getComponent(ref, Player.getComponentType());

				if (player == null || playerref == null) {
					context.getState().state = InteractionState.Failed;
					return;
				}
				Inventory inventory = player.getInventory();
				byte activeSlot = inventory.getActiveHotbarSlot();
				ItemStack hotbarItem = inventory.getActiveHotbarItem();

				Vector3i getTargetWater = getTargetWater(commandBuffer, world, 10F, ref);
				if (getTargetWater == null) {
					context.getState().state = InteractionState.Failed;
					return;
				}
				BlockPosition blockposition = new BlockPosition(getTargetWater.x, getTargetWater.y, getTargetWater.z);

				if (hotbarItem == null || blockposition == null) {
					context.getState().state = InteractionState.Failed;
					return;
				}
				FishingMetaData fishingMetaData = itemstack.getFromMetadataOrNull(FishingMetaData.KEY, FishingMetaData.CODEC);
				if (fishingMetaData != null) {
					// Verify the bobber still exists
					UUID bound = fishingMetaData.getBoundBobber();
					boolean bobberExists = bound != null && world.getEntityStore().getRefFromUUID(bound) != null;

					if (!bobberExists) {
						adjustMetadata(inventory, activeSlot, hotbarItem, null);
						setBoundBobber(commandBuffer, ref, null);
					} else {
						reelBobber(world, commandBuffer, hotbarItem, inventory, activeSlot, fishingMetaData, playerref);
						setBoundBobber(commandBuffer, ref, null);
						return;
					}
				}

				if (hasBoundBobber(commandBuffer, ref)) {
					// Player already has a bound bobber, cannot cast another
					context.getState().state = InteractionState.Failed;
					return;
				}

				FishingStartedEvent startedEvent = new FishingStartedEvent(ref);
				commandBuffer.invoke(ref, startedEvent);

				if (startedEvent.isCancelled()) return;

				int soundEventIndex = SoundEvent.getAssetMap().getIndex("SFX_FFishing_Cast");
				SoundUtil.playSoundEvent2dToPlayer(playerref, soundEventIndex, SoundCategory.SFX);

				// Handle the fishing rod casting logic
				spawnBobber(world, commandBuffer, context, hotbarItem,
						new Vector3i(blockposition.x, blockposition.y, blockposition.z),
						inventory, activeSlot, ref);

			}
		}
	}

	/**
	 * Handles the logic for reeling in the bobber.
	 *
	 * @param world           The game world
	 * @param commandBuffer   The command buffer for entity operations
	 * @param hotbarItem      The fishing rod item in the hotbar
	 * @param inventory       The player's inventory
	 * @param hotbarSlot      The hotbar slot index
	 * @param fishingMetaData The fishing rod's metadata containing the bound bobber UUID
	 */
	private void reelBobber(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer,
	                        @Nonnull ItemStack hotbarItem, Inventory inventory, byte hotbarSlot,
	                        @Nonnull FishingMetaData fishingMetaData, @Nonnull PlayerRef playerRef) {
		ItemStack preReelRod = hotbarItem;
		// Remove old bobber and adjust metadata to unbind
		adjustMetadata(inventory, hotbarSlot, hotbarItem, null);

		int soundEventIndex = SoundEvent.getAssetMap().getIndex("SFX_FFishing_Reel");
		SoundUtil.playSoundEvent2dToPlayer(playerRef, soundEventIndex, SoundCategory.SFX);

		var ref = playerRef.getReference();
		Player player = commandBuffer.getComponent(ref, Player.getComponentType());

		// Handle the bobber retrieval logic here
		Ref<EntityStore> bobberRef = world.getEntityStore().getRefFromUUID(fishingMetaData.getBoundBobber());
		if (bobberRef == null) {
			return;
		}
		BobberComponent component = commandBuffer.getComponent(bobberRef, BobberComponent.getComponentType());
		if (component == null || !component.canCatchFish()) {
			FishingFailedEvent event = new FishingFailedEvent(ref);
			commandBuffer.invoke(ref, event);

			commandBuffer.removeEntity(bobberRef, RemoveReason.REMOVE);
			return;
		}

		FishCatchInfo catchInfo = component.getPendingCatch();
		ItemStack fishStack = catchInfo == null ? FishHelper.createRandomFish() : InventoryHelper.createItem(catchInfo.getItemId());
		if (!fishStack.isEmpty()) {
			FishCaughtEvent caughtEvent = new FishCaughtEvent(fishStack, ref);
			commandBuffer.invoke(ref, caughtEvent);

			if (!caughtEvent.isCancelled()) {
				fishStack = caughtEvent.getCaughtItem();
				if (catchInfo == null) {
					catchInfo = FishCatchHelper.createCatchInfo(fishStack.getItemId());
				}
				if (!catchInfo.getItemId().equals(fishStack.getItemId())) {
					fishStack = new ItemStack(catchInfo.getItemId(), fishStack.getQuantity());
				}

				if (player != null) {
					var addTx = player.getInventory().getCombinedBackpackStorageHotbarFirst().addItemStack(fishStack, false, false, false);
					ItemStack remainder = addTx.getRemainder();
					if (remainder != null && !remainder.isEmpty()) {
						Vector3d direction = TargetUtil.getLook(playerRef.getReference(), commandBuffer).getDirection().negate().add(0, 0.5, 0);
						ItemUtils.throwItem(bobberRef, commandBuffer, remainder, direction, 10.0F);
					}
				} else {
					Vector3d direction = TargetUtil.getLook(playerRef.getReference(), commandBuffer).getDirection().negate().add(0, 0.5, 0);
					ItemUtils.throwItem(bobberRef, commandBuffer, fishStack, direction, 10.0F);
				}

				applyCatchDurabilityLoss(inventory, hotbarSlot, preReelRod, catchInfo);

				FishHistoryStore.appendCatch(playerRef.getUsername(), catchInfo);
				RpgLevelingCompat.awardCatchXp(playerRef, catchInfo);
				unlockFishMemoryIfNeeded(commandBuffer, ref, catchInfo);
				sendCatchNotification(playerRef, fishStack, catchInfo);

				FishRecordStore.RecordUpdate recordUpdate = FishRecordStore.updateGlobalRecord(catchInfo, playerRef.getUsername());
				if (recordUpdate.isNewRecord()) {
					sendGlobalRecordNotification(world, catchInfo, playerRef.getUsername());
					FallenFishingPlugin.LOGGER.atInfo().log(
							"New FFishing global record for %s by %s: %s / %s (%s)",
							catchInfo.getSpeciesName(),
							playerRef.getUsername(),
							catchInfo.getFormattedLengthCm(),
							catchInfo.getFormattedWeightKg(),
							catchInfo.getRarityName()
					);
				}
			}
		}
		commandBuffer.removeEntity(bobberRef, RemoveReason.REMOVE);
	}


	private void applyCatchDurabilityLoss(@Nonnull Inventory inventory, byte hotbarSlot, @Nonnull ItemStack sourceRod, @Nonnull FishCatchInfo catchInfo) {
		ItemStack slotStack = inventory.getHotbar().getItemStack(hotbarSlot);
		ItemStack baseRod = slotStack != null && !slotStack.isEmpty() ? slotStack : sourceRod;

		double maxDurability = baseRod.getMaxDurability() > 0.0D ? baseRod.getMaxDurability() : sourceRod.getMaxDurability();
		if (maxDurability <= 0.0D) {
			maxDurability = 250.0D;
		}

		double currentDurability = baseRod.getDurability();
		if (currentDurability <= 0.0D) {
			currentDurability = maxDurability;
		}

		double durabilityLoss = switch (catchInfo.getRarityName().toLowerCase(Locale.ROOT)) {
			case "uncommon" -> 2.0D;
			case "rare" -> 3.0D;
			case "epic" -> 4.0D;
			case "legendary" -> 5.0D;
			default -> 1.0D;
		};

		double newDurability = Math.max(0.0D, currentDurability - durabilityLoss);
		ItemStack damagedDefaultRod = new ItemStack(
				DEFAULT_ROD_ITEM_ID,
				1,
				newDurability,
				maxDurability,
				null
		);
		damagedDefaultRod.setOverrideDroppedItemAnimation(baseRod.getOverrideDroppedItemAnimation());

		ItemStack previous = inventory.getHotbar().getItemStack(hotbarSlot);
		if (previous != null && !previous.isEmpty()) {
			inventory.getHotbar().replaceItemStackInSlot(hotbarSlot, previous, damagedDefaultRod);
		} else {
			inventory.getHotbar().setItemStackForSlot(hotbarSlot, damagedDefaultRod);
		}
	}

	/**
	 * Handles the logic for spawning a new bobber.
	 *
	 * @param world         The game world
	 * @param commandBuffer The command buffer for entity operations
	 * @param context       The interaction context
	 * @param fishingStack  The fishing rod item stack
	 * @param targetBlock   The target block position
	 * @param inventory     The player's inventory
	 * @param hotbarSlot    The hotbar slot index
	 */
	private void spawnBobber(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionContext context,
	                         @Nonnull ItemStack fishingStack, @Nonnull Vector3i targetBlock, Inventory inventory, byte hotbarSlot, Ref<EntityStore> playerRef) {
		Ref<EntityStore> ref = context.getEntity();
		Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
		Vector3d vector3d = targetBlock.toVector3d();
		vector3d.add(0.5, 0.25, 0.5);

		Vector3f rotation = new Vector3f();
		HeadRotation headRotation = commandBuffer.getComponent(ref, HeadRotation.getComponentType());
		if (headRotation != null) {
			rotation.setYaw(headRotation.getRotation().getYaw() + (float) (Math.PI / 180.0) * 180.0F);
		}

		WorldChunk worldchunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
		if (worldchunk != null) {
			BlockType blockType = worldchunk.getBlockType(targetBlock);
			@SuppressWarnings("removal") int i = worldchunk.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
			BlockBoundingBoxes.RotatedVariantBoxes variantBoxes = BlockBoundingBoxes.getAssetMap()
					.getAsset(blockType.getHitboxTypeIndex())
					.get(i);
			vector3d.add(0.0, variantBoxes.getBoundingBox().max.y - 0.5, 0.0);
		}
		holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(vector3d, rotation));
		holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
		holder.ensureComponent(PhysicsValues.getComponentType());

		UUID uuid = UUID.randomUUID();
		holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(uuid));
		holder.putComponent(NetworkId.getComponentType(), new NetworkId(ref.getStore().getExternalData().takeNextNetworkId()));

		ModelAsset modelasset = ModelAsset.getAssetMap().getAsset("FFishingBobber");
		if (modelasset == null)
			modelasset = ModelAsset.DEBUG;
		Model model = Model.createRandomScaleModel(modelasset);
		holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
		holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
		if (model.getBoundingBox() == null)
			return;

		holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
		holder.addComponent(Velocity.getComponentType(), new Velocity());
		BobberComponent bobberComponent = holder.ensureAndGetComponent(BobberComponent.getComponentType());
		bobberComponent.setTargetWater(new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z));
		commandBuffer.addEntity(holder, AddReason.SPAWN);

		// Update the fishing rod's metadata to bind it to the spawned bobber
		adjustMetadata(inventory, hotbarSlot, fishingStack, uuid);
		// Update the player's bound bobber component
		setBoundBobber(commandBuffer, playerRef, uuid);
	}

	/**
	 * Adjusts the fishing rod's metadata to bind or unbind it from a bobber.
	 *
	 * @param inventory  The player's inventory
	 * @param hotbarSlot The hotbar slot index
	 * @param fishingRod The fishing rod item stack
	 * @param bobberUUID The UUID of the bobber to bind to, or null to unbind
	 */
	private void adjustMetadata(Inventory inventory, byte hotbarSlot, @Nonnull ItemStack fishingRod, @Nullable UUID bobberUUID) {
		ItemStack metadataAdjustedRod;
		if (bobberUUID == null) {
			metadataAdjustedRod = fishingRod.withMetadata(FishingMetaData.KEY, null);
		} else {
			FishingMetaData fishingMetaData = fishingRod.getFromMetadataOrNull(FishingMetaData.KEY, FishingMetaData.CODEC);
			if (fishingMetaData == null) {
				fishingMetaData = new FishingMetaData();
			}
			fishingMetaData.setBoundBobber(bobberUUID);
			metadataAdjustedRod = fishingRod.withMetadata(FishingMetaData.KEYED_CODEC, fishingMetaData);
		}

		String targetItemId = bobberUUID == null ? DEFAULT_ROD_ITEM_ID : WAITING_ROD_ITEM_ID;
		ItemStack newRod = swapRodItem(metadataAdjustedRod, targetItemId);
		inventory.getHotbar().replaceItemStackInSlot(hotbarSlot, fishingRod, newRod);
	}

	private ItemStack swapRodItem(@Nonnull ItemStack sourceRod, @Nonnull String targetItemId) {
		if (targetItemId.equals(sourceRod.getItemId())) {
			return sourceRod;
		}

		ItemStack swappedRod = new ItemStack(
				targetItemId,
				sourceRod.getQuantity(),
				sourceRod.getDurability(),
				sourceRod.getMaxDurability(),
				sourceRod.getMetadata()
		);
		swappedRod.setOverrideDroppedItemAnimation(sourceRod.getOverrideDroppedItemAnimation());
		return swappedRod;
	}


	/**
	 * Gets the target water block based on the raycast parameters.
	 *
	 * @param world       the world reference
	 * @param maxDistance the maximum distance for the raycast
	 * @param ref         the entity reference
	 * @return the target water block position, or null if none found
	 */
	@Nullable
	public Vector3i getTargetWater(@Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull World world, float maxDistance, Ref<EntityStore> ref) {
		Transform transform = TargetUtil.getLook(ref, commandBuffer);
		Vector3d vector3d = transform.getPosition();
		Vector3d vector3d1 = transform.getDirection();
		return TargetUtil.getTargetBlock(
				world,
				(_blockId, fluidId) -> fluidId != 0,
				vector3d.x, vector3d.y, vector3d.z, vector3d1.x, vector3d1.y, vector3d1.z, maxDistance
		);
	}

	/**
	 * Checks if the player has a bound bobber.
	 *
	 * @param componentAccessor the component accessor
	 * @param playerRef         the player reference
	 * @return true if the player has a bound bobber, false otherwise
	 */
	private boolean hasBoundBobber(ComponentAccessor<EntityStore> componentAccessor, Ref<EntityStore> playerRef) {
		return componentAccessor.getComponent(playerRef, FallenFishingPlugin.get().getBoundBobberComponent()) != null;
	}

	/**
	 * Binds the UUID to the player so they can't cast multiple bobbers.
	 *
	 * @param componentAccessor the component accessor
	 * @param playerRef         the player reference
	 * @param uuid              the bobber UUID to bind, or null to unbind
	 */
	private void setBoundBobber(ComponentAccessor<EntityStore> componentAccessor, Ref<EntityStore> playerRef, @Nullable UUID uuid) {
		boolean hasBobber = hasBoundBobber(componentAccessor, playerRef);
		if (hasBobber && uuid == null) {
			componentAccessor.tryRemoveComponent(playerRef, FallenFishingPlugin.get().getBoundBobberComponent());
		} else {
			if (!hasBobber && uuid != null) {
				componentAccessor.addComponent(playerRef, FallenFishingPlugin.get().getBoundBobberComponent(), new BoundBobberComponent(uuid));
			}
		}
	}


	private void unlockFishMemoryIfNeeded(@Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Ref<EntityStore> playerRef, @Nonnull FishCatchInfo catchInfo) {
		try {
			MemoriesPlugin memoriesPlugin = MemoriesPlugin.get();
			if (memoriesPlugin == null) {
				return;
			}

			var componentType = memoriesPlugin.getPlayerMemoriesComponentType();
			PlayerMemories playerMemories = commandBuffer.getComponent(playerRef, componentType);
			if (playerMemories == null) {
				playerMemories = new PlayerMemories();
				commandBuffer.addComponent(playerRef, componentType, playerMemories);
			}

			String npcRole = resolveNpcRoleForCatch(catchInfo);
			if (npcRole == null || npcRole.isBlank()) {
				return;
			}

			if (playerMemories.recordMemory(new NPCMemory(npcRole, NPCMemory.ZONE_NAME_UNKNOWN))) {
				commandBuffer.putComponent(playerRef, componentType, playerMemories);
			}
		} catch (Exception exception) {
			FallenFishingPlugin.LOGGER.atWarning().withCause(exception).log("Unable to unlock fish memory for %s", catchInfo.getSpeciesName());
		}
	}

	private String resolveNpcRoleForCatch(@Nonnull FishCatchInfo catchInfo) {
		String itemId = catchInfo.getItemId();
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		String speciesKey = itemId;
		if (speciesKey.startsWith("FFishing_")) speciesKey = speciesKey.substring("FFishing_".length());
		if (speciesKey.endsWith("_Item")) speciesKey = speciesKey.substring(0, speciesKey.length() - "_Item".length());
		for (String rarity : new String[]{"_Common", "_Uncommon", "_Rare", "_Epic", "_Legendary"}) {
			if (speciesKey.endsWith(rarity)) {
				speciesKey = speciesKey.substring(0, speciesKey.length() - rarity.length());
				break;
			}
		}
		return speciesKey;
	}

	private void sendCatchNotification(@Nonnull PlayerRef playerRef, @Nonnull ItemStack fishStack, @Nonnull FishCatchInfo catchInfo) {
		if (playerRef.getPacketHandler() == null) {
			return;
		}

		Message title = Message.raw("You caught a " + catchInfo.getSpeciesName() + "!").color(Color.WHITE);
		Message subtitle = Message.raw(catchInfo.getCatchSubtitle()).color(Color.WHITE);

		try {
			NotificationUtil.sendNotification(
					playerRef.getPacketHandler(),
					title,
					subtitle,
					fishStack.toPacket()
			);
		} catch (RuntimeException ignored) {
			playerRef.sendMessage(title);
		}
	}

	private void sendGlobalRecordNotification(@Nonnull World world, @Nonnull FishCatchInfo catchInfo, @Nonnull String username) {
		Message title = Message.raw("Global Record by " + username).color(new Color(0xD3B16A));
		String detailsText = (catchInfo.getSpeciesName() + " LENGTH: " + catchInfo.getFormattedLengthCm() + " | WEIGHT: " + catchInfo.getFormattedWeightKg())
				.toUpperCase(Locale.ROOT);
		Message details = Message.raw(detailsText).color(new Color(0xD3B16A));

		for (PlayerRef worldPlayer : world.getPlayerRefs()) {
			if (worldPlayer == null) {
				continue;
			}
			try {
				EventTitleUtil.showEventTitleToPlayer(worldPlayer, title, details, true, null, 5.0F, 1.0F, 1.0F);
			} catch (RuntimeException ignored) {
			}
		}
	}
}
