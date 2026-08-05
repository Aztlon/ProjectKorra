package com.projectkorra.projectkorra.phasing;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.ability.Ability;

public final class PhasedBlockSource {

	private static final PhasedBlockSource NONE = new PhasedBlockSource(null, null);

	@Nullable
	private final UUID sourceEntityUuid;
	@Nullable
	private final String abilityId;

	private PhasedBlockSource(@Nullable final UUID sourceEntityUuid, @Nullable final String abilityId) {
		this.sourceEntityUuid = sourceEntityUuid;
		this.abilityId = abilityId;
	}

	public static PhasedBlockSource none() {
		return NONE;
	}

	public static PhasedBlockSource current() {
		return fromAbility(PhasedIntegrationManager.getCurrentAbilityContext());
	}

	public static PhasedBlockSource fromAbility(@Nullable final Ability ability) {
		if (ability == null) {
			return NONE;
		}

		final LivingEntity caster = ability.getCaster();
		return new PhasedBlockSource(caster == null ? null : caster.getUniqueId(), ability.getName());
	}

	public static PhasedBlockSource fromEntity(@Nullable final LivingEntity source, @Nullable final String abilityId) {
		if (source == null && abilityId == null) {
			return NONE;
		}
		return new PhasedBlockSource(source == null ? null : source.getUniqueId(), abilityId);
	}

	public boolean isEmpty() {
		return this.sourceEntityUuid == null && this.abilityId == null;
	}

	@Nullable
	public UUID getSourceEntityUuid() {
		return this.sourceEntityUuid;
	}

	@Nullable
	public String getAbilityId() {
		return this.abilityId;
	}

	public GateRequest request(final GateStage stage, @Nullable final Location location, @Nullable final UUID viewerUuid) {
		return new GateRequest(this.sourceEntityUuid, null, stage, this.abilityId, location, viewerUuid);
	}
}
