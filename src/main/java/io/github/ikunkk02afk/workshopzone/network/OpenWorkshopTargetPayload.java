package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

public record OpenWorkshopTargetPayload(
	long sessionId,
	long revision,
	int syncId,
	BlockPos targetPos
) implements CustomPayload {
	public static final Id<OpenWorkshopTargetPayload> ID = new Id<>(WorkshopZone.id("open_workshop_target"));
	public static final PacketCodec<RegistryByteBuf, OpenWorkshopTargetPayload> CODEC = CustomPayload.codecOf(
		(payload, buf) -> {
			buf.writeVarLong(payload.sessionId);
			buf.writeVarLong(payload.revision);
			buf.writeVarInt(payload.syncId);
			buf.writeBlockPos(payload.targetPos);
		},
		buf -> new OpenWorkshopTargetPayload(
			buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readBlockPos()
		)
	);

	public OpenWorkshopTargetPayload {
		targetPos = Objects.requireNonNull(targetPos, "targetPos").toImmutable();
		if (sessionId < 0 || revision < 0 || syncId < 0) {
			throw new IllegalArgumentException("Session, revision and sync ids must be non-negative");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
