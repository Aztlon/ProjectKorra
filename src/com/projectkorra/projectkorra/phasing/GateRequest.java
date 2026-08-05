package com.projectkorra.projectkorra.phasing;

import java.util.UUID;

import org.bukkit.Location;
import org.checkerframework.checker.nullness.qual.Nullable;

public class GateRequest {

	@Nullable
	private final UUID sourceEntityUuid;
	@Nullable
	private final UUID targetEntityUuid;
	private final GateStage stage;
	@Nullable
	private final String abilityId;
	@Nullable
	private final String targetAbilityId;
	@Nullable
	private final Location location;
	@Nullable
	private final UUID viewerUuid;

	public GateRequest(@Nullable final UUID sourceEntityUuid, @Nullable final UUID targetEntityUuid, final GateStage stage,
			@Nullable final String abilityId, @Nullable final Location location, @Nullable final UUID viewerUuid) {
		this(sourceEntityUuid, targetEntityUuid, stage, abilityId, location, viewerUuid, null);
	}

	public GateRequest(@Nullable final UUID sourceEntityUuid, @Nullable final UUID targetEntityUuid, final GateStage stage,
			@Nullable final String abilityId, @Nullable final Location location, @Nullable final UUID viewerUuid,
			@Nullable final String targetAbilityId) {
		this.sourceEntityUuid = sourceEntityUuid;
		this.targetEntityUuid = targetEntityUuid;
		this.stage = stage;
		this.abilityId = abilityId;
		this.targetAbilityId = targetAbilityId;
		this.location = location == null ? null : location.clone();
		this.viewerUuid = viewerUuid;
	}

	@Nullable
	public UUID getSourceEntityUuid() {
		return this.sourceEntityUuid;
	}

	@Nullable
	public UUID getTargetEntityUuid() {
		return this.targetEntityUuid;
	}

	public GateStage getStage() {
		return this.stage;
	}

	@Nullable
	public String getAbilityId() {
		return this.abilityId;
	}

	@Nullable
	public String getTargetAbilityId() {
		return this.targetAbilityId;
	}

	@Nullable
	public Location getLocation() {
		return this.location == null ? null : this.location.clone();
	}

	@Nullable
	public UUID getViewerUuid() {
		return this.viewerUuid;
	}
}
