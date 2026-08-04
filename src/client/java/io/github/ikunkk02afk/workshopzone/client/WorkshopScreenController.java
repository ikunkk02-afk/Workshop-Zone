package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.network.SearchWorkshopItemPayload;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchResultCode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorkshopScreenController {
	private static final int CANDIDATE_ROW_HEIGHT = 42;
	private static final int RESULT_ROW_HEIGHT = 36;
	private static final WorkshopContainerHighlightManager HIGHLIGHTS = new WorkshopContainerHighlightManager();

	private final HandledScreen<?> screen;
	private final TextFieldWidget searchField;
	private WorkshopSidebarWidget sidebar;
	private WorkshopSidebarPlacement placement;
	private WorkshopSearchLayout layout;

	public WorkshopScreenController(HandledScreen<?> screen) {
		this.screen = screen;
		MinecraftClient client = MinecraftClient.getInstance();
		this.searchField = new TextFieldWidget(
			client.textRenderer, 0, 0, 100, 18, Text.translatable("gui.workshop_zone.search.title")
		);
		searchField.setMaxLength(128);
		searchField.setPlaceholder(Text.translatable("gui.workshop_zone.search.placeholder"));
		searchField.setChangedListener(ClientWorkshopSearchState::setSearchText);
		searchField.visible = false;
		searchField.active = false;
		ClientWorkshopSearchState.beginScreen(screen.getScreenHandler().syncId);
	}

	public void attachSidebar(WorkshopSidebarWidget sidebar) {
		this.sidebar = sidebar;
	}

	public TextFieldWidget searchField() {
		return searchField;
	}

	public boolean searchMode() {
		return ClientWorkshopSearchState.searchMode();
	}

	public void openSearch(ClientWorkshopSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		ClientWorkshopSearchState.enter(snapshot);
		searchField.visible = true;
		searchField.active = true;
		if (!searchField.getText().equals(ClientWorkshopSearchState.searchText())) {
			searchField.setText(ClientWorkshopSearchState.searchText());
		}
		sendCatalogRequest(snapshot, false);
		screen.setFocused(searchField);
		searchField.setFocused(true);
	}

	public void closeSearch() {
		ClientWorkshopSearchState.closeMode();
		searchField.setFocused(false);
		searchField.visible = false;
		searchField.active = false;
		if (screen.getFocused() == searchField) {
			screen.setFocused(null);
		}
	}

	public void closeTransientUiForCraftConfirmation() {
		closeSearch();
		if (sidebar != null) {
			sidebar.closeTransientUiForCraftConfirmation();
		}
	}

	public void updatePlacement(WorkshopSidebarPlacement placement, boolean expanded) {
		this.placement = placement;
		this.layout = WorkshopSearchLayout.calculate(placement.panel());
		WorkshopSidebarMetrics.Rect field = layout.searchField();
		searchField.setDimensionsAndPosition(field.width(), field.height(), field.left(), field.top());
		boolean visible = searchMode() && expanded && !placement.collapsed() && field.width() > 0 && field.height() > 0;
		searchField.visible = visible;
		searchField.active = visible;
		if (!visible && searchField.isFocused()) {
			searchField.setFocused(false);
		}
	}

	public boolean isOverSearchField(double mouseX, double mouseY) {
		return searchField.visible && searchField.isMouseOver(mouseX, mouseY);
	}

	public boolean shouldCaptureKey(int keyCode) {
		return searchMode() && (keyCode == GLFW.GLFW_KEY_ESCAPE || searchField.isFocused());
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!searchMode()) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			closeSearch();
			return true;
		}
		if (!searchField.isFocused()) {
			return false;
		}
		if (ClientWorkshopSearchState.selectedItem() == null && !ClientWorkshopSearchState.pending()) {
			if (keyCode == GLFW.GLFW_KEY_UP) {
				ClientWorkshopSearchState.moveCandidateSelection(-1);
				ensureSelectedCandidateVisible();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_DOWN) {
				ClientWorkshopSearchState.moveCandidateSelection(1);
				ensureSelectedCandidateVisible();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				selectCandidate(ClientWorkshopSearchState.selectedCandidate());
				return true;
			}
		}
		searchField.keyPressed(keyCode, scanCode, modifiers);
		return true;
	}

	public void clickedOutsideWorkshop(double mouseX, double mouseY) {
		if (!searchMode() || placement == null || placement.panel().contains(mouseX, mouseY)) {
			return;
		}
		searchField.setFocused(false);
		if (screen.getFocused() == searchField) {
			screen.setFocused(null);
		}
	}

	public void renderSearch(DrawContext context, ClientWorkshopSnapshot snapshot, int mouseX, int mouseY, float delta) {
		if (!searchMode() || layout == null) {
			return;
		}
		ClientWorkshopSearchState.synchronizeSnapshot(snapshot);
		ClientWorkshopSearchState.observeDepositResult(snapshot);
		ClientWorkshopSearchState.consumeCatalogNetwork(snapshot);
		WorkshopItemCandidate refreshCandidate = ClientWorkshopSearchState.takePendingDetailedRefreshCandidate();
		if (refreshCandidate != null) {
			sendCandidateSearch(refreshCandidate);
		}
		if (ClientWorkshopSearchState.consumeInventoryChangedNotice()) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				client.player.sendMessage(Text.translatable("gui.workshop_zone.search.inventory_changed"), true);
			}
		}
		if (ClientWorkshopSearchState.shouldRequestCatalog(snapshot)) {
			sendCatalogRequest(snapshot, ClientWorkshopSearchState.selectedItem() != null, true);
		}
		ClientWorkshopSearchState.consumeNetwork(snapshot);
		TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
		renderToolbar(context, renderer, mouseX, mouseY);
		if (ClientWorkshopSearchState.catalogLoading()) {
			String key = ClientWorkshopSearchState.catalogRefreshing()
				? "gui.workshop_zone.search.refreshing_catalog"
				: "gui.workshop_zone.search.catalog_loading";
			context.drawTextWithShadow(renderer, Text.translatable(key), layout.summaryArea().left(), layout.summaryArea().top() + 5, 0xFFE0C060);
			return;
		}
		if (ClientWorkshopSearchState.catalogError() != null) {
			context.drawTextWithShadow(
				renderer,
				WorkshopTextLayout.ellipsize(renderer, Text.translatable(ClientWorkshopSearchState.catalogError().translationKey()), layout.summaryArea().width()),
				layout.summaryArea().left(), layout.summaryArea().top() + 5, 0xFFFF7777
			);
			return;
		}
		if (ClientWorkshopSearchState.pending()) {
			context.drawTextWithShadow(renderer, Text.translatable("gui.workshop_zone.search.searching"), layout.summaryArea().left(), layout.summaryArea().top() + 5, 0xFFE0C060);
			return;
		}
		ClientWorkshopSearchResult result = ClientWorkshopSearchState.result();
		if (result != null) {
			renderResultSummary(context, renderer, result);
			renderResults(context, renderer, result, mouseX, mouseY);
			return;
		}
		WorkshopItemSearchResultCode error = ClientWorkshopSearchState.error();
		if (error != null) {
			Text message = error == WorkshopItemSearchResultCode.NOT_FOUND && ClientWorkshopSearchState.selectedItem() != null
				? Text.translatable(error.translationKey(), ClientWorkshopSearchState.selectedItem().localizedName())
				: Text.translatable(error.translationKey());
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, message, layout.summaryArea().width()), layout.summaryArea().left(), layout.summaryArea().top() + 5, 0xFFFF7777);
			return;
		}
		renderCandidateSummary(context, renderer);
		renderCandidates(context, renderer, mouseX, mouseY);
	}

	public boolean mouseClicked(ClientWorkshopSnapshot snapshot, double mouseX, double mouseY, int button) {
		if (!searchMode() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || layout == null) {
			return false;
		}
		int toolbar = toolbarButtonAt(mouseX, mouseY);
		if (toolbar >= 0) {
			handleToolbar(snapshot, toolbar);
			return true;
		}
		if (ClientWorkshopSearchState.pending()) {
			return layout.listArea().contains(mouseX, mouseY);
		}
		ClientWorkshopSearchResult result = ClientWorkshopSearchState.result();
		if (result != null) {
			int row = layout.rowAt(mouseX, mouseY, RESULT_ROW_HEIGHT, ClientWorkshopSearchState.resultScrollOffset(), result.containers().size());
			if (row < 0) {
				return false;
			}
			ClientWorkshopContainerSearchResult selected = result.containers().get(row);
			WorkshopSidebarMetrics.Rect visibleRow = layout.visibleRow(row, RESULT_ROW_HEIGHT, ClientWorkshopSearchState.resultScrollOffset());
			if (WorkshopSearchLayout.locateButton(visibleRow).contains(mouseX, mouseY)) {
				highlightOne(selected);
			} else if (sidebar != null) {
				sidebar.openSearchResult(snapshot, selected.workshopEntry());
			}
			return true;
		}
		int row = layout.rowAt(mouseX, mouseY, CANDIDATE_ROW_HEIGHT, ClientWorkshopSearchState.candidateScrollOffset(), ClientWorkshopSearchState.candidates().size());
		if (row >= 0) {
			ClientWorkshopSearchState.setSelectedCandidateIndex(row);
			selectCandidate(ClientWorkshopSearchState.candidates().get(row));
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (!searchMode() || layout == null || !layout.listArea().contains(mouseX, mouseY)) {
			return false;
		}
		ClientWorkshopSearchResult result = ClientWorkshopSearchState.result();
		if (result != null) {
			int max = Math.max(0, result.containers().size() * RESULT_ROW_HEIGHT - layout.listArea().height());
			ClientWorkshopSearchState.setResultScrollOffset(MathHelper.clamp(
				ClientWorkshopSearchState.resultScrollOffset() - (int)Math.signum(verticalAmount) * RESULT_ROW_HEIGHT, 0, max
			));
		} else {
			int max = Math.max(0, ClientWorkshopSearchState.candidates().size() * CANDIDATE_ROW_HEIGHT - layout.listArea().height());
			ClientWorkshopSearchState.setCandidateScrollOffset(MathHelper.clamp(
				ClientWorkshopSearchState.candidateScrollOffset() - (int)Math.signum(verticalAmount) * CANDIDATE_ROW_HEIGHT, 0, max
			));
		}
		return true;
	}

	public void removed() {
		closeSearch();
		ClientWorkshopSearchState.beginScreen(-1);
	}

	public static WorkshopContainerHighlightManager highlights() {
		return HIGHLIGHTS;
	}

	private void renderToolbar(DrawContext context, TextRenderer renderer, int mouseX, int mouseY) {
		List<Text> labels = ClientWorkshopSearchState.selectedItem() == null
			? List.of(
				Text.translatable("gui.workshop_zone.search.clear"), Text.translatable("gui.workshop_zone.search.refresh"),
				Text.translatable("gui.workshop_zone.search.close")
			)
			: List.of(
				Text.translatable("gui.workshop_zone.search.back"), Text.translatable("gui.workshop_zone.search.refresh"),
				Text.translatable("gui.workshop_zone.search.highlight_all"), Text.translatable("gui.workshop_zone.search.clear"),
				Text.translatable("gui.workshop_zone.search.close")
			);
		List<WorkshopSidebarMetrics.Rect> buttons = toolbarButtons(labels.size());
		for (int index = 0; index < buttons.size(); index++) {
			WorkshopSidebarMetrics.Rect button = buttons.get(index);
			context.fill(button.left(), button.top(), button.right(), button.bottom(), button.contains(mouseX, mouseY) ? 0xFF626274 : 0xFF424250);
			context.drawCenteredTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, labels.get(index), Math.max(0, button.width() - 4)), button.left() + button.width() / 2, button.top() + 5, 0xFFFFFFFF);
		}
	}

	private void renderCandidateSummary(DrawContext context, TextRenderer renderer) {
		Text summary;
		if (ClientWorkshopSearchState.catalogTruncated()) {
			summary = Text.translatable("gui.workshop_zone.search.catalog_truncated").formatted(Formatting.YELLOW);
		} else if (ClientWorkshopSearchState.candidatesTruncated()) {
			summary = Text.translatable("gui.workshop_zone.search.too_many_candidates").formatted(Formatting.YELLOW);
		} else if (ClientWorkshopSearchState.candidates().isEmpty()) {
			summary = Text.translatable(ClientWorkshopSearchState.searchText().isBlank()
				? "gui.workshop_zone.search.catalog_empty" : "gui.workshop_zone.search.inventory_no_match", ClientWorkshopSearchState.searchText());
		} else {
			summary = Text.translatable("gui.workshop_zone.search.candidates").append(": " + ClientWorkshopSearchState.candidates().size());
		}
		context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, summary, layout.summaryArea().width()), layout.summaryArea().left(), layout.summaryArea().top() + 5, 0xFFD8D8D8);
	}

	private void renderCandidates(DrawContext context, TextRenderer renderer, int mouseX, int mouseY) {
		List<WorkshopItemCandidate> candidates = ClientWorkshopSearchState.candidates();
		int scroll = ClientWorkshopSearchState.candidateScrollOffset();
		WorkshopItemCandidate hoveredCandidate = null;
		context.enableScissor(layout.listArea().left(), layout.listArea().top(), layout.listArea().right(), layout.listArea().bottom());
		for (int index = 0; index < candidates.size(); index++) {
			int y = layout.listArea().top() + index * CANDIDATE_ROW_HEIGHT - scroll;
			if (y + CANDIDATE_ROW_HEIGHT <= layout.listArea().top() || y >= layout.listArea().bottom()) {
				continue;
			}
			WorkshopItemCandidate candidate = candidates.get(index);
			boolean hovered = layout.rowAt(mouseX, mouseY, CANDIDATE_ROW_HEIGHT, scroll, candidates.size()) == index;
			boolean selected = ClientWorkshopSearchState.selectedCandidateIndex() == index;
			context.fill(layout.listArea().left(), y, layout.listArea().right(), y + CANDIDATE_ROW_HEIGHT - 1, hovered ? 0xCC3A3A48 : selected ? 0xBB304866 : 0xAA292934);
			context.drawItem(candidate.icon(), layout.listArea().left() + 4, y + 7);
			int textX = layout.listArea().left() + 24;
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, Text.literal(candidate.localizedName()), Math.max(0, layout.listArea().right() - textX - 4)), textX, y + 4, 0xFFFFFFFF);
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, Text.literal(candidate.itemId().toString()), Math.max(0, layout.listArea().right() - textX - 4)), textX, y + 17, 0xFF909090);
			MutableText inventory = Text.translatable("gui.workshop_zone.search.available_count", candidate.totalCount())
				.append(" · ").append(Text.translatable("gui.workshop_zone.search.available_containers", candidate.matchingContainerCount()));
			if (candidate.multipleVariants()) {
				inventory = inventory.append(Text.literal(" · ")).append(Text.translatable("gui.workshop_zone.search.multiple_variants"));
			}
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, inventory, Math.max(0, layout.listArea().right() - textX - 4)), textX, y + 30, candidate.multipleVariants() ? 0xFFFFAA55 : 0xFFD0D0D0);
			if (hovered) {
				hoveredCandidate = candidate;
			}
		}
		context.disableScissor();
		renderScrollBar(context, candidates.size(), CANDIDATE_ROW_HEIGHT, scroll);
		if (hoveredCandidate != null) {
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(Text.literal(hoveredCandidate.localizedName()));
			tooltip.add(Text.translatable("gui.workshop_zone.search.item_id", hoveredCandidate.itemId().toString()));
			tooltip.add(Text.translatable("gui.workshop_zone.search.available_count", hoveredCandidate.totalCount()));
			tooltip.add(Text.translatable("gui.workshop_zone.search.available_containers", hoveredCandidate.matchingContainerCount()));
			if (hoveredCandidate.multipleVariants()) {
				tooltip.add(Text.translatable("gui.workshop_zone.search.multiple_variants").formatted(Formatting.GOLD));
			}
			context.drawTooltip(renderer, tooltip, mouseX, mouseY);
		}
	}

	private void renderResultSummary(DrawContext context, TextRenderer renderer, ClientWorkshopSearchResult result) {
		WorkshopItemCandidate selected = ClientWorkshopSearchState.selectedItem();
		if (selected != null) {
			context.drawItem(selected.icon(), layout.summaryArea().left(), layout.summaryArea().top() + 2);
		}
		int x = layout.summaryArea().left() + (selected == null ? 0 : 20);
		Text name = selected == null ? Text.literal(result.targetItemId().toString()) : Text.literal(selected.localizedName());
		context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, name, Math.max(0, layout.summaryArea().right() - x)), x, layout.summaryArea().top(), 0xFFFFFFFF);
		Text totals = Text.translatable("gui.workshop_zone.search.total_count", result.totalItemCount())
			.append(" · ").append(Text.translatable("gui.workshop_zone.search.container_count", result.totalMatchingContainers()));
		context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, totals, Math.max(0, layout.summaryArea().right() - x)), x, layout.summaryArea().top() + 11, 0xFFD0D0D0);
	}

	private void renderResults(DrawContext context, TextRenderer renderer, ClientWorkshopSearchResult result, int mouseX, int mouseY) {
		int scroll = ClientWorkshopSearchState.resultScrollOffset();
		context.enableScissor(layout.listArea().left(), layout.listArea().top(), layout.listArea().right(), layout.listArea().bottom());
		ClientWorkshopContainerSearchResult hovered = null;
		for (int index = 0; index < result.containers().size(); index++) {
			int y = layout.listArea().top() + index * RESULT_ROW_HEIGHT - scroll;
			if (y + RESULT_ROW_HEIGHT <= layout.listArea().top() || y >= layout.listArea().bottom()) {
				continue;
			}
			ClientWorkshopContainerSearchResult item = result.containers().get(index);
			boolean rowHovered = layout.rowAt(mouseX, mouseY, RESULT_ROW_HEIGHT, scroll, result.containers().size()) == index;
			context.fill(layout.listArea().left(), y, layout.listArea().right(), y + RESULT_ROW_HEIGHT - 1, rowHovered ? 0xCC3A3A48 : 0xAA292934);
			context.drawItem(item.workshopEntry().icon(), layout.listArea().left() + 4, y + 10);
			WorkshopSidebarMetrics.Rect row = layout.visibleRow(index, RESULT_ROW_HEIGHT, scroll);
			WorkshopSidebarMetrics.Rect locate = WorkshopSearchLayout.locateButton(row);
			context.fill(locate.left(), locate.top(), locate.right(), locate.bottom(), locate.contains(mouseX, mouseY) ? 0xFF767646 : 0xFF505038);
			context.drawCenteredTextWithShadow(renderer, "◎", locate.left() + locate.width() / 2, locate.top() + 4, 0xFFFFFF55);
			int textX = layout.listArea().left() + 24;
			int textRight = locate.left() - 4;
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, item.workshopEntry().displayName(), Math.max(0, textRight - textX)), textX, y + 3, 0xFFFFFFFF);
			Text count = Text.literal("×" + item.serverResult().containerItemCount())
				.append(" · ").append(Text.translatable("gui.workshop_zone.search.distance", String.format(Locale.ROOT, "%.1f", Math.sqrt(item.serverResult().distanceSquared()))));
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, count, Math.max(0, textRight - textX)), textX, y + 15, 0xFFD0D0D0);
			Text pos = Text.literal(item.serverResult().representativePosition().toShortString());
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, pos, Math.max(0, textRight - textX)), textX, y + 26, item.serverResult().multipleVariants() ? 0xFFFFAA55 : 0xFF888888);
			if (rowHovered) {
				hovered = item;
			}
		}
		context.disableScissor();
		renderScrollBar(context, result.containers().size(), RESULT_ROW_HEIGHT, scroll);
		if (hovered != null) {
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(hovered.workshopEntry().displayName());
			tooltip.add(Text.translatable("gui.workshop_zone.search.open_container").formatted(Formatting.GREEN));
			tooltip.add(Text.translatable("gui.workshop_zone.search.locate_container").formatted(Formatting.YELLOW));
			if (hovered.serverResult().multipleVariants()) {
				tooltip.add(Text.translatable("gui.workshop_zone.search.multiple_variants").formatted(Formatting.GOLD));
			}
			if (hovered.serverResult().highlightPositions().size() == 2) {
				tooltip.add(Text.translatable("gui.workshop_zone.search.double_chest").formatted(Formatting.GRAY));
			}
			context.drawTooltip(renderer, tooltip, mouseX, mouseY);
		}
	}

	private void handleToolbar(ClientWorkshopSnapshot snapshot, int index) {
		if (ClientWorkshopSearchState.selectedItem() == null) {
			if (index == 0) {
				searchField.setText("");
				screen.setFocused(searchField);
				searchField.setFocused(true);
			} else if (index == 1) {
				sendCatalogRequest(snapshot, false);
			} else if (index == 2) {
				closeSearch();
			}
			return;
		}
		switch (index) {
			case 0 -> {
				ClientWorkshopSearchState.backToCandidates();
				screen.setFocused(searchField);
				searchField.setFocused(true);
			}
			case 1 -> sendCatalogRequest(snapshot, true);
			case 2 -> highlightAll();
			case 3 -> {
				searchField.setText("");
				screen.setFocused(searchField);
				searchField.setFocused(true);
			}
			case 4 -> closeSearch();
			default -> { }
		}
	}

	private void selectCandidate(WorkshopItemCandidate candidate) {
		sendCandidateSearch(candidate);
	}

	private void sendCandidateSearch(WorkshopItemCandidate candidate) {
		ClientWorkshopSnapshot snapshot = ClientWorkshopState.current();
		if (candidate == null || snapshot == null || snapshot.syncId() != screen.getScreenHandler().syncId
			|| !ClientPlayNetworking.canSend(SearchWorkshopItemPayload.ID)) {
			return;
		}
		SearchWorkshopItemPayload request = ClientWorkshopSearchState.selectCandidate(candidate, snapshot);
		if (request != null) {
			ClientPlayNetworking.send(request);
		}
	}

	private void sendCatalogRequest(ClientWorkshopSnapshot snapshot, boolean refreshSelected) {
		sendCatalogRequest(snapshot, refreshSelected, false);
	}

	private void sendCatalogRequest(
		ClientWorkshopSnapshot snapshot,
		boolean refreshSelected,
		boolean automaticRetry
	) {
		if (snapshot == null || screen.getScreenHandler().syncId != snapshot.syncId()
			|| !ClientPlayNetworking.canSend(RequestWorkshopItemCatalogPayload.ID)) {
			return;
		}
		RequestWorkshopItemCatalogPayload request = automaticRetry
			? ClientWorkshopSearchState.retryCatalog(snapshot, refreshSelected)
			: ClientWorkshopSearchState.requestCatalog(snapshot, refreshSelected);
		if (request != null) {
			ClientPlayNetworking.send(request);
		}
	}

	private void highlightOne(ClientWorkshopContainerSearchResult selected) {
		ClientWorkshopSnapshot snapshot = ClientWorkshopState.current();
		if (snapshot == null) {
			return;
		}
		HIGHLIGHTS.highlightOne(selected.serverResult(), selectedItemId(), snapshot.dimensionId(), Util.getMeasuringTimeMs());
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.translatable(
				"message.workshop_zone.search.highlighted",
				selected.workshopEntry().displayName(),
				ClientWorkshopSearchState.selectedItem() == null
					? Text.literal(selectedItemId().toString())
					: Text.literal(ClientWorkshopSearchState.selectedItem().localizedName()),
				selected.serverResult().containerItemCount()
			), true);
		}
	}

	private void highlightAll() {
		ClientWorkshopSnapshot snapshot = ClientWorkshopState.current();
		ClientWorkshopSearchResult result = ClientWorkshopSearchState.result();
		if (snapshot == null || result == null) {
			return;
		}
		List<WorkshopItemSearchContainerResult> serverResults = result.containers().stream()
			.map(ClientWorkshopContainerSearchResult::serverResult).toList();
		HIGHLIGHTS.highlightAll(serverResults, selectedItemId(), snapshot.dimensionId(), Util.getMeasuringTimeMs());
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.translatable("message.workshop_zone.search.highlighted_all", serverResults.size()), true);
		}
	}

	private Identifier selectedItemId() {
		WorkshopItemCandidate candidate = ClientWorkshopSearchState.selectedItem();
		return candidate == null ? Identifier.ofVanilla("air") : candidate.itemId();
	}

	private List<WorkshopSidebarMetrics.Rect> toolbarButtons(int count) {
		List<WorkshopSidebarMetrics.Rect> result = new ArrayList<>(count);
		int gap = 2;
		int width = Math.max(1, (layout.toolbar().width() - gap * (count - 1)) / count);
		int x = layout.toolbar().left();
		for (int index = 0; index < count; index++) {
			int next = index == count - 1 ? layout.toolbar().right() : x + width;
			result.add(new WorkshopSidebarMetrics.Rect(x, layout.toolbar().top(), Math.max(0, next - x), layout.toolbar().height()));
			x = next + gap;
		}
		return result;
	}

	private int toolbarButtonAt(double mouseX, double mouseY) {
		int count = ClientWorkshopSearchState.selectedItem() == null ? 3 : 5;
		List<WorkshopSidebarMetrics.Rect> buttons = toolbarButtons(count);
		for (int index = 0; index < buttons.size(); index++) {
			if (buttons.get(index).contains(mouseX, mouseY)) {
				return index;
			}
		}
		return -1;
	}

	private void ensureSelectedCandidateVisible() {
		if (layout == null || ClientWorkshopSearchState.selectedCandidateIndex() < 0) {
			return;
		}
		int top = ClientWorkshopSearchState.selectedCandidateIndex() * CANDIDATE_ROW_HEIGHT;
		int bottom = top + CANDIDATE_ROW_HEIGHT;
		int scroll = ClientWorkshopSearchState.candidateScrollOffset();
		if (top < scroll) {
			ClientWorkshopSearchState.setCandidateScrollOffset(top);
		} else if (bottom > scroll + layout.listArea().height()) {
			ClientWorkshopSearchState.setCandidateScrollOffset(bottom - layout.listArea().height());
		}
	}

	private void renderScrollBar(DrawContext context, int entryCount, int rowHeight, int scrollOffset) {
		if (layout == null || layout.listArea().height() <= 0) {
			return;
		}
		int contentHeight = entryCount * rowHeight;
		int maxScroll = Math.max(0, contentHeight - layout.listArea().height());
		if (maxScroll == 0) {
			return;
		}
		int trackTop = layout.listArea().top() + 2;
		int trackHeight = Math.max(1, layout.listArea().height() - 4);
		int thumbHeight = Math.min(trackHeight, Math.max(12, trackHeight * layout.listArea().height() / contentHeight));
		int thumbTop = trackTop + (trackHeight - thumbHeight) * Math.min(scrollOffset, maxScroll) / maxScroll;
		int x = layout.listArea().right() - 3;
		context.fill(x, trackTop, x + 2, trackTop + trackHeight, 0x88484852);
		context.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, 0xFFE0E0E0);
	}
}
