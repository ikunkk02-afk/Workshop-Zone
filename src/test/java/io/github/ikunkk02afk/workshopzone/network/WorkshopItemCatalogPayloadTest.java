package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogEntry;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogResultCode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemCatalogPayloadTest {
	@Test
	void catalogRequestRoundTripsWithOnlySessionIdentity() {
		RequestWorkshopItemCatalogPayload original = new RequestWorkshopItemCatalogPayload(7, 12, 3, 9);
		RegistryByteBuf buffer = buffer();

		RequestWorkshopItemCatalogPayload.CODEC.encode(buffer, original);

		assertEquals(original, RequestWorkshopItemCatalogPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void catalogResponseRoundTripsWithoutContainerOrStackDetails() {
		WorkshopItemCatalogPayload original = new WorkshopItemCatalogPayload(
			7, 12, 3, 9, WorkshopItemCatalogResultCode.SUCCESS, 2, false,
			List.of(
				new WorkshopItemCatalogEntry(Identifier.ofVanilla("iron_ingot"), 112, 2, false),
				new WorkshopItemCatalogEntry(Identifier.ofVanilla("potion"), 3, 1, true)
			)
		);
		RegistryByteBuf buffer = buffer();

		WorkshopItemCatalogPayload.CODEC.encode(buffer, original);
		WorkshopItemCatalogPayload decoded = WorkshopItemCatalogPayload.CODEC.decode(buffer);

		assertEquals(original, decoded);
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void catalogResultIdsAreStableUniqueAndNotOrdinalBased() {
		assertEquals(
			WorkshopItemCatalogResultCode.values().length,
			Arrays.stream(WorkshopItemCatalogResultCode.values()).map(WorkshopItemCatalogResultCode::id).distinct().count()
		);
		assertEquals(Identifier.of("workshop_zone", "success"), WorkshopItemCatalogResultCode.SUCCESS.id());
		assertEquals(Identifier.of("workshop_zone", "empty"), WorkshopItemCatalogResultCode.EMPTY.id());
		for (WorkshopItemCatalogResultCode result : WorkshopItemCatalogResultCode.values()) {
			assertEquals(result, WorkshopItemCatalogResultCode.fromId(result.id()).orElseThrow());
		}
	}

	@Test
	void responseRejectsOversizedNegativeOrInconsistentData() {
		WorkshopItemCatalogEntry entry = new WorkshopItemCatalogEntry(Identifier.ofVanilla("iron_ingot"), 1, 1, false);
		assertThrows(IllegalArgumentException.class, () -> new WorkshopItemCatalogPayload(
			1, 2, 3, 4, WorkshopItemCatalogResultCode.SUCCESS, -1, false, List.of()
		));
		assertThrows(IllegalArgumentException.class, () -> new WorkshopItemCatalogPayload(
			1, 2, 3, 4, WorkshopItemCatalogResultCode.SUCCESS, 1, true, List.of(entry)
		));
		assertThrows(IllegalArgumentException.class, () -> new WorkshopItemCatalogPayload(
			1, 2, 3, 4, WorkshopItemCatalogResultCode.SUCCESS, 4097, false,
			Collections.nCopies(4097, entry)
		));
	}

	@Test
	void decodingRejectsMoreThanMaximumCatalogEntriesBeforeAllocatingThem() {
		RegistryByteBuf buffer = buffer();
		buffer.writeVarLong(1);
		buffer.writeVarLong(2);
		buffer.writeVarLong(3);
		buffer.writeVarInt(4);
		buffer.writeString(WorkshopItemCatalogResultCode.SUCCESS.id().toString(), WorkshopItemCatalogPayload.MAX_IDENTIFIER_LENGTH);
		buffer.writeVarInt(4097);
		buffer.writeBoolean(false);
		buffer.writeVarInt(4097);

		RuntimeException exception = assertThrows(RuntimeException.class, () -> WorkshopItemCatalogPayload.CODEC.decode(buffer));
		assertTrue(exception.getMessage().contains("catalog entry count"));
	}

	private static RegistryByteBuf buffer() {
		return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
	}
}
