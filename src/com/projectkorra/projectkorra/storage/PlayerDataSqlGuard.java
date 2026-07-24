package com.projectkorra.projectkorra.storage;

import java.util.regex.Pattern;

import com.projectkorra.projectkorra.persistence.external.ExternalBendingPlayerPersistence;

/**
 * Defense-in-depth guard preventing external mode from reaching ProjectKorra's
 * legacy player-owned tables, even if a caller misses its mode-specific route.
 */
public final class PlayerDataSqlGuard {

	private static final Pattern PLAYER_TABLE = Pattern.compile("(?i)(?<![a-z0-9_])pk_(?:players|presets|cooldowns|board)(?![a-z0-9_])");

	private PlayerDataSqlGuard() {
	}

	public static boolean isPlayerDataReference(final String value) {
		return value != null && PLAYER_TABLE.matcher(value).find();
	}

	public static void checkQuery(final String query) {
		if (ExternalBendingPlayerPersistence.isExternalMode() && isPlayerDataReference(query)) {
			throw new IllegalStateException("ProjectKorra player-table SQL is disabled in EXTERNAL player-data mode");
		}
	}

	public static void checkTable(final String table) {
		if (ExternalBendingPlayerPersistence.isExternalMode() && isPlayerDataReference(table)) {
			throw new IllegalStateException("ProjectKorra player-table metadata access is disabled in EXTERNAL player-data mode");
		}
	}
}
