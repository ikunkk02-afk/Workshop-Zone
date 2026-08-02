package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.deposit.WorkshopDepositResult;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkshopDepositPayloadTest {
	@Test
	void requestPayloadRoundTrips() {
		DepositWorkshopItemsPayload original = new DepositWorkshopItemsPayload(44, 12, 3, 9, true);
		RegistryByteBuf buffer = buffer();
		DepositWorkshopItemsPayload.CODEC.encode(buffer, original);
		assertEquals(original, DepositWorkshopItemsPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void resultPayloadRoundTrips() {
		WorkshopDepositResultPayload original = new WorkshopDepositResultPayload(
			44, 12, 9, WorkshopDepositResult.PARTIAL, 10, 1, 54, 2
		);
		RegistryByteBuf buffer = buffer();
		WorkshopDepositResultPayload.CODEC.encode(buffer, original);
		assertEquals(original, WorkshopDepositResultPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void resultIdsAreStableAndUnique() {
		assertEquals(
			WorkshopDepositResult.values().length,
			Arrays.stream(WorkshopDepositResult.values()).map(WorkshopDepositResult::id).distinct().count()
		);
		for (WorkshopDepositResult result : WorkshopDepositResult.values()) {
			assertEquals(result, WorkshopDepositResult.fromId(result.id()).orElseThrow());
		}
	}

	private static RegistryByteBuf buffer() {
		return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
	}
}
