package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntryType;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ContainerLabelDetailsPayload(
	long requestId,
	long sessionId,
	long revision,
	int syncId,
	BlockPos openedEntryPosition,
	ContainerLabelEditResult result,
	ContainerLabelMode mode,
	List<ContainerLabelDetailsEntry> entries,
	int unavailableEntryCount,
	boolean contentConflict,
	boolean ruleConflict
) implements CustomPayload {
	public static final Id<ContainerLabelDetailsPayload> ID = new Id<>(WorkshopZone.id("container_label_details"));
	public static final PacketCodec<RegistryByteBuf, ContainerLabelDetailsPayload> CODEC = CustomPayload.codecOf(
		ContainerLabelDetailsPayload::write,
		ContainerLabelDetailsPayload::read
	);

	public ContainerLabelDetailsPayload {
		openedEntryPosition = Objects.requireNonNull(openedEntryPosition, "openedEntryPosition").toImmutable();
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(mode, "mode");
		entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
		if (requestId < 0 || sessionId < 0 || revision < 0 || syncId < 0
			|| entries.size() > ContainerLabelRule.MAX_ENTRIES || unavailableEntryCount < 0
			|| unavailableEntryCount > entries.size() || unavailableEntryCount != entries.stream().filter(ContainerLabelDetailsEntry::unavailable).count()
			|| contentConflict && ruleConflict) {
			throw new IllegalArgumentException("Invalid container label details metadata");
		}
		if (result == ContainerLabelEditResult.SUCCESS) {
			boolean validModeEntries = switch (mode) {
				case NONE -> entries.isEmpty();
				case EXACT_ITEM -> entries.size() == 1 && entries.getFirst().entry().type() == ContainerLabelEntryType.ITEM;
				case ITEM_TAG -> entries.size() == 1 && entries.getFirst().entry().type() == ContainerLabelEntryType.ITEM_TAG;
				case WHITELIST -> !entries.isEmpty();
			};
			if (!validModeEntries || ruleConflict && mode != ContainerLabelMode.NONE || contentConflict && mode == ContainerLabelMode.NONE) {
				throw new IllegalArgumentException("Container label detail entries do not match their mode");
			}
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(ContainerLabelDetailsPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.requestId);
		buf.writeVarLong(payload.sessionId);
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.syncId);
		buf.writeBlockPos(payload.openedEntryPosition);
		buf.writeIdentifier(payload.result.id());
		buf.writeIdentifier(payload.mode.id());
		buf.writeVarInt(payload.entries.size());
		for (ContainerLabelDetailsEntry detail : payload.entries) {
			ContainerLabelNetworkCodecs.writeEntry(buf, detail.entry());
			buf.writeBoolean(detail.unavailable());
			buf.writeBoolean(detail.representativeItemId().isPresent());
			detail.representativeItemId().ifPresent(buf::writeIdentifier);
		}
		buf.writeVarInt(payload.unavailableEntryCount);
		buf.writeBoolean(payload.contentConflict);
		buf.writeBoolean(payload.ruleConflict);
	}

	private static ContainerLabelDetailsPayload read(RegistryByteBuf buf) {
		long requestId = buf.readVarLong();
		long sessionId = buf.readVarLong();
		long revision = buf.readVarLong();
		int syncId = buf.readVarInt();
		BlockPos position = buf.readBlockPos();
		Identifier resultId = buf.readIdentifier();
		ContainerLabelEditResult result = ContainerLabelEditResult.fromId(resultId)
			.orElseThrow(() -> new DecoderException("Unknown container label detail result " + resultId));
		Identifier modeId = buf.readIdentifier();
		ContainerLabelMode mode = ContainerLabelMode.fromId(modeId)
			.orElseThrow(() -> new DecoderException("Unknown container label detail mode " + modeId));
		int count = buf.readVarInt();
		if (count < 0 || count > ContainerLabelRule.MAX_ENTRIES) {
			throw new DecoderException("Container label detail entry count exceeds limit: " + count);
		}
		List<ContainerLabelDetailsEntry> entries = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			var entry = ContainerLabelNetworkCodecs.readEntry(buf);
			boolean unavailable = buf.readBoolean();
			Optional<Identifier> representative = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
			try {
				entries.add(new ContainerLabelDetailsEntry(entry, unavailable, representative));
			} catch (IllegalArgumentException exception) {
				throw new DecoderException("Invalid container label detail entry", exception);
			}
		}
		int unavailableCount = buf.readVarInt();
		boolean contentConflict = buf.readBoolean();
		boolean ruleConflict = buf.readBoolean();
		try {
			return new ContainerLabelDetailsPayload(
				requestId, sessionId, revision, syncId, position, result, mode, entries,
				unavailableCount, contentConflict, ruleConflict
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid container label details payload", exception);
		}
	}
}
