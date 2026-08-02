package io.github.ikunkk02afk.workshopzone.client;

enum WorkshopSidebarPresentation {
	LOADING(false),
	NO_SESSION(false),
	READY(true);

	private final boolean interactive;

	WorkshopSidebarPresentation(boolean interactive) {
		this.interactive = interactive;
	}

	static WorkshopSidebarPresentation resolve(boolean snapshotPresent, boolean syncMatches, boolean clearedByServer) {
		if (snapshotPresent && syncMatches) {
			return READY;
		}
		return clearedByServer ? NO_SESSION : LOADING;
	}

	boolean interactive() {
		return interactive;
	}

	boolean frameworkVisible() {
		return true;
	}
}
