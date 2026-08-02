package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopPayloadTest {
	@Test
	void snapshotRoundTripsWithoutChangingOrder() {
		List<WorkshopNetworkEntry> entries = List.of(entry(WorkshopBlockType.BARREL, 1), entry(WorkshopBlockType.FURNACE, 3));
		WorkshopSnapshotPayload original = new WorkshopSnapshotPayload(
			10, 2, 7, Identifier.ofVanilla("overworld"), BlockPos.ORIGIN, BlockPos.ORIGIN, WorkshopBlockType.CHEST,
			2, 1, 1, false, entries
		);
		RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);

		WorkshopSnapshotPayload.CODEC.encode(buffer, original);
		WorkshopSnapshotPayload decoded = WorkshopSnapshotPayload.CODEC.decode(buffer);

		assertEquals(original, decoded);
		assertEquals(List.of(WorkshopBlockType.BARREL, WorkshopBlockType.FURNACE), decoded.entries().stream().map(WorkshopNetworkEntry::type).toList());
	}

	@Test
	void entryLimitKeepsNearestPrefix() {
		List<WorkshopNetworkEntry> source = new ArrayList<>();
		for (int index = 0; index < WorkshopNetworking.MAX_ENTRIES + 2; index++) {
			source.add(entry(WorkshopBlockType.CHEST, index));
		}

		List<WorkshopNetworkEntry> limited = WorkshopNetworking.limitEntries(source);

		assertEquals(WorkshopNetworking.MAX_ENTRIES, limited.size());
		assertEquals(0.0, limited.getFirst().distanceSquared());
		assertEquals(WorkshopNetworking.MAX_ENTRIES - 1.0, limited.getLast().distanceSquared());
		assertThrows(UnsupportedOperationException.class, limited::clear);
	}

	@Test
	void customNamesAreUnicodeSafelyTruncated() {
		String value = "工".repeat(130);
		String truncated = WorkshopNetworking.truncate(value, 128);
		assertEquals(128, truncated.codePointCount(0, truncated.length()));
	}

	@Test
	void staleSnapshotVersionsAreRejected() {
		assertTrue(WorkshopSnapshotOrder.isNewer(4, 0, 3, 99));
		assertTrue(WorkshopSnapshotOrder.isNewer(4, 2, 4, 1));
		assertFalse(WorkshopSnapshotOrder.isNewer(4, 1, 4, 1));
		assertFalse(WorkshopSnapshotOrder.isNewer(3, 99, 4, 1));
	}

	@Test
	void entryEncodingUsesStableIdentifierAndUnknownIdsFailSafely() {
		WorkshopNetworkEntry original = entry(WorkshopBlockType.SMITHING_TABLE, 1);
		RegistryByteBuf encoded = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		WorkshopNetworkEntry.write(encoded, original);
		assertEquals(WorkshopBlockType.SMITHING_TABLE.networkId(), encoded.readIdentifier());

		RegistryByteBuf unknown = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		unknown.writeIdentifier(Identifier.of("workshop_zone", "not_a_real_type"));
		assertThrows(io.netty.handler.codec.DecoderException.class, () -> WorkshopNetworkEntry.read(unknown));
	}

	@Test
	void unknownOpenedTypeFailsSnapshotDecodingSafely() {
		RegistryByteBuf unknown = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		unknown.writeVarLong(1);
		unknown.writeVarLong(0);
		unknown.writeVarInt(2);
		unknown.writeIdentifier(Identifier.ofVanilla("overworld"));
		unknown.writeBlockPos(BlockPos.ORIGIN);
		unknown.writeBlockPos(BlockPos.ORIGIN);
		unknown.writeIdentifier(Identifier.of("workshop_zone", "not_a_real_type"));

		assertThrows(io.netty.handler.codec.DecoderException.class, () -> WorkshopSnapshotPayload.CODEC.decode(unknown));
	}

	@Test
	void openTargetRequestRoundTripsWithOnlyServerVerifiableFields() {
		OpenWorkshopTargetPayload original = new OpenWorkshopTargetPayload(
			42, 7, 12, new BlockPos(-123, 64, 456)
		);
		RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);

		OpenWorkshopTargetPayload.CODEC.encode(buffer, original);

		assertEquals(original, OpenWorkshopTargetPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void openTargetRequestRejectsNegativeIdentityFields() {
		assertThrows(IllegalArgumentException.class, () -> new OpenWorkshopTargetPayload(-1, 0, 0, BlockPos.ORIGIN));
		assertThrows(IllegalArgumentException.class, () -> new OpenWorkshopTargetPayload(1, -1, 0, BlockPos.ORIGIN));
		assertThrows(IllegalArgumentException.class, () -> new OpenWorkshopTargetPayload(1, 0, -1, BlockPos.ORIGIN));
	}

	private static WorkshopNetworkEntry entry(WorkshopBlockType type, int distance) {
		return new WorkshopNetworkEntry(
			type, new BlockPos(distance, 0, 0), Identifier.ofVanilla(type.name().toLowerCase()), distance,
			type.isContainer(), type.isProcessingDevice(), Optional.empty()
		);
	}
}
