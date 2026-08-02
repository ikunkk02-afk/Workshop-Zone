package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Nameable;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkshopNetworking {
	public static final int MAX_ENTRIES = 128;
	public static final int MAX_CUSTOM_NAME_LENGTH = 128;
	static final int MAX_CUSTOM_NAME_WIRE_LENGTH = MAX_CUSTOM_NAME_LENGTH * 2;

	private WorkshopNetworking() {
	}

	public static void registerCommon() {
		PayloadTypeRegistry.playS2C().register(WorkshopSnapshotPayload.ID, WorkshopSnapshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ClearWorkshopSessionPayload.ID, ClearWorkshopSessionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ContainerLabelEditResultPayload.ID, ContainerLabelEditResultPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestWorkshopRefreshPayload.ID, RequestWorkshopRefreshPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(OpenWorkshopTargetPayload.ID, OpenWorkshopTargetPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpdateContainerLabelPayload.ID, UpdateContainerLabelPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(
			RequestWorkshopRefreshPayload.ID,
			(payload, context) -> WorkshopSessionManager.getInstance().refresh(
				context.player(), payload.sessionId(), payload.syncId()
			)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			OpenWorkshopTargetPayload.ID,
			(payload, context) -> WorkshopSessionManager.getInstance().openTarget(
				context.player(), payload.sessionId(), payload.revision(), payload.syncId(), payload.targetPos()
			)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			UpdateContainerLabelPayload.ID,
			(payload, context) -> WorkshopSessionManager.getInstance().updateContainerLabel(context.player(), payload)
		);
	}

	public static void sendSnapshot(ServerPlayerEntity player, WorkshopSession session) {
		List<WorkshopNetworkEntry> allEntries = new ArrayList<>(Math.min(session.scanResult().size(), MAX_ENTRIES));
		for (int index = 0; index < Math.min(session.scanResult().size(), MAX_ENTRIES); index++) {
			WorkshopBlockEntry entry = session.scanResult().entries().get(index);
			allEntries.add(new WorkshopNetworkEntry(
				entry.type(),
				entry.position(),
				entry.blockId(),
				entry.distanceSquared(),
				entry.container(),
				entry.processingDevice(),
				findCustomName(player.getServerWorld(), entry),
				entry.labelSummary()
			));
		}

		List<WorkshopNetworkEntry> entries = limitEntries(allEntries);
		WorkshopZone.LOGGER.debug(
			"Sending workshop snapshot to player {}: session {} syncId {} with {} entries",
			player.getGameProfile().getName(), session.sessionId(), session.syncId(), entries.size()
		);
		ServerPlayNetworking.send(player, new WorkshopSnapshotPayload(
			session.sessionId(),
			session.revision(),
			session.syncId(),
			session.dimension().getValue(),
			session.scanCenter(),
			session.openedEntryPosition(),
			session.openedBlockType(),
			session.scanResult().size(),
			session.scanResult().containerCount(),
			session.scanResult().processingDeviceCount(),
			session.scanResult().size() > entries.size(),
			entries
		));
	}

	static List<WorkshopNetworkEntry> limitEntries(List<WorkshopNetworkEntry> entries) {
		return List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
	}

	public static void sendClear(ServerPlayerEntity player, WorkshopSession session) {
		ServerPlayNetworking.send(player, new ClearWorkshopSessionPayload(session.sessionId(), session.syncId()));
	}

	public static void sendLabelResult(
		ServerPlayerEntity player,
		long sessionId,
		int syncId,
		ContainerLabelEditResult result,
		Optional<Identifier> mismatchItemId,
		int mismatchSlotCount
	) {
		ServerPlayNetworking.send(player, new ContainerLabelEditResultPayload(
			sessionId, syncId, result, mismatchItemId, mismatchSlotCount
		));
	}

	private static Optional<String> findCustomName(ServerWorld world, WorkshopBlockEntry entry) {
		Optional<String> name = readCustomName(world, entry.position());
		if (name.isPresent() || !(world.getBlockState(entry.position()).getBlock() instanceof ChestBlock)) {
			return name;
		}

		BlockState state = world.getBlockState(entry.position());
		if (!state.contains(ChestBlock.CHEST_TYPE)) {
			return Optional.empty();
		}
		BlockPos otherHalf = entry.position().offset(ChestBlock.getFacing(state));
		return readCustomName(world, otherHalf);
	}

	private static Optional<String> readCustomName(ServerWorld world, BlockPos position) {
		int chunkX = ChunkSectionPos.getSectionCoord(position.getX());
		int chunkZ = ChunkSectionPos.getSectionCoord(position.getZ());
		return world.getChunkManager().isChunkLoaded(chunkX, chunkZ)
			? readCustomName(world.getBlockEntity(position))
			: Optional.empty();
	}

	private static Optional<String> readCustomName(BlockEntity blockEntity) {
		if (!(blockEntity instanceof Nameable nameable) || !nameable.hasCustomName()) {
			return Optional.empty();
		}
		Text customName = nameable.getCustomName();
		if (customName == null) {
			return Optional.empty();
		}
		String value = truncate(customName.getString(), MAX_CUSTOM_NAME_LENGTH);
		return value.isBlank() ? Optional.empty() : Optional.of(value);
	}

	static String truncate(String value, int maxCodePoints) {
		if (value.codePointCount(0, value.length()) <= maxCodePoints) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
	}
}
