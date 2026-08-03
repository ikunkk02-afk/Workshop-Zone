package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.util.math.MathHelper;

import java.util.List;

public final class WorkshopSidebarPlacementResolver {
	private static final int COLLAPSED_HEIGHT = 20;
	private static final int MIN_USABLE_HEIGHT = 72;
	private static final int EDITOR_MIN_HEIGHT = 184;
	private static final int EDITOR_PREFERRED_HEIGHT = 260;
	private static final int CONTROL_STRIP_WIDTH = 96;

	private WorkshopSidebarPlacementResolver() {
	}

	public static WorkshopSidebarPlacement resolve(Input input) {
		int screenWidth = Math.max(
			WorkshopSidebarMetrics.EDGE_GAP * 2 + WorkshopSidebarMetrics.COLLAPSED_WIDTH,
			input.screenWidth()
		);
		int screenHeight = Math.max(
			WorkshopSidebarMetrics.EDGE_GAP * 2 + COLLAPSED_HEIGHT,
			input.screenHeight()
		);
		WorkshopSidebarPosition requested = input.position() == null
			? WorkshopSidebarPosition.AUTO
			: input.position();
		int requestedWidth = MathHelper.clamp(
			input.preferredWidth(),
			WorkshopSidebarMetrics.MIN_PANEL_WIDTH,
			WorkshopSidebarMetrics.MAX_PANEL_WIDTH
		);
		int desiredHeight = input.requiresEditorSpace()
			? Math.max(input.guiHeight(), EDITOR_PREFERRED_HEIGHT)
			: Math.max(MIN_USABLE_HEIGHT, input.guiHeight());
		int minimumHeight = input.requiresEditorSpace() ? EDITOR_MIN_HEIGHT : MIN_USABLE_HEIGHT;

		if (!input.expanded()) {
			return collapsed(input, screenWidth, screenHeight, requested, false);
		}
		if (requested == WorkshopSidebarPosition.CUSTOM) {
			return custom(input, screenWidth, screenHeight, requestedWidth, desiredHeight, minimumHeight);
		}

		List<WorkshopSidebarPosition> order = priority(requested, input.autoAvoidRecipeViewers(), input.recipeViewerDetected());
		for (int index = 0; index < order.size(); index++) {
			WorkshopSidebarPosition position = order.get(index);
			Candidate candidate = candidate(input, screenWidth, screenHeight, position, requestedWidth, desiredHeight, minimumHeight);
			if (candidate != null) {
				return placement(requested, position, candidate.bounds(), false, index > 0, candidate.constrained());
			}
		}
		return collapsed(input, screenWidth, screenHeight, requested, true);
	}

	private static List<WorkshopSidebarPosition> priority(
		WorkshopSidebarPosition requested,
		boolean autoAvoid,
		boolean viewerDetected
	) {
		if (requested == WorkshopSidebarPosition.AUTO) {
			return autoAvoid && viewerDetected
				? List.of(WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.BOTTOM)
				: List.of(WorkshopSidebarPosition.RIGHT, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.BOTTOM);
		}
		return switch (requested) {
			case RIGHT -> List.of(WorkshopSidebarPosition.RIGHT, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.BOTTOM);
			case LEFT -> List.of(WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.RIGHT, WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.BOTTOM);
			case TOP -> List.of(WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.BOTTOM, WorkshopSidebarPosition.RIGHT);
			case BOTTOM -> List.of(WorkshopSidebarPosition.BOTTOM, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.RIGHT);
			case AUTO, CUSTOM -> throw new IllegalStateException("Unexpected placement priority for " + requested);
		};
	}

	private static Candidate candidate(
		Input input,
		int screenWidth,
		int screenHeight,
		WorkshopSidebarPosition position,
		int requestedWidth,
		int desiredHeight,
		int minimumHeight
	) {
		return switch (position) {
			case RIGHT -> side(input, screenWidth, screenHeight, true, requestedWidth, desiredHeight, minimumHeight);
			case LEFT -> input.recipeBookOpen()
				? null
				: side(input, screenWidth, screenHeight, false, requestedWidth, desiredHeight, minimumHeight);
			case TOP -> vertical(input, screenWidth, true, requestedWidth, desiredHeight, minimumHeight);
			case BOTTOM -> vertical(input, screenWidth, false, requestedWidth, desiredHeight, minimumHeight);
			case AUTO, CUSTOM -> null;
		};
	}

