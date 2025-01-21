package com.projectkorra.projectkorra.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.ability.Ability;

public class AbilityPhaseEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Ability ability;
	private final String oldPhase;
	private final String newPhase;

	public AbilityPhaseEvent(final Ability ability, final String oldPhase, final String newPhase) {
		this.ability = ability;
		this.oldPhase = oldPhase;
		this.newPhase = newPhase;
	}

	public Ability getAbility() {
		return this.ability;
	}

	public String getOldPhase() {
		return this.oldPhase;
	}

	public String getNewPhase() {
		return this.newPhase;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
