package com.projectkorra.projectkorra.event;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import com.projectkorra.projectkorra.ability.Ability;

/**
 * Explicit publication helpers for post-commit ability execution evidence.
 * <p>
 * Call these helpers only after the represented effect has successfully been
 * applied. Merely starting, progressing, or ending an ability is not execution
 * evidence. Calls synchronously dispatch an
 * {@link AbilityExecutionEvidenceEvent} through Bukkit's plugin manager.
 */
public final class AbilityExecutionEvidence {
	/** Evidence that an ability committed positive health or absorption damage. Magnitude is actual damage applied. */
	public static final NamespacedKey ENTITY_DAMAGE = key("projectkorra:entity_damage");
	/** Evidence that an ability committed a positive entity velocity. Magnitude is the applied vector length. */
	public static final NamespacedKey VELOCITY_APPLIED = key("projectkorra:velocity_applied");
	/** Evidence that AirShield repelled a living entity or projectile. */
	public static final NamespacedKey AIR_SHIELD_DEFLECTED = key("projectkorra:air_shield_deflected");
	/** Evidence that an ability successfully created temporary fire on a block. */
	public static final NamespacedKey BLOCK_IGNITED = key("projectkorra:block_ignited");
	/** Evidence that WaterBubble converted at least one water block into an air pocket. */
	public static final NamespacedKey AIR_POCKET_CREATED = key("projectkorra:air_pocket_created");

	private AbilityExecutionEvidence() {
	}

	/**
	 * Publishes evidence targeting an entity. The entity's current location is
	 * snapshotted for the event.
	 *
	 * @param ability the ability that committed the effect
	 * @param evidenceKey the plugin-owned evidence identifier
	 * @param target the affected entity
	 * @param magnitude the finite, non-negative magnitude
	 */
	public static void publishEntity(final Ability ability, final NamespacedKey evidenceKey, final Entity target,
			final double magnitude) {
		final Entity checkedTarget = Objects.requireNonNull(target, "target");
		publish(new AbilityExecutionEvidenceEvent(ability, evidenceKey, checkedTarget.getLocation(), checkedTarget, null,
				magnitude));
	}

	/**
	 * Publishes evidence targeting a block. The block's current location is
	 * snapshotted for the event.
	 *
	 * @param ability the ability that committed the effect
	 * @param evidenceKey the plugin-owned evidence identifier
	 * @param target the affected block
	 * @param magnitude the finite, non-negative magnitude
	 */
	public static void publishBlock(final Ability ability, final NamespacedKey evidenceKey, final Block target,
			final double magnitude) {
		final Block checkedTarget = Objects.requireNonNull(target, "target");
		publish(new AbilityExecutionEvidenceEvent(ability, evidenceKey, checkedTarget.getLocation(), null, checkedTarget,
				magnitude));
	}

	/**
	 * Publishes untargeted evidence at a location.
	 *
	 * @param ability the ability that committed the effect
	 * @param evidenceKey the plugin-owned evidence identifier
	 * @param location the location of the committed effect
	 * @param magnitude the finite, non-negative magnitude
	 */
	public static void publishLocation(final Ability ability, final NamespacedKey evidenceKey, final Location location,
			final double magnitude) {
		publish(new AbilityExecutionEvidenceEvent(ability, evidenceKey, location, null, null, magnitude));
	}

	private static void publish(final AbilityExecutionEvidenceEvent event) {
		Bukkit.getPluginManager().callEvent(event);
	}

	private static NamespacedKey key(final String value) {
		return Objects.requireNonNull(NamespacedKey.fromString(value), "Invalid standard evidence key " + value);
	}
}
