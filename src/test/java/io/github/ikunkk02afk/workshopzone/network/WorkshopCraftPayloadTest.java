package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftExecutionResultCode;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMode;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftPreviewResultCode;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopCraftPayloadTest {
	@Test
	void confirmationPayloadRoundTripsWithOnlyNonceAndDecision() {
		ConfirmWorkshopCraftPayload original = new ConfirmWorkshopCraftPayload(51, true);
		RegistryByteBuf buffer = buffer();
		ConfirmWorkshopCraftPayload.CODEC.encode(buffer, original);
		assertEquals(original, ConfirmWorkshopCraftPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void executionResultPayloadRoundTrips() {
		WorkshopCraftExecutionResultPayload original = new WorkshopCraftExecutionResultPayload(
			51, 11, 9, WorkshopCraftExecutionResultCode.SUCCESS,
			Identifier.ofVanilla("crafting_table"), WorkshopCraftMode.BATCH, 32, 128, 8, 120, 1
		);
		RegistryByteBuf buffer = buffer();
		WorkshopCraftExecutionResultPayload.CODEC.encode(buffer, original);
		assertEquals(original, WorkshopCraftExecutionResultPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}


	@Test
	void resultIdsAreStableUniqueAndUnknownIdsAreRejected() {
		assertEquals(WorkshopCraftPreviewResultCode.values().length,
			Arrays.stream(WorkshopCraftPreviewResultCode.values()).map(WorkshopCraftPreviewResultCode::id).distinct().count());
		assertEquals(WorkshopCraftExecutionResultCode.values().length,
			Arrays.stream(WorkshopCraftExecutionResultCode.values()).map(WorkshopCraftExecutionResultCode::id).distinct().count());
		assertEquals(Identifier.of("workshop_zone", "available"), WorkshopCraftPreviewResultCode.AVAILABLE.id());
		assertEquals(Identifier.of("workshop_zone", "success"), WorkshopCraftExecutionResultCode.SUCCESS.id());
		assertTrue(WorkshopCraftPreviewResultCode.fromId(Identifier.of("other", "unknown")).isEmpty());
		assertTrue(WorkshopCraftExecutionResultCode.fromId(Identifier.of("other", "unknown")).isEmpty());
	}

	@Test
	void onlyAvailablePreviewAndRealGridProtectionCancelVanillaPlacement() {
		assertTrue(WorkshopCraftPreviewResultCode.AVAILABLE.cancelsVanillaRequest());
		assertTrue(WorkshopCraftPreviewResultCode.GRID_NOT_EMPTY.cancelsVanillaRequest());
		assertTrue(Arrays.stream(WorkshopCraftPreviewResultCode.values())
			.filter(result -> result != WorkshopCraftPreviewResultCode.AVAILABLE
				&& result != WorkshopCraftPreviewResultCode.GRID_NOT_EMPTY)
			.noneMatch(WorkshopCraftPreviewResultCode::cancelsVanillaRequest));
	}


	@Test
	void unknownWireResultIdFailsDecode() {
		RegistryByteBuf buffer = buffer();
		buffer.writeVarLong(1);
		buffer.writeVarLong(2);
		buffer.writeVarLong(3);
		buffer.writeVarInt(4);
		buffer.writeIdentifier(Identifier.ofVanilla("crafting_table"));
		buffer.writeIdentifier(Identifier.of("other", "unknown"));
		assertThrows(DecoderException.class, () -> WorkshopCraftPreviewPayload.CODEC.decode(buffer));
	}

	@Test
	void unknownWireCraftModeFailsBeforeItemStackOrCountersAreDecoded() {
		RegistryByteBuf preview = buffer();
		preview.writeVarLong(1);
		preview.writeVarLong(2);
		preview.writeVarLong(3);
		preview.writeVarInt(4);
		preview.writeIdentifier(Identifier.ofVanilla("crafting_table"));
		preview.writeIdentifier(WorkshopCraftPreviewResultCode.AVAILABLE.id());
		preview.writeIdentifier(Identifier.of("other", "unknown_mode"));
		assertThrows(DecoderException.class, () -> WorkshopCraftPreviewPayload.CODEC.decode(preview));

		RegistryByteBuf execution = buffer();
		execution.writeVarLong(1);
		execution.writeVarLong(2);
		execution.writeVarInt(4);
		execution.writeIdentifier(WorkshopCraftExecutionResultCode.SUCCESS.id());
		execution.writeIdentifier(Identifier.ofVanilla("crafting_table"));
		execution.writeIdentifier(Identifier.of("other", "unknown_mode"));
		assertThrows(DecoderException.class, () -> WorkshopCraftExecutionResultPayload.CODEC.decode(execution));
	}

	private static RegistryByteBuf buffer() {
		return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
	}
}
