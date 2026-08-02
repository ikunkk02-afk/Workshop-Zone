package io.github.ikunkk02afk.workshopzone.network;

public final class WorkshopSnapshotOrder {
	private WorkshopSnapshotOrder() {
	}

	public static boolean isNewer(long sessionId, long revision, long acceptedSessionId, long acceptedRevision) {
		return sessionId > acceptedSessionId || sessionId == acceptedSessionId && revision > acceptedRevision;
	}
}
