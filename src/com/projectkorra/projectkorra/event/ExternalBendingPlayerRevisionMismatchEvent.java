package com.projectkorra.projectkorra.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ExternalBendingPlayerRevisionMismatchEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();
	private final UUID playerUuid;
	private final String contextToken;
	private final long expectedRevision;
	private final long currentRevision;
	public ExternalBendingPlayerRevisionMismatchEvent(final UUID playerUuid, final String contextToken, final long expectedRevision, final long currentRevision) {
		this.playerUuid = playerUuid; this.contextToken = contextToken; this.expectedRevision = expectedRevision; this.currentRevision = currentRevision;
	}
	public UUID getPlayerUuid() { return this.playerUuid; }
	public String getContextToken() { return this.contextToken; }
	public long getExpectedRevision() { return this.expectedRevision; }
	public long getCurrentRevision() { return this.currentRevision; }
	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
