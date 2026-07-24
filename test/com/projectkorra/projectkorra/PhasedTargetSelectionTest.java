package com.projectkorra.projectkorra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.phasing.GateDecision;
import com.projectkorra.projectkorra.phasing.PhasedAbilityGate;
import com.projectkorra.projectkorra.phasing.PhasedIntegrationManager;
import com.projectkorra.projectkorra.phasing.PhasedIntegrationManager.RolloutMode;

class PhasedTargetSelectionTest {

	private final AtomicReference<Collection<Entity>> nearbyEntities = new AtomicReference<>(List.of());
	private World world;
	private LivingEntity caster;
	private Ability ability;
	private Entity deniedEntity;
	private Entity allowedEntity;

	@BeforeEach
	void setUp() throws ReflectiveOperationException {
		this.world = proxy(World.class, (method, args) -> {
			if ("getNearbyEntities".equals(method.getName())) {
				return this.nearbyEntities.get();
			}
			return defaultValue(method.getReturnType());
		});

		this.caster = livingEntity(UUID.randomUUID(), 1, new Location(this.world, 0, 0, 0));
		this.deniedEntity = entity(UUID.randomUUID(), 2, new Location(this.world, 0.25, 0, 0));
		this.allowedEntity = entity(UUID.randomUUID(), 3, new Location(this.world, 1, 0, 0));
		this.nearbyEntities.set(List.of(this.deniedEntity, this.allowedEntity));
		this.ability = proxy(Ability.class, (method, args) -> switch (method.getName()) {
			case "getCaster" -> this.caster;
			case "getName" -> "TestAbility";
			default -> defaultValue(method.getReturnType());
		});

		final UUID deniedUuid = this.deniedEntity.getUniqueId();
		final PhasedAbilityGate gate = request -> deniedUuid.equals(request.getTargetEntityUuid())
				? GateDecision.deny("OUT_OF_PHASE")
				: GateDecision.allow();

		setStaticField("enabled", true);
		setStaticField("rolloutMode", RolloutMode.SOFT_BLOCK);
		setStaticField("telemetryEnabled", false);
		setStaticField("cachedGate", gate);
		setStaticField("lastServiceLookup", System.currentTimeMillis());
	}

	@AfterEach
	void tearDown() throws ReflectiveOperationException {
		setStaticField("enabled", false);
		setStaticField("rolloutMode", RolloutMode.OBSERVE);
		setStaticField("telemetryEnabled", true);
		setStaticField("cachedGate", null);
		setStaticField("lastServiceLookup", 0L);
	}

	@Test
	void abilityAwareAreaQueryExcludesDeniedTargets() {
		final List<Entity> targets = GeneralMethods.getEntitiesAroundPoint(
				this.ability, new Location(this.world, 0, 0, 0), 2);

		assertEquals(List.of(this.allowedEntity), targets);
	}

	@Test
	void closestEntitySkipsNearerDeniedTarget() {
		final Entity closest = GeneralMethods.getClosestEntity(
				this.ability, new Location(this.world, 0, 0, 0), 2);

		assertSame(this.allowedEntity, closest);
	}

	@Test
	void preInstantiationQueryUsesSourceAndAbilityId() {
		final List<Entity> targets = GeneralMethods.getEntitiesAroundPoint(
				this.caster, "TestAbility", new Location(this.world, 0, 0, 0), 2);

		assertEquals(List.of(this.allowedEntity), targets);
	}

	@Test
	@SuppressWarnings("deprecation")
	void deprecatedSourcelessQueryRetainsLegacyBehavior() {
		final List<Entity> targets = GeneralMethods.getEntitiesAroundPoint(
				new Location(this.world, 0, 0, 0), 2);

		assertEquals(List.of(this.deniedEntity, this.allowedEntity), targets);
	}

	@Test
	@SuppressWarnings("deprecation")
	void deprecatedSourcelessQueryInheritsManagedAbilityContext() {
		final AtomicReference<List<Entity>> targets = new AtomicReference<>();

		PhasedIntegrationManager.runWithAbilityContext(this.ability, () -> targets.set(
				GeneralMethods.getEntitiesAroundPoint(new Location(this.world, 0, 0, 0), 2)));

		assertEquals(List.of(this.allowedEntity), targets.get());
	}

	private LivingEntity livingEntity(final UUID uuid, final int entityId, final Location location) {
		return proxy(LivingEntity.class, (method, args) -> entityValue(method.getName(), method.getReturnType(), uuid, entityId, location));
	}

	private Entity entity(final UUID uuid, final int entityId, final Location location) {
		return proxy(Entity.class, (method, args) -> entityValue(method.getName(), method.getReturnType(), uuid, entityId, location));
	}

	private Object entityValue(final String methodName, final Class<?> returnType, final UUID uuid, final int entityId,
			final Location location) {
		return switch (methodName) {
			case "getUniqueId" -> uuid;
			case "getEntityId" -> entityId;
			case "getLocation" -> location.clone();
			case "getType" -> EntityType.ZOMBIE;
			case "isDead", "hasMetadata" -> false;
			default -> defaultValue(returnType);
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final Invocation invocation) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
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

	private static void setStaticField(final String fieldName, final Object value) throws ReflectiveOperationException {
		final Field field = PhasedIntegrationManager.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

	@FunctionalInterface
	private interface Invocation {
		Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
	}
}
