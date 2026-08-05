package com.projectkorra.projectkorra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OfflineBendingPlayerLifecycleTest {
	private static final AtomicReference<Player> CURRENT = new AtomicReference<>();

	@BeforeAll
	static void installServer() {
		if (Bukkit.getServer() != null) return;
		final Server server = proxy(Server.class, (method, args) -> {
			if (method.getName().equals("getLogger")) return Logger.getLogger("OfflineBendingPlayerLifecycleTest");
			if (method.getName().equals("getPlayer") && args != null && args.length == 1 && args[0] instanceof UUID uuid) {
				final Player player = CURRENT.get();
				return player != null && player.getUniqueId().equals(uuid) ? player : null;
			}
			return defaultValue(method.getReturnType());
		});
		Bukkit.setServer(server);
	}

	@AfterEach
	void clearLifecycleMaps() throws Exception {
		CURRENT.set(null);
		for (final String name : new String[] {"PLAYERS", "ONLINE_PLAYERS", "LOADS", "INITIALIZATION_STATES", "GENERATIONS", "LOAD_PLAYERS"}) {
			map(name).clear();
		}
	}

	@Test
	void quitInvalidatesAndCancelsOnlyThePendingSession() throws Exception {
		final UUID uuid = UUID.randomUUID();
		final Player player = player(uuid, true);
		final CompletableFuture<OfflineBendingPlayer> pending = new CompletableFuture<>();
		map("LOADS").put(uuid, pending);
		map("LOAD_PLAYERS").put(uuid, player);

		assertTrue(OfflineBendingPlayer.isCurrentGeneration(uuid, 0));
		OfflineBendingPlayer.detachExternalRuntime(player);

		assertFalse(OfflineBendingPlayer.isCurrentGeneration(uuid, 0));
		assertTrue(pending.isCancelled());
		assertFalse(map("LOADS").containsKey(uuid));
	}

	@Test
	void obsoleteCompletionCannotRemoveTheNewerLoad() throws Exception {
		final UUID uuid = UUID.randomUUID();
		final Player oldPlayer = player(uuid, false);
		final Player newPlayer = player(uuid, true);
		final CompletableFuture<OfflineBendingPlayer> oldLoad = new CompletableFuture<>();
		final CompletableFuture<OfflineBendingPlayer> newLoad = new CompletableFuture<>();
		map("LOADS").put(uuid, newLoad);
		map("LOAD_PLAYERS").put(uuid, newPlayer);

		cleanup(uuid, oldLoad, oldPlayer);

		assertSame(newLoad, map("LOADS").get(uuid));
		assertSame(newPlayer, map("LOAD_PLAYERS").get(uuid));
	}

	@Test
	void rapidQuitRejoinCyclesSupersedeEveryOlderGeneration() {
		final UUID uuid = UUID.randomUUID();
		long previous = 0;
		for (int cycle = 0; cycle < 20; cycle++) {
			final Player player = player(uuid, false);
			assertTrue(OfflineBendingPlayer.isCurrentGeneration(uuid, previous));
			OfflineBendingPlayer.detachExternalRuntime(player);
			assertFalse(OfflineBendingPlayer.isCurrentGeneration(uuid, previous));
			previous++;
		}
		assertTrue(OfflineBendingPlayer.isCurrentGeneration(uuid, 20));
	}

	@Test
	void readyNeverSurvivesWithMissingOrStaleRuntime() throws Exception {
		final UUID uuid = UUID.randomUUID();
		final Player oldPlayer = player(uuid, false);
		final Player currentPlayer = player(uuid, true);
		CURRENT.set(currentPlayer);
		map("INITIALIZATION_STATES").put(uuid, BendingPlayerInitializationState.READY);
		map("ONLINE_PLAYERS").put(uuid, new BendingPlayer(oldPlayer));

		assertNull(OfflineBendingPlayer.getInitializationState(uuid));
		assertFalse(map("INITIALIZATION_STATES").containsKey(uuid));

		final BendingPlayer currentRuntime = new BendingPlayer(currentPlayer);
		map("ONLINE_PLAYERS").put(uuid, currentRuntime);
		map("INITIALIZATION_STATES").put(uuid, BendingPlayerInitializationState.READY);
		assertEquals(BendingPlayerInitializationState.READY, BendingPlayer.getInitializationState(currentPlayer));
		assertSame(currentRuntime, BendingPlayer.getBendingPlayer(currentPlayer));
		assertSame(currentPlayer, BendingPlayer.getBendingPlayer(currentPlayer).getPlayer());
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> map(final String name) throws Exception {
		final Field field = OfflineBendingPlayer.class.getDeclaredField(name);
		field.setAccessible(true);
		return (Map<Object, Object>) field.get(null);
	}

	private static void cleanup(final UUID uuid, final CompletableFuture<OfflineBendingPlayer> future,
			final Player player) throws Exception {
		final Method method = OfflineBendingPlayer.class.getDeclaredMethod("cleanupLoad", UUID.class,
				CompletableFuture.class, Player.class);
		method.setAccessible(true);
		method.invoke(null, uuid, future, player);
	}

	private static Player player(final UUID uuid, final boolean online) {
		return proxy(Player.class, (method, args) -> switch (method.getName()) {
			case "getUniqueId" -> uuid;
			case "isOnline" -> online;
			case "getName" -> "LifecycleTest";
			default -> defaultValue(method.getReturnType());
		});
	}

	private interface Invocation { Object invoke(Method method, Object[] args); }

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final Invocation invocation) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
				(proxy, method, args) -> invocation.invoke(method, args));
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
}
