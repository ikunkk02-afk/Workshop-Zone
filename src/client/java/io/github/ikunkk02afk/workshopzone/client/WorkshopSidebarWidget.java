package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.mixin.client.HandledScreenAccessor;
import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.label.ContainerItemTags;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerTagCandidate;
import io.github.ikunkk02afk.workshopzone.label.ContainerTagPreset;
import io.github.ikunkk02afk.workshopzone.network.OpenWorkshopTargetPayload;
import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopRefreshPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResultPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelOperation;
import io.github.ikunkk02afk.workshopzone.network.UpdateContainerLabelPayload;
import io.github.ikunkk02afk.workshopzone.network.ItemTagCandidatesPayload;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorkshopSidebarWidget extends ClickableWidget {
	private static final int PANEL_WIDTH = 154;
	private static final int COLLAPSED_WIDTH = 18;
	private static final int EDGE_GAP = 4;
	private static final int HEADER_HEIGHT = 54;
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_SIZE = 16;
	private static final int TAG_ROW_HEIGHT = 22;
	private static final long PENDING_TIMEOUT_MILLIS = 3_000L;
	private static final double MAX_VISUAL_OPEN_DISTANCE_SQUARED = 64.0;

	private final HandledScreen<?> screen;
	private final boolean showWhileLoading;
	private int scrollOffset;
	private boolean forcedCollapsed;
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
	private final Map<Identifier, ItemStack> tagIconCache = new HashMap<>();
	private int tagScrollOffset;
	private boolean tagQueryPending;
	private Identifier queriedItemId;
	private long observedTagResponseSequence;
	private Text labelResult;
	private long observedLabelResultSequence;

	public WorkshopSidebarWidget(HandledScreen<?> screen, boolean showWhileLoading) {
		super(0, 0, PANEL_WIDTH, 120, Text.translatable("gui.workshop_zone.sidebar.title"));
		this.screen = screen;
		this.showWhileLoading = showWhileLoading;
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
			return;
		}
		updatePending(snapshot);
		if (!updateBounds()) {
			active = false;
			return;
		}
		boolean expanded = WorkshopScreenIntegration.isExpanded() && !forcedCollapsed;
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

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
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.workshop_zone.sidebar.title"), getX() + 7, getY() + 6, 0xFFFFFF);
		if (snapshot == null) {
			Text status = Text.translatable(
				presentation == WorkshopSidebarPresentation.NO_SESSION
					? "gui.workshop_zone.sidebar.no_session"
					: "gui.workshop_zone.sidebar.loading"
			);
			context.drawTextWithShadow(textRenderer, status, getX() + 7, getY() + 23, 0xA8A8A8);
			return;
		}
		context.drawTextWithShadow(
			textRenderer,
			Text.translatable("gui.workshop_zone.sidebar.containers", snapshot.containerCount()),
			getX() + 7, getY() + 21, 0xD8D8D8
		);
		context.drawTextWithShadow(
			textRenderer,
			Text.translatable("gui.workshop_zone.sidebar.workstations", snapshot.workstationCount()),
			getX() + 7, getY() + 34, 0xD8D8D8
		);

		int collapseX = getRight() - BUTTON_SIZE - 3;
		int refreshX = collapseX - BUTTON_SIZE - 2;
		int labelX = refreshX - BUTTON_SIZE - 2;
		drawSmallButton(context, collapseX, getY() + 4, "<", mouseX, mouseY);
		drawSmallButton(context, refreshX, getY() + 4, "R", mouseX, mouseY);
		if (supportsLabelEditor(snapshot)) {
			drawSmallButton(context, labelX, getY() + 4, "L", mouseX, mouseY);
		}
		updateLabelResult(snapshot);
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
			boolean hasRightIcon = entry.labelSummary().hasLabel() || entry.labelSummary().conflict() || entry.labelSummary().unavailable();
			String name = textRenderer.trimToWidth(entry.displayName().getString(), PANEL_WIDTH - (hasRightIcon ? 58 : 38));
			context.drawTextWithShadow(textRenderer, name, getX() + 27, rowY + 3, tooFar ? 0x8A8A8A : 0xFFFFFF);
			if (entry.labelSummary().conflict()) {
				context.drawCenteredTextWithShadow(textRenderer, "!", getRight() - 14, rowY + 8, 0xFF5555);
			} else if (!entry.labelIcon().isEmpty()) {
				context.drawItem(entry.labelIcon(), getRight() - 23, rowY + 4);
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
			context.drawTextWithShadow(textRenderer, detail, getX() + 27, rowY + 14, tooFar ? 0x777777 : 0xA8A8A8);
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
		} else if (inside(mouseX, mouseY, collapseX, getY() + 4, BUTTON_SIZE, BUTTON_SIZE)) {
			context.drawTooltip(textRenderer, Text.translatable("gui.workshop_zone.sidebar.collapse"), mouseX, mouseY);
		} else if (inside(mouseX, mouseY, refreshX, getY() + 4, BUTTON_SIZE, BUTTON_SIZE)) {
			Text tooltip = System.currentTimeMillis() < nextLocalRefreshAt
				? Text.translatable("gui.workshop_zone.sidebar.refresh_cooldown")
				: Text.translatable("gui.workshop_zone.sidebar.refresh");
			context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
		} else if (supportsLabelEditor(snapshot) && inside(mouseX, mouseY, labelX, getY() + 4, BUTTON_SIZE, BUTTON_SIZE)) {
			context.drawTooltip(textRenderer, Text.translatable("gui.workshop_zone.label.button"), mouseX, mouseY);
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
		boolean expanded = WorkshopScreenIntegration.isExpanded() && !forcedCollapsed;
		if (!expanded) {
			WorkshopScreenIntegration.setExpanded(true);
			return;
		}
		int collapseX = getRight() - BUTTON_SIZE - 3;
		int refreshX = collapseX - BUTTON_SIZE - 2;
		int labelX = refreshX - BUTTON_SIZE - 2;
		if (inside(mouseX, mouseY, collapseX, getY() + 4, BUTTON_SIZE, BUTTON_SIZE)) {
			WorkshopScreenIntegration.setExpanded(false);
			return;
		}
		if (inside(mouseX, mouseY, refreshX, getY() + 4, BUTTON_SIZE, BUTTON_SIZE)
			&& System.currentTimeMillis() >= nextLocalRefreshAt
			&& ClientPlayNetworking.canSend(RequestWorkshopRefreshPayload.ID)) {
			nextLocalRefreshAt = System.currentTimeMillis() + 1000L;
			ClientPlayNetworking.send(new RequestWorkshopRefreshPayload(snapshot.sessionId(), snapshot.syncId()));
			return;
		}
		if (supportsLabelEditor(snapshot) && inside(mouseX, mouseY, labelX, getY() + 4, BUTTON_SIZE, BUTTON_SIZE)) {
			labelEditor = true;
			ClientWorkshopEntry opened = openedEntry(snapshot);
			labelEditorMode = opened != null && opened.labelSummary().mode() == ContainerLabelMode.ITEM_TAG
				? ContainerLabelMode.ITEM_TAG : ContainerLabelMode.EXACT_ITEM;
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
			tagQueryPending = false;
			queriedItemId = null;
			observedTagResponseSequence = ClientItemTagState.responseSequence();
			observedLabelResultSequence = ClientContainerLabelState.resultSequence();
			labelResult = null;
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
		if (snapshot == null || !isMouseOver(mouseX, mouseY)
			|| !WorkshopScreenIntegration.isExpanded() || forcedCollapsed) {
			return false;
		}
		if (labelEditor) {
			if (labelEditorMode != ContainerLabelMode.ITEM_TAG
				|| !inside(mouseX, mouseY, getX() + 3, tagListTop(), getWidth() - 6, Math.max(0, tagListBottom() - tagListTop()))) {
				return false;
			}
			int viewport = Math.max(0, tagListBottom() - tagListTop());
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
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, getMessage());
		if (narratedEntry != null) {
			builder.put(NarrationPart.USAGE, narratedEntry.displayName().copy().append(". ").append(narratedState));
		}
	}

	private ClientWorkshopSnapshot matchingSnapshot() {
		return matchingSnapshot(ClientWorkshopState.current());
	}

	private ClientWorkshopSnapshot matchingSnapshot(ClientWorkshopSnapshot snapshot) {
		return snapshot != null && snapshot.syncId() == screen.getScreenHandler().syncId ? snapshot : null;
	}

	private boolean updateBounds() {
		HandledScreenAccessor accessor = (HandledScreenAccessor)screen;
		int guiX = accessor.workshopZone$getX();
		int guiY = accessor.workshopZone$getY();
		int guiWidth = accessor.workshopZone$getBackgroundWidth();
		int guiHeight = accessor.workshopZone$getBackgroundHeight();
		int desiredHeight = Math.max(72, Math.min(guiHeight, screen.height - EDGE_GAP * 2));
		int rightX = guiX + guiWidth + EDGE_GAP;
		int leftX = guiX - PANEL_WIDTH - EDGE_GAP;
		boolean rightFits = rightX + PANEL_WIDTH <= screen.width - EDGE_GAP;
		boolean recipeBookOpen = screen instanceof RecipeBookProvider provider && provider.getRecipeBookWidget().isOpen();
		boolean leftFits = !recipeBookOpen && leftX >= EDGE_GAP;
		boolean canExpand = rightFits || leftFits;
		forcedCollapsed = WorkshopScreenIntegration.isExpanded() && !canExpand;
		boolean collapsed = !WorkshopScreenIntegration.isExpanded() || forcedCollapsed;
		setWidth(collapsed ? COLLAPSED_WIDTH : PANEL_WIDTH);
		setHeight(collapsed ? 20 : desiredHeight);
		if (!collapsed && rightFits) {
			setX(rightX);
			setY(MathHelper.clamp(guiY, EDGE_GAP, Math.max(EDGE_GAP, screen.height - getHeight() - EDGE_GAP)));
		} else if (!collapsed && leftFits) {
			setX(leftX);
			setY(MathHelper.clamp(guiY, EDGE_GAP, Math.max(EDGE_GAP, screen.height - getHeight() - EDGE_GAP)));
		} else {
			int rightTabX = guiX + guiWidth + EDGE_GAP;
			int leftTabX = guiX - COLLAPSED_WIDTH - EDGE_GAP;
			if (rightTabX + COLLAPSED_WIDTH <= screen.width - EDGE_GAP) {
				setX(rightTabX);
				setY(MathHelper.clamp(guiY, EDGE_GAP, screen.height - getHeight() - EDGE_GAP));
			} else if (!recipeBookOpen && leftTabX >= EDGE_GAP) {
				setX(leftTabX);
				setY(MathHelper.clamp(guiY, EDGE_GAP, screen.height - getHeight() - EDGE_GAP));
			} else if (guiY - getHeight() - EDGE_GAP >= EDGE_GAP) {
				setX(MathHelper.clamp(guiX + guiWidth - getWidth(), EDGE_GAP, screen.width - getWidth() - EDGE_GAP));
				setY(guiY - getHeight() - EDGE_GAP);
			} else if (guiY + guiHeight + EDGE_GAP + getHeight() <= screen.height - EDGE_GAP) {
				setX(MathHelper.clamp(guiX + guiWidth - getWidth(), EDGE_GAP, screen.width - getWidth() - EDGE_GAP));
				setY(guiY + guiHeight + EDGE_GAP);
			} else {
				return false;
			}
		}
		return true;
	}

	private void drawSmallButton(DrawContext context, int x, int y, String label, int mouseX, int mouseY) {
		context.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, inside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE) ? 0xFF626274 : 0xFF424250);
		context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x + BUTTON_SIZE / 2, y + 4, 0xFFFFFF);
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
		if (entry.labelSummary().unavailable()) {
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
		updateTagQueryResult(snapshot);
		TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
		ClientWorkshopEntry opened = openedEntry(snapshot);
		int top = labelEditorTop();
		context.drawTextWithShadow(renderer, Text.translatable("gui.workshop_zone.label.mode"), getX() + 7, top, 0xA8A8A8);
		drawTextButton(
			context, getX() + 5, top + 11, 70, Text.translatable("gui.workshop_zone.label.mode_exact"),
			!labelPending && labelEditorMode != ContainerLabelMode.EXACT_ITEM, mouseX, mouseY
		);
		drawTextButton(
			context, getX() + 79, top + 11, 70, Text.translatable("gui.workshop_zone.label.mode_tag"),
			!labelPending && labelEditorMode != ContainerLabelMode.ITEM_TAG, mouseX, mouseY
		);
		Text current = currentLabelText(opened);
		context.drawTextWithShadow(
			renderer,
			renderer.trimToWidth(Text.translatable("gui.workshop_zone.label.current_value", current).getString(), PANEL_WIDTH - 14),
			getX() + 7, top + 32, 0xFFD080
		);

		if (labelEditorMode == ContainerLabelMode.EXACT_ITEM) {
			Text candidate = candidateItemId == null
				? Text.translatable("gui.workshop_zone.label.none")
				: candidateItem.getName();
			if (candidateItemId != null) {
				context.drawItem(candidateIcon, getX() + 7, top + 42);
			}
			context.drawTextWithShadow(
				renderer,
				renderer.trimToWidth(Text.translatable("gui.workshop_zone.label.candidate_value", candidate).getString(), PANEL_WIDTH - (candidateItemId == null ? 14 : 34)),
				getX() + (candidateItemId == null ? 7 : 27), top + 45, candidateItemId == null ? 0x777777 : 0xFFFFFF
			);
		} else {
			renderTagChoices(context, renderer, mouseX, mouseY);
		}

		int buttonTop = labelButtonTop();
		boolean ruleConflict = opened != null && opened.labelSummary().ruleConflict();
		if (labelEditorMode == ContainerLabelMode.EXACT_ITEM) {
			drawEditorButton(context, 0, Text.translatable("gui.workshop_zone.label.use_cursor"), !labelPending && !getCursorStack().isEmpty(), mouseX, mouseY);
			drawEditorButton(context, 1, Text.translatable("gui.workshop_zone.label.save"), !labelPending && candidateItemId != null && !ruleConflict, mouseX, mouseY);
		} else {
			drawEditorButton(context, 0, Text.translatable("gui.workshop_zone.label.find_categories"), !labelPending && !tagQueryPending && !getCursorStack().isEmpty(), mouseX, mouseY);
			drawEditorButton(context, 1, Text.translatable("gui.workshop_zone.label.save"), !labelPending && selectedTagId != null && !ruleConflict, mouseX, mouseY);
		}
		drawEditorButton(context, 2, Text.translatable("gui.workshop_zone.label.clear"), !labelPending, mouseX, mouseY);
		drawEditorButton(context, 3, Text.translatable("gui.workshop_zone.label.cancel"), !labelPending, mouseX, mouseY);
		if (labelPending || tagQueryPending || labelResult != null) {
			Text state = labelPending
				? Text.translatable("gui.workshop_zone.label.pending")
				: tagQueryPending ? Text.translatable("gui.workshop_zone.label.finding_categories") : labelResult;
			context.drawTextWithShadow(renderer, renderer.trimToWidth(state.getString(), PANEL_WIDTH - 14), getX() + 7, buttonTop - 11, labelPending || tagQueryPending ? 0xFFFF80 : 0xFFAAAA);
		} else if (labelEditorMode == ContainerLabelMode.ITEM_TAG) {
			Text listName = queriedTagChoices.isEmpty()
				? Text.translatable("gui.workshop_zone.label.common_categories")
				: Text.translatable("gui.workshop_zone.label.item_tag");
			context.drawTextWithShadow(renderer, renderer.trimToWidth(listName.getString(), PANEL_WIDTH - 14), getX() + 7, buttonTop - 11, 0x888888);
		}
		if (inside(mouseX, mouseY, editorButtonX(0), buttonTop, 34, 18) && getCursorStack().isEmpty()) {
			context.drawTooltip(renderer, Text.translatable("gui.workshop_zone.label.cursor_empty"), mouseX, mouseY);
		}
	}

	private void handleLabelEditorClick(ClientWorkshopSnapshot snapshot, double mouseX, double mouseY) {
		if (labelPending) {
			return;
		}
		int top = labelEditorTop();
		if (inside(mouseX, mouseY, getX() + 5, top + 11, 70, 18)) {
			labelEditorMode = ContainerLabelMode.EXACT_ITEM;
			labelResult = null;
			return;
		}
		if (inside(mouseX, mouseY, getX() + 79, top + 11, 70, 18)) {
			labelEditorMode = ContainerLabelMode.ITEM_TAG;
			labelResult = null;
			return;
		}
		if (labelEditorMode == ContainerLabelMode.ITEM_TAG) {
			List<ContainerTagCandidate> choices = combinedTagChoices();
			int index = WorkshopSidebarLayout.rowAt(
				mouseX, mouseY, getX() + 3, getRight() - 3, tagListTop(), tagListBottom(),
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

		int button = editorButtonAt(mouseX, mouseY);
		if (button < 0) {
			return;
		}
		ClientWorkshopEntry opened = openedEntry(snapshot);
		boolean ruleConflict = opened != null && opened.labelSummary().ruleConflict();
		if (button == 2) {
			sendLabelEdit(snapshot, ContainerLabelOperation.CLEAR, null);
		} else if (button == 3) {
			closeLabelEditor();
		} else if (labelEditorMode == ContainerLabelMode.EXACT_ITEM) {
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
			}
		} else if (button == 0) {
			requestTagCandidates(snapshot);
		} else if (button == 1 && selectedTagId != null && !ruleConflict) {
			sendLabelEdit(snapshot, ContainerLabelOperation.SET_ITEM_TAG, selectedTagId);
		}
	}

	private void renderTagChoices(DrawContext context, TextRenderer renderer, int mouseX, int mouseY) {
		int top = labelEditorTop();
		if (selectedTagId == null) {
			context.drawTextWithShadow(
				renderer, Text.translatable("gui.workshop_zone.label.selected_category", Text.translatable("gui.workshop_zone.label.none")),
				getX() + 7, top + 44, 0x777777
			);
		} else {
			context.drawItem(selectedTagIcon, getX() + 7, top + 41);
			context.drawTextWithShadow(
				renderer,
				renderer.trimToWidth(Text.translatable(
					"gui.workshop_zone.label.selected_category", ContainerTagPreset.displayName(selectedTagId)
				).getString(), PANEL_WIDTH - 38),
				getX() + 27, top + 44, 0xFFFFFF
			);
		}

		List<ContainerTagCandidate> choices = combinedTagChoices();
		int listTop = tagListTop();
		int listBottom = tagListBottom();
		int viewport = Math.max(0, listBottom - listTop);
		int maxScroll = Math.max(0, choices.size() * TAG_ROW_HEIGHT - viewport);
		tagScrollOffset = MathHelper.clamp(tagScrollOffset, 0, maxScroll);
		context.enableScissor(getX() + 3, listTop, getRight() - 3, listBottom);
		ContainerTagCandidate hovered = null;
		int hoveredIndex = WorkshopSidebarLayout.rowAt(
			mouseX, mouseY, getX() + 3, getRight() - 3, listTop, listBottom,
			TAG_ROW_HEIGHT, tagScrollOffset, choices.size()
		);
		if (choices.isEmpty()) {
			context.drawCenteredTextWithShadow(
				renderer, Text.translatable("gui.workshop_zone.label.no_categories"), getX() + getWidth() / 2, listTop + 4, 0x777777
			);
		}
		for (int index = 0; index < choices.size(); index++) {
			ContainerTagCandidate choice = choices.get(index);
			int rowY = listTop + index * TAG_ROW_HEIGHT - tagScrollOffset;
			if (rowY + TAG_ROW_HEIGHT <= listTop || rowY >= listBottom) {
				continue;
			}
			boolean rowHovered = hoveredIndex == index;
			boolean selected = choice.tagId().equals(selectedTagId);
			context.fill(
				getX() + 3, rowY, getRight() - 3, rowY + TAG_ROW_HEIGHT - 1,
				selected ? 0xCC405A36 : rowHovered ? 0xCC3A3A48 : 0xAA292934
			);
			context.drawItem(iconForTag(choice.tagId(), choice.representativeItemId()), getX() + 6, rowY + 3);
			Text name = ContainerTagPreset.displayName(choice.tagId());
			context.drawTextWithShadow(renderer, renderer.trimToWidth(name.getString(), PANEL_WIDTH - 36), getX() + 26, rowY + 2, 0xFFFFFF);
			context.drawTextWithShadow(renderer, renderer.trimToWidth("#" + choice.tagId(), PANEL_WIDTH - 36), getX() + 26, rowY + 12, 0x888888);
			if (rowHovered) {
				hovered = choice;
			}
		}
		context.disableScissor();
		if (hovered != null) {
			context.drawTooltip(
				renderer, Text.translatable("gui.workshop_zone.label.tag_id", "#" + hovered.tagId()), mouseX, mouseY
			);
		}
	}

	private Text currentLabelText(ClientWorkshopEntry opened) {
		if (opened == null || opened.labelSummary().mode() == ContainerLabelMode.NONE && !opened.labelSummary().conflict()) {
			return Text.translatable("gui.workshop_zone.label.none");
		}
		if (opened.labelSummary().unavailable()) {
			return Text.translatable("gui.workshop_zone.label.tag_unavailable");
		}
		if (opened.labelSummary().contentConflict()) {
			return Text.translatable("gui.workshop_zone.label.content_conflict");
		}
		if (opened.labelSummary().ruleConflict()) {
			return Text.translatable("gui.workshop_zone.label.conflict");
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
			return new ItemStack(net.minecraft.item.Items.BARRIER);
		}
		return tagIconCache.computeIfAbsent(representativeItemId, id ->
			new ItemStack(Registries.ITEM.getOrEmpty(id).orElse(net.minecraft.item.Items.BARRIER))
		);
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

	private int labelEditorTop() {
		return getY() + HEADER_HEIGHT + 3;
	}

	private int tagListTop() {
		return labelEditorTop() + 54;
	}

	private int tagListBottom() {
		return Math.max(tagListTop(), labelButtonTop() - 13);
	}

	private int labelButtonTop() {
		return getY() + getHeight() - 22;
	}

	private int editorButtonX(int index) {
		return getX() + 5 + index * 36;
	}

	private int editorButtonAt(double mouseX, double mouseY) {
		for (int index = 0; index < 4; index++) {
			if (inside(mouseX, mouseY, editorButtonX(index), labelButtonTop(), 34, 18)) {
				return index;
			}
		}
		return -1;
	}

	private void drawEditorButton(DrawContext context, int index, Text text, boolean enabled, int mouseX, int mouseY) {
		drawTextButton(context, editorButtonX(index), labelButtonTop(), 34, text, enabled, mouseX, mouseY);
	}

	private void closeLabelEditor() {
		labelEditor = false;
		candidateItemId = null;
		candidateItem = null;
		candidateIcon = ItemStack.EMPTY;
		selectedTagId = null;
		selectedTagIcon = ItemStack.EMPTY;
		queriedTagChoices = List.of();
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
		long sequence = ClientContainerLabelState.resultSequence();
		if (sequence == observedLabelResultSequence) {
			return;
		}
		observedLabelResultSequence = sequence;
		ContainerLabelEditResultPayload result = ClientContainerLabelState.lastResult();
		if (result != null && result.sessionId() == snapshot.sessionId() && result.syncId() == snapshot.syncId()) {
			labelPending = false;
			labelResult = Text.translatable(result.result().translationKey());
		}
	}

	private ItemStack getCursorStack() {
		return screen.getScreenHandler().getCursorStack();
	}

	private void drawTextButton(DrawContext context, int x, int y, int width, Text text, boolean enabled, int mouseX, int mouseY) {
		int color = !enabled ? 0xFF292930 : inside(mouseX, mouseY, x, y, width, 18) ? 0xFF626274 : 0xFF424250;
		context.fill(x, y, x + width, y + 18, color);
		TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
		context.drawCenteredTextWithShadow(renderer, renderer.trimToWidth(text.getString(), width - 4), x + width / 2, y + 5, enabled ? 0xFFFFFF : 0x777777);
	}
}
