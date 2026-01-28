package com.projectkorra.projectkorra.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.util.logging.PkLang;

/**
 * Called when an ability starts
 *
 * @author Philip
 *
 */
public class AbilityStartEvent extends Event implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	boolean cancelled = false;
	Ability ability;

	public AbilityStartEvent(final Ability ability) {
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

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	@Override
	public void setCancelled(final boolean cancelled) {
		this.cancelled = cancelled;
//		if (cancelled) {
//			PkLang.info("AbilityStartEvent cancelled for ability: " + ability.getName() + ":");
//			new Exception().printStackTrace();
//		}
	}

}
