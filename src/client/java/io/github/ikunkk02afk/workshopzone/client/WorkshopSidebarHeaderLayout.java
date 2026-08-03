package io.github.ikunkk02afk.workshopzone.client;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkshopSidebarHeaderLayout {
	public static final int BUTTON_SIZE = 16;
	private static final int GAP = 2;
	private static final int LEFT_PADDING = 7;
	private static final int RIGHT_PADDING = 3;
	private static final int MIN_TITLE_WIDTH = 24;

	private static final List<Control> RETENTION_PRIORITY = List.of(
		Control.COLLAPSE, Control.SEARCH, Control.DEPOSIT, Control.LABEL, Control.REFRESH, Control.POSITION
	);
	private static final List<Control> VISUAL_RIGHT_TO_LEFT = List.of(
		Control.COLLAPSE, Control.POSITION, Control.REFRESH, Control.LABEL, Control.DEPOSIT, Control.SEARCH
	);

	private WorkshopSidebarHeaderLayout() {
	}

	public static Layout calculate(int panelLeft, int panelTop, int panelWidth, boolean showLabel) {
		int usable = Math.max(0, panelWidth - LEFT_PADDING - RIGHT_PADDING - MIN_TITLE_WIDTH);
		int capacity = Math.max(0, (usable + GAP) / (BUTTON_SIZE + GAP));
		Set<Control> selected = new LinkedHashSet<>();
		for (Control control : RETENTION_PRIORITY) {
			if (control == Control.LABEL && !showLabel) {
				continue;
			}
			if (selected.size() >= capacity) {
				break;
			}
			selected.add(control);
		}
		Map<Control, WorkshopSidebarMetrics.Rect> controls = new EnumMap<>(Control.class);
		int right = panelLeft + panelWidth - RIGHT_PADDING;
		for (Control control : VISUAL_RIGHT_TO_LEFT) {
			if (!selected.contains(control)) {
				continue;
			}
			right -= BUTTON_SIZE;
			controls.put(control, new WorkshopSidebarMetrics.Rect(right, panelTop + 4, BUTTON_SIZE, BUTTON_SIZE));
			right -= GAP;
		}
		int titleRight = controls.isEmpty()
			? panelLeft + panelWidth - RIGHT_PADDING
			: controls.values().stream().mapToInt(WorkshopSidebarMetrics.Rect::left).min().orElse(right) - 4;
		return new Layout(Map.copyOf(controls), titleRight);
	}

	public enum Control {
		SEARCH,
		DEPOSIT,
		LABEL,
		REFRESH,
		POSITION,
		COLLAPSE
	}

	public record Layout(Map<Control, WorkshopSidebarMetrics.Rect> controls, int titleRight) {
		public Layout {
			controls = Map.copyOf(controls);
		}

		public WorkshopSidebarMetrics.Rect control(Control control) {
			return controls.get(control);
		}
	}
}
