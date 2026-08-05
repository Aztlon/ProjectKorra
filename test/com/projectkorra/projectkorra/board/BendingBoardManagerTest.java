package com.projectkorra.projectkorra.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.persistence.external.BoardPreference;

class BendingBoardManagerTest {
	@AfterEach
	void resetManagerState() throws Exception {
		disabledPlayers().clear();
		scoreboardPlayers().clear();
		setField("enabled", false);
		setField("boardResolver", new ConfiguredAbilityBoardResolver());
		setField("boardFactory", defaultBoardFactory());
		setField("bendingPlayerResolver", (Function<Player, BendingPlayer>) BendingPlayer::getBendingPlayer);
	}

	@Test
	void externalPreferenceOffThenOnRecreatesAndShowsBoard() throws Exception {
		final UUID uuid = UUID.randomUUID();
		final Player player = player(uuid);
		final BendingPlayer bendingPlayer = new BendingPlayer(player);
		final TestBoard original = new TestBoard();
		final TestBoard replacement = new TestBoard();

		setField("enabled", true);
		BendingBoardManager.setBoardResolver(new AbilityBoardResolver() {
			@Override public boolean shouldUseRpgBoard(final Player ignored) { return false; }
			@Override public java.util.List<String> resolveObjectiveLines(final Player ignored, final int maxLines) {
				return java.util.List.of();
			}
		});
		setField("boardFactory", (BiFunction<BendingPlayer, BoardType, AbilityBoard>) (ignored, type) -> replacement);
		setField("bendingPlayerResolver", (Function<Player, BendingPlayer>) ignored -> bendingPlayer);
		scoreboardPlayers().put(player, new BoardHolder(BoardType.PK, original));

		BendingBoardManager.applyExternalPreference(player, BoardPreference.DISABLED);
		assertTrue(original.destroyed);
		assertTrue(BendingBoardManager.isDisabled(player));
		assertFalse(scoreboardPlayers().containsKey(player));

		BendingBoardManager.applyExternalPreference(player, BoardPreference.ENABLED);
		assertFalse(BendingBoardManager.isDisabled(player));
		assertNotSame(original, scoreboardPlayers().get(player).board());
		assertTrue(replacement.updated);
		assertTrue(replacement.visible);
	}

	@SuppressWarnings("unchecked")
	private static Map<Player, BoardHolder> scoreboardPlayers() throws Exception {
		return (Map<Player, BoardHolder>) field(BendingBoardManager.class, "scoreboardPlayers").get(null);
	}

	@SuppressWarnings("unchecked")
	private static java.util.Set<UUID> disabledPlayers() throws Exception {
		return (java.util.Set<UUID>) field(BendingBoardManager.class, "disabledPlayers").get(null);
	}

	@SuppressWarnings("unchecked")
	private static BiFunction<BendingPlayer, BoardType, AbilityBoard> defaultBoardFactory() throws Exception {
		final Method method = BendingBoardManager.class.getDeclaredMethod("newBoard", BendingPlayer.class, BoardType.class);
		method.setAccessible(true);
		return (player, type) -> {
			try { return (AbilityBoard) method.invoke(null, player, type); }
			catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
		};
	}

	private static void setField(final String name, final Object value) throws Exception {
		field(BendingBoardManager.class, name).set(null, value);
	}

	private static Field field(final Class<?> owner, final String name) throws Exception {
		final Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Player player(final UUID uuid) {
		return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getUniqueId" -> uuid;
					case "getName" -> "BoardTest";
					case "isOnline", "hasPermission" -> true;
					default -> defaultValue(method.getReturnType());
				});
	}

	private static Object defaultValue(final Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == char.class) return '\0';
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		return 0D;
	}

	private static final class TestBoard implements AbilityBoard {
		private boolean visible;
		private boolean destroyed;
		private boolean updated;

		@Override public BoardType getType() { return BoardType.PK; }
		@Override public void show() { this.visible = true; }
		@Override public void hide() { this.visible = false; }
		@Override public boolean isVisible() { return this.visible; }
		@Override public void setVisible(final boolean show) { this.visible = show; }
		@Override public void destroy() { this.destroyed = true; this.visible = false; }
		@Override public void updateAll() { this.updated = true; }
		@Override public void clearSlot(final int slot) {}
		@Override public void setSlot(final int slot, final String ability, final boolean cooldown) {}
		@Override public void setActiveSlot(final int newSlot) {}
		@Override public void setAbilityCooldown(final String name, final boolean cooldown) {}
	}
}
