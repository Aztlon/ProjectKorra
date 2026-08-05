package com.projectkorra.projectkorra.event;

import java.util.List;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import com.projectkorra.projectkorra.persistence.external.ExternalDataIssue;

public final class ExternalBendingPlayerDataIssueEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();
	private final UUID playerUuid;
	private final List<ExternalDataIssue> issues;
	public ExternalBendingPlayerDataIssueEvent(final UUID playerUuid, final List<ExternalDataIssue> issues) { this.playerUuid = playerUuid; this.issues = List.copyOf(issues); }
	public UUID getPlayerUuid() { return this.playerUuid; }
	public List<ExternalDataIssue> getIssues() { return this.issues; }
	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
