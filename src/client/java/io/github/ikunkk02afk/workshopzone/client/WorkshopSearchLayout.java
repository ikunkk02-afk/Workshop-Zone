package io.github.ikunkk02afk.workshopzone.client;

public record WorkshopSearchLayout(
	WorkshopSidebarMetrics.Rect searchField,
	WorkshopSidebarMetrics.Rect toolbar,
	WorkshopSidebarMetrics.Rect summaryArea,
	WorkshopSidebarMetrics.Rect listArea
) {
	private static final int INSET = 5;
	private static final int FIELD_HEIGHT = 18;
	private static final int TOOLBAR_HEIGHT = 18;
	private static final int SUMMARY_HEIGHT = 22;

	public static WorkshopSearchLayout calculate(WorkshopSidebarMetrics.Rect panel) {
		int width = Math.max(0, panel.width() - INSET * 2);
		int fieldTop = Math.min(panel.bottom(), panel.top() + WorkshopSidebarMetrics.HEADER_HEIGHT + 3);
		WorkshopSidebarMetrics.Rect field = new WorkshopSidebarMetrics.Rect(panel.left() + INSET, fieldTop, width, Math.min(FIELD_HEIGHT, Math.max(0, panel.bottom() - fieldTop)));
		int toolbarTop = Math.min(panel.bottom(), field.bottom() + 3);
		WorkshopSidebarMetrics.Rect toolbar = new WorkshopSidebarMetrics.Rect(panel.left() + INSET, toolbarTop, width, Math.min(TOOLBAR_HEIGHT, Math.max(0, panel.bottom() - toolbarTop)));
		int summaryTop = Math.min(panel.bottom(), toolbar.bottom() + 2);
		WorkshopSidebarMetrics.Rect summary = new WorkshopSidebarMetrics.Rect(panel.left() + INSET, summaryTop, width, Math.min(SUMMARY_HEIGHT, Math.max(0, panel.bottom() - summaryTop)));
		int listTop = Math.min(panel.bottom(), summary.bottom() + 2);
		WorkshopSidebarMetrics.Rect list = new WorkshopSidebarMetrics.Rect(panel.left() + 3, listTop, Math.max(0, panel.width() - 6), Math.max(0, panel.bottom() - listTop - 4));
		return new WorkshopSearchLayout(field, toolbar, summary, list);
	}

	public int rowAt(double mouseX, double mouseY, int rowHeight, int scrollOffset, int entryCount) {
		return WorkshopSidebarLayout.rowAt(
			mouseX, mouseY, listArea.left(), listArea.right(), listArea.top(), listArea.bottom(),
			rowHeight, scrollOffset, entryCount
		);
	}

	public WorkshopSidebarMetrics.Rect visibleRow(int index, int rowHeight, int scrollOffset) {
		int rawTop = listArea.top() + index * rowHeight - scrollOffset;
		int top = Math.max(listArea.top(), rawTop);
		int bottom = Math.min(listArea.bottom(), rawTop + rowHeight);
		return new WorkshopSidebarMetrics.Rect(listArea.left(), top, listArea.width(), Math.max(0, bottom - top));
	}

	public static WorkshopSidebarMetrics.Rect locateButton(WorkshopSidebarMetrics.Rect row) {
		int size = Math.min(16, Math.max(0, row.height() - 4));
		return new WorkshopSidebarMetrics.Rect(row.right() - size - 3, row.top() + Math.max(0, (row.height() - size) / 2), size, size);
	}
}
