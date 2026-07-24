package com.projectkorra.projectkorra.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.persistence.external.BendingPlayerMutation;
import com.projectkorra.projectkorra.persistence.external.MutationResult;

public final class ExternalBendingPlayerMutationEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();
	private final BendingPlayerMutation mutation;
	private final MutationResult result;
	public ExternalBendingPlayerMutationEvent(final BendingPlayerMutation mutation, final MutationResult result) { this.mutation = mutation; this.result = result; }
	public BendingPlayerMutation getMutation() { return this.mutation; }
	public MutationResult getResult() { return this.result; }
	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
