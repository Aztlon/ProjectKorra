package com.projectkorra.projectkorra.storage.internal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.persistence.external.ExternalBendingPlayerPersistence;
import com.projectkorra.projectkorra.storage.DBConnection;
import com.projectkorra.projectkorra.util.Cooldown;

/**
 * The sole runtime SQL boundary for ProjectKorra-owned player data. Calls to
 * this class are valid only in INTERNAL mode; external-mode routing and the
 * database guard provide independent enforcement.
 */
public final class InternalPlayerDataStore {

	private InternalPlayerDataStore() {
	}

	public static ResultSet findPlayer(final UUID uuid) { ensureInternal(); return DBConnection.sql.readQuery("SELECT * FROM pk_players WHERE uuid = '" + uuid + "'"); }
	public static ResultSet findCooldowns(final UUID uuid) { ensureInternal(); return DBConnection.sql.readQuery("SELECT * FROM pk_cooldowns WHERE uuid = '" + uuid + "'"); }

	public static CompletableFuture<Void> createPlayer(final UUID uuid, final String name) {
		ensureInternal();
		return DBConnection.sql.modifyQueryAsync("INSERT INTO pk_players (uuid, player) VALUES ('" + uuid + "', '" + sql(name) + "')", false);
	}

	public static void createLegacyPlayer(final UUID uuid, final String name) {
		ensureInternal();
		DBConnection.sql.modifyQuery("INSERT INTO pk_players (uuid, player, slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9) VALUES ('"
				+ uuid + "', '" + sql(name) + "', 'null', 'null', 'null', 'null', 'null', 'null', 'null', 'null', 'null')");
	}

	public static CompletableFuture<Void> updatePlayerName(final UUID uuid, final String name, final boolean async) {
		ensureInternal();
		return DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET player = '" + sql(name) + "' WHERE uuid = '" + uuid + "'", async);
	}

	public static void updatePlayerName(final UUID uuid, final String name) { updatePlayerName(uuid, name, true); }

	public static void writeAbilitiesLegacy(final UUID uuid, final Map<Integer, String> abilities) {
		ensureInternal();
		for (int slot = 1; slot <= 9; slot++) writeAbilityLegacy(uuid, slot, abilities.get(slot));
	}

	public static CompletableFuture<Void> writeAbilities(final UUID uuid, final Map<Integer, String> abilities) {
		ensureInternal();
		final CompletableFuture<?>[] writes = new CompletableFuture<?>[9];
		for (int slot = 1; slot <= 9; slot++) writes[slot - 1] = writeAbility(uuid, slot, abilities.get(slot));
		return CompletableFuture.allOf(writes);
	}

	public static void writeAbilityLegacy(final UUID uuid, final int slot, final String ability) {
		ensureInternal();
		DBConnection.sql.modifyQuery("UPDATE pk_players SET slot" + slot + " = '" + (ability == null ? null : sql(ability)) + "' WHERE uuid = '" + uuid + "'");
	}

