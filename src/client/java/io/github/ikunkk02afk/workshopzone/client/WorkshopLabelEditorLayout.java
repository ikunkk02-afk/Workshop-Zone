package io.github.ikunkk02afk.workshopzone.client;

import java.util.ArrayList;
import java.util.List;

public record WorkshopLabelEditorLayout(
	WorkshopSidebarMetrics.Rect content,
	WorkshopSidebarMetrics.Rect modeArea,
	List<WorkshopSidebarMetrics.Rect> modeButtons,
	WorkshopSidebarMetrics.Rect currentArea,
	WorkshopSidebarMetrics.Rect listArea,
	WorkshopSidebarMetrics.Rect statusArea,
	WorkshopSidebarMetrics.Rect actionArea,
	List<WorkshopSidebarMetrics.Rect> actionButtons,
	WorkshopSidebarLayoutMode mode,
	boolean verticalScroll
) {
	private static final int LABEL_HEIGHT = 9;
	private static final int BUTTON_HEIGHT = 18;
	private static final int GAP = 3;
	private static final int SECTION_GAP = 4;
	private static final int CURRENT_HEIGHT = 20;
	private static final int LINE_HEIGHT = 9;
	private static final int MAX_STATUS_LINES = 3;

	public WorkshopLabelEditorLayout {
		modeButtons = List.copyOf(modeButtons);
		actionButtons = List.copyOf(actionButtons);
	}

	public static WorkshopLabelEditorLayout calculate(
		WorkshopSidebarMetrics.Rect panel,
		int headerHeight,
		int modeCount,
		int actionCount,
		int statusLineCount
	) {
		if (modeCount < 1 || actionCount < 1) {
			throw new IllegalArgumentException("Label editor requires mode and action buttons");
		}
		WorkshopSidebarMetrics.Rect content = panel.inset(5, 4);
		WorkshopSidebarLayoutMode layoutMode = WorkshopSidebarLayoutMode.forWidth(panel.width());
		int editorTop = Math.min(content.bottom(), panel.top() + headerHeight + 3);
		int buttonTop = Math.min(content.bottom(), editorTop + LABEL_HEIGHT + 2);
		List<WorkshopSidebarMetrics.Rect> modeButtons = arrangeModes(
			content.left(), buttonTop, content.width(), modeCount, layoutMode
		);
		int modeBottom = modeButtons.stream().mapToInt(WorkshopSidebarMetrics.Rect::bottom).max().orElse(buttonTop);
		WorkshopSidebarMetrics.Rect modeArea = new WorkshopSidebarMetrics.Rect(
			content.left(), editorTop, content.width(), Math.max(0, modeBottom - editorTop)
		);
		int currentTop = Math.min(content.bottom(), modeBottom + SECTION_GAP);
		WorkshopSidebarMetrics.Rect currentArea = new WorkshopSidebarMetrics.Rect(
			content.left(), currentTop, content.width(), Math.max(0, Math.min(CURRENT_HEIGHT, content.bottom() - currentTop))
		);

		List<WorkshopSidebarMetrics.Rect> actionButtons = arrangeActions(
			content.left(), content.bottom(), content.width(), actionCount, layoutMode
		);
		int actionTop = actionButtons.stream().mapToInt(WorkshopSidebarMetrics.Rect::top).min().orElse(content.bottom());
		WorkshopSidebarMetrics.Rect actionArea = new WorkshopSidebarMetrics.Rect(
			content.left(), actionTop, content.width(), Math.max(0, content.bottom() - actionTop)
		);
		int statusHeight = WorkshopTextLayout.heightForLines(statusLineCount, MAX_STATUS_LINES, LINE_HEIGHT);
		int statusTop = Math.max(currentArea.bottom(), actionTop - (statusHeight == 0 ? 0 : statusHeight + 2));
		WorkshopSidebarMetrics.Rect statusArea = new WorkshopSidebarMetrics.Rect(
			content.left(), statusTop, content.width(), Math.max(0, Math.min(statusHeight, actionTop - statusTop))
		);
		int listTop = Math.min(actionTop, currentArea.bottom() + SECTION_GAP);
		int listBottom = Math.max(listTop, statusTop - 2);
		WorkshopSidebarMetrics.Rect listArea = new WorkshopSidebarMetrics.Rect(
			content.left(), listTop, content.width(), Math.max(0, listBottom - listTop)
		);
		boolean verticalScroll = listArea.height() < BUTTON_HEIGHT * 2;
		return new WorkshopLabelEditorLayout(
			content, modeArea, modeButtons, currentArea, listArea, statusArea, actionArea,
			actionButtons, layoutMode, verticalScroll
		);
	}

	private static List<WorkshopSidebarMetrics.Rect> arrangeModes(
		int left,
		int top,
		int width,
		int count,
		WorkshopSidebarLayoutMode mode
	) {
		List<WorkshopSidebarMetrics.Rect> result = new ArrayList<>(count);
		if (mode == WorkshopSidebarLayoutMode.STANDARD || mode == WorkshopSidebarLayoutMode.COMPACT && count <= 2) {
			addEqualRow(result, left, top, width, count);
			return result;
		}
		if (mode == WorkshopSidebarLayoutMode.COMPACT) {
			int firstRowCount = Math.min(2, count);
			addEqualRow(result, left, top, width, firstRowCount);
			for (int index = firstRowCount; index < count; index++) {
				result.add(new WorkshopSidebarMetrics.Rect(left, top + (index - firstRowCount + 1) * (BUTTON_HEIGHT + GAP), width, BUTTON_HEIGHT));
			}
			return result;
		}
		for (int index = 0; index < count; index++) {
			result.add(new WorkshopSidebarMetrics.Rect(left, top + index * (BUTTON_HEIGHT + GAP), width, BUTTON_HEIGHT));
		}
		return result;
	}

	private static List<WorkshopSidebarMetrics.Rect> arrangeActions(
		int left,
		int contentBottom,
		int width,
		int count,
		WorkshopSidebarLayoutMode mode
	) {
		List<WorkshopSidebarMetrics.Rect> result = new ArrayList<>(count);
		if (mode == WorkshopSidebarLayoutMode.STANDARD) {
			addEqualRow(result, left, contentBottom - BUTTON_HEIGHT, width, count);
			return result;
		}
		if (mode == WorkshopSidebarLayoutMode.COMPACT) {
			int columns = Math.min(2, count);
			int rows = (count + columns - 1) / columns;
			int top = contentBottom - rows * BUTTON_HEIGHT - Math.max(0, rows - 1) * GAP;
			int cellWidth = Math.max(1, (width - GAP * (columns - 1)) / columns);
			for (int index = 0; index < count; index++) {
				int row = index / columns;
				int column = index % columns;
				int x = left + column * (cellWidth + GAP);
				int actualWidth = column == columns - 1 ? left + width - x : cellWidth;
				result.add(new WorkshopSidebarMetrics.Rect(x, top + row * (BUTTON_HEIGHT + GAP), actualWidth, BUTTON_HEIGHT));
			}
			return result;
		}
		int top = contentBottom - count * BUTTON_HEIGHT - Math.max(0, count - 1) * GAP;
		for (int index = 0; index < count; index++) {
			result.add(new WorkshopSidebarMetrics.Rect(left, top + index * (BUTTON_HEIGHT + GAP), width, BUTTON_HEIGHT));
		}
		return result;
	}

	private static void addEqualRow(
		List<WorkshopSidebarMetrics.Rect> target,
		int left,
		int top,
		int width,
		int count
	) {
		int cellWidth = Math.max(1, (width - GAP * (count - 1)) / count);
		for (int index = 0; index < count; index++) {
			int x = left + index * (cellWidth + GAP);
			int actualWidth = index == count - 1 ? left + width - x : cellWidth;
			target.add(new WorkshopSidebarMetrics.Rect(x, top, actualWidth, BUTTON_HEIGHT));
		}
	}
}
