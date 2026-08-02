package io.github.ikunkk02afk.workshopzone.network;

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
	Optional<String> customName
) {
	public WorkshopNetworkEntry {
		Objects.requireNonNull(type, "type");
		position = Objects.requireNonNull(position, "position").toImmutable();
		Objects.requireNonNull(blockId, "blockId");
		customName = Objects.requireNonNull(customName, "customName");
		if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
			throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
		}
		if (container != type.isContainer() || workstation != type.isProcessingDevice()) {
			throw new IllegalArgumentException("Entry flags must match its block type");
		}
		customName.ifPresent(name -> {
			if (name.isEmpty() || name.codePointCount(0, name.length()) > WorkshopNetworking.MAX_CUSTOM_NAME_LENGTH) {
				throw new IllegalArgumentException("Invalid custom name length");
			}
		});
	}

	static void write(RegistryByteBuf buf, WorkshopNetworkEntry entry) {
		buf.writeVarInt(entry.type.ordinal());
		buf.writeBlockPos(entry.position);
		buf.writeIdentifier(entry.blockId);
		buf.writeDouble(entry.distanceSquared);
		buf.writeBoolean(entry.container);
		buf.writeBoolean(entry.workstation);
		buf.writeBoolean(entry.customName.isPresent());
		entry.customName.ifPresent(name -> buf.writeString(name, WorkshopNetworking.MAX_CUSTOM_NAME_WIRE_LENGTH));
	}

	static WorkshopNetworkEntry read(RegistryByteBuf buf) {
		int ordinal = buf.readVarInt();
		WorkshopBlockType[] values = WorkshopBlockType.values();
		if (ordinal < 0 || ordinal >= values.length) {
			throw new DecoderException("Unknown workshop block type: " + ordinal);
		}
		WorkshopBlockType type = values[ordinal];
		BlockPos position = buf.readBlockPos();
		Identifier blockId = buf.readIdentifier();
		double distanceSquared = buf.readDouble();
		boolean container = buf.readBoolean();
		boolean workstation = buf.readBoolean();
		Optional<String> customName = buf.readBoolean()
			? Optional.of(buf.readString(WorkshopNetworking.MAX_CUSTOM_NAME_WIRE_LENGTH))
			: Optional.empty();
		try {
			return new WorkshopNetworkEntry(type, position, blockId, distanceSquared, container, workstation, customName);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop entry", exception);
		}
	}
}
