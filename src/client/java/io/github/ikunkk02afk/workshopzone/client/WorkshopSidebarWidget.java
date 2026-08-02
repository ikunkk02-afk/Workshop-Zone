package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.mixin.client.HandledScreenAccessor;
import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.network.OpenWorkshopTargetPayload;
import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopRefreshPayload;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorkshopSidebarWidget extends ClickableWidget {
	private static final int PANEL_WIDTH = 154;
	private static final int COLLAPSED_WIDTH = 18;
	private static final int EDGE_GAP = 4;
	private static final int HEADER_HEIGHT = 54;
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_SIZE = 16;
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
		drawSmallButton(context, collapseX, getY() + 4, "<", mouseX, mouseY);
		drawSmallButton(context, refreshX, getY() + 4, "R", mouseX, mouseY);

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
			String name = textRenderer.trimToWidth(entry.displayName().getString(), PANEL_WIDTH - 38);
			context.drawTextWithShadow(textRenderer, name, getX() + 27, rowY + 3, tooFar ? 0x8A8A8A : 0xFFFFFF);
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
		if (matchingSnapshot() == null || !isMouseOver(mouseX, mouseY)
			|| !WorkshopScreenIntegration.isExpanded() || forcedCollapsed) {
			return false;
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
}
