package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerItemTagsTest {
	private static final Identifier ICON = Identifier.ofVanilla("stone");

	@Test
	void candidatesSortPresetThenMinecraftThenOtherNamespaces() {
		ContainerItemTags.QueryResult result = ContainerItemTags.sortAndLimit(List.of(
			candidate("example:zeta"), candidate("minecraft:zz_custom"), candidate("c:ingots"),
			candidate("minecraft:aa_custom"), candidate("minecraft:logs")
		), 64);
		assertEquals(List.of(
			Identifier.ofVanilla("logs"), Identifier.of("c", "ingots"), Identifier.ofVanilla("aa_custom"),
			Identifier.ofVanilla("zz_custom"), Identifier.of("example", "zeta")
		), result.candidates().stream().map(ContainerTagCandidate::tagId).toList());
	}

	@Test
	void candidatesAreDeduplicatedByTagId() {
		ContainerItemTags.QueryResult result = ContainerItemTags.sortAndLimit(List.of(
			candidate("minecraft:logs"), candidate("minecraft:logs")
		), 64);
		assertEquals(1, result.candidates().size());
	}

	@Test
	void candidatesAreLimitedToSixtyFourAndMarkedTruncated() {
		List<ContainerTagCandidate> candidates = new ArrayList<>();
		for (int index = 0; index < 70; index++) {
			candidates.add(candidate("example:tag_" + index));
		}
		ContainerItemTags.QueryResult result = ContainerItemTags.sortAndLimit(candidates, 64);
		assertEquals(64, result.candidates().size());
		assertTrue(result.truncated());
	}

	private static ContainerTagCandidate candidate(String id) {
		return new ContainerTagCandidate(Identifier.of(id), ICON);
	}
}
