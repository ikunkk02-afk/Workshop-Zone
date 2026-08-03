package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntry;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record UpdateContainerLabelPayload(
	long sessionId,
	long revision,
	int syncId,
	BlockPos openedEntryPosition,
	ContainerLabelOperation operation,
	Optional<Identifier> valueId,
	List<ContainerLabelEntry> entries
) implements CustomPayload {
	public static final Id<UpdateContainerLabelPayload> ID = new Id<>(WorkshopZone.id("update_container_label"));
	public static final PacketCodec<RegistryByteBuf, UpdateContainerLabelPayload> CODEC = CustomPayload.codecOf(
		UpdateContainerLabelPayload::write,
		UpdateContainerLabelPayload::read
	);

	public UpdateContainerLabelPayload(
		long sessionId,
		long revision,
		int syncId,
		BlockPos openedEntryPosition,
		ContainerLabelOperation operation,
		Optional<Identifier> valueId
	) {
		this(sessionId, revision, syncId, openedEntryPosition, operation, valueId, List.of());
	}

	public UpdateContainerLabelPayload {
		openedEntryPosition = Objects.requireNonNull(openedEntryPosition, "openedEntryPosition").toImmutable();
		Objects.requireNonNull(operation, "operation");
		valueId = Objects.requireNonNull(valueId, "valueId");
		entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
		if (sessionId < 0 || revision < 0 || syncId < 0 || entries.size() > ContainerLabelRule.MAX_ENTRIES
			|| valueId.map(id -> id.toString().length() > ContainerLabelEntry.MAX_IDENTIFIER_LENGTH).orElse(false)) {
			throw new IllegalArgumentException("Invalid container label edit payload metadata");
		}
		boolean validFields = switch (operation) {
			case CLEAR -> valueId.isEmpty() && entries.isEmpty();
			case SET_EXACT_ITEM, SET_ITEM_TAG -> valueId.isPresent() && entries.isEmpty();
			case SET_WHITELIST -> valueId.isEmpty() && !entries.isEmpty();
		};
		if (!validFields) {
			throw new IllegalArgumentException("Container label edit fields do not match the operation");
		}
	}

	public Optional<Identifier> itemId() {
		return operation == ContainerLabelOperation.SET_EXACT_ITEM ? valueId : Optional.empty();
	}

	public Optional<Identifier> tagId() {
		return operation == ContainerLabelOperation.SET_ITEM_TAG ? valueId : Optional.empty();
	}

	public List<ContainerLabelEntry> whitelistEntries() {
		return operation == ContainerLabelOperation.SET_WHITELIST ? entries : List.of();
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
		ContainerLabelNetworkCodecs.writeIdentifier(buf, payload.operation.id());
		buf.writeBoolean(payload.valueId.isPresent());
		payload.valueId.ifPresent(id -> ContainerLabelNetworkCodecs.writeIdentifier(buf, id));
		ContainerLabelNetworkCodecs.writeEntries(buf, payload.entries);
	}

	private static UpdateContainerLabelPayload read(RegistryByteBuf buf) {
		long sessionId = buf.readVarLong();
		long revision = buf.readVarLong();
		int syncId = buf.readVarInt();
		BlockPos position = buf.readBlockPos();
		Identifier operationId = ContainerLabelNetworkCodecs.readIdentifier(buf, "container label operation");
		ContainerLabelOperation operation = ContainerLabelOperation.fromId(operationId)
			.orElseThrow(() -> new DecoderException("Unknown container label operation " + operationId));
		Optional<Identifier> valueId = buf.readBoolean()
			? Optional.of(ContainerLabelNetworkCodecs.readIdentifier(buf, "container label value"))
			: Optional.empty();
		List<ContainerLabelEntry> entries = ContainerLabelNetworkCodecs.readEntries(buf, ContainerLabelRule.MAX_ENTRIES);
		try {
			return new UpdateContainerLabelPayload(sessionId, revision, syncId, position, operation, valueId, entries);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid container label edit payload", exception);
		}
	}
}
