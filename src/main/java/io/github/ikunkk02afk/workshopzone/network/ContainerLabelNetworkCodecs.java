package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntry;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntryType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

final class ContainerLabelNetworkCodecs {
	private ContainerLabelNetworkCodecs() {
	}

	static void writeEntry(RegistryByteBuf buf, ContainerLabelEntry entry) {
		writeIdentifier(buf, entry.type().id());
		writeIdentifier(buf, entry.valueId());
	}

	static ContainerLabelEntry readEntry(RegistryByteBuf buf) {
		Identifier typeId = readIdentifier(buf, "container label entry type");
		ContainerLabelEntryType type = ContainerLabelEntryType.fromId(typeId)
			.orElseThrow(() -> new DecoderException("Unknown container label entry type " + typeId));
		Identifier valueId = readIdentifier(buf, "container label entry value");
		try {
			return new ContainerLabelEntry(type, valueId);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid container label entry", exception);
		}
	}

	static void writeIdentifier(RegistryByteBuf buf, Identifier id) {
		String value = id.toString();
		if (value.length() > ContainerLabelEntry.MAX_IDENTIFIER_LENGTH) {
			throw new IllegalArgumentException("Container label identifier exceeds protocol limit");
		}
		buf.writeString(value, ContainerLabelEntry.MAX_IDENTIFIER_LENGTH);
	}

	static Identifier readIdentifier(RegistryByteBuf buf, String fieldName) {
		String value = buf.readString(ContainerLabelEntry.MAX_IDENTIFIER_LENGTH);
		Identifier id = Identifier.tryParse(value);
		if (id == null) {
			throw new DecoderException("Invalid " + fieldName + " " + value);
		}
		return id;
	}

	static void writeEntries(RegistryByteBuf buf, List<ContainerLabelEntry> entries) {
		buf.writeVarInt(entries.size());
		entries.forEach(entry -> writeEntry(buf, entry));
	}

	static List<ContainerLabelEntry> readEntries(RegistryByteBuf buf, int maxEntries) {
		int count = buf.readVarInt();
		if (count < 0 || count > maxEntries) {
			throw new DecoderException("Container label entry count exceeds limit: " + count);
		}
		List<ContainerLabelEntry> entries = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			entries.add(readEntry(buf));
		}
		return List.copyOf(entries);
	}
}
