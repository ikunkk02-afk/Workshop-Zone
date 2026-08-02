package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.Optional;

public record UpdateContainerLabelPayload(
	long sessionId,
	long revision,
	int syncId,
	BlockPos openedEntryPosition,
	ContainerLabelOperation operation,
	Optional<Identifier> valueId
) implements CustomPayload {
	public static final Id<UpdateContainerLabelPayload> ID = new Id<>(WorkshopZone.id("update_container_label"));
	public static final PacketCodec<RegistryByteBuf, UpdateContainerLabelPayload> CODEC = CustomPayload.codecOf(
		UpdateContainerLabelPayload::write,
		UpdateContainerLabelPayload::read
	);

	public UpdateContainerLabelPayload {
		openedEntryPosition = Objects.requireNonNull(openedEntryPosition, "openedEntryPosition").toImmutable();
		Objects.requireNonNull(operation, "operation");
		valueId = Objects.requireNonNull(valueId, "valueId");
		if (sessionId < 0 || revision < 0 || syncId < 0
			|| (operation != ContainerLabelOperation.CLEAR) != valueId.isPresent()) {
			throw new IllegalArgumentException("Invalid container label edit payload");
		}
	}

	public Optional<Identifier> itemId() {
		return operation == ContainerLabelOperation.SET_EXACT_ITEM ? valueId : Optional.empty();
	}

	public Optional<Identifier> tagId() {
		return operation == ContainerLabelOperation.SET_ITEM_TAG ? valueId : Optional.empty();
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(UpdateContainerLabelPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.sessionId);
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.syncId);
		buf.writeBlockPos(payload.openedEntryPosition);
		buf.writeIdentifier(payload.operation.id());
		buf.writeBoolean(payload.valueId.isPresent());
		payload.valueId.ifPresent(buf::writeIdentifier);
	}

	private static UpdateContainerLabelPayload read(RegistryByteBuf buf) {
		long sessionId = buf.readVarLong();
		long revision = buf.readVarLong();
		int syncId = buf.readVarInt();
		BlockPos position = buf.readBlockPos();
		Identifier operationId = buf.readIdentifier();
		ContainerLabelOperation operation = ContainerLabelOperation.fromId(operationId)
			.orElseThrow(() -> new DecoderException("Unknown container label operation " + operationId));
		Optional<Identifier> itemId = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
		try {
			return new UpdateContainerLabelPayload(sessionId, revision, syncId, position, operation, itemId);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid container label edit payload", exception);
		}
	}
}
