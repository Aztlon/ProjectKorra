package com.projectkorra.projectkorra.phasing;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.configuration.ConfigManager;

public final class PhasedIntegrationManager {

	public enum RolloutMode {
		OBSERVE,
		SOFT_BLOCK,
		ENFORCE
	}

	public enum FallbackBehavior {
		ALLOW,
		DENY
	}

	private static volatile boolean enabled;
	private static volatile RolloutMode rolloutMode = RolloutMode.OBSERVE;
	private static volatile FallbackBehavior fallbackBehavior = FallbackBehavior.ALLOW;
	private static volatile boolean telemetryEnabled = true;
	private static volatile boolean logAllowed = false;
	private static volatile double samplingRate = 0.05;
	private static volatile long summaryIntervalMs = 60000L;

	private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();
	private static final AtomicLong LAST_SUMMARY_MS = new AtomicLong(0);

	private static volatile long lastServiceLookup;
	private static volatile PhasedAbilityGate cachedGate;

	private PhasedIntegrationManager() {
	}

	public static void reloadFromConfig() {
		final FileConfiguration config = ConfigManager.getConfig();
		enabled = config.getBoolean("Properties.PhasedIntegration.Enabled", false);
		rolloutMode = parseRolloutMode(config.getString("Properties.PhasedIntegration.RolloutMode", "observe"));
		fallbackBehavior = parseFallbackBehavior(config.getString("Properties.PhasedIntegration.FallbackBehavior", "allow"));
		telemetryEnabled = config.getBoolean("Properties.PhasedIntegration.Telemetry.Enabled", true);
		logAllowed = config.getBoolean("Properties.PhasedIntegration.Telemetry.LogAllowed", false);
		samplingRate = clamp(config.getDouble("Properties.PhasedIntegration.Telemetry.SamplingRate", 0.05), 0, 1);
		summaryIntervalMs = Math.max(5000L, config.getLong("Properties.PhasedIntegration.Telemetry.SummaryIntervalMs", 60000L));
		lastServiceLookup = 0L;
		cachedGate = null;
		ProjectKorra.log.info("Phased integration config loaded: enabled=" + enabled + ", mode=" + rolloutMode + ", fallback=" + fallbackBehavior);
	}

	public static boolean shouldAllow(final GateRequest request) {
		if (!enabled || request == null) {
			return true;
		}

		final GateDecision providerDecision = evaluateProvider(request);
		final GateDecision effectiveDecision = normalizeDecision(providerDecision);
		final boolean denied = shouldDenyByRollout(effectiveDecision, request.getStage());
		recordTelemetry(request, effectiveDecision, denied);
		return !denied;
	}

	public static boolean shouldViewerObserve(final GateRequest request) {
		if (!enabled || request == null) {
			return true;
		}

		final GateDecision providerDecision = evaluateProvider(request);
		final GateDecision effectiveDecision = normalizeDecision(providerDecision);
		return effectiveDecision.isAllowed();
	}

	public static GateRequest requestFromAbility(@Nullable final Ability ability, @Nullable final UUID targetEntityUuid, final GateStage stage,
			@Nullable final Location location, @Nullable final UUID viewerUuid) {
		return new GateRequest(sourceUuid(ability), targetEntityUuid, stage, ability == null ? null : ability.getName(), location, viewerUuid);
	}

	private static GateDecision evaluateProvider(final GateRequest request) {
		final PhasedAbilityGate gate = getGateService();
		if (gate == null) {
			return GateDecision.unknown();
		}

		try {
			final GateDecision decision = gate.evaluate(request);
			return decision == null ? GateDecision.unknown() : decision;
		} catch (final Throwable throwable) {
			ProjectKorra.log.warning("Phased gate provider threw during evaluate(): " + throwable.getMessage());
			return GateDecision.unknown();
		}
	}

