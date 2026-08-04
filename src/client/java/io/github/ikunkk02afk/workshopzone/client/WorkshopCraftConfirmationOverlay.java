package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public final class WorkshopCraftConfirmationOverlay {
	private static final int WIDTH = 236;
	private static final int MIN_HEIGHT = 188;
	private final CraftingScreen screen;
	private final WorkshopScreenController controller;
	private boolean closedTransientUi;

	public WorkshopCraftConfirmationOverlay(CraftingScreen screen, WorkshopScreenController controller) {
		this.screen = screen;
		this.controller = controller;
	}

	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen != screen || !ClientWorkshopCraftState.isVisible()) {
			closedTransientUi = false;
			return;
		}
		if (!closedTransientUi) {
			controller.closeTransientUiForCraftConfirmation();
			closedTransientUi = true;
		}
		Bounds bounds = bounds();
		TextRenderer renderer = client.textRenderer;
		context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0xF0181818);
		context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + 1, 0xFFE0A83A);
		context.drawTextWithShadow(renderer, Text.translatable("gui.workshop_zone.craft.confirm.title"), bounds.x + 8, bounds.y + 7, 0xFFFFFFFF);

		WorkshopCraftPreviewView view = WorkshopCraftPreviewView.from(ClientWorkshopCraftState.preview());
		ItemStack output = view.output();
		context.drawItem(output, bounds.x + 8, bounds.y + 22);
		context.drawItemInSlot(renderer, output, bounds.x + 8, bounds.y + 22);
		context.drawTextWithShadow(renderer, Text.translatable(
			"gui.workshop_zone.craft.confirm.output", output.getName(), output.getCount()
		), bounds.x + 32, bounds.y + 27, 0xFFFFFFFF);

		int textY = bounds.y + 46;
		List<OrderedText> description = renderer.wrapLines(Text.translatable("gui.workshop_zone.craft.confirm.description"), bounds.width - 16);
		for (OrderedText line : description) {
			context.drawTextWithShadow(renderer, line, bounds.x + 8, textY, 0xFFD0D0D0);
			textY += 10;
		}

		int buttonHeight = 22;
		int buttonY = bounds.y + bounds.height - buttonHeight - 8;
		int listTop = textY + 4;
		int listBottom = buttonY - 5;
		int rowHeight = 28;
		int visibleRows = Math.max(1, (listBottom - listTop) / rowHeight);
		int first = WorkshopCraftClientFilter.clampScrollIndex(
			ClientWorkshopCraftState.scrollIndex(), view.materials().size(), visibleRows
		);
		List<WorkshopCraftMaterialSummaryView> materials = view.materials();
		for (int visible = 0; visible < visibleRows && first + visible < materials.size(); visible++) {
			WorkshopCraftMaterialSummaryView material = materials.get(first + visible);
			int rowY = listTop + visible * rowHeight;
			ItemStack stack = material.stack();
			context.drawItem(stack, bounds.x + 8, rowY);
			context.drawItemInSlot(renderer, stack, bounds.x + 8, rowY);
			context.drawTextWithShadow(renderer, Text.translatable(
				"gui.workshop_zone.craft.confirm.output", stack.getName(), material.totalAmount()
			), bounds.x + 32, rowY + 1, 0xFFFFFFFF);
			context.drawTextWithShadow(renderer, Text.translatable(
				"gui.workshop_zone.craft.confirm.player_amount", material.playerAmount()
			), bounds.x + 32, rowY + 11, 0xFFB0B0B0);
			context.drawTextWithShadow(renderer, Text.translatable(
				"gui.workshop_zone.craft.confirm.storage_amount", material.storageAmount()
			), bounds.x + 110, rowY + 11, 0xFFFFD080);
			if (mouseX >= bounds.x + 8 && mouseX < bounds.x + 28 && mouseY >= rowY && mouseY < rowY + 20) {
				List<Text> tooltip = new ArrayList<>();
				tooltip.add(stack.getName());
				tooltip.add(Text.literal(Registries.ITEM.getId(stack.getItem()).toString()));
				context.drawTooltip(renderer, tooltip, mouseX, mouseY);
			}
		}
		if (mouseX >= bounds.x + 8 && mouseX < bounds.x + 28 && mouseY >= bounds.y + 22 && mouseY < bounds.y + 42) {
			List<Text> tooltip = List.of(
				output.getName(),
				Text.literal(Registries.ITEM.getId(output.getItem()).toString()),
				Text.translatable("gui.workshop_zone.craft.confirm.recipe_id", ClientWorkshopCraftState.preview().recipeId())
			);
			context.drawTooltip(renderer, tooltip, mouseX, mouseY);
		}
		if (materials.size() > visibleRows) {
			context.drawTextWithShadow(renderer, Text.literal("▲▼"), bounds.x + bounds.width - 22, listTop, 0xFFB0B0B0);
		}

		boolean expired = ClientWorkshopCraftState.isExpired(client);
		context.drawTextWithShadow(renderer, expired
			? Text.translatable("gui.workshop_zone.craft.confirm.expired")
			: Text.translatable("gui.workshop_zone.craft.confirm.storage_summary", view.usedContainerCount(), view.storageItemCount()),
			bounds.x + 8, buttonY - 14, expired ? 0xFFFF7070 : 0xFFD0D0D0);
		int gap = 5;
		int acceptWidth = (bounds.width - 16 - gap) / 2;
		context.fill(bounds.x + 8, buttonY, bounds.x + 8 + acceptWidth, buttonY + buttonHeight,
			expired || ClientWorkshopCraftState.isPending() ? 0xFF555555 : 0xFF4F7F42);
		context.fill(bounds.x + 8 + acceptWidth + gap, buttonY, bounds.x + bounds.width - 8, buttonY + buttonHeight, 0xFF6B4545);
		context.drawCenteredTextWithShadow(renderer, ClientWorkshopCraftState.isPending()
			? Text.translatable("gui.workshop_zone.craft.confirm.pending")
			: Text.translatable("gui.workshop_zone.craft.confirm.accept"),
			bounds.x + 8 + acceptWidth / 2, buttonY + 7, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(renderer, Text.translatable("gui.workshop_zone.craft.confirm.cancel"),
			bounds.x + 8 + acceptWidth + gap + (bounds.width - 16 - acceptWidth - gap) / 2, buttonY + 7, 0xFFFFFFFF);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!ClientWorkshopCraftState.isVisible()) {
			return false;
		}
		Bounds bounds = bounds();
		int buttonY = bounds.y + bounds.height - 30;
		int gap = 5;
		int acceptWidth = (bounds.width - 16 - gap) / 2;
		if (mouseX >= bounds.x + 8 && mouseX < bounds.x + 8 + acceptWidth
			&& mouseY >= buttonY && mouseY < buttonY + 22) {
			ClientWorkshopCraftState.confirm(MinecraftClient.getInstance());
			return true;
		}
		if (mouseX >= bounds.x + 8 + acceptWidth + gap && mouseX < bounds.x + bounds.width - 8
			&& mouseY >= buttonY && mouseY < buttonY + 22) {
			ClientWorkshopCraftState.cancel(MinecraftClient.getInstance());
			return true;
		}
		ClientWorkshopCraftState.cancel(MinecraftClient.getInstance());
		return true;
	}

	public boolean mouseScrolled(double horizontal, double vertical) {
		if (!ClientWorkshopCraftState.isVisible()) {
			return false;
		}
		ClientWorkshopCraftState.scroll(vertical > 0 ? -1 : 1, 3);
		return true;
	}

	private Bounds bounds() {
		return WorkshopSidebarPlacementRegistry.get(screen)
			.filter(placement -> !placement.collapsed() && placement.panel().width() >= WIDTH && placement.panel().height() >= MIN_HEIGHT)
			.map(placement -> new Bounds(placement.panel().left(), placement.panel().top(), placement.panel().width(), placement.panel().height()))
			.orElseGet(() -> fallbackBounds());
	}

	private Bounds fallbackBounds() {
		int width = Math.min(WIDTH, screen.width - 12);
		int height = Math.min(236, screen.height - 12);
		int centerX = screen.width / 2;
		int x = MathHelper.clamp(centerX - width / 2, 6, Math.max(6, screen.width - width - 6));
		int y = MathHelper.clamp(screen.height / 2 - height / 2, 6, Math.max(6, screen.height - height - 6));
		return new Bounds(x, y, width, height);
	}

	private record Bounds(int x, int y, int width, int height) {
	}
}