	public static CompletableFuture<Void> writeAbility(final UUID uuid, final int slot, final String ability) {
		ensureInternal();
		return DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET slot" + slot + " = "
				+ (ability == null ? "NULL" : "'" + sql(ability) + "'") + " WHERE uuid = '" + uuid + "'");
	}

	public static void clearAbility(final UUID uuid, final int slot) { writeAbility(uuid, slot, null).join(); }

	public static void saveCooldowns(final UUID uuid, final Map<String, Cooldown> cooldowns) {
		ensureInternal();
		DBConnection.sql.modifyQuery("DELETE FROM pk_cooldowns WHERE uuid = '" + uuid + "'", false);
		for (final Map.Entry<String, Cooldown> entry : cooldowns.entrySet()) {
			if (!entry.getValue().isDatabase()) continue;
			final String key = sql(entry.getKey());
			try (ResultSet rs = DBConnection.sql.readQuery("SELECT value FROM pk_cooldowns WHERE uuid = '" + uuid + "' AND cooldown = '" + key + "'")) {
				if (rs.next()) DBConnection.sql.modifyQuery("UPDATE pk_cooldowns SET value = " + entry.getValue().getCooldown()
						+ " WHERE uuid = '" + uuid + "' AND cooldown = '" + key + "'", false);
				else DBConnection.sql.modifyQuery("INSERT INTO pk_cooldowns (uuid, cooldown, value) VALUES ('" + uuid + "', '" + key + "', "
						+ entry.getValue().getCooldown() + ")", false);
			} catch (final SQLException error) {
				throw new java.util.concurrent.CompletionException(error);
			}
		}
	}

	public static void setPermaRemoved(final UUID uuid, final boolean removed) {
		ensureInternal();
		DBConnection.sql.modifyQuery("UPDATE pk_players SET permaremoved = '" + removed + "' WHERE uuid = '" + uuid + "'");
	}

	public static void saveElements(final UUID uuid, final String value) {
		ensureInternal();
		DBConnection.sql.modifyQuery("UPDATE pk_players SET element = '" + sql(value) + "' WHERE uuid = '" + uuid + "'");
	}
	public static CompletableFuture<Void> saveElementsAsync(final UUID uuid, final String value) {
		ensureInternal();
		return DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET element = '" + sql(value) + "' WHERE uuid = '" + uuid + "'");
	}
	public static void saveSubelements(final UUID uuid, final String value) {
		ensureInternal();
		DBConnection.sql.modifyQuery("UPDATE pk_players SET subelement = '" + sql(value) + "' WHERE uuid = '" + uuid + "'");
	}
	public static CompletableFuture<Void> saveSubelementsAsync(final UUID uuid, final String value) {
		ensureInternal();
		return DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET subelement = '" + sql(value) + "' WHERE uuid = '" + uuid + "'");
	}

	public static List<PresetRow> loadPresets(final UUID uuid) throws SQLException {
		ensureInternal();
		final List<PresetRow> rows = new ArrayList<>();
		try (PreparedStatement statement = DBConnection.sql.getConnection().prepareStatement("SELECT * FROM pk_presets WHERE uuid = ?")) {
			statement.setString(1, uuid.toString());
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					final Map<Integer, String> abilities = new HashMap<>();
					for (int slot = 1; slot <= 9; slot++) {
						final String ability = result.getString("slot" + slot);
						if (ability != null) abilities.put(slot, ability);
					}
					rows.add(new PresetRow(result.getString("name"), abilities));
				}
			}
		}
		return rows;
	}

	public static void deletePreset(final UUID uuid, final String name) throws SQLException {
		ensureInternal();
		try (PreparedStatement statement = DBConnection.sql.getConnection().prepareStatement("DELETE FROM pk_presets WHERE uuid = ? AND name = ?")) {
			statement.setString(1, uuid.toString()); statement.setString(2, name); statement.execute();
		}
	}

	public static void insertPreset(final UUID uuid, final String name, final Map<Integer, String> abilities) throws SQLException {
		ensureInternal();
		try (PreparedStatement statement = DBConnection.sql.getConnection().prepareStatement(
				"INSERT INTO pk_presets (uuid, name, slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			statement.setString(1, uuid.toString()); statement.setString(2, name);
			for (int slot = 1; slot <= 9; slot++) statement.setString(slot + 2, abilities.get(slot));
			statement.execute();
		}
	}

	public static Set<UUID> loadDisabledBoards() throws SQLException {
		ensureInternal();
		final Set<UUID> disabled = new HashSet<>();
		try (ResultSet result = DBConnection.sql.readQuery("SELECT uuid FROM pk_board WHERE enabled = 0")) {
			while (result.next()) disabled.add(UUID.fromString(result.getString("uuid")));
		}
		return disabled;
	}

	public static void saveBoardPreference(final UUID uuid, final boolean enabled) {
		ensureInternal();
		Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, () -> {
			try (PreparedStatement query = DBConnection.sql.getConnection().prepareStatement("SELECT enabled FROM pk_board WHERE uuid = ? LIMIT 1")) {
				query.setString(1, uuid.toString());
				final boolean exists;
				try (ResultSet result = query.executeQuery()) { exists = result.next(); }
				final String sql = exists ? "UPDATE pk_board SET enabled = ? WHERE uuid = ?" : "INSERT INTO pk_board (enabled, uuid) VALUES (?, ?)";
				try (PreparedStatement update = DBConnection.sql.getConnection().prepareStatement(sql)) {
					update.setBoolean(1, enabled); update.setString(2, uuid.toString()); update.execute();
				}
			} catch (final SQLException error) { error.printStackTrace(); }
		});
	}

	private static String sql(final String value) { return value == null ? "" : value.replace("'", "''"); }
	private static void ensureInternal() {
		if (ExternalBendingPlayerPersistence.isExternalMode()) throw new IllegalStateException("Internal player-data storage is disabled in EXTERNAL mode");
	}

	public record PresetRow(String name, Map<Integer, String> abilities) {
		public PresetRow { abilities = Map.copyOf(abilities); }
	}
}
