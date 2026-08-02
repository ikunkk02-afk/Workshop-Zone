package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.Optional;

public record WorkshopNetworkEntry(
	WorkshopBlockType type,
	BlockPos position,
	Identifier blockId,
	double distanceSquared,
	boolean container,
	boolean workstation,
	Optional<String> customName,
	ContainerLabelSummary labelSummary
) {
	public WorkshopNetworkEntry {
		Objects.requireNonNull(type, "type");
		position = Objects.requireNonNull(position, "position").toImmutable();
		Objects.requireNonNull(blockId, "blockId");
		customName = Objects.requireNonNull(customName, "customName");
		Objects.requireNonNull(labelSummary, "labelSummary");
		if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
			throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
		}
		if (container != type.isContainer() || workstation != type.isProcessingDevice()) {
			throw new IllegalArgumentException("Entry flags must match its block type");
		}
		if (!container && !labelSummary.equals(ContainerLabelSummary.NONE)) {
			throw new IllegalArgumentException("Non-container entries cannot have container labels");
		}
		customName.ifPresent(name -> {
			if (name.isEmpty() || name.codePointCount(0, name.length()) > WorkshopNetworking.MAX_CUSTOM_NAME_LENGTH) {
				throw new IllegalArgumentException("Invalid custom name length");
			}
		});
	}

	public WorkshopNetworkEntry(
		WorkshopBlockType type,
		BlockPos position,
		Identifier blockId,
		double distanceSquared,
		boolean container,
		boolean workstation,
		Optional<String> customName
	) {
		this(type, position, blockId, distanceSquared, container, workstation, customName, ContainerLabelSummary.NONE);
	}

	static void write(RegistryByteBuf buf, WorkshopNetworkEntry entry) {
		buf.writeIdentifier(entry.type.networkId());
		buf.writeBlockPos(entry.position);
		buf.writeIdentifier(entry.blockId);
		buf.writeDouble(entry.distanceSquared);
		buf.writeBoolean(entry.container);
		buf.writeBoolean(entry.workstation);
		buf.writeBoolean(entry.customName.isPresent());
		entry.customName.ifPresent(name -> buf.writeString(name, WorkshopNetworking.MAX_CUSTOM_NAME_WIRE_LENGTH));
		buf.writeIdentifier(entry.labelSummary.mode().id());
		buf.writeBoolean(entry.labelSummary.exactItemId().isPresent());
		entry.labelSummary.exactItemId().ifPresent(buf::writeIdentifier);
		buf.writeBoolean(entry.labelSummary.itemTagId().isPresent());
		entry.labelSummary.itemTagId().ifPresent(buf::writeIdentifier);
		buf.writeBoolean(entry.labelSummary.representativeItemId().isPresent());
		entry.labelSummary.representativeItemId().ifPresent(buf::writeIdentifier);
		buf.writeBoolean(entry.labelSummary.conflict());
		buf.writeBoolean(entry.labelSummary.unavailable());
	}

	static WorkshopNetworkEntry read(RegistryByteBuf buf) {
		Identifier networkId = buf.readIdentifier();
		WorkshopBlockType type = WorkshopBlockType.fromNetworkId(networkId)
			.orElseThrow(() -> new DecoderException("Unknown workshop block type: " + networkId));
		BlockPos position = buf.readBlockPos();
		Identifier blockId = buf.readIdentifier();
		double distanceSquared = buf.readDouble();
		boolean container = buf.readBoolean();
		boolean workstation = buf.readBoolean();
		Optional<String> customName = buf.readBoolean()
			? Optional.of(buf.readString(WorkshopNetworking.MAX_CUSTOM_NAME_WIRE_LENGTH))
			: Optional.empty();
		Identifier labelModeId = buf.readIdentifier();
		ContainerLabelMode labelMode = ContainerLabelMode.fromId(labelModeId)
			.orElseThrow(() -> new DecoderException("Unknown container label mode: " + labelModeId));
		Optional<Identifier> exactItemId = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
		Optional<Identifier> itemTagId = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
		Optional<Identifier> representativeItemId = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
		boolean conflict = buf.readBoolean();
		boolean unavailable = buf.readBoolean();
		try {
			return new WorkshopNetworkEntry(
				type, position, blockId, distanceSquared, container, workstation, customName,
				new ContainerLabelSummary(labelMode, exactItemId, itemTagId, representativeItemId, conflict, unavailable)
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop entry", exception);
		}
	}
}
