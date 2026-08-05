package com.projectkorra.projectkorra.event;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.ability.Ability;

/**
 * Non-cancellable evidence that an ability-owned effect has already been
 * committed.
 * <p>
 * This event is emitted only when core or addon code explicitly calls an
 * {@link AbilityExecutionEvidence} publication helper. ProjectKorra does not
 * infer evidence from ability lifecycle events or automatically publish it for
 * existing abilities.
 * <p>
 * The meaning and unit of {@link #getMagnitude()} are defined by
 * {@link #getEvidenceKey()}. At most one of {@link #getTargetEntity()} and
 * {@link #getTargetBlock()} is present. The location is snapshotted when the
 * event is created and defensively copied when read.
 */
public final class AbilityExecutionEvidenceEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Ability ability;
	private final NamespacedKey evidenceKey;
	private final Location location;
	private final @Nullable Entity targetEntity;
	private final @Nullable Block targetBlock;
	private final double magnitude;

	AbilityExecutionEvidenceEvent(final Ability ability, final NamespacedKey evidenceKey, final Location location,
			final @Nullable Entity targetEntity, final @Nullable Block targetBlock, final double magnitude) {
		this.ability = Objects.requireNonNull(ability, "ability");
		this.evidenceKey = Objects.requireNonNull(evidenceKey, "evidenceKey");

		final Location checkedLocation = Objects.requireNonNull(location, "location");
		if (checkedLocation.getWorld() == null) {
			throw new IllegalArgumentException("location must have a world");
		}
		if (targetEntity != null && targetBlock != null) {
			throw new IllegalArgumentException("entity and block targets are mutually exclusive");
		}
		if (!Double.isFinite(magnitude) || magnitude < 0D) {
			throw new IllegalArgumentException("magnitude must be finite and non-negative");
		}

		this.location = checkedLocation.clone();
		this.targetEntity = targetEntity;
		this.targetBlock = targetBlock;
		this.magnitude = magnitude;
	}

	/**
	 * Returns the ability that committed the evidenced effect.
	 *
	 * @return the source ability
	 */
	public Ability getAbility() {
		return this.ability;
	}

	/**
	 * Returns the plugin-owned identifier defining this evidence's meaning and
	 * magnitude unit.
	 *
	 * @return the evidence key
	 */
	public NamespacedKey getEvidenceKey() {
		return this.evidenceKey;
	}

	/**
	 * Returns a defensive copy of the location captured at publication time.
	 *
	 * @return the evidence location
	 */
	public Location getLocation() {
		return this.location.clone();
	}

	/**
	 * Returns the target entity when entity evidence was published.
	 *
	 * @return the target entity, or {@code null}
	 */
	public @Nullable Entity getTargetEntity() {
		return this.targetEntity;
	}

	/**
	 * Returns the target block when block evidence was published.
	 *
	 * @return the target block, or {@code null}
	 */
	public @Nullable Block getTargetBlock() {
		return this.targetBlock;
	}

	/**
	 * Returns the finite, non-negative magnitude whose unit is defined by the
	 * evidence key.
	 *
	 * @return the evidence magnitude
	 */
	public double getMagnitude() {
		return this.magnitude;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
