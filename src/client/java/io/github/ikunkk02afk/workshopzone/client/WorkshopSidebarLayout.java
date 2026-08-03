package io.github.ikunkk02afk.workshopzone.client;

public final class WorkshopSidebarLayout {
	public static final int LABEL_PREVIEW_ICON_SIZE = 12;
	public static final int LABEL_PREVIEW_ICON_STEP = 9;
	public static final int MAX_ROW_LABEL_ICONS = 3;

	private WorkshopSidebarLayout() {
	}

	public static int rowAt(
		double mouseX,
		double mouseY,
		int rowLeft,
		int rowRight,
		int listTop,
		int listBottom,
		int rowHeight,
		int scrollOffset,
		int entryCount
	) {
		if (mouseX < rowLeft || mouseX >= rowRight || mouseY < listTop || mouseY >= listBottom) {
			return -1;
		}
		int contentY = (int)Math.floor(mouseY - listTop) + scrollOffset;
		int index = Math.floorDiv(contentY, rowHeight);
		return index >= 0 && index < entryCount ? index : -1;
	}

	public static RowBounds visibleRowBounds(
		int index,
		int listTop,
		int listBottom,
		int rowHeight,
		int scrollOffset
	) {
		int rowTop = listTop + index * rowHeight - scrollOffset;
		return new RowBounds(Math.max(listTop, rowTop), Math.min(listBottom, rowTop + rowHeight));
	}

	public static int remainingLabelCount(int totalEntryCount, int displayedIconCount) {
		return Math.max(0, totalEntryCount - Math.max(0, displayedIconCount));
	}

	public static int labelPreviewWidth(int displayedIconCount, int remainingTextWidth) {
		int safeIconCount = Math.max(0, Math.min(MAX_ROW_LABEL_ICONS, displayedIconCount));
		int iconWidth = safeIconCount == 0
			? 0
			: LABEL_PREVIEW_ICON_SIZE + (safeIconCount - 1) * LABEL_PREVIEW_ICON_STEP;
		int counterWidth = remainingTextWidth <= 0 ? 0 : remainingTextWidth + 4;
		return iconWidth + (iconWidth > 0 && counterWidth > 0 ? 2 : 0) + counterWidth;
	}

	public record RowBounds(int top, int bottom) {
		public boolean visible() {
			return top < bottom;
		}
	}
}
