package com.projectkorra.projectkorra.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.persistence.external.ApplyReason;
import com.projectkorra.projectkorra.persistence.external.ApplyResult;

public final class ExternalBendingPlayerSnapshotEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();
	private final Player player;
	private final ApplyReason reason;
	private final ApplyResult result;
	public ExternalBendingPlayerSnapshotEvent(final Player player, final ApplyReason reason, final ApplyResult result) {
		this.player = player; this.reason = reason; this.result = result;
	}
	public Player getPlayer() { return this.player; }
	public ApplyReason getReason() { return this.reason; }
	public ApplyResult getResult() { return this.result; }
	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
