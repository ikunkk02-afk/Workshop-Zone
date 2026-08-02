package io.github.ikunkk02afk.workshopzone.client;

public final class WorkshopSidebarLayout {
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

	public record RowBounds(int top, int bottom) {
		public boolean visible() {
			return top < bottom;
		}
	}
}
