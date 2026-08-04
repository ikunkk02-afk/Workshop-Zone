package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class WorkshopItemCandidateSearch {
	public static final int MAX_RESULTS = 50;
	public static final int MAX_RECOMMENDATIONS = 20;

	private WorkshopItemCandidateSearch() {
	}

	public static Result searchCatalog(String rawQuery, List<WorkshopItemCatalogEntry> catalog) {
		Objects.requireNonNull(catalog, "catalog");
		List<WorkshopItemCandidate> source = new ArrayList<>(catalog.size());
		for (WorkshopItemCatalogEntry entry : catalog) {
			Item item = Registries.ITEM.getOrEmpty(entry.itemId()).orElse(null);
			if (item == null || item == Items.AIR) {
				WorkshopZone.LOGGER.debug("Skipping unknown workshop catalog item {} on client", entry.itemId());
				continue;
			}
			ItemStack icon = new ItemStack(item);
			if (WorkshopItemCandidate.isValid(entry.itemId(), icon)) {
				source.add(new WorkshopItemCandidate(
					entry.itemId(), icon.getName().getString(), icon, entry.itemId().getNamespace(),
					entry.totalCount(), entry.matchingContainerCount(), entry.multipleVariants()
				));
			}
		}
		Result result = search(source, WorkshopItemSearchQuery.parse(rawQuery));
		WorkshopZone.LOGGER.debug(
			"Workshop item catalog query produced {} candidates from {} entries (truncated={})",
			result.candidates().size(), catalog.size(), result.truncated()
		);
		return result;
	}

	static Result search(Collection<WorkshopItemCandidate> source, WorkshopItemSearchQuery query) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(query, "query");
		List<RankedCandidate> matches = new ArrayList<>();
		for (WorkshopItemCandidate candidate : source) {
			Integer rank = rank(candidate.itemId(), candidate.localizedName(), candidate.namespace(), query);
			if (rank != null) {
				matches.add(new RankedCandidate(candidate, rank, candidate.localizedName().toLowerCase(Locale.ROOT)));
			}
		}
		matches.sort(Comparator.comparingInt(RankedCandidate::rank)
			.thenComparing(Comparator.comparingLong((RankedCandidate value) -> value.candidate().totalCount()).reversed())
			.thenComparing(RankedCandidate::normalizedName)
			.thenComparing(value -> value.candidate().itemId().toString()));
		int limit = query.empty() ? MAX_RECOMMENDATIONS : MAX_RESULTS;
		return new Result(
			matches.stream().limit(limit).map(RankedCandidate::candidate).toList(),
			!query.empty() && matches.size() > MAX_RESULTS
		);
	}

	static MetadataResult searchMetadata(Collection<WorkshopItemCandidateMetadata> source, WorkshopItemSearchQuery query) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(query, "query");
		List<RankedMetadata> matches = new ArrayList<>();
		for (WorkshopItemCandidateMetadata candidate : source) {
			Integer rank = rank(candidate.itemId(), candidate.localizedName(), candidate.namespace(), query);
			if (rank != null) {
				matches.add(new RankedMetadata(candidate, rank, candidate.localizedName().toLowerCase(Locale.ROOT)));
			}
		}
		matches.sort(Comparator.comparingInt(RankedMetadata::rank)
			.thenComparing(Comparator.comparingLong((RankedMetadata value) -> value.candidate().totalCount()).reversed())
			.thenComparing(RankedMetadata::normalizedName)
			.thenComparing(value -> value.candidate().itemId().toString()));
		int limit = query.empty() ? MAX_RECOMMENDATIONS : MAX_RESULTS;
		return new MetadataResult(
			matches.stream().limit(limit).map(RankedMetadata::candidate).toList(),
			!query.empty() && matches.size() > MAX_RESULTS
		);
	}

	private static Integer rank(Identifier itemId, String localizedName, String candidateNamespace, WorkshopItemSearchQuery query) {
		String namespace = candidateNamespace.toLowerCase(Locale.ROOT);
		if (!query.namespaceFilter().isEmpty() && !namespace.equals(query.namespaceFilter())) {
			return null;
		}
		String text = query.text();
		String id = itemId.toString().toLowerCase(Locale.ROOT);
		String path = itemId.getPath().toLowerCase(Locale.ROOT);
		String name = localizedName.toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			return 6;
		}
		if (id.equals(text)) {
			return 0;
		}
		if (name.equals(text)) {
			return 1;
		}
		if (name.startsWith(text)) {
			return 2;
		}
		if (path.startsWith(text)) {
			return 3;
		}
		if (name.contains(text)) {
			return 4;
		}
		return id.contains(text) || path.contains(text) || namespace.contains(text) ? 5 : null;
	}


	public record Result(List<WorkshopItemCandidate> candidates, boolean truncated) {
		public Result {
			candidates = List.copyOf(candidates);
			if (candidates.size() > MAX_RESULTS) {
				throw new IllegalArgumentException("Workshop item candidates exceed result limit");
			}
		}
	}

	public record MetadataResult(List<WorkshopItemCandidateMetadata> candidates, boolean truncated) {
		public MetadataResult {
			candidates = List.copyOf(candidates);
			if (candidates.size() > MAX_RESULTS) {
				throw new IllegalArgumentException("Workshop item candidate metadata exceeds result limit");
			}
		}
	}

	private record RankedCandidate(WorkshopItemCandidate candidate, int rank, String normalizedName) {
	}

	private record RankedMetadata(WorkshopItemCandidateMetadata candidate, int rank, String normalizedName) {
	}
}
