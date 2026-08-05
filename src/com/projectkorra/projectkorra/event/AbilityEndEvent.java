package com.projectkorra.projectkorra.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.projectkorra.projectkorra.ability.Ability;

/**
 * Called when an ability enters its removal lifecycle.
 * <p>
 * Removal may follow cancellation, expiry, failure, or an ability that never
 * produced an effect. This lifecycle signal is therefore not success evidence;
 * explicit post-commit outcomes are represented by
 * {@link AbilityExecutionEvidenceEvent}.
 */
public class AbilityEndEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	Ability ability;

	public AbilityEndEvent(final Ability ability) {
		this.ability = ability;
	}

	public Ability getAbility() {
		return this.ability;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
