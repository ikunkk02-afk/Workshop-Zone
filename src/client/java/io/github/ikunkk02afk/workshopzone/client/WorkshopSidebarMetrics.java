package io.github.ikunkk02afk.workshopzone.client;

public record WorkshopSidebarMetrics(
	Rect panel,
	Rect content,
	Rect headerArea,
	Rect listArea,
	Side side,
	WorkshopSidebarLayoutMode layoutMode,
	boolean collapsed,
	boolean verticalScroll
) {
	public static final int MIN_PANEL_WIDTH = 154;
	public static final int PREFERRED_PANEL_WIDTH = 210;
	public static final int MAX_PANEL_WIDTH = 280;
	public static final int COLLAPSED_WIDTH = 18;
	public static final int EDGE_GAP = 4;
	public static final int HEADER_HEIGHT = 54;

	public static WorkshopSidebarMetrics calculate(
		int screenWidth,
		int screenHeight,
		int guiX,
		int guiY,
		int guiWidth,
		int guiHeight,
		boolean recipeBookOpen,
		boolean expanded,
		boolean labelEditor,
		int preferredWidth
	) {
		WorkshopSidebarPlacement placement = WorkshopSidebarPlacementResolver.resolve(
			new WorkshopSidebarPlacementResolver.Input(
				screenWidth, screenHeight, guiX, guiY, guiWidth, guiHeight, recipeBookOpen,
				WorkshopSidebarPosition.AUTO, true, false, expanded, labelEditor,
				preferredWidth, WorkshopClientConfig.DEFAULT_CUSTOM_X, WorkshopClientConfig.DEFAULT_CUSTOM_Y
			)
		);
		return fromPlacement(placement);
	}

	public static WorkshopSidebarMetrics fromPlacement(WorkshopSidebarPlacement placement) {
		Rect panel = placement.panel();
		boolean collapsed = placement.collapsed();
		Rect content = panel.inset(collapsed ? 0 : 5, collapsed ? 0 : 4);
		int headerHeight = collapsed ? panel.height() : Math.min(HEADER_HEIGHT, panel.height());
		Rect header = new Rect(panel.left(), panel.top(), panel.width(), headerHeight);
		int listTop = Math.min(panel.bottom(), panel.top() + headerHeight);
		Rect list = new Rect(panel.left() + (collapsed ? 0 : 3), listTop,
			Math.max(0, panel.width() - (collapsed ? 0 : 6)), Math.max(0, panel.bottom() - listTop - (collapsed ? 0 : 4)));
		return new WorkshopSidebarMetrics(
			panel, content, header, list, sideFor(placement.resolvedPosition()),
			WorkshopSidebarLayoutMode.forWidth(panel.width()), collapsed, false
		);
	}

	private static Side sideFor(WorkshopSidebarPosition position) {
		return switch (position) {
			case RIGHT -> Side.RIGHT;
			case LEFT -> Side.LEFT;
			case TOP -> Side.ABOVE;
			case BOTTOM -> Side.BELOW;
			case AUTO, CUSTOM -> Side.SCREEN_EDGE;
		};
	}


	public enum Side {
		RIGHT,
		LEFT,
		ABOVE,
		BELOW,
		SCREEN_EDGE,
		NONE
	}

	public record Rect(int left, int top, int width, int height) {
		public Rect {
			if (width < 0 || height < 0) {
				throw new IllegalArgumentException("Rectangle dimensions cannot be negative");
			}
		}

		public int right() {
			return left + width;
		}

		public int bottom() {
			return top + height;
		}

		public boolean contains(double x, double y) {
			return x >= left && x < right() && y >= top && y < bottom();
		}

		public boolean intersects(Rect other) {
			return left < other.right() && right() > other.left && top < other.bottom() && bottom() > other.top;
		}

		public Rect inset(int horizontal, int vertical) {
			int insetWidth = Math.max(0, width - horizontal * 2);
			int insetHeight = Math.max(0, height - vertical * 2);
			return new Rect(left + Math.min(horizontal, width / 2), top + Math.min(vertical, height / 2), insetWidth, insetHeight);
		}
	}

}
