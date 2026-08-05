package com.projectkorra.projectkorra.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.projectkorra.projectkorra.ability.Ability;

class AbilityExecutionEvidenceTest {
	private static final List<Event> PUBLISHED = new ArrayList<>();
	private static final NamespacedKey EVIDENCE_KEY = new NamespacedKey("test-addon", "committed_effect");
	private static Field serverField;
	private static Server previousServer;

	private World world;
	private Ability ability;

	@BeforeAll
	static void installCapturingServer() throws ReflectiveOperationException {
		serverField = Bukkit.class.getDeclaredField("server");
		serverField.setAccessible(true);
		previousServer = (Server) serverField.get(null);
		serverField.set(null, null);

		final PluginManager pluginManager = proxy(PluginManager.class, (method, args) -> {
			if (method.getName().equals("callEvent")) {
				PUBLISHED.add((Event) args[0]);
			}
			return defaultValue(method.getReturnType());
		});
		final Server server = proxy(Server.class, (method, args) -> switch (method.getName()) {
			case "getPluginManager" -> pluginManager;
			case "getLogger" -> Logger.getLogger(AbilityExecutionEvidenceTest.class.getName());
			default -> defaultValue(method.getReturnType());
		});
		Bukkit.setServer(server);
	}

	@AfterAll
	static void restoreServer() throws IllegalAccessException {
		serverField.set(null, previousServer);
	}

	@BeforeEach
	void setUp() {
		PUBLISHED.clear();
		this.world = proxy(World.class, (method, args) -> defaultValue(method.getReturnType()));
		this.ability = proxy(Ability.class, (method, args) -> switch (method.getName()) {
			case "getName" -> "EvidenceTestAbility";
			default -> defaultValue(method.getReturnType());
		});
	}

	@Test
	void exposesStableStandardEvidenceKeys() {
		assertEquals("projectkorra:entity_damage", AbilityExecutionEvidence.ENTITY_DAMAGE.toString());
		assertEquals("projectkorra:velocity_applied", AbilityExecutionEvidence.VELOCITY_APPLIED.toString());
		assertEquals("projectkorra:air_shield_deflected", AbilityExecutionEvidence.AIR_SHIELD_DEFLECTED.toString());
		assertEquals("projectkorra:block_ignited", AbilityExecutionEvidence.BLOCK_IGNITED.toString());
		assertEquals("projectkorra:air_pocket_created", AbilityExecutionEvidence.AIR_POCKET_CREATED.toString());
	}

	@Test
	void remainsDormantUntilLocationEvidenceIsExplicitlyPublished() {
		final Location location = new Location(this.world, 1D, 2D, 3D);

		assertEquals(0, PUBLISHED.size());
		AbilityExecutionEvidence.publishLocation(this.ability, EVIDENCE_KEY, location, 0D);

		final AbilityExecutionEvidenceEvent event = publishedEvent();
		assertSame(this.ability, event.getAbility());
		assertSame(EVIDENCE_KEY, event.getEvidenceKey());
		assertEquals(0D, event.getMagnitude());
		assertNull(event.getTargetEntity());
		assertNull(event.getTargetBlock());
		assertFalse(Cancellable.class.isAssignableFrom(event.getClass()));
		assertSame(AbilityExecutionEvidenceEvent.getHandlerList(), event.getHandlers());

		assertNotSame(location, event.getLocation());
		location.setX(100D);
		assertEquals(1D, event.getLocation().getX());
		final Location returned = event.getLocation();
		returned.setY(100D);
		assertEquals(2D, event.getLocation().getY());
	}

	@Test
	void entityEvidenceCarriesOnlyTheEntityAndItsLocationSnapshot() {
		final Location targetLocation = new Location(this.world, 4D, 5D, 6D);
		final Entity entity = entity(targetLocation);

		AbilityExecutionEvidence.publishEntity(this.ability, EVIDENCE_KEY, entity, 2.5D);

		final AbilityExecutionEvidenceEvent event = publishedEvent();
		assertSame(entity, event.getTargetEntity());
		assertNull(event.getTargetBlock());
		assertEquals(targetLocation, event.getLocation());
		assertEquals(2.5D, event.getMagnitude());
	}

