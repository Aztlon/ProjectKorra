package com.projectkorra.projectkorra.event;

import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.BendingPlayerInitializationPhase;

/** Fired on the primary thread when bending-player initialization fails. */
public final class BendingPlayerInitializationFailureEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();
	private final UUID playerUuid;
	private final String playerName;
	private final BendingPlayerInitializationPhase phase;
	private final Throwable cause;

	public BendingPlayerInitializationFailureEvent(final UUID playerUuid, final String playerName,
			final BendingPlayerInitializationPhase phase, final Throwable cause) {
		this.playerUuid = playerUuid;
		this.playerName = playerName;
		this.phase = phase;
		this.cause = cause;
	}

	public UUID getPlayerUuid() { return this.playerUuid; }
	public String getPlayerName() { return this.playerName; }
	public BendingPlayerInitializationPhase getPhase() { return this.phase; }
	public Throwable getCause() { return this.cause; }

	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
