package com.projectkorra.projectkorra.persistence.external;

public enum PlayerDataMode {
	INTERNAL,
	EXTERNAL;

	public static PlayerDataMode parse(final String value) {
		if (value == null) return INTERNAL;
		try {
			return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (final IllegalArgumentException ignored) {
			return INTERNAL;
		}
	}
}