	@Test
	void blockEvidenceCarriesOnlyTheBlockAndItsLocationSnapshot() {
		final Location targetLocation = new Location(this.world, 7D, 8D, 9D);
		final Block block = proxy(Block.class, (method, args) -> switch (method.getName()) {
			case "getLocation" -> targetLocation.clone();
			default -> defaultValue(method.getReturnType());
		});

		AbilityExecutionEvidence.publishBlock(this.ability, EVIDENCE_KEY, block, 1D);

		final AbilityExecutionEvidenceEvent event = publishedEvent();
		assertNull(event.getTargetEntity());
		assertSame(block, event.getTargetBlock());
		assertEquals(targetLocation, event.getLocation());
		assertEquals(1D, event.getMagnitude());
	}

	@Test
	void rejectsNullArgumentsAndWorldlessLocationsBeforeDispatch() {
		final Location location = new Location(this.world, 1D, 2D, 3D);
		final Entity entity = entity(location);
		final Block block = proxy(Block.class, (method, args) -> "getLocation".equals(method.getName())
				? location.clone() : defaultValue(method.getReturnType()));

		assertThrows(NullPointerException.class,
				() -> AbilityExecutionEvidence.publishLocation(null, EVIDENCE_KEY, location, 1D));
		assertThrows(NullPointerException.class,
				() -> AbilityExecutionEvidence.publishLocation(this.ability, null, location, 1D));
		assertThrows(NullPointerException.class,
				() -> AbilityExecutionEvidence.publishLocation(this.ability, EVIDENCE_KEY, null, 1D));
		assertThrows(NullPointerException.class,
				() -> AbilityExecutionEvidence.publishEntity(this.ability, EVIDENCE_KEY, null, 1D));
		assertThrows(NullPointerException.class,
				() -> AbilityExecutionEvidence.publishBlock(this.ability, EVIDENCE_KEY, null, 1D));
		assertThrows(IllegalArgumentException.class, () -> AbilityExecutionEvidence.publishLocation(
				this.ability, EVIDENCE_KEY, new Location(null, 1D, 2D, 3D), 1D));

		assertEquals(0, PUBLISHED.size());
		assertSame(this.world, entity.getLocation().getWorld());
		assertSame(this.world, block.getLocation().getWorld());
	}

	@Test
	void acceptsFiniteNonNegativeMagnitudeAndRejectsAllOtherValues() {
		final Location location = new Location(this.world, 1D, 2D, 3D);
		AbilityExecutionEvidence.publishLocation(this.ability, EVIDENCE_KEY, location, Double.MAX_VALUE);
		assertEquals(Double.MAX_VALUE, publishedEvent().getMagnitude());

		for (final double invalid : new double[] {-0.01D, Double.NaN, Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class,
					() -> AbilityExecutionEvidence.publishLocation(this.ability, EVIDENCE_KEY, location, invalid));
		}
		assertEquals(1, PUBLISHED.size());
	}

	@Test
	void eventInvariantRejectsSimultaneousEntityAndBlockTargets() {
		final Location location = new Location(this.world, 1D, 2D, 3D);
		final Entity entity = entity(location);
		final Block block = proxy(Block.class, (method, args) -> defaultValue(method.getReturnType()));

		assertThrows(IllegalArgumentException.class, () -> new AbilityExecutionEvidenceEvent(
				this.ability, EVIDENCE_KEY, location, entity, block, 1D));
	}

	private AbilityExecutionEvidenceEvent publishedEvent() {
		assertEquals(1, PUBLISHED.size());
		return (AbilityExecutionEvidenceEvent) PUBLISHED.get(0);
	}

	private Entity entity(final Location location) {
		final UUID uuid = UUID.randomUUID();
		return proxy(Entity.class, (method, args) -> switch (method.getName()) {
			case "getLocation" -> location.clone();
			case "getUniqueId" -> uuid;
			default -> defaultValue(method.getReturnType());
		});
	}

	@FunctionalInterface
	private interface Invocation {
		Object invoke(Method method, Object[] args) throws Throwable;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final Invocation invocation) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				return switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> type.getSimpleName() + "Proxy";
					default -> null;
				};
			}
			return invocation.invoke(method, args);
		});
	}

	private static Object defaultValue(final Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == char.class) {
			return '\0';
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		return 0D;
	}
}
