package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;

public final class WorkshopTextLayout {
	private static final String ELLIPSIS = "…";

	private WorkshopTextLayout() {
	}

	public static Text ellipsize(TextRenderer renderer, Text fullText, int availableWidth) {
		Objects.requireNonNull(renderer, "renderer");
		Objects.requireNonNull(fullText, "fullText");
		if (availableWidth <= 0) {
			return Text.empty();
		}
		if (renderer.getWidth(fullText) <= availableWidth) {
			return fullText;
		}
		int ellipsisWidth = renderer.getWidth(ELLIPSIS);
		if (ellipsisWidth >= availableWidth) {
			return Text.literal(renderer.trimToWidth(ELLIPSIS, availableWidth));
		}
		String prefix = renderer.trimToWidth(fullText.getString(), availableWidth - ellipsisWidth);
		return Text.literal(prefix + ELLIPSIS).setStyle(fullText.getStyle());
	}

	public static boolean isTruncated(TextRenderer renderer, Text fullText, int availableWidth) {
		return availableWidth < 0 || renderer.getWidth(fullText) > availableWidth;
	}

	public static Wrapped wrap(TextRenderer renderer, Text text, int availableWidth, int maxLines) {
		Objects.requireNonNull(renderer, "renderer");
		Objects.requireNonNull(text, "text");
		if (availableWidth <= 0 || maxLines <= 0) {
			return new Wrapped(List.of(), !text.getString().isEmpty());
		}
		List<OrderedText> allLines = renderer.wrapLines(text, availableWidth);
		int visibleCount = Math.min(maxLines, allLines.size());
		return new Wrapped(List.copyOf(allLines.subList(0, visibleCount)), allLines.size() > visibleCount);
	}

	public static int heightForLines(int lineCount, int maxLines, int lineHeight) {
		if (lineCount <= 0 || maxLines <= 0 || lineHeight <= 0) {
			return 0;
		}
		return Math.min(lineCount, maxLines) * lineHeight;
	}

	public record Wrapped(List<OrderedText> lines, boolean truncated) {
		public Wrapped {
			lines = List.copyOf(lines);
		}
	}
}
