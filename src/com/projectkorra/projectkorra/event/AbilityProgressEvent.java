package com.projectkorra.projectkorra.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.projectkorra.projectkorra.ability.Ability;

/**
 * Called after an ability receives a lifecycle progress update.
 * <p>
 * This signal does not imply that the update produced a successful effect.
 * Explicit post-commit outcomes are represented by
 * {@link AbilityExecutionEvidenceEvent}.
 *
 * @author Philip
 *
 */
public class AbilityProgressEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	Ability ability;

	public AbilityProgressEvent(final Ability ability) {
		this.ability = ability;
	}

	public Ability getAbility() {
		return this.ability;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