	private static Candidate side(
		Input input,
		int screenWidth,
		int screenHeight,
		boolean right,
		int requestedWidth,
		int desiredHeight,
		int minimumHeight
	) {
		int edge = WorkshopSidebarMetrics.EDGE_GAP;
		int availableWidth = right
			? screenWidth - edge - (input.guiX() + input.guiWidth() + edge)
			: input.guiX() - edge * 2;
		if (availableWidth < WorkshopSidebarMetrics.MIN_PANEL_WIDTH) {
			return null;
		}
		int availableHeight = screenHeight - edge * 2;
		if (availableHeight < minimumHeight) {
			return null;
		}
		int width = Math.min(requestedWidth, availableWidth);
		int height = Math.min(desiredHeight, availableHeight);
		int x = right
			? input.guiX() + input.guiWidth() + edge
			: input.guiX() - edge - width;
		int y = MathHelper.clamp(input.guiY(), edge, Math.max(edge, screenHeight - edge - height));
		return new Candidate(
			new WorkshopSidebarMetrics.Rect(x, y, width, height),
			width != requestedWidth || height != desiredHeight || y != input.guiY()
		);
	}

	private static Candidate vertical(
		Input input,
		int screenWidth,
		boolean top,
		int requestedWidth,
		int desiredHeight,
		int minimumHeight
	) {
		int edge = WorkshopSidebarMetrics.EDGE_GAP;
		int availableWidth = screenWidth - edge * 2;
		if (availableWidth < WorkshopSidebarMetrics.MIN_PANEL_WIDTH) {
			return null;
		}
		int availableHeight = top
			? input.guiY() - edge * 2
			: input.screenHeight() - (input.guiY() + input.guiHeight()) - edge * 2;
		if (availableHeight < minimumHeight) {
			return null;
		}
		int width = Math.min(requestedWidth, availableWidth);
		int height = Math.min(desiredHeight, availableHeight);
		int centerX = input.guiX() + input.guiWidth() / 2;
		int x = MathHelper.clamp(centerX - width / 2, edge, Math.max(edge, screenWidth - edge - width));
		int y = top
			? input.guiY() - edge - height
			: input.guiY() + input.guiHeight() + edge;
		return new Candidate(
			new WorkshopSidebarMetrics.Rect(x, y, width, height),
			width != requestedWidth || height != desiredHeight || x != centerX - width / 2
		);
	}

	private static WorkshopSidebarPlacement custom(
		Input input,
		int screenWidth,
		int screenHeight,
		int requestedWidth,
		int desiredHeight,
		int minimumHeight
	) {
		int edge = WorkshopSidebarMetrics.EDGE_GAP;
		int availableWidth = screenWidth - edge * 2;
		int availableHeight = screenHeight - edge * 2;
		if (availableWidth < WorkshopSidebarMetrics.MIN_PANEL_WIDTH || availableHeight < minimumHeight) {
			return collapsed(input, screenWidth, screenHeight, WorkshopSidebarPosition.CUSTOM, true);
		}
		int width = Math.min(requestedWidth, availableWidth);
		int height = Math.min(desiredHeight, availableHeight);
		double normalizedX = finiteClamp(input.customX(), WorkshopClientConfig.DEFAULT_CUSTOM_X);
		double normalizedY = finiteClamp(input.customY(), WorkshopClientConfig.DEFAULT_CUSTOM_Y);
		int xRange = Math.max(0, availableWidth - width);
		int yRange = Math.max(0, availableHeight - height);
		int x = edge + (int)Math.round(xRange * normalizedX);
		int y = edge + (int)Math.round(yRange * normalizedY);
		boolean constrained = width != requestedWidth
			|| height != desiredHeight
			|| normalizedX != input.customX()
			|| normalizedY != input.customY();
		return placement(
			WorkshopSidebarPosition.CUSTOM,
			WorkshopSidebarPosition.CUSTOM,
			new WorkshopSidebarMetrics.Rect(x, y, width, height),
			false,
			false,
			constrained
		);
	}

