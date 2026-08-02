package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.util.math.MathHelper;

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
	private static final int COLLAPSED_HEIGHT = 20;
	private static final int EDITOR_PREFERRED_HEIGHT = 260;

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
		int safeScreenWidth = Math.max(EDGE_GAP * 2 + COLLAPSED_WIDTH, screenWidth);
		int safeScreenHeight = Math.max(EDGE_GAP * 2 + COLLAPSED_HEIGHT, screenHeight);
		int requestedWidth = MathHelper.clamp(preferredWidth, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
		int rightX = guiX + guiWidth + EDGE_GAP;
		int rightSpace = Math.max(0, safeScreenWidth - EDGE_GAP - rightX);
		int leftRight = guiX - EDGE_GAP;
		int leftSpace = recipeBookOpen ? 0 : Math.max(0, leftRight - EDGE_GAP);

		Side chosen = Side.NONE;
		int panelWidth = COLLAPSED_WIDTH;
		if (expanded) {
			if (rightSpace >= requestedWidth) {
				chosen = Side.RIGHT;
				panelWidth = requestedWidth;
			} else if (leftSpace >= requestedWidth) {
				chosen = Side.LEFT;
				panelWidth = requestedWidth;
			} else if (rightSpace >= MIN_PANEL_WIDTH || leftSpace >= MIN_PANEL_WIDTH) {
				chosen = rightSpace >= leftSpace ? Side.RIGHT : Side.LEFT;
				panelWidth = Math.min(requestedWidth, chosen == Side.RIGHT ? rightSpace : leftSpace);
			}
		}

		boolean collapsed = chosen == Side.NONE;
		int availableHeight = Math.max(COLLAPSED_HEIGHT, safeScreenHeight - EDGE_GAP * 2);
		int desiredHeight = collapsed
			? COLLAPSED_HEIGHT
			: Math.min(availableHeight, Math.max(72, labelEditor ? Math.max(guiHeight, EDITOR_PREFERRED_HEIGHT) : guiHeight));
		int panelX;
		int panelY;
		if (!collapsed && chosen == Side.RIGHT) {
			panelX = rightX;
			panelY = clampToScreen(guiY, desiredHeight, safeScreenHeight);
		} else if (!collapsed) {
			panelX = leftRight - panelWidth;
			panelY = clampToScreen(guiY, desiredHeight, safeScreenHeight);
		} else {
			TabPlacement tab = placeCollapsedTab(safeScreenWidth, safeScreenHeight, guiX, guiY, guiWidth, guiHeight, recipeBookOpen);
			panelX = tab.x();
			panelY = tab.y();
			chosen = tab.side();
		}

		Rect panel = new Rect(panelX, panelY, panelWidth, desiredHeight);
		Rect content = panel.inset(collapsed ? 0 : 5, collapsed ? 0 : 4);
		int headerHeight = collapsed ? desiredHeight : Math.min(HEADER_HEIGHT, desiredHeight);
		Rect header = new Rect(panel.left(), panel.top(), panel.width(), headerHeight);
		int listTop = Math.min(panel.bottom(), panel.top() + headerHeight);
		Rect list = new Rect(panel.left() + (collapsed ? 0 : 3), listTop,
			Math.max(0, panel.width() - (collapsed ? 0 : 6)), Math.max(0, panel.bottom() - listTop - (collapsed ? 0 : 4)));
		return new WorkshopSidebarMetrics(
			panel, content, header, list, chosen, WorkshopSidebarLayoutMode.forWidth(panelWidth), collapsed, false
		);
	}

	private static TabPlacement placeCollapsedTab(
		int screenWidth,
		int screenHeight,
		int guiX,
		int guiY,
		int guiWidth,
		int guiHeight,
		boolean recipeBookOpen
	) {
		int rightX = guiX + guiWidth + EDGE_GAP;
		if (rightX + COLLAPSED_WIDTH <= screenWidth - EDGE_GAP) {
			return new TabPlacement(rightX, clampToScreen(guiY, COLLAPSED_HEIGHT, screenHeight), Side.RIGHT);
		}
		int leftX = guiX - COLLAPSED_WIDTH - EDGE_GAP;
		if (!recipeBookOpen && leftX >= EDGE_GAP) {
			return new TabPlacement(leftX, clampToScreen(guiY, COLLAPSED_HEIGHT, screenHeight), Side.LEFT);
		}
		int centeredX = MathHelper.clamp(guiX + guiWidth - COLLAPSED_WIDTH, EDGE_GAP, screenWidth - COLLAPSED_WIDTH - EDGE_GAP);
		if (guiY - COLLAPSED_HEIGHT - EDGE_GAP >= EDGE_GAP) {
			return new TabPlacement(centeredX, guiY - COLLAPSED_HEIGHT - EDGE_GAP, Side.ABOVE);
		}
		if (guiY + guiHeight + EDGE_GAP + COLLAPSED_HEIGHT <= screenHeight - EDGE_GAP) {
			return new TabPlacement(centeredX, guiY + guiHeight + EDGE_GAP, Side.BELOW);
		}
		return new TabPlacement(screenWidth - COLLAPSED_WIDTH - EDGE_GAP, EDGE_GAP, Side.SCREEN_EDGE);
	}

	private static int clampToScreen(int desiredY, int height, int screenHeight) {
		return MathHelper.clamp(desiredY, EDGE_GAP, Math.max(EDGE_GAP, screenHeight - height - EDGE_GAP));
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

	private record TabPlacement(int x, int y, Side side) {
	}
}
