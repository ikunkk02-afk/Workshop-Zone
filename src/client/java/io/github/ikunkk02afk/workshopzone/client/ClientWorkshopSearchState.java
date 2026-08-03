package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.network.SearchWorkshopItemPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchResultCode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientWorkshopSearchState {
	private static boolean searchMode;
	private static String searchText = "";
	private static List<WorkshopItemCandidate> candidates = List.of();
	private static boolean candidatesTruncated;
	private static int selectedCandidateIndex = -1;
	private static int candidateScrollOffset;
	private static int resultScrollOffset;
	private static WorkshopItemCandidate selectedItem;
	private static boolean pending;
	private static long pendingRequestId = -1;
	private static long pendingSessionId = -1;
	private static long pendingRevision = -1;
	private static int pendingSyncId = -1;
	private static long currentSessionId = -1;
	private static long currentRevision = -1;
	private static int currentSyncId = -1;
	private static ClientWorkshopSearchResult result;
	private static WorkshopItemSearchResultCode error;
	private static long resultSequence;
	private static long nextRequestId = 1;
	private static WorkshopItemSearchResultPayload networkResult;
	private static long networkSequence;
	private static long observedNetworkSequence;

	private ClientWorkshopSearchState() {
	}

	public static void beginScreen(int syncId) {
		resetUi();
		currentSyncId = syncId;
		observedNetworkSequence = networkSequence;
	}

	public static void enter(ClientWorkshopSnapshot snapshot) {
		synchronizeSnapshot(snapshot);
		searchMode = true;
		WorkshopZone.LOGGER.debug("Workshop search mode opened for session {} syncId {}", snapshot.sessionId(), snapshot.syncId());
	}

	public static void closeMode() {
		if (searchMode) {
			WorkshopZone.LOGGER.debug("Workshop search mode closed for session {} syncId {}", currentSessionId, currentSyncId);
		}
		searchMode = false;
	}

	public static void synchronizeSnapshot(ClientWorkshopSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		if (currentSessionId != snapshot.sessionId() || currentSyncId != snapshot.syncId()) {
			boolean keepMode = searchMode && currentSyncId == snapshot.syncId() && currentSessionId < 0;
			resetUi();
			searchMode = keepMode;
			currentSessionId = snapshot.sessionId();
			currentRevision = snapshot.revision();
			currentSyncId = snapshot.syncId();
			observedNetworkSequence = networkSequence;
			return;
		}
		if (currentRevision != snapshot.revision()) {
			currentRevision = snapshot.revision();
			selectedItem = null;
			clearServerResult();
		}
	}

	public static void setSearchText(String value) {
		searchText = value == null ? "" : value;
		WorkshopItemCandidateSearch.Result search = WorkshopItemCandidateSearch.searchRegistry(searchText);
		candidates = search.candidates();
		candidatesTruncated = search.truncated();
		selectedCandidateIndex = candidates.isEmpty() ? -1 : 0;
		candidateScrollOffset = 0;
		selectedItem = null;
		clearServerResult();
	}

	public static void refreshLocalizedCandidates() {
		if (searchText.isBlank()) {
			candidates = List.of();
			candidatesTruncated = false;
			selectedCandidateIndex = -1;
			return;
		}
		WorkshopItemCandidateSearch.Result search = WorkshopItemCandidateSearch.searchRegistry(searchText);
		candidates = search.candidates();
		candidatesTruncated = search.truncated();
		selectedCandidateIndex = candidates.isEmpty()
			? -1 : Math.min(Math.max(0, selectedCandidateIndex), candidates.size() - 1);
		if (selectedItem != null) {
			Identifier selectedId = selectedItem.itemId();
			selectedItem = candidates.stream()
				.filter(candidate -> candidate.itemId().equals(selectedId))
				.findFirst()
				.orElse(selectedItem);
		}
	}

	public static SearchWorkshopItemPayload selectCandidate(WorkshopItemCandidate candidate, ClientWorkshopSnapshot snapshot) {
		selectedItem = candidate;
		pending = true;
		pendingRequestId = nextRequestId();
		pendingSessionId = snapshot.sessionId();
		pendingRevision = snapshot.revision();
		pendingSyncId = snapshot.syncId();
		result = null;
		error = null;
		resultScrollOffset = 0;
		WorkshopZone.LOGGER.debug(
			"Sending workshop item search requestId {} item {} session {} revision {} syncId {}",
			pendingRequestId, candidate.itemId(), pendingSessionId, pendingRevision, pendingSyncId
		);
		return new SearchWorkshopItemPayload(
			pendingRequestId, pendingSessionId, pendingRevision, pendingSyncId, candidate.itemId()
		);
	}

	public static void acceptNetwork(WorkshopItemSearchResultPayload payload) {
		networkResult = payload;
		networkSequence++;
	}

	public static boolean consumeNetwork(ClientWorkshopSnapshot snapshot) {
		if (observedNetworkSequence == networkSequence) {
			return false;
		}
		observedNetworkSequence = networkSequence;
		WorkshopItemSearchResultPayload payload = networkResult;
		if (!WorkshopItemSearchResultFilter.matches(
			payload, pendingRequestId, snapshot.sessionId(), snapshot.revision(), snapshot.syncId()
		)) {
			WorkshopZone.LOGGER.debug(
				"Rejected stale workshop item search result requestId {} for pending requestId {}",
				payload == null ? -1 : payload.requestId(), pendingRequestId
			);
			return false;
		}
		pending = false;
		Map<BlockPos, ClientWorkshopEntry> entries = new HashMap<>();
		for (ClientWorkshopEntry entry : snapshot.entries()) {
			entries.put(entry.position(), entry);
		}
		Set<BlockPos> positions = entries.keySet();
		List<WorkshopItemSearchContainerResult> existing = WorkshopItemSearchResultFilter.filterExisting(payload.results(), positions);
		List<ClientWorkshopContainerSearchResult> clientResults = new ArrayList<>(existing.size());
		for (WorkshopItemSearchContainerResult serverResult : existing) {
			ClientWorkshopEntry entry = entries.get(serverResult.representativePosition());
			if (entry != null) {
				clientResults.add(new ClientWorkshopContainerSearchResult(serverResult, entry));
			}
		}
		if (payload.resultId() == WorkshopItemSearchResultCode.SUCCESS) {
			result = new ClientWorkshopSearchResult(
				payload.targetItemId(), payload.totalItemCount(), payload.totalMatchingContainers(),
				payload.truncated(), clientResults
			);
			error = null;
		} else {
			result = null;
			error = payload.resultId();
		}
		resultSequence++;
		WorkshopZone.LOGGER.debug(
			"Accepted workshop item search result requestId {} result {} returned {} visible containers",
			payload.requestId(), payload.resultId(), clientResults.size()
		);
		return true;
	}

	public static void backToCandidates() {
		selectedItem = null;
		clearServerResult();
	}

	public static boolean moveCandidateSelection(int delta) {
		if (candidates.isEmpty()) {
			selectedCandidateIndex = -1;
			return false;
		}
		selectedCandidateIndex = Math.floorMod(Math.max(0, selectedCandidateIndex) + delta, candidates.size());
		return true;
	}

	public static WorkshopItemCandidate selectedCandidate() {
		return selectedCandidateIndex >= 0 && selectedCandidateIndex < candidates.size()
			? candidates.get(selectedCandidateIndex) : null;
	}

	public static void setSelectedCandidateIndex(int index) {
		selectedCandidateIndex = index >= 0 && index < candidates.size() ? index : -1;
	}

	public static boolean searchMode() { return searchMode; }
	public static String searchText() { return searchText; }
	public static List<WorkshopItemCandidate> candidates() { return candidates; }
	public static boolean candidatesTruncated() { return candidatesTruncated; }
	public static int selectedCandidateIndex() { return selectedCandidateIndex; }
	public static int candidateScrollOffset() { return candidateScrollOffset; }
	public static void setCandidateScrollOffset(int value) { candidateScrollOffset = Math.max(0, value); }
	public static int resultScrollOffset() { return resultScrollOffset; }
	public static void setResultScrollOffset(int value) { resultScrollOffset = Math.max(0, value); }
	public static WorkshopItemCandidate selectedItem() { return selectedItem; }
	public static boolean pending() { return pending; }
	public static ClientWorkshopSearchResult result() { return result; }
	public static WorkshopItemSearchResultCode error() { return error; }
	public static long resultSequence() { return resultSequence; }
	public static long pendingRequestId() { return pendingRequestId; }
	public static long pendingSessionId() { return pendingSessionId; }
	public static long pendingRevision() { return pendingRevision; }
	public static int pendingSyncId() { return pendingSyncId; }

	public static void resetConnection() {
		resetUi();
		networkResult = null;
		networkSequence++;
		observedNetworkSequence = networkSequence;
		WorkshopItemCandidateSearch.invalidateRegistryCache();
	}

	private static void clearServerResult() {
		pending = false;
		pendingRequestId = -1;
		pendingSessionId = -1;
		pendingRevision = -1;
		pendingSyncId = -1;
		result = null;
		error = null;
		resultScrollOffset = 0;
	}

	private static void resetUi() {
		searchMode = false;
		searchText = "";
		candidates = List.of();
		candidatesTruncated = false;
		selectedCandidateIndex = -1;
		candidateScrollOffset = 0;
		selectedItem = null;
		clearServerResult();
		currentSessionId = -1;
		currentRevision = -1;
		currentSyncId = -1;
		resultSequence++;
	}

	private static long nextRequestId() {
		long value = nextRequestId++;
		if (nextRequestId < 0) {
			nextRequestId = 1;
		}
		return value;
	}
}