	private static double finiteClamp(double value, double fallback) {
		if (!Double.isFinite(value)) {
			return fallback;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static WorkshopSidebarPlacement collapsed(
		Input input,
		int screenWidth,
		int screenHeight,
		WorkshopSidebarPosition requested,
		boolean fallback
	) {
		List<WorkshopSidebarPosition> order;
		if (requested == WorkshopSidebarPosition.AUTO) {
			order = input.autoAvoidRecipeViewers() && input.recipeViewerDetected()
				? List.of(WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.BOTTOM, WorkshopSidebarPosition.RIGHT)
				: List.of(WorkshopSidebarPosition.RIGHT, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.BOTTOM);
		} else if (requested == WorkshopSidebarPosition.CUSTOM) {
			order = List.of(WorkshopSidebarPosition.RIGHT, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.TOP, WorkshopSidebarPosition.BOTTOM);
		} else {
			order = priority(requested, input.autoAvoidRecipeViewers(), input.recipeViewerDetected());
		}
		for (WorkshopSidebarPosition position : order) {
			WorkshopSidebarMetrics.Rect bounds = collapsedAt(input, screenWidth, screenHeight, position);
			if (bounds != null) {
				return placement(requested, position, bounds, true, fallback, false);
			}
		}
		int edge = WorkshopSidebarMetrics.EDGE_GAP;
		return placement(
			requested,
			WorkshopSidebarPosition.RIGHT,
			new WorkshopSidebarMetrics.Rect(
				Math.max(edge, screenWidth - edge - WorkshopSidebarMetrics.COLLAPSED_WIDTH),
				edge,
				WorkshopSidebarMetrics.COLLAPSED_WIDTH,
				COLLAPSED_HEIGHT
			),
			true,
			fallback,
			false
		);
	}

	private static WorkshopSidebarMetrics.Rect collapsedAt(
		Input input,
		int screenWidth,
		int screenHeight,
		WorkshopSidebarPosition position
	) {
		int edge = WorkshopSidebarMetrics.EDGE_GAP;
		int centeredX = MathHelper.clamp(
			input.guiX() + input.guiWidth() / 2 - WorkshopSidebarMetrics.COLLAPSED_WIDTH / 2,
			edge,
			Math.max(edge, screenWidth - edge - WorkshopSidebarMetrics.COLLAPSED_WIDTH)
		);
		return switch (position) {
			case RIGHT -> {
				int x = input.guiX() + input.guiWidth() + edge;
				yield x + WorkshopSidebarMetrics.COLLAPSED_WIDTH <= screenWidth - edge
					? new WorkshopSidebarMetrics.Rect(x, clampY(input.guiY(), COLLAPSED_HEIGHT, screenHeight), WorkshopSidebarMetrics.COLLAPSED_WIDTH, COLLAPSED_HEIGHT)
					: null;
			}
			case LEFT -> {
				int x = input.guiX() - WorkshopSidebarMetrics.COLLAPSED_WIDTH - edge;
				yield !input.recipeBookOpen() && x >= edge
					? new WorkshopSidebarMetrics.Rect(x, clampY(input.guiY(), COLLAPSED_HEIGHT, screenHeight), WorkshopSidebarMetrics.COLLAPSED_WIDTH, COLLAPSED_HEIGHT)
					: null;
			}
			case TOP -> input.guiY() - COLLAPSED_HEIGHT - edge >= edge
				? new WorkshopSidebarMetrics.Rect(centeredX, input.guiY() - COLLAPSED_HEIGHT - edge, WorkshopSidebarMetrics.COLLAPSED_WIDTH, COLLAPSED_HEIGHT)
				: null;
			case BOTTOM -> input.guiY() + input.guiHeight() + edge + COLLAPSED_HEIGHT <= screenHeight - edge
				? new WorkshopSidebarMetrics.Rect(centeredX, input.guiY() + input.guiHeight() + edge, WorkshopSidebarMetrics.COLLAPSED_WIDTH, COLLAPSED_HEIGHT)
				: null;
			case AUTO, CUSTOM -> null;
		};
	}

	private static int clampY(int desiredY, int height, int screenHeight) {
		int edge = WorkshopSidebarMetrics.EDGE_GAP;
		return MathHelper.clamp(desiredY, edge, Math.max(edge, screenHeight - edge - height));
	}

	private static WorkshopSidebarPlacement placement(
		WorkshopSidebarPosition requested,
		WorkshopSidebarPosition resolved,
		WorkshopSidebarMetrics.Rect panel,
		boolean collapsed,
		boolean fallback,
		boolean constrained
	) {
		WorkshopSidebarMetrics.Rect dragArea = collapsed
			? new WorkshopSidebarMetrics.Rect(panel.left(), panel.top(), panel.width(), panel.height())
			: new WorkshopSidebarMetrics.Rect(
				panel.left() + 4,
				panel.top(),
				Math.max(0, panel.width() - CONTROL_STRIP_WIDTH),
				Math.min(WorkshopSidebarMetrics.HEADER_HEIGHT, panel.height())
			);
		return new WorkshopSidebarPlacement(requested, resolved, panel, collapsed, fallback, constrained, dragArea);
	}

	public record Input(
		int screenWidth,
		int screenHeight,
		int guiX,
		int guiY,
		int guiWidth,
		int guiHeight,
		boolean recipeBookOpen,
		WorkshopSidebarPosition position,
		boolean autoAvoidRecipeViewers,
		boolean recipeViewerDetected,
		boolean expanded,
		boolean requiresEditorSpace,
		int preferredWidth,
		double customX,
		double customY
	) {
	}

	private record Candidate(WorkshopSidebarMetrics.Rect bounds, boolean constrained) {
	}
}
