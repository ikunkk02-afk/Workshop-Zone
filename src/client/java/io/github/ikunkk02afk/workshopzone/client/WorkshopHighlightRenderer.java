package io.github.ikunkk02afk.workshopzone.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class WorkshopHighlightRenderer {
	private static final RenderLayer THROUGH_WALL_LINES = new RenderLayer(
		"workshop_zone_highlight_lines",
		VertexFormats.LINES,
		VertexFormat.DrawMode.LINES,
		RenderLayer.DEFAULT_BUFFER_SIZE,
		false,
		true,
		() -> {
			RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
			RenderSystem.lineWidth(3.0F);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.disableDepthTest();
			RenderSystem.depthMask(false);
		},
		() -> {
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
			RenderSystem.lineWidth(1.0F);
		}
	) { };

	private WorkshopHighlightRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(WorkshopHighlightRenderer::render);
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) ->
			WorkshopScreenController.highlights().clear()
		);
	}

	private static void render(WorldRenderContext context) {
		MatrixStack matrices = context.matrixStack();
		if (matrices == null || context.consumers() == null || context.world() == null) {
			return;
		}
		Identifier dimensionId = context.world().getRegistryKey().getValue();
		List<WorkshopContainerHighlight> highlights = WorkshopScreenController.highlights()
			.active(Util.getMeasuringTimeMs(), dimensionId);
		if (highlights.isEmpty()) {
			return;
		}
		VertexConsumer consumer = context.consumers().getBuffer(THROUGH_WALL_LINES);
		Vec3d camera = context.camera().getPos();
		matrices.push();
		try {
			for (WorkshopContainerHighlight highlight : highlights) {
				float red = highlight.selected() ? 1.0F : 0.2F;
				float green = highlight.selected() ? 0.85F : 0.9F;
				float blue = highlight.selected() ? 0.1F : 1.0F;
				for (BlockPos position : highlight.positions()) {
					Box box = new Box(position).expand(0.003).offset(-camera.x, -camera.y, -camera.z);
					WorldRenderer.drawBox(matrices, consumer, box, red, green, blue, 0.95F);
					Box marker = new Box(
						position.getX() + 0.47, position.getY() + 1.0, position.getZ() + 0.47,
						position.getX() + 0.53, position.getY() + 1.6, position.getZ() + 0.53
					).offset(-camera.x, -camera.y, -camera.z);
					WorldRenderer.drawBox(matrices, consumer, marker, red, green, blue, 0.8F);
				}
			}
		} finally {
			matrices.pop();
		}
	}
}
