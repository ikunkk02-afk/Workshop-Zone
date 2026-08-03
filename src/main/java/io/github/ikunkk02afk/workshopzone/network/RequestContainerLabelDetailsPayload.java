package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

public record RequestContainerLabelDetailsPayload(
	long requestId,
	long sessionId,
	long revision,
	int syncId,
	BlockPos openedEntryPosition
) implements CustomPayload {
	public static final Id<RequestContainerLabelDetailsPayload> ID = new Id<>(WorkshopZone.id("request_container_label_details"));
	public static final PacketCodec<RegistryByteBuf, RequestContainerLabelDetailsPayload> CODEC = CustomPayload.codecOf(
		RequestContainerLabelDetailsPayload::write,
		RequestContainerLabelDetailsPayload::read
	);

	public RequestContainerLabelDetailsPayload {
		openedEntryPosition = Objects.requireNonNull(openedEntryPosition, "openedEntryPosition").toImmutable();
		if (requestId < 0 || sessionId < 0 || revision < 0 || syncId < 0) {
			throw new IllegalArgumentException("Invalid container label details request");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(RequestContainerLabelDetailsPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.requestId);
		buf.writeVarLong(payload.sessionId);
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.syncId);
		buf.writeBlockPos(payload.openedEntryPosition);
	}

	private static RequestContainerLabelDetailsPayload read(RegistryByteBuf buf) {
		try {
			return new RequestContainerLabelDetailsPayload(
				buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readBlockPos()
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid container label details request", exception);
		}
	}
}
