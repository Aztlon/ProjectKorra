package com.projectkorra.projectkorra.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.BendingPlayerInitializationPhase;
import com.projectkorra.projectkorra.persistence.external.PlayerIdentity;

public final class ExternalBendingPlayerLoadEvent extends Event {
	public enum Result { STARTED, COMPLETED, FAILED }
	private static final HandlerList HANDLERS = new HandlerList();
	private final PlayerIdentity player;
	private final Result result;
	private final BendingPlayerInitializationPhase phase;
	private final long revision;
	private final Throwable error;

	public ExternalBendingPlayerLoadEvent(final PlayerIdentity player, final Result result,
			final BendingPlayerInitializationPhase phase, final long revision, final Throwable error) {
		this.player = player; this.result = result; this.phase = phase; this.revision = revision; this.error = error;
	}
	public PlayerIdentity getPlayerIdentity() { return this.player; }
	public Result getResult() { return this.result; }
	public BendingPlayerInitializationPhase getPhase() { return this.phase; }
	public long getRevision() { return this.revision; }
	public Throwable getError() { return this.error; }
	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
