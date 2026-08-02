package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record WorkshopSnapshotPayload(
	long sessionId,
	long revision,
	int syncId,
	Identifier dimensionId,
	BlockPos center,
	WorkshopBlockType openedBlockType,
	int totalEntryCount,
	int containerCount,
	int workstationCount,
	boolean truncated,
	List<WorkshopNetworkEntry> entries
) implements CustomPayload {
	public static final Id<WorkshopSnapshotPayload> ID = new Id<>(WorkshopZone.id("workshop_snapshot"));
	public static final PacketCodec<RegistryByteBuf, WorkshopSnapshotPayload> CODEC = CustomPayload.codecOf(
		WorkshopSnapshotPayload::write,
		WorkshopSnapshotPayload::read
	);

	public WorkshopSnapshotPayload {
		Objects.requireNonNull(dimensionId, "dimensionId");
		center = Objects.requireNonNull(center, "center").toImmutable();
		Objects.requireNonNull(openedBlockType, "openedBlockType");
		entries = List.copyOf(entries);
		if (sessionId < 0 || revision < 0 || syncId < 0 || totalEntryCount < 0
			|| containerCount < 0 || workstationCount < 0 || entries.size() > WorkshopNetworking.MAX_ENTRIES
			|| containerCount + workstationCount != totalEntryCount
			|| totalEntryCount < entries.size() || truncated != (totalEntryCount > entries.size())) {
			throw new IllegalArgumentException("Invalid workshop snapshot metadata");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(WorkshopSnapshotPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.sessionId);
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.syncId);
		buf.writeIdentifier(payload.dimensionId);
		buf.writeBlockPos(payload.center);
		buf.writeVarInt(payload.openedBlockType.ordinal());
		buf.writeVarInt(payload.totalEntryCount);
		buf.writeVarInt(payload.containerCount);
		buf.writeVarInt(payload.workstationCount);
		buf.writeBoolean(payload.truncated);
		buf.writeVarInt(payload.entries.size());
		payload.entries.forEach(entry -> WorkshopNetworkEntry.write(buf, entry));
	}

	private static WorkshopSnapshotPayload read(RegistryByteBuf buf) {
		long sessionId = buf.readVarLong();
		long revision = buf.readVarLong();
		int syncId = buf.readVarInt();
		Identifier dimensionId = buf.readIdentifier();
		BlockPos center = buf.readBlockPos();
		int ordinal = buf.readVarInt();
		WorkshopBlockType[] values = WorkshopBlockType.values();
		if (ordinal < 0 || ordinal >= values.length) {
			throw new DecoderException("Unknown opened workshop block type: " + ordinal);
		}
		int totalEntryCount = buf.readVarInt();
		int containerCount = buf.readVarInt();
		int workstationCount = buf.readVarInt();
		boolean truncated = buf.readBoolean();
		int entryCount = buf.readVarInt();
		if (entryCount < 0 || entryCount > WorkshopNetworking.MAX_ENTRIES) {
			throw new DecoderException("Workshop entry count exceeds limit: " + entryCount);
		}
		List<WorkshopNetworkEntry> entries = new ArrayList<>(entryCount);
		for (int index = 0; index < entryCount; index++) {
			entries.add(WorkshopNetworkEntry.read(buf));
		}
		try {
			return new WorkshopSnapshotPayload(
				sessionId, revision, syncId, dimensionId, center, values[ordinal], totalEntryCount,
				containerCount, workstationCount, truncated, entries
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop snapshot", exception);
		}
	}
}
