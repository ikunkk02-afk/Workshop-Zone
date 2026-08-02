package io.github.ikunkk02afk.workshopzone.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class WorkshopSessionStore {
	private final Map<UUID, WorkshopSession> sessions = new HashMap<>();

	public Optional<WorkshopSession> get(UUID playerId) {
		return Optional.ofNullable(sessions.get(playerId));
	}

	public WorkshopSession put(WorkshopSession session) {
		return sessions.put(session.playerId(), session);
	}

	public WorkshopSession remove(UUID playerId) {
		return sessions.remove(playerId);
	}

	public UUID[] playerIds() {
		return sessions.keySet().toArray(UUID[]::new);
	}

	public int removeIf(Predicate<WorkshopSession> predicate) {
		int previousSize = sessions.size();
		sessions.values().removeIf(predicate);
		return previousSize - sessions.size();
	}
}
