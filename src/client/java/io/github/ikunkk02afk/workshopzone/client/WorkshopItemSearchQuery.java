package io.github.ikunkk02afk.workshopzone.client;

import java.util.Locale;

public record WorkshopItemSearchQuery(
	String raw,
	String namespaceFilter,
	String text
) {
	public WorkshopItemSearchQuery {
		raw = raw == null ? "" : raw;
		namespaceFilter = normalize(namespaceFilter);
		text = normalize(text);
	}

	public static WorkshopItemSearchQuery parse(String raw) {
		String value = raw == null ? "" : raw.strip();
		String namespace = "";
		String text = value;
		if (value.startsWith("@")) {
			int separator = firstWhitespace(value);
			String token = separator < 0 ? value.substring(1) : value.substring(1, separator);
			namespace = token.strip();
			text = separator < 0 ? "" : value.substring(separator).strip();
		}
		return new WorkshopItemSearchQuery(value, namespace, text);
	}

	public boolean empty() {
		return namespaceFilter.isEmpty() && text.isEmpty();
	}

	private static int firstWhitespace(String value) {
		for (int index = 0; index < value.length(); index++) {
			if (Character.isWhitespace(value.charAt(index))) {
				return index;
			}
		}
		return -1;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
	}
}
