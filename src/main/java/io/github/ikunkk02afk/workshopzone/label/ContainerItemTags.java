package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ContainerItemTags {
	public static final int MAX_CANDIDATES = 64;

	private static final Map<Identifier, TagKey<Item>> TAG_KEYS = new ConcurrentHashMap<>();
	private static final Map<Identifier, Optional<Identifier>> REPRESENTATIVE_CACHE = new HashMap<>();
	private static final Comparator<ContainerTagCandidate> CANDIDATE_ORDER = Comparator
		.comparingInt((ContainerTagCandidate candidate) -> ContainerTagPreset.find(candidate.tagId())
			.map(ContainerTagPreset::priority).orElse(Integer.MAX_VALUE))
		.thenComparingInt(candidate -> candidate.tagId().getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? 0 : 1)
		.thenComparing(candidate -> candidate.tagId().toString());

	private ContainerItemTags() {
	}

	public static TagKey<Item> key(Identifier tagId) {
		return TAG_KEYS.computeIfAbsent(tagId, id -> TagKey.of(RegistryKeys.ITEM, id));
	}

	public static Availability availability(Identifier tagId) {
		Optional<RegistryEntryList.Named<Item>> entries = Registries.ITEM.getEntryList(key(tagId));
		if (entries.isEmpty()) {
			return Availability.UNAVAILABLE;
		}
		return entries.orElseThrow().size() == 0 ? Availability.EMPTY : Availability.AVAILABLE;
	}

	public static Optional<Identifier> representativeItemId(Identifier tagId) {
		synchronized (REPRESENTATIVE_CACHE) {
			return REPRESENTATIVE_CACHE.computeIfAbsent(tagId, ContainerItemTags::findRepresentativeItemId);
		}
	}

	private static Optional<Identifier> findRepresentativeItemId(Identifier tagId) {
		RegistryEntryList.Named<Item> entries = Registries.ITEM.getEntryList(key(tagId)).orElse(null);
		if (entries == null || entries.size() == 0) {
			return Optional.empty();
		}
		Optional<ContainerTagPreset> preset = ContainerTagPreset.find(tagId);
		if (preset.isPresent()) {
			Item iconItem = Registries.ITEM.getOrEmpty(preset.orElseThrow().iconItemId()).orElse(null);
			RegistryEntry<Item> icon = iconItem == null ? null : Registries.ITEM.getEntry(iconItem);
			if (icon != null && icon.value() != Items.AIR && entries.contains(icon)) {
				return Optional.of(preset.orElseThrow().iconItemId());
			}
		}
		for (RegistryEntry<Item> entry : entries) {
			if (entry.value() != Items.AIR) {
				return Optional.of(Registries.ITEM.getId(entry.value()));
			}
		}
		return Optional.empty();
	}

	public static List<ContainerTagCandidate> availablePresets() {
		List<ContainerTagCandidate> result = new ArrayList<>();
		for (ContainerTagPreset preset : ContainerTagPreset.ordered()) {
			representativeItemId(preset.tagId()).ifPresent(icon ->
				result.add(new ContainerTagCandidate(preset.tagId(), icon))
			);
		}
		return List.copyOf(result);
	}

	public static QueryResult candidatesFor(Identifier itemId) {
		Item item = Registries.ITEM.getOrEmpty(itemId).orElse(null);
		if (item == null || item == Items.AIR) {
			return new QueryResult(List.of(), false);
		}
		Map<Identifier, ContainerTagCandidate> unique = new LinkedHashMap<>();
		Registries.ITEM.getEntry(item).streamTags().forEach(tag -> representativeItemId(tag.id()).ifPresent(icon ->
			unique.putIfAbsent(tag.id(), new ContainerTagCandidate(tag.id(), icon))
		));
		return sortAndLimit(unique.values(), MAX_CANDIDATES);
	}

	static QueryResult sortAndLimit(Collection<ContainerTagCandidate> candidates, int limit) {
		if (limit < 0) {
			throw new IllegalArgumentException("Candidate limit must be non-negative");
		}
		Map<Identifier, ContainerTagCandidate> unique = new LinkedHashMap<>();
		candidates.forEach(candidate -> unique.putIfAbsent(candidate.tagId(), candidate));
		List<ContainerTagCandidate> sorted = new ArrayList<>(unique.values());
		sorted.sort(CANDIDATE_ORDER);
		boolean truncated = sorted.size() > limit;
		return new QueryResult(List.copyOf(sorted.subList(0, Math.min(sorted.size(), limit))), truncated);
	}

	public static void clearReloadableCaches() {
		synchronized (REPRESENTATIVE_CACHE) {
			REPRESENTATIVE_CACHE.clear();
		}
	}

	public enum Availability {
		AVAILABLE,
		EMPTY,
		UNAVAILABLE
	}

	public record QueryResult(List<ContainerTagCandidate> candidates, boolean truncated) {
		public QueryResult {
			candidates = List.copyOf(candidates);
			if (candidates.size() > MAX_CANDIDATES) {
				throw new IllegalArgumentException("Too many item tag candidates");
			}
		}
	}
}
