package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public record ContainerLabelEditResultPayload(
	long sessionId,
	int syncId,
	ContainerLabelEditResult result,
	Optional<Identifier> mismatchItemId,
	int mismatchSlotCount
) implements CustomPayload {
	public static final Id<ContainerLabelEditResultPayload> ID = new Id<>(WorkshopZone.id("container_label_edit_result"));
	public static final PacketCodec<RegistryByteBuf, ContainerLabelEditResultPayload> CODEC = CustomPayload.codecOf(
		ContainerLabelEditResultPayload::write,
		ContainerLabelEditResultPayload::read
	);

	public ContainerLabelEditResultPayload {
		Objects.requireNonNull(result, "result");
		mismatchItemId = Objects.requireNonNull(mismatchItemId, "mismatchItemId");
		if (sessionId < 0 || syncId < 0 || mismatchSlotCount < 0
			|| (mismatchSlotCount == 0) != mismatchItemId.isEmpty()) {
			throw new IllegalArgumentException("Invalid container label result payload");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(ContainerLabelEditResultPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.sessionId);
		buf.writeVarInt(payload.syncId);
		buf.writeIdentifier(payload.result.id());
		buf.writeBoolean(payload.mismatchItemId.isPresent());
		payload.mismatchItemId.ifPresent(buf::writeIdentifier);
		buf.writeVarInt(payload.mismatchSlotCount);
	}

	private static ContainerLabelEditResultPayload read(RegistryByteBuf buf) {
		long sessionId = buf.readVarLong();
		int syncId = buf.readVarInt();
		Identifier resultId = buf.readIdentifier();
		ContainerLabelEditResult result = ContainerLabelEditResult.fromId(resultId)
			.orElseThrow(() -> new DecoderException("Unknown container label result " + resultId));
		Optional<Identifier> mismatch = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
		int count = buf.readVarInt();
		try {
			return new ContainerLabelEditResultPayload(sessionId, syncId, result, mismatch, count);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid container label result payload", exception);
		}
	}
}
