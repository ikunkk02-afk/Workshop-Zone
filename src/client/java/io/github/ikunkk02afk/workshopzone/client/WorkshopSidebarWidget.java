package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.mixin.client.HandledScreenAccessor;
import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.label.ContainerItemTags;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntry;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntryType;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerTagCandidate;
import io.github.ikunkk02afk.workshopzone.label.ContainerTagPreset;
import io.github.ikunkk02afk.workshopzone.network.OpenWorkshopTargetPayload;
import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopRefreshPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelDetailsEntry;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelDetailsPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResultPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelOperation;
import io.github.ikunkk02afk.workshopzone.network.UpdateContainerLabelPayload;
import io.github.ikunkk02afk.workshopzone.network.ItemTagCandidatesPayload;
import io.github.ikunkk02afk.workshopzone.network.RequestContainerLabelDetailsPayload;
import io.github.ikunkk02afk.workshopzone.network.RequestItemTagCandidatesPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WorkshopSidebarWidget extends ClickableWidget {
	private static final int HEADER_HEIGHT = WorkshopSidebarMetrics.HEADER_HEIGHT;
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_SIZE = 16;
	private static final int TAG_ROW_HEIGHT = 22;
	private static final int WHITELIST_ROW_HEIGHT = 26;
	private static final int LABEL_EDITOR_HEADER_HEIGHT = 44;
	private static final int BUTTON_HORIZONTAL_PADDING = 10;
	private static final int POSITION_MENU_RESERVED_HEIGHT = 46;
	private static final List<WorkshopSidebarPosition> POSITION_OPTIONS = List.of(
		WorkshopSidebarPosition.AUTO,
		WorkshopSidebarPosition.RIGHT,
		WorkshopSidebarPosition.LEFT,
		WorkshopSidebarPosition.TOP,
		WorkshopSidebarPosition.BOTTOM,
		WorkshopSidebarPosition.CUSTOM
	);
	private static final long PENDING_TIMEOUT_MILLIS = 3_000L;
	private static final double MAX_VISUAL_OPEN_DISTANCE_SQUARED = 64.0;

	private final HandledScreen<?> screen;
	private final boolean showWhileLoading;
	private final WorkshopScreenController controller;
	private int scrollOffset;
	private long nextLocalRefreshAt;
	private BlockPos pendingTarget;
	private long pendingSessionId = -1;
	private long pendingRevision = -1;
	private int pendingSyncId = -1;
	private long pendingExpiresAt;
	private ClientWorkshopEntry narratedEntry;
	private Text narratedState;
	private boolean labelEditor;
	private ContainerLabelMode labelEditorMode = ContainerLabelMode.EXACT_ITEM;
	private Identifier candidateItemId;
	private Item candidateItem;
	private ItemStack candidateIcon = ItemStack.EMPTY;
	private boolean labelPending;
	private Identifier selectedTagId;
	private ItemStack selectedTagIcon = ItemStack.EMPTY;
	private List<ContainerTagCandidate> commonTagChoices = List.of();
	private List<ContainerTagCandidate> queriedTagChoices = List.of();
	private int tagScrollOffset;
	private boolean tagQueryPending;
	private Identifier queriedItemId;
	private long observedTagResponseSequence;
	private Text labelResult;
	private long observedLabelResultSequence;
	private List<ContainerLabelEntry> whitelistEntries = List.of();
	private List<ContainerLabelDetailsEntry> labelDetails = List.of();
	private int selectedWhitelistIndex = -1;
	private int whitelistScrollOffset;
	private boolean whitelistTagPicker;
	private boolean whitelistDirty;
	private boolean whitelistSavePending;
	private boolean labelDetailsPending;
	private long pendingLabelDetailsRequestId;
	private long pendingLabelDetailsRevision = -1;
	private long loadedLabelDetailsRevision = -1;
	private long nextLabelDetailsRequestAt;
	private long observedLabelDetailsSequence;
	private boolean depositPending;
	private long pendingDepositRequestId;
	private long pendingDepositSessionId;
	private int pendingDepositSyncId;
	private long pendingDepositExpiresAt;
	private long observedDepositSequence;
	private WorkshopSidebarMetrics sidebarMetrics;
	private WorkshopSidebarPlacement sidebarPlacement;
	private WorkshopLabelEditorLayout labelLayout;
	private Text narratedControl;
	private boolean positionMenuOpen;
	private final WorkshopSidebarDragState dragState = new WorkshopSidebarDragState();
	private WorkshopSidebarMetrics.Rect draggedPanel;

	public WorkshopSidebarWidget(HandledScreen<?> screen, boolean showWhileLoading, WorkshopScreenController controller) {
		super(0, 0, WorkshopSidebarMetrics.PREFERRED_PANEL_WIDTH, 120, Text.translatable("gui.workshop_zone.sidebar.title"));
		this.screen = screen;
		this.showWhileLoading = showWhileLoading;
		this.controller = controller;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		ClientWorkshopSnapshot current = ClientWorkshopState.current();
		ClientWorkshopSnapshot snapshot = matchingSnapshot(current);
		WorkshopSidebarPresentation presentation = WorkshopSidebarPresentation.resolve(
			current != null, snapshot != null, ClientWorkshopState.wasClearedByServer()
		);
		visible = presentation.frameworkVisible() && (snapshot != null || showWhileLoading);
		active = presentation.interactive();
		if (!visible) {
			WorkshopSidebarPlacementRegistry.remove(screen);
			return;
		}
		narratedControl = null;
		updatePending(snapshot);
		updateLabelResult(snapshot);
		updateDepositResult(snapshot);
		if (labelEditor && snapshot != null) {
			updateTagQueryResult(snapshot);
			updateLabelDetails(snapshot);
		}
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		if (!updateBounds(snapshot, textRenderer)) {
			active = false;
			return;
		}
		boolean expanded = WorkshopScreenIntegration.isExpanded() && !sidebarMetrics.collapsed();

		if (!expanded) {
			context.fill(getX(), getY(), getRight(), getY() + getHeight(), 0xDD181820);
			context.drawCenteredTextWithShadow(textRenderer, ">", getX() + getWidth() / 2, getY() + 5, 0xFFFFFF);
			if (isMouseOver(mouseX, mouseY)) {
				context.drawTooltip(textRenderer, Text.translatable("gui.workshop_zone.sidebar.expand"), mouseX, mouseY);
			}
			return;
		}

		context.fill(getX(), getY(), getRight(), getY() + getHeight(), 0xE6101018);
		context.fill(getX() + 1, getY() + 1, getRight() - 1, getY() + HEADER_HEIGHT, 0xEE242432);
		WorkshopSidebarHeaderLayout.Layout headerLayout = headerLayout(snapshot);
		context.drawTextWithShadow(
			textRenderer,
			WorkshopTextLayout.ellipsize(
				textRenderer,
				Text.translatable("gui.workshop_zone.sidebar.title"),
				Math.max(0, headerLayout.titleRight() - (getX() + 7))
			),
			getX() + 7, getY() + 6, 0xFFFFFF
		);
		if (snapshot == null) {
			Text status = Text.translatable(
				presentation == WorkshopSidebarPresentation.NO_SESSION
					? "gui.workshop_zone.sidebar.no_session"
					: "gui.workshop_zone.sidebar.loading"
			);
			context.drawTextWithShadow(
				textRenderer, WorkshopTextLayout.ellipsize(textRenderer, status, Math.max(0, getWidth() - 14)),
				getX() + 7, getY() + 23, 0xA8A8A8
			);
			return;
		}
		context.drawTextWithShadow(
			textRenderer,
			WorkshopTextLayout.ellipsize(
				textRenderer, Text.translatable("gui.workshop_zone.sidebar.containers", snapshot.containerCount()), Math.max(0, getWidth() - 14)
			),
			getX() + 7, getY() + 21, 0xD8D8D8
		);
		context.drawTextWithShadow(
			textRenderer,
			WorkshopTextLayout.ellipsize(
				textRenderer, Text.translatable("gui.workshop_zone.sidebar.workstations", snapshot.workstationCount()), Math.max(0, getWidth() - 14)
			),
			getX() + 7, getY() + 34, 0xD8D8D8
		);

		drawHeaderControls(context, headerLayout, mouseX, mouseY);
		if (controller.searchMode()) {
			controller.renderSearch(context, snapshot, mouseX, mouseY, delta);
			renderHeaderTooltip(context, textRenderer, headerLayout, mouseX, mouseY);
			return;
		}
		if (positionMenuOpen) {
			renderPositionMenu(context, textRenderer, mouseX, mouseY);
			return;
		}
		if (labelEditor && supportsLabelEditor(snapshot)) {
			renderLabelEditor(context, snapshot, mouseX, mouseY);
			return;
		}

		int listTop = getY() + HEADER_HEIGHT + (snapshot.truncated() ? 13 : 0);
		if (snapshot.truncated()) {
			context.drawTextWithShadow(
				textRenderer,
				Text.translatable("gui.workshop_zone.sidebar.truncated", snapshot.entries().size(), snapshot.totalEntryCount())
					.formatted(Formatting.YELLOW),
				getX() + 7, getY() + HEADER_HEIGHT + 2, 0xFFE080
			);
		}
		int listBottom = getY() + getHeight() - 4;
		int viewportHeight = Math.max(0, listBottom - listTop);
		int maxScroll = Math.max(0, snapshot.entries().size() * ROW_HEIGHT - viewportHeight);
		scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

		context.enableScissor(getX() + 2, listTop, getRight() - 2, listBottom);
		ClientWorkshopEntry hovered = null;
		Text hoveredState = null;
		if (snapshot.entries().isEmpty()) {
			context.drawCenteredTextWithShadow(
				textRenderer,
				Text.translatable("gui.workshop_zone.sidebar.empty"),
				getX() + getWidth() / 2,
				listTop + 8,
				0xA8A8A8
			);
		}
		for (int index = 0; index < snapshot.entries().size(); index++) {
			int rowY = listTop + index * ROW_HEIGHT - scrollOffset;
			if (rowY + ROW_HEIGHT <= listTop || rowY >= listBottom) {
				continue;
			}
			ClientWorkshopEntry entry = snapshot.entries().get(index);
			boolean currentEntry = entry.position().equals(snapshot.openedEntryPosition());
			boolean tooFar = isTooFar(entry);
			boolean pending = entry.position().equals(pendingTarget);
			int hoveredIndex = WorkshopSidebarLayout.rowAt(
				mouseX, mouseY, getX() + 3, getRight() - 3, listTop, listBottom,
				ROW_HEIGHT, scrollOffset, snapshot.entries().size()
			);
			boolean rowHovered = hoveredIndex == index;
			int background = currentEntry ? 0xCC304866 : tooFar ? 0xAA202028 : pending ? 0xCC66552A : 0xAA292934;
			if (rowHovered) {
				background = currentEntry ? 0xDD3B5C80 : tooFar ? 0xCC34343A : pending ? 0xDD806B34 : 0xCC3A3A48;
			}
			context.fill(getX() + 3, rowY, getRight() - 3, rowY + ROW_HEIGHT - 1, background);
			context.drawItem(entry.icon(), getX() + 7, rowY + 4);
			int labelPreviewWidth = labelPreviewWidth(textRenderer, entry);
			int nameRight = getRight() - 8 - labelPreviewWidth - (labelPreviewWidth > 0 ? 4 : 0);
			int availableNameWidth = Math.max(0, nameRight - (getX() + 27));
			Text name = WorkshopTextLayout.ellipsize(textRenderer, entry.displayName(), availableNameWidth);
			context.drawTextWithShadow(textRenderer, name, getX() + 27, rowY + 3, tooFar ? 0x8A8A8A : 0xFFFFFF);
			if (entry.labelSummary().conflict()) {
				context.drawCenteredTextWithShadow(textRenderer, "!", getRight() - 14, rowY + 8, 0xFF5555);
			} else {
				renderLabelPreview(context, textRenderer, entry, getRight() - 8, rowY + 6);
			}
			Text detail;
			if (currentEntry) {
				detail = Text.translatable("gui.workshop_zone.sidebar.current");
			} else if (pending) {
				detail = Text.translatable("gui.workshop_zone.sidebar.switching");
			} else if (tooFar) {
				detail = Text.translatable("gui.workshop_zone.sidebar.too_far");
			} else {
				detail = Text.translatable(
					entry.container() ? "gui.workshop_zone.sidebar.entry.container" : "gui.workshop_zone.sidebar.entry.workstation"
				).append(" · ").append(String.format(Locale.ROOT, "%.1f", Math.sqrt(entry.distanceSquared())));
			}
			context.drawTextWithShadow(
				textRenderer,
				WorkshopTextLayout.ellipsize(textRenderer, detail, availableNameWidth),
				getX() + 27, rowY + 14, tooFar ? 0x777777 : 0xA8A8A8
			);
			if (rowHovered) {
				hovered = entry;
				hoveredState = detail;
			}
		}
		context.disableScissor();

		if (maxScroll > 0) {
			int trackTop = listTop + 2;
			int trackHeight = Math.max(1, viewportHeight - 4);
			int thumbHeight = Math.min(trackHeight, Math.max(12, trackHeight * viewportHeight / (snapshot.entries().size() * ROW_HEIGHT)));
			int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
			context.fill(getRight() - 4, trackTop, getRight() - 2, trackTop + trackHeight, 0x88484852);
			context.fill(getRight() - 4, thumbY, getRight() - 2, thumbY + thumbHeight, 0xFFE0E0E0);
		}

		if (hovered != null) {
			boolean currentEntry = hovered.position().equals(snapshot.openedEntryPosition());
			context.drawTooltip(textRenderer, tooltip(hovered, currentEntry, isTooFar(hovered), hovered.position().equals(pendingTarget)), mouseX, mouseY);
		} else {
			renderHeaderTooltip(context, textRenderer, headerLayout, mouseX, mouseY);
		}
		narratedEntry = hovered;
		narratedState = hoveredState;
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		ClientWorkshopSnapshot snapshot = matchingSnapshot();
		if (snapshot == null) {
			return;
		}
		if (sidebarMetrics == null) {
			updateBounds(snapshot, MinecraftClient.getInstance().textRenderer);
		}
		boolean expanded = WorkshopScreenIntegration.isExpanded() && sidebarMetrics != null && !sidebarMetrics.collapsed();
		if (!expanded) {
			WorkshopScreenIntegration.setExpanded(true);
			return;
		}
		WorkshopSidebarHeaderLayout.Layout headerLayout = headerLayout(snapshot);
		boolean hasLabel = supportsLabelEditor(snapshot);
		if (headerHit(headerLayout, WorkshopSidebarHeaderLayout.Control.COLLAPSE, mouseX, mouseY)) {
			controller.closeSearch();
			WorkshopScreenIntegration.setExpanded(false);
			return;
		}
		if (headerHit(headerLayout, WorkshopSidebarHeaderLayout.Control.SEARCH, mouseX, mouseY)) {
			if (controller.searchMode()) {
				controller.closeSearch();
			} else {
				positionMenuOpen = false;
				if (labelEditor) {
					closeLabelEditor();
				}
				controller.openSearch(snapshot);
			}
			return;
		}
		if (headerHit(headerLayout, WorkshopSidebarHeaderLayout.Control.POSITION, mouseX, mouseY)) {
			controller.closeSearch();
			positionMenuOpen = !positionMenuOpen;
			if (positionMenuOpen && labelEditor) {
				closeLabelEditor();
			}
			return;
		}
		if (positionMenuOpen) {
			handlePositionMenuClick(mouseX, mouseY);
			return;
		}
		if (headerHit(headerLayout, WorkshopSidebarHeaderLayout.Control.REFRESH, mouseX, mouseY)
			&& System.currentTimeMillis() >= nextLocalRefreshAt
			&& ClientPlayNetworking.canSend(RequestWorkshopRefreshPayload.ID)) {
			nextLocalRefreshAt = System.currentTimeMillis() + 1000L;
			ClientPlayNetworking.send(new RequestWorkshopRefreshPayload(snapshot.sessionId(), snapshot.syncId()));
			return;
		}
		if (headerHit(headerLayout, WorkshopSidebarHeaderLayout.Control.DEPOSIT, mouseX, mouseY)) {
			if (!labelEditor && !depositPending && ClientPlayNetworking.canSend(
				io.github.ikunkk02afk.workshopzone.network.DepositWorkshopItemsPayload.ID)) {
				sendDepositRequest(snapshot);
			}
			return;
		}
		if (hasLabel && headerHit(headerLayout, WorkshopSidebarHeaderLayout.Control.LABEL, mouseX, mouseY)) {
			controller.closeSearch();
			labelEditor = true;
			ClientWorkshopEntry opened = openedEntry(snapshot);
			labelEditorMode = opened == null || opened.labelSummary().mode() == ContainerLabelMode.NONE
				? ContainerLabelMode.EXACT_ITEM : opened.labelSummary().mode();
			candidateItemId = null;
			candidateItem = null;
			candidateIcon = ItemStack.EMPTY;
			selectedTagId = opened == null ? null : opened.labelSummary().itemTagId().orElse(null);
			selectedTagIcon = selectedTagId == null ? ItemStack.EMPTY : iconForTag(
				selectedTagId, opened.labelSummary().representativeItemId().orElse(null)
			);
			commonTagChoices = ContainerItemTags.availablePresets();
			queriedTagChoices = List.of();
			tagScrollOffset = 0;
			whitelistEntries = List.of();
			labelDetails = List.of();
			selectedWhitelistIndex = -1;
			whitelistScrollOffset = 0;
			whitelistTagPicker = false;
			whitelistDirty = false;
			whitelistSavePending = false;
			labelDetailsPending = false;
			loadedLabelDetailsRevision = -1;
			nextLabelDetailsRequestAt = 0;
			tagQueryPending = false;
			queriedItemId = null;
			observedTagResponseSequence = ClientItemTagState.responseSequence();
			observedLabelResultSequence = ClientContainerLabelState.resultSequence();
			observedLabelDetailsSequence = ClientContainerLabelDetailsState.responseSequence();
			labelResult = null;
			requestLabelDetails(snapshot);
			return;
		}
		if (controller.searchMode()) {
			controller.mouseClicked(snapshot, mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
			return;
		}
		if (labelEditor && supportsLabelEditor(snapshot)) {
			handleLabelEditorClick(snapshot, mouseX, mouseY);
			return;
		}

		int listTop = listTop(snapshot);
		int listBottom = getY() + getHeight() - 4;
		int row = WorkshopSidebarLayout.rowAt(
			mouseX, mouseY, getX() + 3, getRight() - 3, listTop, listBottom,
			ROW_HEIGHT, scrollOffset, snapshot.entries().size()
		);
		if (row >= 0) {
			ClientWorkshopEntry entry = snapshot.entries().get(row);
			if (canRequestSwitch(snapshot, entry)) {
				sendOpenRequest(snapshot, entry);
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		ClientWorkshopSnapshot snapshot = matchingSnapshot();
		if (dragState.dragging()) {
			return true;
		}
		if (positionMenuOpen) {
			return isMouseOver(mouseX, mouseY);
		}
		if (snapshot == null || !isMouseOver(mouseX, mouseY)
			|| !WorkshopScreenIntegration.isExpanded() || sidebarMetrics == null || sidebarMetrics.collapsed()) {
			return false;
		}
		if (controller.searchMode()) {
			return controller.mouseScrolled(mouseX, mouseY, verticalAmount);
		}
		if (labelEditor) {
			if (labelLayout == null || !labelLayout.listArea().contains(mouseX, mouseY)) {
				return false;
			}
			int viewport = labelLayout.listArea().height();
			if (labelEditorMode == ContainerLabelMode.WHITELIST && !whitelistTagPicker) {
				int maxScroll = Math.max(0, whitelistEntries.size() * WHITELIST_ROW_HEIGHT - viewport);
				whitelistScrollOffset = MathHelper.clamp(
					whitelistScrollOffset - (int)Math.signum(verticalAmount) * WHITELIST_ROW_HEIGHT, 0, maxScroll
				);
				return true;
			}
			if (labelEditorMode != ContainerLabelMode.ITEM_TAG
				&& !(labelEditorMode == ContainerLabelMode.WHITELIST && whitelistTagPicker)) {
				return false;
			}
			int maxScroll = Math.max(0, combinedTagChoices().size() * TAG_ROW_HEIGHT - viewport);
			tagScrollOffset = MathHelper.clamp(
				tagScrollOffset - (int)Math.signum(verticalAmount) * TAG_ROW_HEIGHT, 0, maxScroll
			);
			return true;
		}
		scrollOffset = Math.max(0, scrollOffset - (int)Math.signum(verticalAmount) * ROW_HEIGHT);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (controller.isOverSearchField(mouseX, mouseY)) {
			return false;
		}
		if (button == 0
			&& active
			&& visible
			&& !positionMenuOpen
			&& WorkshopScreenIntegration.isExpanded()
			&& WorkshopClientConfigManager.get().sidebarPosition() == WorkshopSidebarPosition.CUSTOM
			&& sidebarPlacement != null
			&& !sidebarPlacement.collapsed()) {
			ClientWorkshopSnapshot snapshot = matchingSnapshot();
			WorkshopSidebarMetrics.Rect titleArea = new WorkshopSidebarMetrics.Rect(
				getX(), getY(), getWidth(), Math.min(HEADER_HEIGHT, getHeight())
			);
			if (dragState.beginDrag(mouseX, mouseY, sidebarPlacement.panel(), titleArea, headerControlBounds(snapshot))) {
				draggedPanel = sidebarPlacement.panel();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && dragState.dragging()) {
			draggedPanel = dragState.updateDrag(mouseX, mouseY, screen.width, screen.height);
			applyPanelBounds(draggedPanel);
			sidebarPlacement = placementForDraggedPanel(draggedPanel);
			sidebarMetrics = WorkshopSidebarMetrics.fromPlacement(sidebarPlacement);
			WorkshopSidebarPlacementRegistry.update(screen, sidebarPlacement);
			controller.updatePlacement(sidebarPlacement, true);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragState.dragging()) {
			Optional<WorkshopSidebarDragState.CustomPosition> saved = dragState.finishDrag(screen.width, screen.height);
			draggedPanel = null;
			saved.ifPresent(position -> WorkshopClientConfigManager.update(
				WorkshopClientConfigManager.get().withCustomPosition(position.x(), position.y())
			));
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256 && dragState.dragging()) {
			WorkshopSidebarMetrics.Rect original = dragState.cancelDrag();
			draggedPanel = null;
			if (original != null) {
				applyPanelBounds(original);
			}
			return true;
		}
		if (keyCode == 256 && positionMenuOpen) {
			positionMenuOpen = false;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, getMessage());
		if (narratedControl != null) {
			builder.put(NarrationPart.USAGE, narratedControl);
		} else if (narratedEntry != null) {
			builder.put(NarrationPart.USAGE, narratedEntry.displayName().copy().append(". ").append(narratedState));
		}
	}

	private ClientWorkshopSnapshot matchingSnapshot() {
		return matchingSnapshot(ClientWorkshopState.current());
	}

	private ClientWorkshopSnapshot matchingSnapshot(ClientWorkshopSnapshot snapshot) {
		return snapshot != null && snapshot.syncId() == screen.getScreenHandler().syncId ? snapshot : null;
	}

	public Optional<WorkshopSidebarPlacement> currentPlacementForCompatibility() {
		ClientWorkshopSnapshot current = ClientWorkshopState.current();
		ClientWorkshopSnapshot snapshot = matchingSnapshot(current);
		WorkshopSidebarPresentation presentation = WorkshopSidebarPresentation.resolve(
			current != null, snapshot != null, ClientWorkshopState.wasClearedByServer()
		);
		if (!presentation.frameworkVisible() || (snapshot == null && !showWhileLoading)) {
			WorkshopSidebarPlacementRegistry.remove(screen);
			return Optional.empty();
		}
		if (!updateBounds(snapshot, MinecraftClient.getInstance().textRenderer)) {
			WorkshopSidebarPlacementRegistry.remove(screen);
			return Optional.empty();
		}
		return Optional.of(sidebarPlacement);
	}

	public void closeTransientUiForCraftConfirmation() {
		positionMenuOpen = false;
		labelEditor = false;
		candidateItemId = null;
		selectedTagId = null;
		if (dragState.dragging()) {
			WorkshopSidebarMetrics.Rect original = dragState.cancelDrag();
			draggedPanel = null;
			if (original != null) {
				applyPanelBounds(original);
			}
		}
	}

	private boolean updateBounds(ClientWorkshopSnapshot snapshot, TextRenderer renderer) {
		HandledScreenAccessor accessor = (HandledScreenAccessor)screen;
		int guiX = accessor.workshopZone$getX();
		int guiY = accessor.workshopZone$getY();
		int guiWidth = accessor.workshopZone$getBackgroundWidth();
		int guiHeight = accessor.workshopZone$getBackgroundHeight();
		boolean recipeBookOpen = screen instanceof RecipeBookProvider provider && provider.getRecipeBookWidget().isOpen();
		int preferredWidth = preferredPanelWidth(renderer);
		WorkshopClientConfig config = WorkshopClientConfigManager.get();
		sidebarPlacement = WorkshopSidebarPlacementResolver.resolve(
			new WorkshopSidebarPlacementResolver.Input(
				screen.width, screen.height, guiX, guiY, guiWidth, guiHeight, recipeBookOpen,
				config.sidebarPosition(), config.autoAvoidRecipeViewers(), RecipeViewerDetector.hasAny(),
				WorkshopScreenIntegration.isExpanded(), labelEditor || positionMenuOpen || controller.searchMode(), preferredWidth,
				config.customX(), config.customY()
			)
		);
		if (sidebarPlacement.collapsed() && positionMenuOpen) {
			positionMenuOpen = false;
			sidebarPlacement = WorkshopSidebarPlacementResolver.resolve(
				new WorkshopSidebarPlacementResolver.Input(
					screen.width, screen.height, guiX, guiY, guiWidth, guiHeight, recipeBookOpen,
					config.sidebarPosition(), config.autoAvoidRecipeViewers(), RecipeViewerDetector.hasAny(),
					WorkshopScreenIntegration.isExpanded(), labelEditor, preferredWidth,
					config.customX(), config.customY()
				)
			);
		}
		if (dragState.dragging() && draggedPanel != null) {
			sidebarPlacement = placementForDraggedPanel(draggedPanel);
		}
		sidebarMetrics = WorkshopSidebarMetrics.fromPlacement(sidebarPlacement);
		WorkshopSidebarMetrics.Rect panel = sidebarMetrics.panel();
		setX(panel.left());
		setY(panel.top());
		setWidth(panel.width());
		setHeight(panel.height());
		if (!sidebarMetrics.collapsed() && labelEditor) {
			List<Text> modeTexts = labelModeTexts();
			List<Text> actionTexts = labelActionTexts();
			labelLayout = WorkshopLabelEditorLayout.calculate(
				panel, LABEL_EDITOR_HEADER_HEIGHT,
				buttonMinimumWidths(renderer, modeTexts), buttonMinimumWidths(renderer, actionTexts),
				editorStatusLineCount(renderer, snapshot), labelEditorMode == ContainerLabelMode.WHITELIST,
				labelEditorMode == ContainerLabelMode.WHITELIST ? WHITELIST_ROW_HEIGHT : TAG_ROW_HEIGHT
			);
		} else {
			labelLayout = null;
		}
		WorkshopSidebarPlacementRegistry.update(screen, sidebarPlacement);
		controller.updatePlacement(
			sidebarPlacement,
			WorkshopScreenIntegration.isExpanded() && !sidebarPlacement.collapsed()
		);
		return panel.width() > 0 && panel.height() > 0;
	}

	private int preferredPanelWidth(TextRenderer renderer) {
		List<Integer> modeWidths = buttonMinimumWidths(renderer, labelModeTexts());
		List<Integer> whitelistActionWidths = buttonMinimumWidths(renderer, whitelistActionTexts());
		int translatedPreference = Math.max(
			WorkshopSidebarMetrics.PREFERRED_PANEL_WIDTH,
			Math.max(
				WorkshopLabelEditorLayout.requiredWidthForColumns(modeWidths, 3) + 10,
				WorkshopLabelEditorLayout.requiredWidthForColumns(whitelistActionWidths, 3) + 10
			)
		);
		return Math.min(WorkshopSidebarMetrics.MAX_PANEL_WIDTH, translatedPreference);
	}

	private int editorStatusLineCount(TextRenderer renderer, ClientWorkshopSnapshot snapshot) {
		Text state = editorStateText();
		if (state == null) {
			return 0;
		}
		int width = Math.max(1, getWidth() - 10);
		return renderer.wrapLines(state, width).size();
	}

	private Text editorStateText() {
		if (labelPending) {
			return Text.translatable("gui.workshop_zone.label.pending");
		}
		if (labelDetailsPending) {
			return Text.translatable("gui.workshop_zone.label.loading_details");
		}
		if (tagQueryPending) {
			return Text.translatable("gui.workshop_zone.label.finding_categories");
		}
		if (labelResult != null) {
			return labelResult;
		}
		if (labelEditorMode == ContainerLabelMode.WHITELIST && whitelistDirty) {
			return Text.translatable("gui.workshop_zone.label.unsaved_changes");
		}
		long unavailable = labelDetails.stream().filter(ContainerLabelDetailsEntry::unavailable).count();
		return labelEditorMode == ContainerLabelMode.WHITELIST && unavailable > 0
			? Text.translatable("gui.workshop_zone.label.unavailable_entries", unavailable)
			: null;
	}

	private void renderPositionMenu(DrawContext context, TextRenderer renderer, int mouseX, int mouseY) {
		context.drawTextWithShadow(
			renderer,
			WorkshopTextLayout.ellipsize(
				renderer,
				Text.translatable("gui.workshop_zone.position.title"),
				Math.max(0, getWidth() - 14)
			),
			getX() + 7,
			getY() + HEADER_HEIGHT + 3,
			0xFFFFFFFF
		);
		List<WorkshopSidebarMetrics.Rect> buttons = positionMenuBounds();
		WorkshopSidebarPosition selected = WorkshopClientConfigManager.get().sidebarPosition();
		for (int index = 0; index < POSITION_OPTIONS.size(); index++) {
			WorkshopSidebarPosition option = POSITION_OPTIONS.get(index);
			Text label = Text.literal(option == selected ? "● " : "  ")
				.append(Text.translatable(positionTranslationKey(option)));
			drawTextButton(context, buttons.get(index), label, true, mouseX, mouseY);
		}
		drawTextButton(
			context,
			buttons.get(buttons.size() - 1),
			Text.translatable("gui.workshop_zone.position.reset"),
			true,
			mouseX,
			mouseY
		);

		WorkshopSidebarMetrics.Rect lastButton = buttons.get(buttons.size() - 1);
		WorkshopSidebarMetrics.Rect statusArea = new WorkshopSidebarMetrics.Rect(
			getX() + 7,
			lastButton.bottom() + 3,
			Math.max(0, getWidth() - 14),
			Math.max(0, getY() + getHeight() - lastButton.bottom() - 7)
		);
		Text status = positionMenuStatus();
		if (status != null && statusArea.height() > 0) {
			drawWrappedText(context, renderer, status, statusArea, 3, 0xFFA8A8A8);
		}
	}

	private List<WorkshopSidebarMetrics.Rect> positionMenuBounds() {
		int optionCount = POSITION_OPTIONS.size() + 1;
		int available = Math.max(optionCount * 12, getHeight() - HEADER_HEIGHT - POSITION_MENU_RESERVED_HEIGHT);
		int rowHeight = Math.max(12, Math.min(18, available / optionCount));
		int top = getY() + HEADER_HEIGHT + 17;
		List<WorkshopSidebarMetrics.Rect> result = new ArrayList<>(optionCount);
		for (int index = 0; index < optionCount; index++) {
			result.add(new WorkshopSidebarMetrics.Rect(
				getX() + 5,
				top + index * rowHeight,
				Math.max(0, getWidth() - 10),
				Math.max(1, rowHeight - 2)
			));
		}
		return List.copyOf(result);
	}

	private void handlePositionMenuClick(double mouseX, double mouseY) {
		List<WorkshopSidebarMetrics.Rect> buttons = positionMenuBounds();
		for (int index = 0; index < buttons.size(); index++) {
			if (!buttons.get(index).contains(mouseX, mouseY)) {
				continue;
			}
			WorkshopClientConfig updated = index < POSITION_OPTIONS.size()
				? WorkshopClientConfigManager.get().withPosition(POSITION_OPTIONS.get(index))
				: WorkshopClientConfigManager.get().reset();
			WorkshopClientConfigManager.update(updated);
			positionMenuOpen = false;
			return;
		}
	}

	private Text positionMenuStatus() {
		WorkshopClientConfig config = WorkshopClientConfigManager.get();
		if (config.sidebarPosition() == WorkshopSidebarPosition.CUSTOM) {
			return Text.translatable("gui.workshop_zone.position.drag_hint");
		}
		if (sidebarPlacement != null && sidebarPlacement.collapsed()) {
			return Text.translatable("gui.workshop_zone.position.no_space");
		}
		if (sidebarPlacement != null && sidebarPlacement.fallbackUsed()) {
			return Text.translatable("gui.workshop_zone.position.fallback");
		}
		if (RecipeViewerDetector.hasAny()) {
			String names = String.join(", ", RecipeViewerDetector.detected().stream()
				.map(DetectedRecipeViewer::displayName)
				.toList());
			Text detected = Text.translatable("gui.workshop_zone.position.detected", names);
			if (RecipeViewerDetector.detected().size() > 1) {
				detected = Text.translatable("gui.workshop_zone.position.detected_multiple").append(" ").append(detected);
			}
			if (config.sidebarPosition() == WorkshopSidebarPosition.AUTO && config.autoAvoidRecipeViewers()) {
				return detected.copy().append(" · ").append(Text.translatable("gui.workshop_zone.position.auto_recipe_viewer"));
			}
			return detected;
		}
		return null;
	}

	private static String positionTranslationKey(WorkshopSidebarPosition position) {
		return "gui.workshop_zone.position." + position.id();
	}

	private List<WorkshopSidebarMetrics.Rect> headerControlBounds(ClientWorkshopSnapshot snapshot) {
		return List.copyOf(headerLayout(snapshot).controls().values());
	}

	private WorkshopSidebarPlacement placementForDraggedPanel(WorkshopSidebarMetrics.Rect panel) {
		WorkshopSidebarHeaderLayout.Layout header = WorkshopSidebarHeaderLayout.calculate(
			panel.left(), panel.top(), panel.width(), matchingSnapshot() != null && supportsLabelEditor(matchingSnapshot())
		);
		return new WorkshopSidebarPlacement(
			WorkshopSidebarPosition.CUSTOM,
			WorkshopSidebarPosition.CUSTOM,
			panel,
			false,
			false,
			true,
			new WorkshopSidebarMetrics.Rect(
				panel.left() + 4,
				panel.top(),
				Math.max(0, header.titleRight() - panel.left() - 4),
				Math.min(HEADER_HEIGHT, panel.height())
			)
		);
	}

	private void applyPanelBounds(WorkshopSidebarMetrics.Rect panel) {
		setX(panel.left());
		setY(panel.top());
		setWidth(panel.width());
		setHeight(panel.height());
	}

	private WorkshopSidebarHeaderLayout.Layout headerLayout(ClientWorkshopSnapshot snapshot) {
		if (snapshot == null) {
			return new WorkshopSidebarHeaderLayout.Layout(Map.of(), getRight() - 7);
		}
		return WorkshopSidebarHeaderLayout.calculate(getX(), getY(), getWidth(), supportsLabelEditor(snapshot));
	}

	private void drawHeaderControls(
		DrawContext context,
		WorkshopSidebarHeaderLayout.Layout layout,
		int mouseX,
		int mouseY
	) {
		for (Map.Entry<WorkshopSidebarHeaderLayout.Control, WorkshopSidebarMetrics.Rect> entry : layout.controls().entrySet()) {
			WorkshopSidebarMetrics.Rect bounds = entry.getValue();
			context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), bounds.contains(mouseX, mouseY) ? 0xFF626274 : 0xFF424250);
			String label = switch (entry.getKey()) {
				case COLLAPSE -> "<";
				case SEARCH -> "S";
				case DEPOSIT -> depositPending ? "..." : "⇩";
				case LABEL -> "L";
				case REFRESH -> "R";
				case POSITION -> "P";
			};
			context.drawCenteredTextWithShadow(
				MinecraftClient.getInstance().textRenderer, label,
				bounds.left() + bounds.width() / 2, bounds.top() + 4, 0xFFFFFF
			);
		}
	}

	private void renderHeaderTooltip(
		DrawContext context,
		TextRenderer renderer,
		WorkshopSidebarHeaderLayout.Layout layout,
		int mouseX,
		int mouseY
	) {
		for (Map.Entry<WorkshopSidebarHeaderLayout.Control, WorkshopSidebarMetrics.Rect> entry : layout.controls().entrySet()) {
			if (!entry.getValue().contains(mouseX, mouseY)) {
				continue;
			}
			Text tooltip = switch (entry.getKey()) {
				case COLLAPSE -> Text.translatable("gui.workshop_zone.sidebar.collapse");
				case SEARCH -> Text.translatable("gui.workshop_zone.search.button");
				case DEPOSIT -> depositPending
					? Text.translatable("gui.workshop_zone.deposit.pending")
					: Text.translatable("gui.workshop_zone.deposit.button");
				case LABEL -> Text.translatable("gui.workshop_zone.label.button");
				case REFRESH -> System.currentTimeMillis() < nextLocalRefreshAt
					? Text.translatable("gui.workshop_zone.sidebar.refresh_cooldown")
					: Text.translatable("gui.workshop_zone.sidebar.refresh");
				case POSITION -> Text.translatable("gui.workshop_zone.position.button");
			};
			context.drawTooltip(renderer, tooltip, mouseX, mouseY);
			return;
		}
	}

	private static boolean headerHit(
		WorkshopSidebarHeaderLayout.Layout layout,
		WorkshopSidebarHeaderLayout.Control control,
		double mouseX,
		double mouseY
	) {
		WorkshopSidebarMetrics.Rect bounds = layout.control(control);
		return bounds != null && bounds.contains(mouseX, mouseY);
	}

	private static List<Text> labelModeTexts() {
		return List.of(
			Text.translatable("gui.workshop_zone.label.mode_exact"),
			Text.translatable("gui.workshop_zone.label.mode_tag"),
			Text.translatable("gui.workshop_zone.label.mode_whitelist")
		);
	}

	private List<Text> labelActionTexts() {
		if (labelEditorMode == ContainerLabelMode.EXACT_ITEM) {
			return List.of(
				Text.translatable("gui.workshop_zone.label.use_cursor"),
				Text.translatable("gui.workshop_zone.label.save"),
				Text.translatable("gui.workshop_zone.label.clear"),
				Text.translatable("gui.workshop_zone.label.cancel")
			);
		}
		if (labelEditorMode == ContainerLabelMode.ITEM_TAG) {
			return List.of(
				Text.translatable("gui.workshop_zone.label.find_categories"),
				Text.translatable("gui.workshop_zone.label.save"),
				Text.translatable("gui.workshop_zone.label.clear"),
				Text.translatable("gui.workshop_zone.label.cancel")
			);
		}
		return whitelistActionTexts();
	}

	private static List<Text> whitelistActionTexts() {
		return List.of(
			Text.translatable("gui.workshop_zone.label.add_cursor_item"),
			Text.translatable("gui.workshop_zone.label.find_categories"),
			Text.translatable("gui.workshop_zone.label.add_selected_tag"),
			Text.translatable("gui.workshop_zone.label.remove_selected"),
			Text.translatable("gui.workshop_zone.label.save"),
			Text.translatable("gui.workshop_zone.label.clear"),
			Text.translatable("gui.workshop_zone.label.cancel")
		);
	}

	private static List<Integer> buttonMinimumWidths(TextRenderer renderer, List<Text> texts) {
		return texts.stream().map(text -> renderer.getWidth(text) + BUTTON_HORIZONTAL_PADDING).toList();
	}

	private static int labelPreviewWidth(TextRenderer renderer, ClientWorkshopEntry entry) {
		if (entry.labelSummary().conflict()) {
			return WorkshopSidebarLayout.LABEL_PREVIEW_ICON_SIZE;
		}
		int displayedIcons = Math.min(WorkshopSidebarLayout.MAX_ROW_LABEL_ICONS, entry.labelIcons().size());
		int remaining = entry.labelSummary().mode() == ContainerLabelMode.WHITELIST && !entry.labelSummary().unavailable()
			? WorkshopSidebarLayout.remainingLabelCount(entry.labelSummary().whitelistEntryCount(), displayedIcons)
			: 0;
		int remainingTextWidth = remaining == 0 ? 0 : renderer.getWidth("+" + remaining);
		return WorkshopSidebarLayout.labelPreviewWidth(displayedIcons, remainingTextWidth);
	}

	private static void renderLabelPreview(
		DrawContext context,
		TextRenderer renderer,
		ClientWorkshopEntry entry,
		int right,
		int top
	) {
		int displayedIcons = Math.min(WorkshopSidebarLayout.MAX_ROW_LABEL_ICONS, entry.labelIcons().size());
		int remaining = entry.labelSummary().mode() == ContainerLabelMode.WHITELIST && !entry.labelSummary().unavailable()
			? WorkshopSidebarLayout.remainingLabelCount(entry.labelSummary().whitelistEntryCount(), displayedIcons)
			: 0;
		int iconWidth = displayedIcons == 0 ? 0
			: WorkshopSidebarLayout.LABEL_PREVIEW_ICON_SIZE
				+ (displayedIcons - 1) * WorkshopSidebarLayout.LABEL_PREVIEW_ICON_STEP;
		if (remaining > 0) {
			String counter = "+" + remaining;
			int textWidth = renderer.getWidth(counter);
			int counterRight = right - iconWidth - (iconWidth > 0 ? 2 : 0);
			int counterLeft = counterRight - textWidth - 4;
			context.fill(counterLeft, top + 1, counterRight, top + 11, 0xCC101018);
			context.drawTextWithShadow(renderer, counter, counterLeft + 2, top + 2, 0xFFFFFF);
		}
		for (int index = displayedIcons - 1; index >= 0; index--) {
			int x = right - WorkshopSidebarLayout.LABEL_PREVIEW_ICON_SIZE
				- index * WorkshopSidebarLayout.LABEL_PREVIEW_ICON_STEP;
			renderScaledItem(context, entry.labelIcons().get(index), x, top, WorkshopSidebarLayout.LABEL_PREVIEW_ICON_SIZE);
		}
	}

	private static void renderScaledItem(DrawContext context, ItemStack stack, int x, int y, int size) {
		if (stack.isEmpty() || size <= 0) {
			return;
		}
		float scale = size / 16.0F;
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawItem(stack, 0, 0);
		context.getMatrices().pop();
	}

	private static List<Text> tooltip(ClientWorkshopEntry entry, boolean current, boolean tooFar, boolean pending) {
		List<Text> lines = new ArrayList<>();
		lines.add(entry.displayName());
		lines.add(Text.translatable(
			"gui.workshop_zone.sidebar.entry.position",
			entry.position().getX(), entry.position().getY(), entry.position().getZ()
		).formatted(Formatting.GRAY));
		lines.add(Text.translatable(
			"gui.workshop_zone.sidebar.entry.registry_id", entry.blockId().toString()
		).formatted(Formatting.GRAY));
		lines.add(Text.translatable(
			"gui.workshop_zone.sidebar.entry.distance",
			String.format(Locale.ROOT, "%.1f", Math.sqrt(entry.distanceSquared()))
		).formatted(Formatting.GRAY));
		lines.add(Text.translatable(entry.type().translationKey()).formatted(Formatting.GRAY));
		if (entry.labelSummary().mode() == ContainerLabelMode.WHITELIST) {
			lines.add(Text.translatable(
				"gui.workshop_zone.label.whitelist_count", entry.labelSummary().whitelistEntryCount()
			).formatted(Formatting.GOLD));
			lines.add(Text.translatable("gui.workshop_zone.label.preview").formatted(Formatting.YELLOW));
			for (int index = 0; index < entry.labelSummary().previewItemIds().size() && index < entry.labelIcons().size(); index++) {
				lines.add(Text.literal("- ").append(entry.labelIcons().get(index).getName()).formatted(Formatting.GRAY));
			}
			int previewRemaining = WorkshopSidebarLayout.remainingLabelCount(
				entry.labelSummary().whitelistEntryCount(), entry.labelSummary().previewItemIds().size()
			);
			if (previewRemaining > 0) {
				lines.add(Text.translatable("gui.workshop_zone.label.preview_more", previewRemaining).formatted(Formatting.GRAY));
			}
			if (entry.labelSummary().unavailableEntryCount() > 0) {
				lines.add(Text.translatable(
					"gui.workshop_zone.label.unavailable_entries", entry.labelSummary().unavailableEntryCount()
				).formatted(Formatting.RED));
			}
			if (entry.labelSummary().contentConflict()) {
				lines.add(Text.translatable("gui.workshop_zone.label.content_conflict").formatted(Formatting.RED));
			}
			lines.add(Text.translatable("gui.workshop_zone.label.open_to_view_all").formatted(Formatting.DARK_GRAY));
		} else if (entry.labelSummary().unavailable()) {
			lines.add(Text.translatable("gui.workshop_zone.label.tag_unavailable").formatted(Formatting.RED));
			entry.labelSummary().itemTagId().ifPresent(tagId -> lines.add(
				Text.translatable("gui.workshop_zone.label.tag_id", "#" + tagId).formatted(Formatting.GRAY)
			));
		} else if (entry.labelSummary().contentConflict()) {
			lines.add(Text.translatable("gui.workshop_zone.label.content_conflict").formatted(Formatting.RED));
			entry.labelSummary().itemTagId().ifPresent(tagId -> lines.add(
				Text.translatable("gui.workshop_zone.label.tag_id", "#" + tagId).formatted(Formatting.GRAY)
			));
		} else if (entry.labelSummary().ruleConflict()) {
			lines.add(Text.translatable("gui.workshop_zone.label.conflict").formatted(Formatting.RED));
		} else if (entry.labelSummary().mode() == ContainerLabelMode.ITEM_TAG) {
			Identifier tagId = entry.labelSummary().itemTagId().orElseThrow();
			lines.add(Text.translatable(
				"gui.workshop_zone.label.allowed_category", ContainerTagPreset.displayName(tagId)
			).formatted(Formatting.GOLD));
			lines.add(Text.translatable("gui.workshop_zone.label.tag_id", "#" + tagId).formatted(Formatting.GRAY));
		} else if (entry.labelSummary().hasLabel()) {
			lines.add(Text.translatable("gui.workshop_zone.label.allowed_item", entry.labelIcon().getName()).formatted(Formatting.GOLD));
		}
		if (current) {
			lines.add(Text.translatable("gui.workshop_zone.sidebar.current").formatted(Formatting.AQUA));
		} else if (pending) {
			lines.add(Text.translatable("gui.workshop_zone.sidebar.switching").formatted(Formatting.YELLOW));
		} else if (tooFar) {
			lines.add(Text.translatable("gui.workshop_zone.sidebar.too_far").formatted(Formatting.DARK_GRAY));
		} else {
			lines.add(Text.translatable("gui.workshop_zone.sidebar.click_to_open").formatted(Formatting.GREEN));
		}
		return lines;
	}

	private int listTop(ClientWorkshopSnapshot snapshot) {
		return getY() + HEADER_HEIGHT + (snapshot.truncated() ? 13 : 0);
	}

	private boolean canRequestSwitch(ClientWorkshopSnapshot snapshot, ClientWorkshopEntry entry) {
		return pendingTarget == null
			&& !entry.position().equals(snapshot.openedEntryPosition())
			&& !isTooFar(entry)
			&& ClientPlayNetworking.canSend(OpenWorkshopTargetPayload.ID);
	}

	void openSearchResult(ClientWorkshopSnapshot snapshot, ClientWorkshopEntry entry) {
		if (snapshot != null
			&& snapshot.sessionId() == ClientWorkshopSearchState.pendingSessionId()
			&& snapshot.revision() == ClientWorkshopSearchState.pendingRevision()
			&& snapshot.syncId() == ClientWorkshopSearchState.pendingSyncId()
			&& canRequestSwitch(snapshot, entry)) {
			sendOpenRequest(snapshot, entry);
		}
	}

	private void sendOpenRequest(ClientWorkshopSnapshot snapshot, ClientWorkshopEntry entry) {
		pendingTarget = entry.position();
		pendingSessionId = snapshot.sessionId();
		pendingRevision = snapshot.revision();
		pendingSyncId = snapshot.syncId();
		pendingExpiresAt = Util.getMeasuringTimeMs() + PENDING_TIMEOUT_MILLIS;
		WorkshopZone.LOGGER.debug(
			"Sending workshop switch request for target {} session {} revision {} syncId {}",
			entry.position(), snapshot.sessionId(), snapshot.revision(), snapshot.syncId()
		);
		ClientPlayNetworking.send(new OpenWorkshopTargetPayload(
			snapshot.sessionId(), snapshot.revision(), snapshot.syncId(), entry.position()
		));
	}

	private void updatePending(ClientWorkshopSnapshot snapshot) {
		if (pendingTarget == null) {
			return;
		}
		if (snapshot == null
			|| snapshot.sessionId() != pendingSessionId
			|| snapshot.revision() != pendingRevision
			|| snapshot.syncId() != pendingSyncId
			|| screen.getScreenHandler().syncId != pendingSyncId
			|| Util.getMeasuringTimeMs() >= pendingExpiresAt) {
			pendingTarget = null;
			pendingSessionId = -1;
			pendingRevision = -1;
			pendingSyncId = -1;
			pendingExpiresAt = 0;
		}
	}

	private static boolean isTooFar(ClientWorkshopEntry entry) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return true;
		}
		double distanceSquared = client.player.squaredDistanceTo(Vec3d.ofCenter(entry.position()));
		return !Double.isFinite(distanceSquared) || distanceSquared > MAX_VISUAL_OPEN_DISTANCE_SQUARED;
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private boolean supportsLabelEditor(ClientWorkshopSnapshot snapshot) {
		return switch (snapshot.openedBlockType()) {
			case CHEST, TRAPPED_CHEST, BARREL -> snapshot.entries().stream()
				.anyMatch(entry -> entry.position().equals(snapshot.openedEntryPosition()));
			default -> false;
		};
	}

	private ClientWorkshopEntry openedEntry(ClientWorkshopSnapshot snapshot) {
		return snapshot.entries().stream()
			.filter(entry -> entry.position().equals(snapshot.openedEntryPosition()))
			.findFirst().orElse(null);
	}

	private void renderLabelEditor(DrawContext context, ClientWorkshopSnapshot snapshot, int mouseX, int mouseY) {
		if (labelLayout == null) {
			return;
		}
		TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
		ClientWorkshopEntry opened = openedEntry(snapshot);
		WorkshopSidebarMetrics.Rect modeArea = labelLayout.modeArea();
		context.drawTextWithShadow(renderer, Text.translatable("gui.workshop_zone.label.mode"), modeArea.left() + 2, modeArea.top(), 0xA8A8A8);

		List<Text> modeTexts = labelModeTexts();
		List<ContainerLabelMode> modes = List.of(
			ContainerLabelMode.EXACT_ITEM, ContainerLabelMode.ITEM_TAG, ContainerLabelMode.WHITELIST
		);
		for (int index = 0; index < modeTexts.size(); index++) {
			ContainerLabelMode mode = modes.get(index);
			drawTextButton(
				context, labelLayout.modeButtons().get(index), modeTexts.get(index),
				!labelPending && labelEditorMode != mode, mouseX, mouseY
			);
		}

		Text currentText = Text.translatable("gui.workshop_zone.label.current_value", currentLabelText(opened));
		boolean currentTruncated;
		if (labelEditorMode == ContainerLabelMode.WHITELIST) {
			WorkshopSidebarMetrics.Rect currentArea = labelLayout.currentArea();
			Text countText = Text.translatable(
				"gui.workshop_zone.label.whitelist_list_count", whitelistEntries.size(),
				io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule.MAX_ENTRIES
			);
			if (currentArea.height() >= 9) {
				Text visibleCount = WorkshopTextLayout.ellipsize(renderer, countText, Math.max(0, currentArea.width() / 2));
				int countWidth = renderer.getWidth(visibleCount);
				int currentWidth = Math.max(0, currentArea.width() - countWidth - 5);
				Text visibleCurrent = WorkshopTextLayout.ellipsize(renderer, currentText, currentWidth);
				context.drawTextWithShadow(renderer, visibleCurrent, currentArea.left(), currentArea.top(), 0xFFD080);
				context.drawTextWithShadow(
					renderer, visibleCount, currentArea.right() - countWidth, currentArea.top(), 0xA8A8A8
				);
				currentTruncated = WorkshopTextLayout.isTruncated(renderer, currentText, currentWidth);
			} else {
				currentTruncated = true;
			}
			if (currentArea.height() >= 23) {
				renderWhitelistOverview(context, renderer);
			}
		} else {
			currentTruncated = drawWrappedText(
				context, renderer, currentText, labelLayout.currentArea(), 2, 0xFFD080
			);
		}

		if (labelEditorMode == ContainerLabelMode.EXACT_ITEM) {
			renderExactCandidate(context, renderer);
		} else if (labelEditorMode == ContainerLabelMode.ITEM_TAG || whitelistTagPicker) {
			renderTagChoices(context, renderer, mouseX, mouseY);
		} else {
			renderWhitelistEntries(context, renderer, mouseX, mouseY);
		}

		boolean ruleConflict = opened != null && opened.labelSummary().ruleConflict();
		List<Text> actionTexts;
		List<Boolean> actionEnabled;
		if (labelEditorMode == ContainerLabelMode.EXACT_ITEM) {
			actionTexts = List.of(
				Text.translatable("gui.workshop_zone.label.use_cursor"),
				Text.translatable("gui.workshop_zone.label.save"),
				Text.translatable("gui.workshop_zone.label.clear"),
				Text.translatable("gui.workshop_zone.label.cancel")
			);
			actionEnabled = List.of(
				!labelPending && !getCursorStack().isEmpty(),
				!labelPending && candidateItemId != null && !ruleConflict,
				!labelPending,
				!labelPending
			);
		} else if (labelEditorMode == ContainerLabelMode.ITEM_TAG) {
			actionTexts = List.of(
				Text.translatable("gui.workshop_zone.label.find_categories"),
				Text.translatable("gui.workshop_zone.label.save"),
				Text.translatable("gui.workshop_zone.label.clear"),
				Text.translatable("gui.workshop_zone.label.cancel")
			);
			actionEnabled = List.of(
				!labelPending && !tagQueryPending && !getCursorStack().isEmpty(),
				!labelPending && selectedTagId != null && !ruleConflict,
				!labelPending,
				!labelPending
			);
		} else {
			boolean full = whitelistEntries.size() >= io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule.MAX_ENTRIES;
			actionTexts = whitelistActionTexts();
			actionEnabled = List.of(
				!labelPending && !full && !getCursorStack().isEmpty(),
				!labelPending && !tagQueryPending && !getCursorStack().isEmpty(),
				!labelPending && !full && whitelistTagPicker && selectedTagId != null,
				!labelPending && !whitelistTagPicker && selectedWhitelistIndex >= 0 && selectedWhitelistIndex < whitelistEntries.size(),
				!labelPending && !labelDetailsPending && !whitelistEntries.isEmpty() && !ruleConflict,
				!labelPending,
				!labelPending
			);
		}
		for (int index = 0; index < actionTexts.size(); index++) {
			drawTextButton(
				context, labelLayout.actionButtons().get(index), actionTexts.get(index),
				actionEnabled.get(index), mouseX, mouseY
			);
		}

		Text state = editorStateText();
		boolean stateTruncated = state != null && drawWrappedText(
			context, renderer, state, labelLayout.statusArea(), 3,
			labelPending || labelDetailsPending || tagQueryPending ? 0xFFFF80 : 0xFFAAAA
		);
		if (state == null && (labelEditorMode == ContainerLabelMode.ITEM_TAG || whitelistTagPicker)
			&& labelLayout.statusArea().height() > 0) {
			Text listName = queriedTagChoices.isEmpty()
				? Text.translatable("gui.workshop_zone.label.common_categories")
				: Text.translatable("gui.workshop_zone.label.item_tag");
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, listName, labelLayout.statusArea().width()),
				labelLayout.statusArea().left(), labelLayout.statusArea().top(), 0x888888);
		}

		for (int index = 0; index < modeTexts.size(); index++) {
			showControlTooltip(context, renderer, labelLayout.modeButtons().get(index), modeTexts.get(index), true, mouseX, mouseY);
		}
		for (int index = 0; index < actionTexts.size(); index++) {
			showControlTooltip(
				context, renderer, labelLayout.actionButtons().get(index), actionTexts.get(index), actionEnabled.get(index), mouseX, mouseY
			);
		}
		ContainerLabelEntry overviewEntry = labelEditorMode == ContainerLabelMode.WHITELIST
			? whitelistOverviewEntryAt(mouseX, mouseY)
			: null;
		if (currentTruncated && labelLayout.currentArea().contains(mouseX, mouseY)
			&& mouseY < labelLayout.currentArea().top() + 9) {
			context.drawTooltip(renderer, currentText, mouseX, mouseY);
		} else if (stateTruncated && state != null && labelLayout.statusArea().contains(mouseX, mouseY)) {
			context.drawTooltip(renderer, state, mouseX, mouseY);
		} else if (overviewEntry != null) {
			context.drawTooltip(renderer, whitelistEntryTooltip(overviewEntry), mouseX, mouseY);
		} else if (!actionEnabled.getFirst() && labelLayout.actionButtons().getFirst().contains(mouseX, mouseY)
			&& getCursorStack().isEmpty()) {
			context.drawTooltip(renderer, Text.translatable("gui.workshop_zone.label.cursor_empty"), mouseX, mouseY);
		}
	}

	private void renderExactCandidate(DrawContext context, TextRenderer renderer) {
		WorkshopSidebarMetrics.Rect area = labelLayout.listArea();
		if (area.height() <= 0) {
			return;
		}
		Text candidate = candidateItemId == null
			? Text.translatable("gui.workshop_zone.label.none")
			: candidateItem.getName();
		int textLeft = area.left();
		if (candidateItemId != null && area.height() >= 16) {
			context.drawItem(candidateIcon, area.left(), area.top());
			textLeft += 20;
		}
		WorkshopSidebarMetrics.Rect textArea = new WorkshopSidebarMetrics.Rect(
			textLeft, area.top(), Math.max(0, area.right() - textLeft), Math.min(area.height(), 27)
		);
		drawWrappedText(
			context, renderer, Text.translatable("gui.workshop_zone.label.candidate_value", candidate),
			textArea, 3, candidateItemId == null ? 0x777777 : 0xFFFFFF
		);
	}

	private void renderWhitelistEntries(DrawContext context, TextRenderer renderer, int mouseX, int mouseY) {
		WorkshopSidebarMetrics.Rect list = labelLayout.listArea();
		int viewport = list.height();
		int maxScroll = Math.max(0, whitelistEntries.size() * WHITELIST_ROW_HEIGHT - viewport);
		whitelistScrollOffset = MathHelper.clamp(whitelistScrollOffset, 0, maxScroll);
		int hoveredIndex = WorkshopSidebarLayout.rowAt(
			mouseX, mouseY, list.left(), list.right(), list.top(), list.bottom(),
			WHITELIST_ROW_HEIGHT, whitelistScrollOffset, whitelistEntries.size()
		);
		context.enableScissor(list.left(), list.top(), list.right(), list.bottom());
		if (whitelistEntries.isEmpty()) {
			context.drawCenteredTextWithShadow(
				renderer, Text.translatable("gui.workshop_zone.label.whitelist_empty"),
				list.left() + list.width() / 2, list.top() + 4, 0x777777
			);
		}
		for (int index = 0; index < whitelistEntries.size(); index++) {
			ContainerLabelEntry entry = whitelistEntries.get(index);
			int rowY = list.top() + index * WHITELIST_ROW_HEIGHT - whitelistScrollOffset;
			if (rowY + WHITELIST_ROW_HEIGHT <= list.top() || rowY >= list.bottom()) {
				continue;
			}
			boolean selected = index == selectedWhitelistIndex;
			boolean hovered = index == hoveredIndex;
			boolean unavailable = labelDetails.stream().anyMatch(detail -> detail.entry().equals(entry) && detail.unavailable());
			context.fill(
				list.left(), rowY, list.right(), rowY + WHITELIST_ROW_HEIGHT - 1,
				selected ? 0xCC405A36 : hovered ? 0xCC3A3A48 : 0xAA292934
			);
			ItemStack icon;
			Text name;
			String idText;
			if (entry.type() == ContainerLabelEntryType.ITEM) {
				Item item = Registries.ITEM.getOrEmpty(entry.valueId()).orElse(net.minecraft.item.Items.BARRIER);
				icon = ClientWorkshopState.labelIcon(entry.valueId());
				name = item.getName();
				idText = entry.valueId().toString();
			} else {
				Identifier representative = labelDetails.stream().filter(detail -> detail.entry().equals(entry))
					.findFirst().flatMap(ContainerLabelDetailsEntry::representativeItemId)
					.orElseGet(() -> ContainerItemTags.representativeItemId(entry.valueId()).orElse(null));
				icon = iconForTag(entry.valueId(), representative);
				name = ContainerTagPreset.displayName(entry.valueId());
				idText = "#" + entry.valueId();
			}
			context.drawItem(icon, list.left() + 3, rowY + 5);
			int textLeft = list.left() + 23;
			int textWidth = Math.max(0, list.right() - (unavailable ? 17 : 7) - textLeft);
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, name, textWidth), textLeft, rowY + 3,
				unavailable ? 0xFF7777 : 0xFFFFFF);
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, Text.literal(idText), textWidth),
				textLeft, rowY + 14, 0x888888);
			if (unavailable) {
				context.drawCenteredTextWithShadow(renderer, "!", list.right() - 10, rowY + 8, 0xFF5555);
			}
		}
		context.disableScissor();
		if (maxScroll > 0 && viewport > 0) {
			int trackHeight = Math.max(1, viewport - 4);
			int thumbHeight = Math.min(trackHeight, Math.max(12, trackHeight * viewport / Math.max(1, whitelistEntries.size() * WHITELIST_ROW_HEIGHT)));
			int thumbY = list.top() + 2 + (trackHeight - thumbHeight) * whitelistScrollOffset / maxScroll;
			context.fill(list.right() - 3, list.top() + 2, list.right() - 1, list.top() + 2 + trackHeight, 0x88484852);
			context.fill(list.right() - 3, thumbY, list.right() - 1, thumbY + thumbHeight, 0xFFE0E0E0);
		}
		if (hoveredIndex >= 0 && hoveredIndex < whitelistEntries.size()) {
			ContainerLabelEntry hovered = whitelistEntries.get(hoveredIndex);
			context.drawTooltip(renderer, whitelistEntryTooltip(hovered), mouseX, mouseY);
		}
	}

	private void renderWhitelistOverview(DrawContext context, TextRenderer renderer) {
		WorkshopSidebarMetrics.Rect area = labelLayout.currentArea();
		if (area.height() < 23) {
			return;
		}
		int count = whitelistOverviewCount();
		int top = area.top() + 11;
		for (int index = 0; index < count; index++) {
			renderScaledItem(context, whitelistIcon(whitelistEntries.get(index)), area.left() + index * 13, top, 12);
		}
		int remaining = whitelistEntries.size() - count;
		if (remaining > 0) {
			String counter = "+" + remaining;
			int x = area.left() + count * 13 + 1;
			int width = renderer.getWidth(counter) + 4;
			context.fill(x, top + 1, Math.min(area.right(), x + width), top + 11, 0xCC101018);
			context.drawTextWithShadow(renderer, counter, x + 2, top + 2, 0xFFFFFF);
		}
	}

	private ContainerLabelEntry whitelistOverviewEntryAt(double mouseX, double mouseY) {
		WorkshopSidebarMetrics.Rect area = labelLayout.currentArea();
		if (area.height() < 23) {
			return null;
		}
		int top = area.top() + 11;
		if (mouseY < top || mouseY >= top + 12 || mouseX < area.left()) {
			return null;
		}
		int index = (int)(mouseX - area.left()) / 13;
		int count = whitelistOverviewCount();
		return index >= 0 && index < count && mouseX < area.left() + index * 13 + 12
			? whitelistEntries.get(index)
			: null;
	}

	private int whitelistOverviewCount() {
		WorkshopSidebarMetrics.Rect area = labelLayout.currentArea();
		if (area.height() < 23) {
			return 0;
		}
		int heightLimit = area.height() >= 25 ? 8 : 4;
		int widthLimit = Math.max(0, (area.width() - (whitelistEntries.size() > heightLimit ? 20 : 0)) / 13);
		return Math.min(whitelistEntries.size(), Math.min(heightLimit, widthLimit));
	}

	private ItemStack whitelistIcon(ContainerLabelEntry entry) {
		if (entry.type() == ContainerLabelEntryType.ITEM) {
			return ClientWorkshopState.labelIcon(entry.valueId());
		}
		ContainerLabelDetailsEntry detail = labelDetail(entry);
		Identifier representative = detail == null
			? ContainerItemTags.representativeItemId(entry.valueId()).orElse(null)
			: detail.representativeItemId().orElse(null);
		return iconForTag(entry.valueId(), representative);
	}

	private List<Text> whitelistEntryTooltip(ContainerLabelEntry entry) {
		boolean unavailable = isWhitelistEntryUnavailable(entry);
		List<Text> tooltip = new ArrayList<>();
		tooltip.add(entry.type() == ContainerLabelEntryType.ITEM
			? Registries.ITEM.getOrEmpty(entry.valueId()).orElse(net.minecraft.item.Items.BARRIER).getName()
			: ContainerTagPreset.displayName(entry.valueId()));
		tooltip.add(Text.literal((entry.type() == ContainerLabelEntryType.ITEM_TAG ? "#" : "") + entry.valueId()).formatted(Formatting.GRAY));
		if (unavailable) {
			tooltip.add(Text.translatable("gui.workshop_zone.label.tag_unavailable").formatted(Formatting.RED));
		}
		return tooltip;
	}

	private boolean isWhitelistEntryUnavailable(ContainerLabelEntry entry) {
		if (entry.type() == ContainerLabelEntryType.ITEM) {
			return false;
		}
		ContainerLabelDetailsEntry detail = labelDetail(entry);
		return detail == null
			? ContainerItemTags.availability(entry.valueId()) != ContainerItemTags.Availability.AVAILABLE
			: detail.unavailable();
	}

	private ContainerLabelDetailsEntry labelDetail(ContainerLabelEntry entry) {
		return labelDetails.stream().filter(detail -> detail.entry().equals(entry)).findFirst().orElse(null);
	}

	private void handleLabelEditorClick(ClientWorkshopSnapshot snapshot, double mouseX, double mouseY) {
		if (labelPending || labelLayout == null) {
			return;
		}
		if (labelLayout.modeButtons().get(0).contains(mouseX, mouseY)) {
			labelEditorMode = ContainerLabelMode.EXACT_ITEM;
			labelResult = null;
			whitelistTagPicker = false;
			tagScrollOffset = 0;
			return;
		}
		if (labelLayout.modeButtons().get(1).contains(mouseX, mouseY)) {
			labelEditorMode = ContainerLabelMode.ITEM_TAG;
			labelResult = null;
			whitelistTagPicker = false;
			tagScrollOffset = 0;
			return;
		}
		if (labelLayout.modeButtons().get(2).contains(mouseX, mouseY)) {
			labelEditorMode = ContainerLabelMode.WHITELIST;
			labelResult = null;
			whitelistTagPicker = false;
			selectedWhitelistIndex = -1;
			whitelistScrollOffset = 0;
			return;
		}
		if (labelEditorMode == ContainerLabelMode.ITEM_TAG
			|| labelEditorMode == ContainerLabelMode.WHITELIST && whitelistTagPicker) {
			List<ContainerTagCandidate> choices = combinedTagChoices();
			WorkshopSidebarMetrics.Rect list = labelLayout.listArea();
			int index = WorkshopSidebarLayout.rowAt(
				mouseX, mouseY, list.left(), list.right(), list.top(), list.bottom(),
				TAG_ROW_HEIGHT, tagScrollOffset, choices.size()
			);
			if (index >= 0 && index < choices.size()) {
				ContainerTagCandidate choice = choices.get(index);
				selectedTagId = choice.tagId();
				selectedTagIcon = iconForTag(choice.tagId(), choice.representativeItemId());
				labelResult = null;
				return;
			}
		}
		if (labelEditorMode == ContainerLabelMode.WHITELIST && !whitelistTagPicker) {
			WorkshopSidebarMetrics.Rect list = labelLayout.listArea();
			int index = WorkshopSidebarLayout.rowAt(
				mouseX, mouseY, list.left(), list.right(), list.top(), list.bottom(),
				WHITELIST_ROW_HEIGHT, whitelistScrollOffset, whitelistEntries.size()
			);
			if (index >= 0 && index < whitelistEntries.size()) {
				selectedWhitelistIndex = index;
				labelResult = null;
				return;
			}
		}

		int button = editorButtonAt(mouseX, mouseY);
		if (button < 0) {
			return;
		}
		ClientWorkshopEntry opened = openedEntry(snapshot);
		boolean ruleConflict = opened != null && opened.labelSummary().ruleConflict();
		if (labelEditorMode == ContainerLabelMode.EXACT_ITEM) {
			if (button == 0) {
				ItemStack cursor = getCursorStack();
				if (!cursor.isEmpty()) {
					candidateItemId = Registries.ITEM.getId(cursor.getItem());
					candidateItem = cursor.getItem();
					candidateIcon = new ItemStack(candidateItem);
					labelResult = null;
				}
			} else if (button == 1 && candidateItemId != null && !ruleConflict) {
				sendLabelEdit(snapshot, ContainerLabelOperation.SET_EXACT_ITEM, candidateItemId);
			} else if (button == 2) {
				sendLabelEdit(snapshot, ContainerLabelOperation.CLEAR, null);
			} else if (button == 3) {
				closeLabelEditor();
			}
		} else if (labelEditorMode == ContainerLabelMode.ITEM_TAG) {
			if (button == 0) {
				requestTagCandidates(snapshot);
			} else if (button == 1 && selectedTagId != null && !ruleConflict) {
				sendLabelEdit(snapshot, ContainerLabelOperation.SET_ITEM_TAG, selectedTagId);
			} else if (button == 2) {
				sendLabelEdit(snapshot, ContainerLabelOperation.CLEAR, null);
			} else if (button == 3) {
				closeLabelEditor();
			}
		} else {
			handleWhitelistAction(snapshot, button, ruleConflict);
		}
	}

	private void handleWhitelistAction(ClientWorkshopSnapshot snapshot, int button, boolean ruleConflict) {
		switch (button) {
			case 0 -> {
				ItemStack cursor = getCursorStack();
				if (!cursor.isEmpty()) {
					addWhitelistEntry(ContainerLabelEntry.item(Registries.ITEM.getId(cursor.getItem())));
				}
			}
			case 1 -> {
				whitelistTagPicker = true;
				selectedTagId = null;
				tagScrollOffset = 0;
				requestTagCandidates(snapshot);
			}
			case 2 -> {
				if (whitelistTagPicker && selectedTagId != null) {
					addWhitelistEntry(ContainerLabelEntry.itemTag(selectedTagId));
					whitelistTagPicker = false;
					selectedTagId = null;
				}
			}
			case 3 -> {
				if (!whitelistTagPicker && selectedWhitelistIndex >= 0 && selectedWhitelistIndex < whitelistEntries.size()) {
					ContainerLabelEntry removed = whitelistEntries.get(selectedWhitelistIndex);
					List<ContainerLabelEntry> updated = new ArrayList<>(whitelistEntries);
					updated.remove(selectedWhitelistIndex);
					whitelistEntries = List.copyOf(updated);
					labelDetails = labelDetails.stream().filter(detail -> !detail.entry().equals(removed)).toList();
					selectedWhitelistIndex = -1;
					whitelistDirty = true;
					labelResult = null;
				}
			}
			case 4 -> {
				if (!labelDetailsPending && !ruleConflict && !whitelistEntries.isEmpty()) {
					sendWhitelistEdit(snapshot);
				}
			}
			case 5 -> {
				whitelistEntries = List.of();
				labelDetails = List.of();
				selectedWhitelistIndex = -1;
				whitelistDirty = false;
				loadedLabelDetailsRevision = -1;
				sendLabelEdit(snapshot, ContainerLabelOperation.CLEAR, null);
			}
			case 6 -> closeLabelEditor();
			default -> {
			}
		}
	}

	private void addWhitelistEntry(ContainerLabelEntry entry) {
		if (whitelistEntries.contains(entry)) {
			labelResult = Text.translatable("message.workshop_zone.label.duplicate_entry");
			return;
		}
		if (whitelistEntries.size() >= io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule.MAX_ENTRIES) {
			labelResult = Text.translatable("gui.workshop_zone.label.whitelist_full");
			return;
		}
		List<ContainerLabelEntry> updated = new ArrayList<>(whitelistEntries);
		updated.add(entry);
		updated.sort(ContainerLabelEntry.ORDER);
		whitelistEntries = List.copyOf(updated);
		selectedWhitelistIndex = whitelistEntries.indexOf(entry);
		whitelistDirty = true;
		labelResult = null;
	}

	private void sendWhitelistEdit(ClientWorkshopSnapshot snapshot) {
		if (!ClientPlayNetworking.canSend(UpdateContainerLabelPayload.ID)) {
			return;
		}
		labelPending = true;
		whitelistSavePending = true;
		labelResult = null;
		observedLabelResultSequence = ClientContainerLabelState.resultSequence();
		ClientPlayNetworking.send(new UpdateContainerLabelPayload(
			snapshot.sessionId(), snapshot.revision(), snapshot.syncId(), snapshot.openedEntryPosition(),
			ContainerLabelOperation.SET_WHITELIST, java.util.Optional.empty(), whitelistEntries
		));
	}

	private void renderTagChoices(DrawContext context, TextRenderer renderer, int mouseX, int mouseY) {
		if (labelLayout == null) {
			return;
		}
		List<ContainerTagCandidate> choices = combinedTagChoices();
		WorkshopSidebarMetrics.Rect list = labelLayout.listArea();
		int viewport = list.height();
		int maxScroll = Math.max(0, choices.size() * TAG_ROW_HEIGHT - viewport);
		tagScrollOffset = MathHelper.clamp(tagScrollOffset, 0, maxScroll);
		context.enableScissor(list.left(), list.top(), list.right(), list.bottom());
		ContainerTagCandidate hovered = null;
		int hoveredIndex = WorkshopSidebarLayout.rowAt(
			mouseX, mouseY, list.left(), list.right(), list.top(), list.bottom(),
			TAG_ROW_HEIGHT, tagScrollOffset, choices.size()
		);
		if (choices.isEmpty()) {
			context.drawCenteredTextWithShadow(
				renderer, Text.translatable("gui.workshop_zone.label.no_categories"),
				list.left() + list.width() / 2, list.top() + 4, 0x777777
			);
		}
		for (int index = 0; index < choices.size(); index++) {
			ContainerTagCandidate choice = choices.get(index);
			int rowY = list.top() + index * TAG_ROW_HEIGHT - tagScrollOffset;
			if (rowY + TAG_ROW_HEIGHT <= list.top() || rowY >= list.bottom()) {
				continue;
			}
			boolean rowHovered = hoveredIndex == index;
			boolean selected = choice.tagId().equals(selectedTagId);
			context.fill(
				list.left(), rowY, list.right(), rowY + TAG_ROW_HEIGHT - 1,
				selected ? 0xCC405A36 : rowHovered ? 0xCC3A3A48 : 0xAA292934
			);
			context.drawItem(iconForTag(choice.tagId(), choice.representativeItemId()), list.left() + 3, rowY + 3);
			Text name = ContainerTagPreset.displayName(choice.tagId());
			int textLeft = list.left() + 23;
			int textWidth = Math.max(0, list.right() - 7 - textLeft);
			context.drawTextWithShadow(renderer, WorkshopTextLayout.ellipsize(renderer, name, textWidth), textLeft, rowY + 2, 0xFFFFFF);
			context.drawTextWithShadow(
				renderer, WorkshopTextLayout.ellipsize(renderer, Text.literal("#" + choice.tagId()), textWidth),
				textLeft, rowY + 12, 0x888888
			);
			if (rowHovered) {
				hovered = choice;
			}
		}
		context.disableScissor();
		if (maxScroll > 0 && viewport > 0) {
			int trackHeight = Math.max(1, viewport - 4);
			int thumbHeight = Math.min(trackHeight, Math.max(12, trackHeight * viewport / Math.max(1, choices.size() * TAG_ROW_HEIGHT)));
			int thumbY = list.top() + 2 + (trackHeight - thumbHeight) * tagScrollOffset / maxScroll;
			context.fill(list.right() - 3, list.top() + 2, list.right() - 1, list.top() + 2 + trackHeight, 0x88484852);
			context.fill(list.right() - 3, thumbY, list.right() - 1, thumbY + thumbHeight, 0xFFE0E0E0);
		}
		if (hovered != null) {
			context.drawTooltip(
				renderer,
				List.of(
					ContainerTagPreset.displayName(hovered.tagId()),
					Text.translatable("gui.workshop_zone.label.tag_id", "#" + hovered.tagId()).formatted(Formatting.GRAY)
				),
				mouseX, mouseY
			);
		}
	}

	private Text currentLabelText(ClientWorkshopEntry opened) {
		if (opened == null || opened.labelSummary().mode() == ContainerLabelMode.NONE && !opened.labelSummary().conflict()) {
			return Text.translatable("gui.workshop_zone.label.none");
		}
		if (opened.labelSummary().ruleConflict()) {
			return Text.translatable("gui.workshop_zone.label.conflict");
		}
		if (opened.labelSummary().mode() == ContainerLabelMode.WHITELIST) {
			return Text.translatable("gui.workshop_zone.label.whitelist_count", opened.labelSummary().whitelistEntryCount());
		}
		if (opened.labelSummary().unavailable()) {
			return Text.translatable("gui.workshop_zone.label.tag_unavailable");
		}
		if (opened.labelSummary().contentConflict()) {
			return Text.translatable("gui.workshop_zone.label.content_conflict");
		}
		if (opened.labelSummary().mode() == ContainerLabelMode.ITEM_TAG) {
			return ContainerTagPreset.displayName(opened.labelSummary().itemTagId().orElseThrow());
		}
		return Text.translatable("gui.workshop_zone.label.allowed_item", opened.labelIcon().getName());
	}

	private List<ContainerTagCandidate> combinedTagChoices() {
		Map<Identifier, ContainerTagCandidate> combined = new LinkedHashMap<>();
		commonTagChoices.forEach(choice -> combined.putIfAbsent(choice.tagId(), choice));
		queriedTagChoices.forEach(choice -> combined.putIfAbsent(choice.tagId(), choice));
		return List.copyOf(combined.values());
	}

	private ItemStack iconForTag(Identifier tagId, Identifier representativeItemId) {
		if (representativeItemId == null) {
			return ClientWorkshopState.labelIcon(Identifier.ofVanilla("barrier"));
		}
		return ClientWorkshopState.labelIcon(representativeItemId);
	}

	private void requestTagCandidates(ClientWorkshopSnapshot snapshot) {
		ItemStack cursor = getCursorStack();
		if (cursor.isEmpty() || tagQueryPending || !ClientPlayNetworking.canSend(RequestItemTagCandidatesPayload.ID)) {
			return;
		}
		queriedItemId = Registries.ITEM.getId(cursor.getItem());
		tagQueryPending = true;
		labelResult = null;
		observedTagResponseSequence = ClientItemTagState.responseSequence();
		ClientPlayNetworking.send(new RequestItemTagCandidatesPayload(
			snapshot.sessionId(), snapshot.syncId(), snapshot.revision(), queriedItemId
		));
	}

	private void updateTagQueryResult(ClientWorkshopSnapshot snapshot) {
		long sequence = ClientItemTagState.responseSequence();
		if (!tagQueryPending || sequence == observedTagResponseSequence) {
			return;
		}
		observedTagResponseSequence = sequence;
		ItemTagCandidatesPayload response = ClientItemTagState.lastResponse();
		if (response == null || response.sessionId() != snapshot.sessionId() || response.syncId() != snapshot.syncId()
			|| response.revision() != snapshot.revision() || !response.itemId().equals(queriedItemId)) {
			return;
		}
		tagQueryPending = false;
		queriedTagChoices = response.candidates();
		tagScrollOffset = 0;
		labelResult = switch (response.result()) {
			case SUCCESS -> null;
			case TOO_MANY_CANDIDATES -> Text.translatable("gui.workshop_zone.label.categories_truncated");
			case NO_MATCHING_TAGS -> Text.translatable("gui.workshop_zone.label.no_categories");
			default -> Text.translatable(response.result().translationKey());
		};
	}

	private void requestLabelDetails(ClientWorkshopSnapshot snapshot) {
		if (labelDetailsPending || Util.getMeasuringTimeMs() < nextLabelDetailsRequestAt
			|| !ClientPlayNetworking.canSend(RequestContainerLabelDetailsPayload.ID)) {
			return;
		}
		pendingLabelDetailsRequestId = System.nanoTime() & Long.MAX_VALUE;
		pendingLabelDetailsRevision = snapshot.revision();
		labelDetailsPending = true;
		observedLabelDetailsSequence = ClientContainerLabelDetailsState.responseSequence();
		ClientPlayNetworking.send(new RequestContainerLabelDetailsPayload(
			pendingLabelDetailsRequestId, snapshot.sessionId(), snapshot.revision(), snapshot.syncId(), snapshot.openedEntryPosition()
		));
	}

	private void updateLabelDetails(ClientWorkshopSnapshot snapshot) {
		if (!labelPending && !labelDetailsPending && loadedLabelDetailsRevision != snapshot.revision()) {
			requestLabelDetails(snapshot);
		}
		long sequence = ClientContainerLabelDetailsState.responseSequence();
		if (!labelDetailsPending || sequence == observedLabelDetailsSequence) {
			return;
		}
		observedLabelDetailsSequence = sequence;
		ContainerLabelDetailsPayload response = ClientContainerLabelDetailsState.lastResponse();
		if (response == null || response.requestId() != pendingLabelDetailsRequestId
			|| response.sessionId() != snapshot.sessionId() || response.syncId() != snapshot.syncId()
			|| !response.openedEntryPosition().equals(snapshot.openedEntryPosition())) {
			return;
		}
		if (response.result() == io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResult.SUCCESS
			&& response.revision() != pendingLabelDetailsRevision) {
			return;
		}
		labelDetailsPending = false;
		if (response.result() != io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResult.SUCCESS) {
			nextLabelDetailsRequestAt = Util.getMeasuringTimeMs()
				+ (response.result() == io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResult.COOLDOWN ? 300L : 1_000L);
			labelResult = Text.translatable(response.result().translationKey());
			return;
		}
		nextLabelDetailsRequestAt = 0;
		loadedLabelDetailsRevision = response.revision();
		labelDetails = response.entries();
		if (response.mode() == ContainerLabelMode.WHITELIST && !whitelistDirty) {
			whitelistEntries = response.entries().stream().map(ContainerLabelDetailsEntry::entry).toList();
			selectedWhitelistIndex = -1;
			whitelistScrollOffset = 0;
		}
	}

	private int editorButtonAt(double mouseX, double mouseY) {
		if (labelLayout == null) {
			return -1;
		}
		for (int index = 0; index < labelLayout.actionButtons().size(); index++) {
			if (labelLayout.actionButtons().get(index).contains(mouseX, mouseY)) {
				return index;
			}
		}
		return -1;
	}

	private void closeLabelEditor() {
		labelEditor = false;
		candidateItemId = null;
		candidateItem = null;
		candidateIcon = ItemStack.EMPTY;
		selectedTagId = null;
		selectedTagIcon = ItemStack.EMPTY;
		queriedTagChoices = List.of();
		whitelistEntries = List.of();
		labelDetails = List.of();
		selectedWhitelistIndex = -1;
		whitelistScrollOffset = 0;
		whitelistTagPicker = false;
		whitelistDirty = false;
		whitelistSavePending = false;
		labelDetailsPending = false;
		loadedLabelDetailsRevision = -1;
		nextLabelDetailsRequestAt = 0;
		tagQueryPending = false;
		queriedItemId = null;
		labelResult = null;
	}

	private void sendLabelEdit(ClientWorkshopSnapshot snapshot, ContainerLabelOperation operation, Identifier itemId) {
		if (!ClientPlayNetworking.canSend(UpdateContainerLabelPayload.ID)) {
			return;
		}
		labelPending = true;
		labelResult = null;
		observedLabelResultSequence = ClientContainerLabelState.resultSequence();
		ClientPlayNetworking.send(new UpdateContainerLabelPayload(
			snapshot.sessionId(), snapshot.revision(), snapshot.syncId(), snapshot.openedEntryPosition(), operation,
			java.util.Optional.ofNullable(itemId)
		));
	}

	private void updateLabelResult(ClientWorkshopSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		long sequence = ClientContainerLabelState.resultSequence();
		if (sequence == observedLabelResultSequence) {
			return;
		}
		observedLabelResultSequence = sequence;
		ContainerLabelEditResultPayload result = ClientContainerLabelState.lastResult();
		if (result != null && result.sessionId() == snapshot.sessionId() && result.syncId() == snapshot.syncId()) {
			labelPending = false;
			if (whitelistSavePending) {
				if (result.result() == io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResult.WHITELIST_SUCCESS
					|| result.result() == io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResult.SUCCESS) {
					whitelistDirty = false;
					loadedLabelDetailsRevision = -1;
				}
				whitelistSavePending = false;
			}
			labelResult = Text.translatable(result.result().translationKey());
		}
	}

	private void sendDepositRequest(ClientWorkshopSnapshot snapshot) {
		boolean shift = net.minecraft.client.util.InputUtil.isKeyPressed(
			MinecraftClient.getInstance().getWindow().getHandle(),
			net.minecraft.client.util.InputUtil.GLFW_KEY_LEFT_SHIFT
		) || net.minecraft.client.util.InputUtil.isKeyPressed(
			MinecraftClient.getInstance().getWindow().getHandle(),
			net.minecraft.client.util.InputUtil.GLFW_KEY_RIGHT_SHIFT
		);
		pendingDepositRequestId = System.nanoTime();
		pendingDepositSessionId = snapshot.sessionId();
		pendingDepositSyncId = snapshot.syncId();
		pendingDepositExpiresAt = net.minecraft.util.Util.getMeasuringTimeMs() + PENDING_TIMEOUT_MILLIS;
		depositPending = true;
		observedDepositSequence = ClientDepositState.resultSequence();
		ClientPlayNetworking.send(
			new io.github.ikunkk02afk.workshopzone.network.DepositWorkshopItemsPayload(
				pendingDepositRequestId, snapshot.sessionId(), snapshot.revision(), snapshot.syncId(), shift
			)
		);
		WorkshopZone.LOGGER.debug(
			"Sending workshop deposit request requestId {} session {} includeHotbar {}",
			pendingDepositRequestId, snapshot.sessionId(), shift
		);
	}

	private void updateDepositResult(ClientWorkshopSnapshot snapshot) {
		if (!depositPending) {
			return;
		}
		if (snapshot == null
			|| snapshot.sessionId() != pendingDepositSessionId
			|| snapshot.syncId() != pendingDepositSyncId
			|| screen.getScreenHandler().syncId != pendingDepositSyncId
			|| net.minecraft.util.Util.getMeasuringTimeMs() >= pendingDepositExpiresAt) {
			depositPending = false;
			return;
		}
		long sequence = ClientDepositState.resultSequence();
		if (sequence == observedDepositSequence) {
			return;
		}
		observedDepositSequence = sequence;
		io.github.ikunkk02afk.workshopzone.network.WorkshopDepositResultPayload result = ClientDepositState.lastResult();
		if (WorkshopDepositResultFilter.matches(result, pendingDepositRequestId, snapshot.sessionId(), snapshot.syncId())) {
			depositPending = false;
		}
	}

	private ItemStack getCursorStack() {
		return screen.getScreenHandler().getCursorStack();
	}

	private void drawTextButton(
		DrawContext context,
		WorkshopSidebarMetrics.Rect bounds,
		Text text,
		boolean enabled,
		int mouseX,
		int mouseY
	) {
		int color = !enabled ? 0xFF292930 : bounds.contains(mouseX, mouseY) ? 0xFF626274 : 0xFF424250;
		context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), color);
		TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
		Text visibleText = WorkshopTextLayout.ellipsize(renderer, text, Math.max(0, bounds.width() - 6));
		context.drawCenteredTextWithShadow(
			renderer, visibleText, bounds.left() + bounds.width() / 2,
			bounds.top() + Math.max(1, (bounds.height() - 8) / 2), enabled ? 0xFFFFFF : 0x777777
		);
	}

	private boolean drawWrappedText(
		DrawContext context,
		TextRenderer renderer,
		Text text,
		WorkshopSidebarMetrics.Rect area,
		int maxLines,
		int color
	) {
		int visibleLines = Math.min(maxLines, area.height() / 9);
		WorkshopTextLayout.Wrapped wrapped = WorkshopTextLayout.wrap(renderer, text, area.width(), visibleLines);
		for (int index = 0; index < wrapped.lines().size(); index++) {
			context.drawTextWithShadow(renderer, wrapped.lines().get(index), area.left(), area.top() + index * 9, color);
		}
		return wrapped.truncated();
	}

	private void showControlTooltip(
		DrawContext context,
		TextRenderer renderer,
		WorkshopSidebarMetrics.Rect bounds,
		Text fullText,
		boolean enabled,
		int mouseX,
		int mouseY
	) {
		if (!bounds.contains(mouseX, mouseY)) {
			return;
		}
		narratedControl = fullText;
		if (!enabled || WorkshopTextLayout.isTruncated(renderer, fullText, Math.max(0, bounds.width() - 6))) {
			context.drawTooltip(renderer, fullText, mouseX, mouseY);
		}
	}
}
