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
	private static final int SECTION_GAP = 2;
	private static final int CURRENT_HEIGHT = 20;
	private static final int WHITELIST_CURRENT_HEIGHT = 26;
	private static final int LINE_HEIGHT = 9;
	private static final int MAX_STATUS_LINES = 3;

	public WorkshopLabelEditorLayout {
		modeButtons = List.copyOf(modeButtons);
		actionButtons = List.copyOf(actionButtons);
	}

	public static WorkshopLabelEditorLayout calculate(
		WorkshopSidebarMetrics.Rect panel,
		int headerHeight,
		List<Integer> modeMinimumWidths,
		List<Integer> actionMinimumWidths,
		int statusLineCount,
		boolean whitelistMode,
		int listRowHeight
	) {
		if (modeMinimumWidths.isEmpty() || actionMinimumWidths.isEmpty() || listRowHeight < 1) {
			throw new IllegalArgumentException("Label editor requires measured buttons and a positive row height");
		}
		List<Integer> safeModeWidths = copyWidths(modeMinimumWidths);
		List<Integer> safeActionWidths = copyWidths(actionMinimumWidths);
		WorkshopSidebarMetrics.Rect content = panel.inset(5, 4);
		WorkshopSidebarLayoutMode layoutMode = WorkshopSidebarLayoutMode.forWidth(panel.width());
		int editorTop = Math.min(content.bottom(), panel.top() + headerHeight + 3);
		int buttonTop = Math.min(content.bottom(), editorTop + LABEL_HEIGHT + 2);
		List<WorkshopSidebarMetrics.Rect> modeButtons = arrangeTopRows(
			content.left(), buttonTop, content.width(), safeModeWidths, chooseModeColumns(content.width(), safeModeWidths)
		);
		int modeBottom = modeButtons.stream().mapToInt(WorkshopSidebarMetrics.Rect::bottom).max().orElse(buttonTop);
		WorkshopSidebarMetrics.Rect modeArea = new WorkshopSidebarMetrics.Rect(
			content.left(), editorTop, content.width(), Math.max(0, modeBottom - editorTop)
		);
		int currentTop = Math.min(content.bottom(), modeBottom + SECTION_GAP);
		int wantedCurrentHeight = whitelistMode ? WHITELIST_CURRENT_HEIGHT : CURRENT_HEIGHT;
		WorkshopSidebarMetrics.Rect currentArea = new WorkshopSidebarMetrics.Rect(
			content.left(), currentTop, content.width(), Math.max(0, Math.min(wantedCurrentHeight, content.bottom() - currentTop))
		);

		int actionColumns = chooseActionColumns(content.width(), safeActionWidths);
		List<WorkshopSidebarMetrics.Rect> actionButtons = arrangeBottomRows(
			content.left(), content.bottom(), content.width(), safeActionWidths, actionColumns
		);
		int actionTop = actionButtons.stream().mapToInt(WorkshopSidebarMetrics.Rect::top).min().orElse(content.bottom());
		WorkshopSidebarMetrics.Rect actionArea = new WorkshopSidebarMetrics.Rect(
			content.left(), actionTop, content.width(), Math.max(0, content.bottom() - actionTop)
		);
		int currentBottomLimit = Math.max(currentTop, actionTop - SECTION_GAP);
		if (currentArea.bottom() > currentBottomLimit) {
			currentArea = new WorkshopSidebarMetrics.Rect(
				currentArea.left(), currentArea.top(), currentArea.width(), Math.max(0, currentBottomLimit - currentArea.top())
			);
		}
		int desiredStatusHeight = WorkshopTextLayout.heightForLines(statusLineCount, MAX_STATUS_LINES, LINE_HEIGHT);
		int minimumListHeight = whitelistMode ? listRowHeight * 4 : 0;
		int maxStatusHeight = Math.max(
			0, actionTop - currentArea.bottom() - SECTION_GAP * 3 - minimumListHeight
		);
		int statusHeight = Math.min(desiredStatusHeight, maxStatusHeight);
		int statusTop = Math.max(currentArea.bottom(), actionTop - (statusHeight == 0 ? 0 : statusHeight + SECTION_GAP));
		WorkshopSidebarMetrics.Rect statusArea = new WorkshopSidebarMetrics.Rect(
			content.left(), statusTop, content.width(), Math.max(0, Math.min(statusHeight, actionTop - statusTop))
		);
		int listTop = Math.min(actionTop, currentArea.bottom() + SECTION_GAP);
		int listBottom = Math.max(listTop, statusTop - (statusHeight == 0 ? 0 : SECTION_GAP));
		WorkshopSidebarMetrics.Rect listArea = new WorkshopSidebarMetrics.Rect(
			content.left(), listTop, content.width(), Math.max(0, listBottom - listTop)
		);
		boolean verticalScroll = listArea.height() < listRowHeight * 4;
		return new WorkshopLabelEditorLayout(
			content, modeArea, modeButtons, currentArea, listArea, statusArea, actionArea,
			actionButtons, layoutMode, verticalScroll
		);
	}

	public static WorkshopLabelEditorLayout calculate(
		WorkshopSidebarMetrics.Rect panel,
		int headerHeight,
		int modeCount,
		int actionCount,
		int statusLineCount
	) {
		return calculate(
			panel, headerHeight,
			java.util.Collections.nCopies(modeCount, 1),
			java.util.Collections.nCopies(actionCount, 1),
			statusLineCount, actionCount == 7, 26
		);
	}

	static int chooseModeColumns(int availableWidth, List<Integer> minimumWidths) {
		for (int columns = Math.min(3, minimumWidths.size()); columns >= 1; columns--) {
			if (rowsFit(availableWidth, minimumWidths, columns)) {
				return columns;
			}
		}
		return 1;
	}

	static int chooseActionColumns(int availableWidth, List<Integer> minimumWidths) {
		for (int columns = Math.min(3, minimumWidths.size()); columns >= 2; columns--) {
			if (rowsFit(availableWidth, minimumWidths, columns)) {
				return columns;
			}
		}
		if (minimumWidths.size() > 4 && availableWidth >= 80) {
			return 2;
		}
		return 1;
	}

	static int requiredWidthForColumns(List<Integer> minimumWidths, int columns) {
		List<Integer> safeWidths = copyWidths(minimumWidths);
		int required = 0;
		int index = 0;
		for (int rowSize : rowSizes(safeWidths.size(), Math.max(1, Math.min(columns, safeWidths.size())))) {
			int rowWidth = GAP * Math.max(0, rowSize - 1);
			for (int item = 0; item < rowSize; item++) {
				rowWidth += safeWidths.get(index++);
			}
			required = Math.max(required, rowWidth);
		}
		return required;
	}

	private static List<WorkshopSidebarMetrics.Rect> arrangeTopRows(
		int left,
		int top,
		int width,
		List<Integer> minimumWidths,
		int columns
	) {
		List<WorkshopSidebarMetrics.Rect> result = new ArrayList<>(minimumWidths.size());
		int index = 0;
		for (int rowSize : rowSizes(minimumWidths.size(), columns)) {
			addMeasuredRow(result, left, top, width, minimumWidths, index, rowSize);
			index += rowSize;
			top += BUTTON_HEIGHT + GAP;
		}
		return result;
	}

	private static List<WorkshopSidebarMetrics.Rect> arrangeBottomRows(
		int left,
		int bottom,
		int width,
		List<Integer> minimumWidths,
		int columns
	) {
		List<Integer> rowSizes = rowSizes(minimumWidths.size(), columns);
		int top = bottom - rowSizes.size() * BUTTON_HEIGHT - Math.max(0, rowSizes.size() - 1) * GAP;
		List<WorkshopSidebarMetrics.Rect> result = new ArrayList<>(minimumWidths.size());
		int index = 0;
		for (int rowSize : rowSizes) {
			addMeasuredRow(result, left, top, width, minimumWidths, index, rowSize);
			index += rowSize;
			top += BUTTON_HEIGHT + GAP;
		}
		return result;
	}

	private static void addMeasuredRow(
		List<WorkshopSidebarMetrics.Rect> target,
		int left,
		int top,
		int width,
		List<Integer> minimumWidths,
		int start,
		int count
	) {
		int minimumTotal = GAP * Math.max(0, count - 1);
		for (int index = 0; index < count; index++) {
			minimumTotal += minimumWidths.get(start + index);
		}
		int extra = Math.max(0, width - minimumTotal);
		int x = left;
		for (int index = 0; index < count; index++) {
			int remaining = count - index;
			int bonus = extra / remaining;
			int actualWidth = index == count - 1
				? left + width - x
				: minimumWidths.get(start + index) + bonus;
			target.add(new WorkshopSidebarMetrics.Rect(x, top, Math.max(1, actualWidth), BUTTON_HEIGHT));
			x += actualWidth + GAP;
			extra -= bonus;
		}
	}

	private static boolean rowsFit(int availableWidth, List<Integer> minimumWidths, int columns) {
		int index = 0;
		for (int rowSize : rowSizes(minimumWidths.size(), columns)) {
			int rowWidth = GAP * Math.max(0, rowSize - 1);
			for (int item = 0; item < rowSize; item++) {
				rowWidth += minimumWidths.get(index++);
			}
			if (rowWidth > availableWidth) {
				return false;
			}
		}
		return true;
	}

	private static List<Integer> rowSizes(int count, int columns) {
		int rows = (count + columns - 1) / columns;
		int base = count / rows;
		int widerRows = count % rows;
		List<Integer> sizes = new ArrayList<>(rows);
		for (int row = 0; row < rows; row++) {
			sizes.add(base + (row < widerRows ? 1 : 0));
		}
		return sizes;
	}

	private static List<Integer> copyWidths(List<Integer> widths) {
		List<Integer> copy = new ArrayList<>(widths.size());
		for (Integer width : widths) {
			if (width == null || width < 1) {
				throw new IllegalArgumentException("Button minimum widths must be positive");
			}
			copy.add(width);
		}
		return List.copyOf(copy);
	}
}
