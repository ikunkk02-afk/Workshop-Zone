package io.github.ikunkk02afk.workshopzone.scan;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopBlockTypeTest {
	@Test
	void everyTypeHasAUniqueStableNetworkId() {
		HashSet<Identifier> ids = new HashSet<>();
		for (WorkshopBlockType type : WorkshopBlockType.values()) {
			assertTrue(ids.add(type.networkId()), () -> "Duplicate network id: " + type.networkId());
			assertEquals(type, WorkshopBlockType.fromNetworkId(type.networkId()).orElseThrow());
			assertEquals("workshop_zone", type.networkId().getNamespace());
			assertEquals(type.isWorkstation(), type.isProcessingDevice());
		}
		assertEquals(Arrays.stream(WorkshopBlockType.values()).count(), WorkshopBlockType.networkTypes().size());
	}

	@Test
	void networkTypeMapIsReadOnlyAndUnknownIdsAreAbsent() {
		Map<Identifier, WorkshopBlockType> types = WorkshopBlockType.networkTypes();
		assertThrows(UnsupportedOperationException.class, () ->
			types.put(Identifier.of("workshop_zone", "invalid"), WorkshopBlockType.CHEST)
		);
		assertTrue(WorkshopBlockType.fromNetworkId(Identifier.of("workshop_zone", "unknown")).isEmpty());
	}
}
