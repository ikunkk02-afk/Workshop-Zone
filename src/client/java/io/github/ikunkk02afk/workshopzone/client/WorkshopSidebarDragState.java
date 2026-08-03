package io.github.ikunkk02afk.workshopzone.client;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class WorkshopSidebarDragState {
	private boolean dragging;
	private double startMouseX;
	private double startMouseY;
	private WorkshopSidebarMetrics.Rect originalPanel;
	private WorkshopSidebarMetrics.Rect currentPanel;

	public boolean beginDrag(
		double mouseX,
		double mouseY,
		WorkshopSidebarMetrics.Rect panel,
		WorkshopSidebarMetrics.Rect titleArea,
		Collection<WorkshopSidebarMetrics.Rect> blockedAreas
	) {
		if (!titleArea.contains(mouseX, mouseY) || blockedAreas.stream().anyMatch(area -> area.contains(mouseX, mouseY))) {
			return false;
		}
		dragging = true;
		startMouseX = mouseX;
		startMouseY = mouseY;
		originalPanel = panel;
		currentPanel = panel;
		return true;
	}

	public WorkshopSidebarMetrics.Rect updateDrag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		if (!dragging || originalPanel == null) {
			return currentPanel;
		}
		int requestedX = originalPanel.left() + (int)Math.round(mouseX - startMouseX);
		int requestedY = originalPanel.top() + (int)Math.round(mouseY - startMouseY);
		int minX = WorkshopSidebarMetrics.EDGE_GAP;
		int minY = WorkshopSidebarMetrics.EDGE_GAP;
		int maxX = Math.max(minX, screenWidth - WorkshopSidebarMetrics.EDGE_GAP - originalPanel.width());
		int maxY = Math.max(minY, screenHeight - WorkshopSidebarMetrics.EDGE_GAP - originalPanel.height());
		currentPanel = new WorkshopSidebarMetrics.Rect(
			Math.max(minX, Math.min(maxX, requestedX)),
			Math.max(minY, Math.min(maxY, requestedY)),
			originalPanel.width(),
			originalPanel.height()
		);
		return currentPanel;
	}

	public Optional<CustomPosition> finishDrag(int screenWidth, int screenHeight) {
		if (!dragging || currentPanel == null) {
			return Optional.empty();
		}
		int availableX = Math.max(0, screenWidth - WorkshopSidebarMetrics.EDGE_GAP * 2 - currentPanel.width());
		int availableY = Math.max(0, screenHeight - WorkshopSidebarMetrics.EDGE_GAP * 2 - currentPanel.height());
		double normalizedX = availableX == 0 ? 0.0
			: (double)(currentPanel.left() - WorkshopSidebarMetrics.EDGE_GAP) / availableX;
		double normalizedY = availableY == 0 ? 0.0
			: (double)(currentPanel.top() - WorkshopSidebarMetrics.EDGE_GAP) / availableY;
		CustomPosition result = new CustomPosition(normalizedX, normalizedY);
		clear();
		return Optional.of(result);
	}

	public WorkshopSidebarMetrics.Rect cancelDrag() {
		WorkshopSidebarMetrics.Rect result = originalPanel;
		clear();
		return result;
	}

	public Optional<CustomPosition> previewPosition() {
		return Optional.empty();
	}

	public boolean dragging() {
		return dragging;
	}

	public WorkshopSidebarMetrics.Rect currentPanel() {
		return currentPanel;
	}

	private void clear() {
		dragging = false;
		startMouseX = 0;
		startMouseY = 0;
		originalPanel = null;
		currentPanel = null;
	}

	public record CustomPosition(double x, double y) {
	}
}
