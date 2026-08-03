package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
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
	private static List<WorkshopItemCandidate> cachedRegistryCandidates;

	private WorkshopItemCandidateSearch() {
	}

	public static synchronized Result searchRegistry(String rawQuery) {
		if (cachedRegistryCandidates == null) {
			cachedRegistryCandidates = buildRegistryCandidates();
		}
		Result result = search(cachedRegistryCandidates, WorkshopItemSearchQuery.parse(rawQuery));
		WorkshopZone.LOGGER.debug("Workshop item candidate query produced {} candidates (truncated={})", result.candidates().size(), result.truncated());
		return result;
	}

	public static synchronized void invalidateRegistryCache() {
		cachedRegistryCandidates = null;
	}

	static Result search(Collection<WorkshopItemCandidate> source, WorkshopItemSearchQuery query) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(query, "query");
		if (query.empty()) {
			return new Result(List.of(), false);
		}
		List<RankedCandidate> matches = new ArrayList<>();
		for (WorkshopItemCandidate candidate : source) {
			Integer rank = rank(candidate.itemId(), candidate.localizedName(), candidate.namespace(), query);
			if (rank != null) {
				matches.add(new RankedCandidate(candidate, rank, candidate.localizedName().toLowerCase(Locale.ROOT)));
			}
		}
		matches.sort(Comparator.comparingInt(RankedCandidate::rank)
			.thenComparing(RankedCandidate::normalizedName)
			.thenComparing(value -> value.candidate().itemId().toString()));
		return new Result(matches.stream().limit(MAX_RESULTS).map(RankedCandidate::candidate).toList(), matches.size() > MAX_RESULTS);
	}

	static MetadataResult searchMetadata(Collection<WorkshopItemCandidateMetadata> source, WorkshopItemSearchQuery query) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(query, "query");
		if (query.empty()) {
			return new MetadataResult(List.of(), false);
		}
		List<RankedMetadata> matches = new ArrayList<>();
		for (WorkshopItemCandidateMetadata candidate : source) {
			Integer rank = rank(candidate.itemId(), candidate.localizedName(), candidate.namespace(), query);
			if (rank != null) {
				matches.add(new RankedMetadata(candidate, rank, candidate.localizedName().toLowerCase(Locale.ROOT)));
			}
		}
		matches.sort(Comparator.comparingInt(RankedMetadata::rank)
			.thenComparing(RankedMetadata::normalizedName)
			.thenComparing(value -> value.candidate().itemId().toString()));
		return new MetadataResult(matches.stream().limit(MAX_RESULTS).map(RankedMetadata::candidate).toList(), matches.size() > MAX_RESULTS);
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

	private static List<WorkshopItemCandidate> buildRegistryCandidates() {
		List<WorkshopItemCandidate> candidates = new ArrayList<>(Registries.ITEM.size());
		for (Item item : Registries.ITEM) {
			Identifier id = Registries.ITEM.getId(item);
			if (item == Items.AIR || Identifier.ofVanilla("air").equals(id)) {
				continue;
			}
			ItemStack icon = new ItemStack(item);
			if (WorkshopItemCandidate.isValid(id, icon)) {
				candidates.add(new WorkshopItemCandidate(id, icon.getName().getString(), icon));
			}
		}
		return List.copyOf(candidates);
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