	private static GateDecision normalizeDecision(final GateDecision providerDecision) {
		if (providerDecision.isAllowed()) {
			return providerDecision;
		}
		if (GateReasonCode.UNKNOWN.equals(providerDecision.getReasonCode())) {
			return fallbackBehavior == FallbackBehavior.DENY
					? GateDecision.deny(GateReasonCode.UNKNOWN)
					: GateDecision.allow();
		}
		return providerDecision;
	}

	private static boolean shouldDenyByRollout(final GateDecision decision, final GateStage stage) {
		if (decision.isAllowed()) {
			return false;
		}
		switch (rolloutMode) {
			case OBSERVE:
				return false;
			case SOFT_BLOCK:
				return stage == GateStage.DAMAGE || stage == GateStage.COLLISION;
			case ENFORCE:
			default:
				return true;
		}
	}

	private static void recordTelemetry(final GateRequest request, final GateDecision decision, final boolean denied) {
		if (!telemetryEnabled) {
			return;
		}

		final String key = request.getStage().getId() + ":" + decision.getReasonCode();
		COUNTERS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();

		if (shouldLogDecision(decision, denied)) {
			ProjectKorra.log.info("[PhasedGate] abilityId=" + value(request.getAbilityId())
					+ " stage=" + request.getStage().getId()
					+ " sourceEntityUuid=" + value(request.getSourceEntityUuid())
					+ " targetEntityUuid=" + value(request.getTargetEntityUuid())
					+ " viewerUuid=" + value(request.getViewerUuid())
					+ " allowed=" + !denied
					+ " reasonCode=" + decision.getReasonCode()
					+ " rolloutMode=" + rolloutMode);
		}

		final long now = System.currentTimeMillis();
		final long last = LAST_SUMMARY_MS.get();
		if (now - last >= summaryIntervalMs && LAST_SUMMARY_MS.compareAndSet(last, now)) {
			logSummary();
		}
	}

	private static boolean shouldLogDecision(final GateDecision decision, final boolean denied) {
		if (denied) {
			return true;
		}
		if (!logAllowed) {
			return false;
		}
		if (samplingRate >= 1) {
			return true;
		}
		return Math.random() < samplingRate;
	}

	private static void logSummary() {
		if (COUNTERS.isEmpty()) {
			return;
		}
		final StringBuilder builder = new StringBuilder("[PhasedGate] summary ");
		boolean first = true;
		for (final Map.Entry<String, AtomicLong> entry : COUNTERS.entrySet()) {
			if (!first) {
				builder.append(", ");
			}
			builder.append(entry.getKey()).append("=").append(entry.getValue().get());
			first = false;
		}
		ProjectKorra.log.info(builder.toString());
	}

	private static PhasedAbilityGate getGateService() {
		final long now = System.currentTimeMillis();
		if (cachedGate != null && now - lastServiceLookup < 2000L) {
			return cachedGate;
		}
		lastServiceLookup = now;
		cachedGate = Bukkit.getServicesManager().load(PhasedAbilityGate.class);
		return cachedGate;
	}

	private static RolloutMode parseRolloutMode(@Nullable final String mode) {
		if (mode == null) {
			return RolloutMode.OBSERVE;
		}
		switch (mode.trim().toLowerCase()) {
			case "soft-block":
			case "soft_block":
				return RolloutMode.SOFT_BLOCK;
			case "enforce":
				return RolloutMode.ENFORCE;
			default:
				return RolloutMode.OBSERVE;
		}
	}

	private static FallbackBehavior parseFallbackBehavior(@Nullable final String behavior) {
		if (behavior == null) {
			return FallbackBehavior.ALLOW;
		}
		return "deny".equalsIgnoreCase(behavior) ? FallbackBehavior.DENY : FallbackBehavior.ALLOW;
	}

	private static double clamp(final double value, final double min, final double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String value(@Nullable final Object value) {
		return value == null ? "null" : value.toString();
	}

	@Nullable
	private static UUID sourceUuid(@Nullable final Ability ability) {
		if (ability == null) {
			return null;
		}
		final LivingEntity caster = ability.getCaster();
		return caster == null ? null : caster.getUniqueId();
	}
}
