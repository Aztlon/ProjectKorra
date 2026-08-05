package com.projectkorra.projectkorra;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.board.BendingBoardManager;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.ExternalBendingPlayerDataIssueEvent;
import com.projectkorra.projectkorra.event.ExternalBendingPlayerLoadEvent;
import com.projectkorra.projectkorra.event.ExternalBendingPlayerMutationEvent;
import com.projectkorra.projectkorra.event.ExternalBendingPlayerRevisionMismatchEvent;
import com.projectkorra.projectkorra.event.ExternalBendingPlayerSnapshotEvent;
import com.projectkorra.projectkorra.firebending.passive.FirePassive;
import com.projectkorra.projectkorra.object.Preset;
import com.projectkorra.projectkorra.persistence.external.ApplyReason;
import com.projectkorra.projectkorra.persistence.external.ApplyResult;
import com.projectkorra.projectkorra.persistence.external.BendingPlayerMutation;
import com.projectkorra.projectkorra.persistence.external.BendingPlayerMutationOperation;
import com.projectkorra.projectkorra.persistence.external.BoardPreference;
import com.projectkorra.projectkorra.persistence.external.CooldownPersistence;
import com.projectkorra.projectkorra.persistence.external.ExternalBendingPlayerData;
import com.projectkorra.projectkorra.persistence.external.ExternalBendingPlayerPersistence;
import com.projectkorra.projectkorra.persistence.external.ExternalBendingPlayerProvider;
import com.projectkorra.projectkorra.persistence.external.ExternalDataIssue;
import com.projectkorra.projectkorra.persistence.external.ExternalPersistenceDiagnostics;
import com.projectkorra.projectkorra.persistence.external.FlushReason;
import com.projectkorra.projectkorra.persistence.external.MutationDecision;
import com.projectkorra.projectkorra.persistence.external.MutationResult;
import com.projectkorra.projectkorra.persistence.external.MutationSource;
import com.projectkorra.projectkorra.persistence.external.MutationIntent;
import com.projectkorra.projectkorra.command.PKCommand;
import com.projectkorra.projectkorra.persistence.external.PlayerIdentity;
import com.projectkorra.projectkorra.persistence.external.UnknownIdentifierPolicy;
import com.projectkorra.projectkorra.storage.internal.InternalPlayerDataStore;
import com.projectkorra.projectkorra.util.ChatUtil;
import com.projectkorra.projectkorra.util.Cooldown;
import com.projectkorra.projectkorra.util.logging.PkLang;

/** Internal serialization, validation and projection engine behind the public external API. */
public final class ExternalPersistenceCoordinator {
	private static final int MAX_ELEMENTS = 32;
	private static final int MAX_SUBELEMENTS = 128;
	private static final int MAX_PRESETS = 100;
	private static final int MAX_PERSISTENT_COOLDOWNS = 512;
	private static final int MAX_IDENTIFIER_LENGTH = 128;
	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	private static final Map<String, Long> COMPATIBILITY_WARNINGS = new ConcurrentHashMap<>();
	private static final Map<String, Long> FAILURE_WARNINGS = new ConcurrentHashMap<>();

	private ExternalPersistenceCoordinator() {}

	public static boolean isExternalMode() { return ExternalBendingPlayerPersistence.isExternalMode(); }

	public static boolean isProjectionGuardActive(final UUID uuid) {
		final Session session = SESSIONS.get(uuid);
		return session != null && session.guardDepth.get() > 0;
	}

	public static void warnLegacySave(final Bender target, final String method) {
		if (!isExternalMode()) return;
		final String caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
				.map(StackWalker.StackFrame::getClassName)
				.filter(name -> !name.startsWith("com.projectkorra.projectkorra.Bender")
						&& !name.startsWith("com.projectkorra.projectkorra.OfflineBendingPlayer")
						&& !name.startsWith("com.projectkorra.projectkorra.GeneralMethods"))
				.findFirst().orElse("unknown"));
		final String key = caller + '#' + method;
		final long now = System.currentTimeMillis();
		final Long previous = COMPATIBILITY_WARNINGS.putIfAbsent(key, now);
		if (previous != null && now - previous < 60_000L) return;
		COMPATIBILITY_WARNINGS.put(key, now);
		PkLang.warning("Legacy " + method + " call from " + caller + " for " + target.getUUID()
				+ " is a provider flush in external player-data mode; use requestMutationAsync for an observable result");
	}

	static CompletableFuture<OfflineBendingPlayer> loadExternal(final UUID uuid, final boolean onStartup,
			final long generation, final Player requestedPlayer) {
		ExternalBendingPlayerPersistence.closeRegistration();
		final Session session = SESSIONS.computeIfAbsent(uuid, Session::new);
		final long epoch = session.epoch.incrementAndGet();
		synchronized (session) { session.tail = CompletableFuture.completedFuture(null); }
		session.blocked = true;
		session.phase = BendingPlayerInitializationPhase.EXTERNAL_DATA_LOAD.name();
		final ExternalBendingPlayerProvider provider = ExternalBendingPlayerPersistence.getProvider().orElse(null);
		if (provider == null) {
			return failedLoad(uuid, generation, BendingPlayerInitializationPhase.EXTERNAL_PROVIDER_UNAVAILABLE,
					new IllegalStateException("Storage.PlayerDataMode is EXTERNAL but no provider is registered"));
		}

		final PlayerIdentity identity = identity(uuid);
		fireEvent(new ExternalBendingPlayerLoadEvent(identity, ExternalBendingPlayerLoadEvent.Result.STARTED,
				BendingPlayerInitializationPhase.EXTERNAL_DATA_LOAD, -1, null));
		session.pending.incrementAndGet();
		final CompletableFuture<ExternalBendingPlayerData> load = timed(callProvider(() -> provider.load(identity)), loadTimeout(), "external player load");
		return load.thenCompose(data -> onMain(() -> {
			if (session.epoch.get() != epoch) throw new SupersededException("External load was superseded");
			final Player online = Bukkit.getPlayer(uuid);
			if (requestedPlayer != null && !OfflineBendingPlayer.canPublishRuntime(uuid, generation, requestedPlayer))
				throw new SupersededException("Player connection changed while external data was loading");
			if (online != null && online.isOnline()) {
				final BendingPlayer target = new BendingPlayer(online);
				final List<ExternalDataIssue> issues = applyData(target, data, ApplyReason.INITIAL_LOAD, session);
				target.loading = false;
				session.blocked = false;
				OfflineBendingPlayer.publishExternal(target, generation, requestedPlayer == null ? online : requestedPlayer);
				// Passive registration resolves the runtime through getBendingPlayer(), so the
				// fully projected player must be lookup-visible before post-load derived state
				// is refreshed. The snapshot (including all toggles) has already been applied.
				target.postLoad();
				OfflineBendingPlayer.completeExternalPublication(target);
				session.lastSuccessfulLoad = Instant.now();
				final ApplyResult applied = ApplyResult.applied(data.revision(), issues);
				Bukkit.getPluginManager().callEvent(new ExternalBendingPlayerSnapshotEvent(online, ApplyReason.INITIAL_LOAD, applied));
				Bukkit.getPluginManager().callEvent(new ExternalBendingPlayerLoadEvent(identity, ExternalBendingPlayerLoadEvent.Result.COMPLETED,
						BendingPlayerInitializationPhase.EXTERNAL_DATA_APPLY, data.revision(), null));
				return (OfflineBendingPlayer) target;
			}

			final OfflineBendingPlayer target = new OfflineBendingPlayer(Bukkit.getOfflinePlayer(uuid));
			applyData(target, data, ApplyReason.INITIAL_LOAD, session);
			target.loading = false;
			OfflineBendingPlayer.markExternalReady(uuid, generation);
			session.blocked = false;
			session.lastSuccessfulLoad = Instant.now();
			Bukkit.getPluginManager().callEvent(new ExternalBendingPlayerLoadEvent(identity, ExternalBendingPlayerLoadEvent.Result.COMPLETED,
					BendingPlayerInitializationPhase.EXTERNAL_DATA_APPLY, data.revision(), null));
			return target;
		})).whenComplete((value, error) -> {
			session.pending.decrementAndGet();
			if (error != null && !(unwrap(error) instanceof SupersededException)) {
				session.lastFailure = describe(unwrap(error));
			}
		}).exceptionallyCompose(error -> {
			final Throwable cause = unwrap(error);
			if (cause instanceof SupersededException || cause instanceof java.util.concurrent.CancellationException
					|| !OfflineBendingPlayer.isCurrentGeneration(uuid, generation))
				return CompletableFuture.failedFuture(cause);
			return failedLoad(uuid, generation, classifyLoadFailure(error), cause);
		});
	}

	private static BendingPlayerInitializationPhase classifyLoadFailure(final Throwable error) {
		final Throwable cause = unwrap(error);
		if (cause instanceof ExternalValidationException) return BendingPlayerInitializationPhase.EXTERNAL_DATA_VALIDATION;
		if (cause instanceof ExternalApplyException) return BendingPlayerInitializationPhase.EXTERNAL_DATA_APPLY;
		return BendingPlayerInitializationPhase.EXTERNAL_DATA_LOAD;
	}

	private static CompletableFuture<OfflineBendingPlayer> failedLoad(final UUID uuid, final long generation,
			final BendingPlayerInitializationPhase phase, final Throwable error) {
		if (!OfflineBendingPlayer.isCurrentGeneration(uuid, generation)) return CompletableFuture.failedFuture(error);
		final Session session = SESSIONS.computeIfAbsent(uuid, Session::new);
		session.phase = phase.name();
		session.blocked = true;
		session.lastFailure = describe(error);
		final String warningKey = uuid + ":load:" + phase;
		final long now = System.currentTimeMillis();
		final Long previous = FAILURE_WARNINGS.putIfAbsent(warningKey, now);
		if (previous == null || now - previous >= 30_000L) {
			FAILURE_WARNINGS.put(warningKey, now);
			PkLang.warning("External player load failed uuid=" + uuid + " phase=" + phase + " failure=" + describe(error));
		}
		fireEvent(new ExternalBendingPlayerLoadEvent(identity(uuid), ExternalBendingPlayerLoadEvent.Result.FAILED, phase, -1, error));
		return OfflineBendingPlayer.failExternalInitialization(uuid, phase, error);
	}

	public static CompletionStage<ApplyResult> applySnapshot(final Player player, final ExternalBendingPlayerData data,
			final ApplyReason reason) {
		if (player == null || data == null || reason == null) return CompletableFuture.failedFuture(new NullPointerException("player, data and reason are required"));
		final Session session = SESSIONS.computeIfAbsent(player.getUniqueId(), Session::new);
		final long epoch;
		if (reason.supersedesPending()) {
			epoch = session.epoch.incrementAndGet();
			session.blocked = true;
			synchronized (session) { session.tail = CompletableFuture.completedFuture(null); }
			return applySnapshotNow(player, data, reason, session, epoch);
		}
		epoch = session.epoch.get();
		final CompletableFuture<ApplyResult> result = new CompletableFuture<>();
		session.pending.incrementAndGet();
		synchronized (session) {
			session.tail = session.tail.handle((ignored, error) -> null)
					.thenCompose(ignored -> applySnapshotNow(player, data, reason, session, epoch))
					.handle((applied, error) -> {
						if (error == null) result.complete(applied); else result.completeExceptionally(unwrap(error));
						return null;
					});
		}
		result.whenComplete((ignored, error) -> session.pending.decrementAndGet());
		return result;
	}

	private static CompletionStage<ApplyResult> applySnapshotNow(final Player player, final ExternalBendingPlayerData data, final ApplyReason reason, final Session session, final long epoch) {
		return onMain(() -> {
			if (session.epoch.get() != epoch) return new ApplyResult(ApplyResult.Status.SUPERSEDED, -1, List.of(), "Snapshot was superseded", null);
			final BendingPlayer target = BendingPlayer.getBendingPlayer(player);
			if (target == null) return new ApplyResult(ApplyResult.Status.REJECTED, -1, List.of(), "BendingPlayer is not ready", null);
			try {
				final List<ExternalDataIssue> issues = applyDataAndRefresh(target, data, reason, session);
				if (reason.supersedesPending()) session.blocked = false;
				session.lastSuccessfulLoad = Instant.now();
				final ApplyResult result = ApplyResult.applied(data.revision(), issues);
				Bukkit.getPluginManager().callEvent(new ExternalBendingPlayerSnapshotEvent(player, reason, result));
				return result;
			} catch (final Throwable error) {
				session.lastFailure = describe(error);
				final ApplyResult result = new ApplyResult(ApplyResult.Status.FAILED, -1, List.of(), "External snapshot apply failed", error);
				Bukkit.getPluginManager().callEvent(new ExternalBendingPlayerSnapshotEvent(player, reason, result));
				return result;
			}
		});
	}

	public static CompletionStage<MutationResult> mutate(final Bender target, final BendingPlayerMutationOperation operation, final MutationSource source) {
		return mutate(target, operation, source, MutationIntent.STANDARD);
	}

	/** Internal capability-gated entry point for already-authorized ProjectKorra commands. */
	public static CompletionStage<MutationResult> mutateFromAuthorizedCommand(final Bender target,
			final BendingPlayerMutationOperation operation, final MutationSource source,
			final PKCommand.AdministrativeMutationCapability capability) {
		if (!PKCommand.isAdministrativeMutationCapability(capability)) {
			throw new SecurityException("Administrative mutation capability was not issued by ProjectKorra's command layer");
		}
		return mutate(target, operation, source, MutationIntent.ADMINISTRATIVE);
	}

	private static CompletionStage<MutationResult> mutate(final Bender target, final BendingPlayerMutationOperation operation,
			final MutationSource source, final MutationIntent intent) {
		if (isProjectionGuardActive(target.getUUID())) {
			try {
				applyOperationDirect(target, operation);
				return CompletableFuture.completedFuture(MutationResult.accepted(-1));
			} catch (final Throwable error) {
				return CompletableFuture.completedFuture(MutationResult.failed("apply_failed", error.getMessage(), error));
			}
		}
		if (!isExternalMode()) {
			try {
				applyOperationDirect(target, operation);
				persistInternalOperation(target, operation);
				return CompletableFuture.completedFuture(MutationResult.accepted(-1));
			} catch (final Throwable error) {
				return CompletableFuture.completedFuture(MutationResult.failed("apply_failed", error.getMessage(), error));
			}
		}

		final Session session = SESSIONS.computeIfAbsent(target.getUUID(), Session::new);
		if (session.blocked || session.contextToken == null) {
			return CompletableFuture.completedFuture(new MutationResult(MutationResult.Status.CONFLICT, "not_ready", "External state is not ready", session.revision, null));
		}
		final ExternalBendingPlayerProvider provider = ExternalBendingPlayerPersistence.getProvider().orElse(null);
		if (provider == null) return CompletableFuture.completedFuture(MutationResult.failed("provider_unavailable", "External provider is unavailable", null));

		final CompletableFuture<MutationResult> result = new CompletableFuture<>();
		final long epoch = session.epoch.get();
		session.pending.incrementAndGet();
		synchronized (session) {
			session.tail = session.tail.handle((ignored, previousError) -> null).thenCompose(ignored -> {
				if (session.epoch.get() != epoch) {
					result.complete(new MutationResult(MutationResult.Status.SUPERSEDED, "superseded", "Mutation belongs to an inactive profile", session.revision, null));
					return CompletableFuture.completedFuture(null);
				}
				final BendingPlayerMutation mutation = new BendingPlayerMutation(identity(target.getUUID()), session.contextToken,
						session.revision, source, intent, operation);
				return timed(provider.requestMutation(mutation), mutationTimeout(), "external mutation")
						.thenCompose(decision -> handleDecision(target, mutation, decision, session, epoch))
						.handle((mutationResult, error) -> {
							if (error != null) {
								final Throwable cause = unwrap(error);
								session.lastFailure = describe(cause);
								result.complete(emitMutation(mutation, MutationResult.failed(
										cause instanceof TimeoutException ? "timeout" : "provider_failure", cause.getMessage(), cause)));
							} else {
								result.complete(mutationResult);
							}
							return null;
						});
			});
		}
		result.whenComplete((value, error) -> session.pending.decrementAndGet());
		return result;
	}

	private static CompletionStage<MutationResult> handleDecision(final Bender target, final BendingPlayerMutation requested,
			final MutationDecision decision, final Session session, final long epoch) {
		switch (decision) {
			case null -> {
				return CompletableFuture.completedFuture(emitMutation(requested,
						MutationResult.failed("invalid_decision", "Provider returned null", null)));
			}
			case MutationDecision.Accepted accepted -> {
				final String invalidAcceptance = validateAcceptance(requested, accepted, session);
				if (invalidAcceptance != null) {
					session.blocked = true;
					session.lastFailure = invalidAcceptance;
					reloadAfterConflict(target, session);
					return CompletableFuture.completedFuture(emitMutation(requested,
							MutationResult.failed("invalid_acceptance", invalidAcceptance, null)));
				}
				return onMain(() -> {
					if (session.epoch.get() != epoch) {
						final MutationResult result = new MutationResult(MutationResult.Status.SUPERSEDED, "superseded", "Mutation belongs to an inactive profile", session.revision, null);
						Bukkit.getPluginManager().callEvent(new ExternalBendingPlayerMutationEvent(requested, result));
						return result;
					}
					if (accepted.canonicalSnapshot() != null)
						applyDataAndRefresh(target, accepted.canonicalSnapshot(), ApplyReason.ACCEPTED_MUTATION, session);
					else applyAcceptedOperation(target, accepted, session);
					session.lastSuccessfulMutation = Instant.now();
					final MutationResult result = MutationResult.accepted(accepted.revision());
					Bukkit.getPluginManager().callEvent(new ExternalBendingPlayerMutationEvent(requested, result));
					return result;
				});
			}
			case MutationDecision.Rejected rejected -> {
				return reconcileIfPresent(target, rejected.authoritativeSnapshot(), session)
						.thenApply(ignored -> emitMutation(requested, new MutationResult(MutationResult.Status.REJECTED, rejected.code(), rejected.message(), session.revision, null)));
			}
			case MutationDecision.Conflict conflict -> {
				fireEvent(new ExternalBendingPlayerRevisionMismatchEvent(target.getUUID(), requested.contextToken(), requested.expectedRevision(), conflict.currentRevision()));
				if (conflict.authoritativeSnapshot() == null) {
					session.blocked = true;
					reloadAfterConflict(target, session);
					return CompletableFuture.completedFuture(emitMutation(requested, new MutationResult(MutationResult.Status.CONFLICT, conflict.code(), conflict.message(), conflict.currentRevision(), null)));
				}
				return reconcileIfPresent(target, conflict.authoritativeSnapshot(), session)
						.thenApply(ignored -> emitMutation(requested, new MutationResult(MutationResult.Status.CONFLICT, conflict.code(), conflict.message(), conflict.currentRevision(), null)));
			}
			default -> {
			}
		}
		final MutationDecision.Failed failed = (MutationDecision.Failed) decision;
		return CompletableFuture.completedFuture(emitMutation(requested, MutationResult.failed(failed.code(), failed.message(), failed.cause())));
	}

	private static String validateAcceptance(final BendingPlayerMutation requested,
			final MutationDecision.Accepted accepted, final Session session) {
		if (!requested.contextToken().equals(session.contextToken)) return "Player context changed while mutation was pending";
		if (!requested.contextToken().equals(accepted.contextToken())) return "Accepted mutation changed context without a canonical snapshot replacement";
		if (accepted.revision() <= requested.expectedRevision() || accepted.revision() < session.revision) return "Accepted mutation returned a non-advancing revision";
		if (accepted.canonicalSnapshot() != null) {
			if (!accepted.contextToken().equals(accepted.canonicalSnapshot().contextToken())
					|| accepted.revision() != accepted.canonicalSnapshot().revision()) return "Canonical snapshot metadata does not match acceptance metadata";
			return null;
		}
		final BendingPlayerMutation canonical = accepted.canonicalMutation();
		if (!requested.player().uuid().equals(canonical.player().uuid())) return "Canonical mutation targets a different player";
		if (!accepted.contextToken().equals(canonical.contextToken())) return "Canonical mutation context does not match acceptance metadata";
		return null;
	}

	private static CompletionStage<Void> reconcileIfPresent(final Bender target, final ExternalBendingPlayerData data, final Session session) {
		if (data == null) return CompletableFuture.completedFuture(null);
		return onMain(() -> {
			applyDataAndRefresh(target, data, ApplyReason.CONFLICT_RECONCILIATION, session);
			return null;
		});
	}

	private static void reloadAfterConflict(final Bender target, final Session session) {
		final ExternalBendingPlayerProvider provider = ExternalBendingPlayerPersistence.getProvider().orElse(null);
		if (provider == null) return;
		timed(callProvider(() -> provider.load(identity(target.getUUID()))), loadTimeout(), "external conflict reload")
				.thenCompose(data -> onMain(() -> {
					applyDataAndRefresh(target, data, ApplyReason.CONFLICT_RECONCILIATION, session);
					session.blocked = false;
					return null;
				})).exceptionally(error -> { session.lastFailure = describe(unwrap(error)); return null; });
	}

	public static CompletionStage<Void> flush(final Bender target, final FlushReason reason) {
		if (!isExternalMode()) return CompletableFuture.completedFuture(null);
		return flush(target.getUUID(), reason);
	}

	public static CompletionStage<Void> flush(final UUID uuid, final FlushReason reason) {
		final ExternalBendingPlayerProvider provider = ExternalBendingPlayerPersistence.getProvider().orElse(null);
		if (provider == null) return CompletableFuture.failedFuture(new IllegalStateException("External provider is unavailable"));
		final Session session = SESSIONS.computeIfAbsent(uuid, Session::new);
		final CompletableFuture<Void> result = new CompletableFuture<>();
		synchronized (session) {
			session.tail = session.tail.handle((ignored, error) -> null)
					.thenCompose(ignored -> timed(provider.flush(identity(uuid), reason), flushTimeout(), "external flush"))
					.whenComplete((ignored, error) -> {
						if (error == null) result.complete(null); else result.completeExceptionally(unwrap(error));
					});
		}
		return result;
	}

	public static CooldownPersistence classifyCooldown(final Bender target, final String key, final boolean persistenceRequested) {
		if (!isExternalMode()) return persistenceRequested ? CooldownPersistence.PROFILE_PERSISTENT : CooldownPersistence.RUNTIME;
		final ExternalBendingPlayerProvider provider = ExternalBendingPlayerPersistence.getProvider().orElse(null);
		if (provider == null) return CooldownPersistence.RUNTIME;
		try {
			final CooldownPersistence result = provider.classifyCooldown(identity(target.getUUID()), key, persistenceRequested);
			return result == null ? CooldownPersistence.RUNTIME : result;
		} catch (final Throwable error) {
			final Session session = SESSIONS.computeIfAbsent(target.getUUID(), Session::new);
			session.lastFailure = describe(error);
			return CooldownPersistence.RUNTIME;
		}
	}

	public static void detach(final UUID uuid) {
		final Session session = SESSIONS.get(uuid);
		if (session != null) session.epoch.incrementAndGet();
	}

	public static void providerUnavailable(final String reason) {
		for (final Session session : SESSIONS.values()) {
			session.epoch.incrementAndGet();
			session.blocked = true;
			session.lastFailure = reason;
			OfflineBendingPlayer.markExternalFailed(session.uuid);
		}
	}

	public static ExternalPersistenceDiagnostics diagnostics(final UUID uuid) {
		final Session session = SESSIONS.get(uuid);
		final ExternalBendingPlayerProvider provider = ExternalBendingPlayerPersistence.getProvider().orElse(null);
		final String providerName = provider == null ? "<none>" : provider.providerName();
		final String providerVersion = provider == null ? "" : provider.providerVersion();
		if (session == null) return new ExternalPersistenceDiagnostics(uuid, ExternalBendingPlayerPersistence.getMode(), providerName, providerVersion,
				ExternalBendingPlayerPersistence.CONTRACT_VERSION, String.valueOf(BendingPlayer.getInitializationState(uuid)), "", "", -1,
				null, null, 0, "", false);
		return new ExternalPersistenceDiagnostics(uuid, ExternalBendingPlayerPersistence.getMode(), providerName, providerVersion,
				ExternalBendingPlayerPersistence.CONTRACT_VERSION, String.valueOf(BendingPlayer.getInitializationState(uuid)), session.phase,
				session.contextToken == null ? "" : session.contextToken, session.revision, session.lastSuccessfulLoad,
				session.lastSuccessfulMutation, session.pending.get(), session.lastFailure, session.guardDepth.get() > 0);
	}

	private static List<ExternalDataIssue> applyData(final Bender target, final ExternalBendingPlayerData data,
			final ApplyReason reason, final Session session) {
		final ResolvedData resolved;
		try {
			resolved = resolve(data);
		} catch (final ExternalValidationException error) {
			reportIssues(target.getUUID(), error.issues);
			throw error;
		}
		final RuntimeBackup backup = new RuntimeBackup(target);
		try {
			withGuard(session, () -> {
				target.elements.clear();
				target.elements.addAll(resolved.elements);
				target.subelements.clear();
				target.subelements.addAll(resolved.subelements);
				target.abilities = new HashMap<>(resolved.abilities);
				if (reason.clearsRuntimeCooldowns()) target.cooldowns.clear();
				else target.cooldowns.entrySet().removeIf(entry -> entry.getValue().isDatabase());
				target.cooldowns.putAll(resolved.cooldowns);
				target.toggled = data.bendingEnabled();
				target.toggledElements.clear();
				target.toggledElements.addAll(resolved.disabledElements);
				target.permaRemoved = false;
				Preset.replaceExternalPresets(target.getUUID(), resolved.presets);
				if (target instanceof BendingPlayer player) {
					BendingBoardManager.applyExternalPreference(player.getPlayer(), data.boardPreference());
				}
			});
			session.contextToken = data.contextToken();
			session.revision = data.revision();
			session.phase = BendingPlayerInitializationPhase.EXTERNAL_DATA_APPLY.name();
			if (!resolved.issues.isEmpty()) reportIssues(target.getUUID(), resolved.issues);
			return resolved.issues;
		} catch (final Throwable error) {
			withGuard(session, () -> backup.restore(target));
			throw new ExternalApplyException("Could not apply external bending-player snapshot", error);
		}
	}

	private static List<ExternalDataIssue> applyDataAndRefresh(final Bender target, final ExternalBendingPlayerData data,
			final ApplyReason reason, final Session session) {
		final RuntimeBackup backup = new RuntimeBackup(target);
		final String previousContext = session.contextToken;
		final long previousRevision = session.revision;
		try {
			final List<ExternalDataIssue> issues = applyData(target, data, reason, session);
			if (target instanceof BendingPlayer player) refreshDerivedState(player, true);
			return issues;
		} catch (final Throwable error) {
			withGuard(session, () -> backup.restore(target));
			session.contextToken = previousContext;
			session.revision = previousRevision;
			throw error;
		}
	}

	private static void applyAcceptedOperation(final Bender target, final MutationDecision.Accepted accepted,
			final Session session) {
		final RuntimeBackup backup = new RuntimeBackup(target);
		final String previousContext = session.contextToken;
		final long previousRevision = session.revision;
		try {
			withGuard(session, () -> applyOperationDirect(target, accepted.canonicalMutation().operation()));
			session.contextToken = accepted.contextToken();
			session.revision = accepted.revision();
			if (target instanceof BendingPlayer player) refreshDerivedState(player, replacesBindingsOrElements(accepted.canonicalMutation().operation()));
		} catch (final Throwable error) {
			withGuard(session, () -> backup.restore(target));
			session.contextToken = previousContext;
			session.revision = previousRevision;
			session.blocked = true;
			reloadAfterConflict(target, session);
			throw error;
		}
	}

	private static ResolvedData resolve(final ExternalBendingPlayerData data) {
		final List<ExternalDataIssue> issues = new ArrayList<>();
		if (data.elements().size() > MAX_ELEMENTS) issue(issues, ExternalDataIssue.Category.LIMIT_EXCEEDED, "elements", "", "Too many elements");
		if (data.subelements().size() > MAX_SUBELEMENTS) issue(issues, ExternalDataIssue.Category.LIMIT_EXCEEDED, "subelements", "", "Too many subelements");
		if (data.abilities().size() > 9) issue(issues, ExternalDataIssue.Category.LIMIT_EXCEEDED, "abilities", "", "Too many ability slots");
		if (data.presets().size() > MAX_PRESETS) issue(issues, ExternalDataIssue.Category.LIMIT_EXCEEDED, "presets", "", "Too many presets");
		if (data.persistentCooldownExpirations().size() > MAX_PERSISTENT_COOLDOWNS) issue(issues, ExternalDataIssue.Category.LIMIT_EXCEEDED, "persistentCooldownExpirations", "", "Too many persistent cooldowns");
		if (issues.stream().anyMatch(issue -> issue.category() == ExternalDataIssue.Category.LIMIT_EXCEEDED)) {
			throw new ExternalValidationException("External snapshot exceeds a safety limit", issues);
		}

		final List<Element> elements = new ArrayList<>();
		final Set<Element> seenElements = new HashSet<>();
		for (final String id : data.elements()) {
			final Element element = resolveElement(id);
			if (element == null || element instanceof SubElement) issue(issues, ExternalDataIssue.Category.UNKNOWN_IDENTIFIER, "elements", id, "Unknown main element");
			else if (!seenElements.add(element)) issue(issues, ExternalDataIssue.Category.INVALID_VALUE, "elements", id, "Duplicate element");
			else elements.add(element);
		}

		final List<SubElement> subelements = new ArrayList<>();
		final Set<SubElement> seenSubelements = new HashSet<>();
		for (final String id : data.subelements()) {
			final Element element = resolveElement(id);
			if (!(element instanceof SubElement sub)) issue(issues, ExternalDataIssue.Category.UNKNOWN_IDENTIFIER, "subelements", id, "Unknown subelement");
			else if (!seenSubelements.add(sub)) issue(issues, ExternalDataIssue.Category.INVALID_VALUE, "subelements", id, "Duplicate subelement");
			else if (!hasParent(elements, sub)) issue(issues, ExternalDataIssue.Category.INVALID_VALUE, "subelements", id, "Subelement has no active parent element");
			else subelements.add(sub);
		}

		final Map<Integer, String> abilities = new HashMap<>();
		for (final Map.Entry<Integer, String> entry : data.abilities().entrySet()) {
			if (entry.getKey() == null || entry.getKey() < 1 || entry.getKey() > 9) {
				issue(issues, ExternalDataIssue.Category.INVALID_SLOT, "abilities", String.valueOf(entry.getKey()), "Slot must be between 1 and 9");
				continue;
			}
			final CoreAbility ability = entry.getValue() == null ? null : CoreAbility.getAbility(entry.getValue());
			if (ability == null || !ability.isEnabled()) issue(issues, ExternalDataIssue.Category.UNKNOWN_IDENTIFIER, "abilities." + entry.getKey(), entry.getValue(), "Unknown or disabled ability");
			else if (!structurallyBindable(ability, elements, subelements)) issue(issues, ExternalDataIssue.Category.INCOMPATIBLE_BIND, "abilities." + entry.getKey(), entry.getValue(), "Ability is incompatible with the supplied elements");
			else abilities.put(entry.getKey(), ability.getName());
		}

		final long now = System.currentTimeMillis();
		final Map<String, Cooldown> cooldowns = new HashMap<>();
		for (final Map.Entry<String, Long> entry : data.persistentCooldownExpirations().entrySet()) {
			if (!validIdentifier(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0) {
				issue(issues, ExternalDataIssue.Category.INVALID_VALUE, "persistentCooldownExpirations", entry.getKey(), "Invalid cooldown key or expiration");
			} else if (entry.getValue() > now) cooldowns.put(entry.getKey(), new Cooldown(entry.getValue(), true));
		}

		final Map<String, Map<Integer, String>> presets = new HashMap<>();
		for (final Map.Entry<String, Map<Integer, String>> preset : data.presets().entrySet()) {
			if (!validIdentifier(preset.getKey())) {
				issue(issues, ExternalDataIssue.Category.INVALID_IDENTIFIER, "presets", preset.getKey(), "Invalid preset name");
				continue;
			}
			if (preset.getValue().size() > 9) {
				issue(issues, ExternalDataIssue.Category.LIMIT_EXCEEDED, "presets." + preset.getKey(), "", "Preset has too many slots");
				continue;
			}
			final Map<Integer, String> presetAbilities = new HashMap<>();
			for (final Map.Entry<Integer, String> entry : preset.getValue().entrySet()) {
				if (entry.getKey() == null || entry.getKey() < 1 || entry.getKey() > 9) {
					issue(issues, ExternalDataIssue.Category.INVALID_SLOT, "presets." + preset.getKey(), String.valueOf(entry.getKey()), "Slot must be between 1 and 9");
					continue;
				}
				final CoreAbility ability = entry.getValue() == null ? null : CoreAbility.getAbility(entry.getValue());
				if (ability == null || !ability.isEnabled()) {
					issue(issues, ExternalDataIssue.Category.UNKNOWN_IDENTIFIER, "presets." + preset.getKey() + '.' + entry.getKey(), entry.getValue(), "Unknown or disabled ability");
					continue;
				}
				if (!structurallyBindable(ability, elements, subelements)) {
					issue(issues, ExternalDataIssue.Category.INCOMPATIBLE_BIND, "presets." + preset.getKey() + '.' + entry.getKey(), entry.getValue(), "Preset ability is incompatible with the supplied elements");
					continue;
				}
				presetAbilities.put(entry.getKey(), ability.getName());
			}
			presets.put(preset.getKey(), Map.copyOf(presetAbilities));
		}

		final Set<Element> disabledElements = new HashSet<>();
		for (final Map.Entry<String, Boolean> entry : data.elementEnabled().entrySet()) {
			final Element element = resolveElement(entry.getKey());
			if (element == null || !elements.contains(element)) issue(issues, ExternalDataIssue.Category.UNKNOWN_IDENTIFIER, "elementEnabled", entry.getKey(), "Toggle references an inactive element");
			else if (Boolean.FALSE.equals(entry.getValue())) disabledElements.add(element);
		}

		if (data.unknownIdentifierPolicy() == UnknownIdentifierPolicy.STRICT && !issues.isEmpty()) {
			throw new ExternalValidationException("External snapshot failed validation: " + issues.getFirst().message(), issues);
		}
		return new ResolvedData(elements, subelements, abilities, presets, cooldowns, disabledElements, List.copyOf(issues));
	}

	private static void reportIssues(final UUID uuid, final List<ExternalDataIssue> issues) {
		PkLang.warning("External bending data for " + uuid + " contained " + issues.size() + " issue(s); valid entries were applied");
		ExternalBendingPlayerPersistence.getProvider().ifPresent(provider -> {
			try { provider.reportDataIssues(identity(uuid), issues).exceptionally(error -> null); }
			catch (final Throwable ignored) {}
		});
		fireEvent(new ExternalBendingPlayerDataIssueEvent(uuid, issues));
	}

	private static MutationResult emitMutation(final BendingPlayerMutation mutation, final MutationResult result) {
		if (!result.accepted() && result.status() != MutationResult.Status.SUPERSEDED) {
			final String operation = mutation.operation().getClass().getSimpleName();
			final String warningKey = mutation.player().uuid() + ":" + operation + ":" + result.code();
			final long now = System.currentTimeMillis();
			final Long previous = FAILURE_WARNINGS.putIfAbsent(warningKey, now);
			if (previous == null || now - previous >= 30_000L) {
				FAILURE_WARNINGS.put(warningKey, now);
				PkLang.warning("External mutation " + result.status() + " uuid=" + mutation.player().uuid()
						+ " operation=" + operation + " source=" + mutation.source().pluginName() + '/' + mutation.source().detail()
						+ " context=" + mutation.contextToken() + " revision=" + mutation.expectedRevision()
						+ " code=" + result.code() + " message=" + result.message());
			}
		}
		fireEvent(new ExternalBendingPlayerMutationEvent(mutation, result));
		return result;
	}

	private static void fireEvent(final org.bukkit.event.Event event) {
		if (Bukkit.isPrimaryThread()) Bukkit.getPluginManager().callEvent(event);
		else Bukkit.getScheduler().runTask(ProjectKorra.plugin, () -> Bukkit.getPluginManager().callEvent(event));
	}

	private static void applyOperationDirect(final Bender target, final BendingPlayerMutationOperation operation) {
		if (operation instanceof BendingPlayerMutationOperation.AddElement add) {
			final Element element = requireMainElement(add.element());
			if (!target.elements.contains(element)) target.elements.add(element);
		} else if (operation instanceof BendingPlayerMutationOperation.RemoveElement remove) {
			final Element element = requireMainElement(remove.element());
			target.elements.remove(element);
			target.subelements.removeIf(sub -> !hasParent(target.elements, sub));
			filterInvalidBinds(target);
		} else if (operation instanceof BendingPlayerMutationOperation.ReplaceElements replace) {
			final List<Element> elements = replace.elements().stream().map(ExternalPersistenceCoordinator::requireMainElement).distinct().toList();
			target.elements.clear();
			target.elements.addAll(elements);
			target.subelements.removeIf(sub -> !hasParent(target.elements, sub));
			filterInvalidBinds(target);
		} else if (operation instanceof BendingPlayerMutationOperation.AddSubelement add) {
			final SubElement sub = requireSubelement(add.subelement());
			if (!hasParent(target.elements, sub)) throw new IllegalArgumentException("Subelement parent is not active: " + add.subelement());
			if (!target.subelements.contains(sub)) target.subelements.add(sub);
		} else if (operation instanceof BendingPlayerMutationOperation.RemoveSubelement remove) {
			target.subelements.remove(requireSubelement(remove.subelement()));
			filterInvalidBinds(target);
		} else if (operation instanceof BendingPlayerMutationOperation.ReplaceSubelements replace) {
			final List<SubElement> subelements = replace.subelements().stream().map(ExternalPersistenceCoordinator::requireSubelement).distinct().toList();
			for (final SubElement sub : subelements) if (!hasParent(target.elements, sub)) throw new IllegalArgumentException("Subelement parent is not active: " + sub.getName());
			target.subelements.clear();
			target.subelements.addAll(subelements);
			filterInvalidBinds(target);
		} else if (operation instanceof BendingPlayerMutationOperation.SetBind bind) {
			if (bind.slot() < 1 || bind.slot() > 9) throw new IllegalArgumentException("slot must be between 1 and 9");
			if (bind.ability() == null) target.abilities.remove(bind.slot());
			else {
				final CoreAbility ability = CoreAbility.getAbility(bind.ability());
				if (ability == null || !structurallyBindable(ability, target.elements, target.subelements)) throw new IllegalArgumentException("Unknown or incompatible ability " + bind.ability());
				target.abilities.put(bind.slot(), ability.getName());
			}
		} else if (operation instanceof BendingPlayerMutationOperation.ReplaceBinds replace) {
			final HashMap<Integer, String> binds = new HashMap<>();
			for (final Map.Entry<Integer, String> entry : replace.abilities().entrySet()) {
				if (entry.getKey() < 1 || entry.getKey() > 9) throw new IllegalArgumentException("slot must be between 1 and 9");
				if (entry.getValue() == null) continue;
				final CoreAbility ability = CoreAbility.getAbility(entry.getValue());
				if (ability == null || !structurallyBindable(ability, target.elements, target.subelements)) throw new IllegalArgumentException("Unknown or incompatible ability " + entry.getValue());
				binds.put(entry.getKey(), ability.getName());
			}
			target.abilities = binds;
		} else if (operation instanceof BendingPlayerMutationOperation.UpsertPreset preset) {
			Preset.applyExternalPresetMutation(target.getUUID(), preset.name(), preset.abilities());
		} else if (operation instanceof BendingPlayerMutationOperation.DeletePreset preset) {
			Preset.removeExternalPreset(target.getUUID(), preset.name());
		} else if (operation instanceof BendingPlayerMutationOperation.SetPersistentCooldown cooldown) {
			if (cooldown.expiration() > System.currentTimeMillis()) target.cooldowns.put(cooldown.key(), new Cooldown(cooldown.expiration(), true));
			else target.cooldowns.remove(cooldown.key());
		} else if (operation instanceof BendingPlayerMutationOperation.RemovePersistentCooldown cooldown) {
			target.cooldowns.remove(cooldown.key());
		} else if (operation instanceof BendingPlayerMutationOperation.ClearPersistentCooldowns) {
			target.cooldowns.entrySet().removeIf(entry -> entry.getValue().isDatabase());
		} else if (operation instanceof BendingPlayerMutationOperation.SetBendingEnabled toggle) {
			target.toggled = toggle.enabled();
		} else if (operation instanceof BendingPlayerMutationOperation.SetElementEnabled toggle) {
			final Element element = requireMainElement(toggle.element());
			if (toggle.enabled()) target.toggledElements.remove(element); else target.toggledElements.add(element);
		} else if (operation instanceof BendingPlayerMutationOperation.SetBoardPreference board) {
			if (target instanceof BendingPlayer player) BendingBoardManager.applyExternalPreference(player.getPlayer(), board.preference());
		} else if (operation instanceof BendingPlayerMutationOperation.Reset) {
			target.elements.clear();
			target.subelements.clear();
			target.abilities.clear();
			target.cooldowns.entrySet().removeIf(entry -> entry.getValue().isDatabase());
			target.toggledElements.clear();
			target.toggled = true;
			Preset.clearExternalPresets(target.getUUID());
		}
	}

	private static void persistInternalOperation(final Bender target, final BendingPlayerMutationOperation operation) throws Exception {
		final UUID uuid = target.getUUID();
		if (operation instanceof BendingPlayerMutationOperation.AddElement
				|| operation instanceof BendingPlayerMutationOperation.RemoveElement
				|| operation instanceof BendingPlayerMutationOperation.ReplaceElements) {
			if (target instanceof OfflineBendingPlayer player) {
				player.saveElements();
				player.saveSubElements();
			}
		} else if (operation instanceof BendingPlayerMutationOperation.AddSubelement
				|| operation instanceof BendingPlayerMutationOperation.RemoveSubelement
				|| operation instanceof BendingPlayerMutationOperation.ReplaceSubelements) {
			if (target instanceof OfflineBendingPlayer player) player.saveSubElements();
		} else if (operation instanceof BendingPlayerMutationOperation.SetBind bind) {
			InternalPlayerDataStore.writeAbilityLegacy(uuid, bind.slot(), target.abilities.get(bind.slot()));
		} else if (operation instanceof BendingPlayerMutationOperation.ReplaceBinds) {
			InternalPlayerDataStore.writeAbilitiesLegacy(uuid, target.abilities);
		} else if (operation instanceof BendingPlayerMutationOperation.UpsertPreset preset) {
			InternalPlayerDataStore.insertPreset(uuid, preset.name(), preset.abilities());
		} else if (operation instanceof BendingPlayerMutationOperation.DeletePreset preset) {
			InternalPlayerDataStore.deletePreset(uuid, preset.name());
		} else if (operation instanceof BendingPlayerMutationOperation.SetPersistentCooldown
				|| operation instanceof BendingPlayerMutationOperation.RemovePersistentCooldown
				|| operation instanceof BendingPlayerMutationOperation.ClearPersistentCooldowns) {
			InternalPlayerDataStore.saveCooldowns(uuid, target.cooldowns);
		} else if (operation instanceof BendingPlayerMutationOperation.SetBoardPreference board) {
			InternalPlayerDataStore.saveBoardPreference(uuid, board.preference() != BoardPreference.DISABLED);
		} else if (operation instanceof BendingPlayerMutationOperation.Reset) {
			if (target instanceof OfflineBendingPlayer player) {
				player.saveElements();
				player.saveSubElements();
			}
			InternalPlayerDataStore.writeAbilitiesLegacy(uuid, target.abilities);
			InternalPlayerDataStore.saveCooldowns(uuid, target.cooldowns);
		}
	}

	private static void refreshDerivedState(final BendingPlayer player, final boolean resetMultiAbility) {
		if (resetMultiAbility) MultiAbilityManager.clearExternalProjectionState(player.getPlayer());
		player.removeUnusableAbilities();
		com.projectkorra.projectkorra.ability.util.PassiveManager.registerPassives(player.getPlayer());
		FirePassive.handle(player.getPlayer());
		player.refreshChatPrefix();
		ChatUtil.displayMovePreview(player.getPlayer());
		BendingBoardManager.updateAllSlots(player.getPlayer());
		BendingBoardManager.changeActiveSlot(player.getPlayer(), player.getPlayer().getInventory().getHeldItemSlot() + 1);
	}

	private static boolean replacesBindingsOrElements(final BendingPlayerMutationOperation operation) {
		return operation instanceof BendingPlayerMutationOperation.AddElement
				|| operation instanceof BendingPlayerMutationOperation.RemoveElement
				|| operation instanceof BendingPlayerMutationOperation.ReplaceElements
				|| operation instanceof BendingPlayerMutationOperation.AddSubelement
				|| operation instanceof BendingPlayerMutationOperation.RemoveSubelement
				|| operation instanceof BendingPlayerMutationOperation.ReplaceSubelements
				|| operation instanceof BendingPlayerMutationOperation.SetBind
				|| operation instanceof BendingPlayerMutationOperation.ReplaceBinds
				|| operation instanceof BendingPlayerMutationOperation.Reset;
	}

	private static void filterInvalidBinds(final Bender target) {
		target.abilities.entrySet().removeIf(entry -> {
			final CoreAbility ability = CoreAbility.getAbility(entry.getValue());
			return ability == null || !structurallyBindable(ability, target.elements, target.subelements);
		});
	}

	private static boolean structurallyBindable(final CoreAbility ability, final List<Element> elements, final List<SubElement> subelements) {
		if (ability == null) return false;
		final Element element = ability.getElement();
		if (element instanceof SubElement sub) return hasParent(elements, sub) && subelements.contains(sub);
		return elements.contains(element) || (ability instanceof com.projectkorra.projectkorra.ability.AvatarAbility avatar && !avatar.requireAvatar());
	}

	private static boolean hasParent(final List<Element> elements, final SubElement sub) {
		if (sub instanceof Element.MultiSubElement multi) {
			for (final Element parent : multi.getParentElements()) if (elements.contains(parent)) return true;
			return false;
		}
		return elements.contains(sub.getParentElement());
	}

	private static Element resolveElement(final String id) {
		if (!validIdentifier(id)) return null;
		Element element = Element.getElement(id);
		if (element == null) element = Element.fromString(id);
		return element;
	}

	private static Element requireMainElement(final String id) {
		final Element element = resolveElement(id);
		if (element == null || element instanceof SubElement) throw new IllegalArgumentException("Unknown main element " + id);
		return element;
	}

	private static SubElement requireSubelement(final String id) {
		final Element element = resolveElement(id);
		if (!(element instanceof SubElement sub)) throw new IllegalArgumentException("Unknown subelement " + id);
		return sub;
	}

	private static boolean validIdentifier(final String id) {
		return id != null && !id.isBlank() && id.length() <= MAX_IDENTIFIER_LENGTH;
	}

	private static void issue(final List<ExternalDataIssue> issues, final ExternalDataIssue.Category category,
			final String path, final String value, final String message) {
		issues.add(new ExternalDataIssue(category, path, value, message));
	}

	private static void withGuard(final Session session, final Runnable action) {
		session.guardDepth.incrementAndGet();
		try { action.run(); } finally { session.guardDepth.decrementAndGet(); }
	}

	private static PlayerIdentity identity(final UUID uuid) {
		final Player online = Bukkit.getPlayer(uuid);
		final OfflinePlayer player = online == null ? Bukkit.getOfflinePlayer(uuid) : online;
		return new PlayerIdentity(uuid, player.getName(), online != null && online.isOnline());
	}

	private static <T> CompletableFuture<T> onMain(final Supplier<T> action) {
		if (Bukkit.isPrimaryThread()) {
			try { return CompletableFuture.completedFuture(action.get()); }
			catch (final Throwable error) { return CompletableFuture.failedFuture(error); }
		}
		final CompletableFuture<T> result = new CompletableFuture<>();
		Bukkit.getScheduler().runTask(ProjectKorra.plugin, () -> {
			try { result.complete(action.get()); } catch (final Throwable error) { result.completeExceptionally(error); }
		});
		return result;
	}

	private static <T> CompletableFuture<T> timed(final CompletionStage<T> stage, final long timeoutMillis, final String operation) {
		if (stage == null) return CompletableFuture.failedFuture(new IllegalStateException("Provider returned null CompletionStage for " + operation));
		return stage.toCompletableFuture().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
	}

	private static <T> CompletionStage<T> callProvider(final Supplier<CompletionStage<T>> invocation) {
		try {
			final CompletionStage<T> stage = invocation.get();
			return stage == null ? CompletableFuture.failedFuture(new IllegalStateException("External provider returned a null CompletionStage")) : stage;
		} catch (final Throwable error) {
			return CompletableFuture.failedFuture(error);
		}
	}

	private static long loadTimeout() { return Math.max(1, ConfigManager.getConfig().getLong("Storage.External.LoadTimeoutMillis", 10_000L)); }
	private static long mutationTimeout() { return Math.max(1, ConfigManager.getConfig().getLong("Storage.External.MutationTimeoutMillis", 5_000L)); }
	private static long flushTimeout() { return Math.max(1, ConfigManager.getConfig().getLong("Storage.External.FlushTimeoutMillis", 5_000L)); }

	private static Throwable unwrap(final Throwable error) {
		Throwable current = error;
		while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) current = current.getCause();
		return current;
	}

	private static String describe(final Throwable error) {
		if (error == null) return "";
		return error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
	}

	private record ResolvedData(List<Element> elements, List<SubElement> subelements, Map<Integer, String> abilities,
			Map<String, Map<Integer, String>> presets, Map<String, Cooldown> cooldowns,
			Set<Element> disabledElements, List<ExternalDataIssue> issues) {}

	private static final class RuntimeBackup {
		private final List<Element> elements;
		private final List<SubElement> subelements;
		private final HashMap<Integer, String> abilities;
		private final Map<String, Cooldown> cooldowns;
		private final Set<Element> toggledElements;
		private final boolean toggled;
		private final boolean permaRemoved;
		private final Map<String, Map<Integer, String>> presets;
		private final BoardPreference boardPreference;

		private RuntimeBackup(final Bender target) {
			this.elements = new ArrayList<>(target.elements);
			this.subelements = new ArrayList<>(target.subelements);
			this.abilities = new HashMap<>(target.abilities);
			this.cooldowns = new HashMap<>(target.cooldowns);
			this.toggledElements = new HashSet<>(target.toggledElements);
			this.toggled = target.toggled;
			this.permaRemoved = target.permaRemoved;
			this.presets = Preset.snapshotExternalPresets(target.getUUID());
			this.boardPreference = target instanceof BendingPlayer
					? BendingBoardManager.getExternalRuntimePreference(target.getUUID()) : BoardPreference.UNSPECIFIED;
		}

		private void restore(final Bender target) {
			target.elements.clear(); target.elements.addAll(this.elements);
			target.subelements.clear(); target.subelements.addAll(this.subelements);
			target.abilities = new HashMap<>(this.abilities);
			target.cooldowns.clear(); target.cooldowns.putAll(this.cooldowns);
			target.toggledElements.clear(); target.toggledElements.addAll(this.toggledElements);
			target.toggled = this.toggled;
			target.permaRemoved = this.permaRemoved;
			Preset.replaceExternalPresets(target.getUUID(), this.presets);
			if (target instanceof BendingPlayer player) BendingBoardManager.applyExternalPreference(player.getPlayer(), this.boardPreference);
		}
	}

	private static final class Session {
		private final UUID uuid;
		private final AtomicLong epoch = new AtomicLong();
		private final AtomicInteger pending = new AtomicInteger();
		private final AtomicInteger guardDepth = new AtomicInteger();
		private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
		private volatile String contextToken;
		private volatile long revision = -1;
		private volatile boolean blocked;
		private volatile Instant lastSuccessfulLoad;
		private volatile Instant lastSuccessfulMutation;
		private volatile String lastFailure = "";
		private volatile String phase = "";

		private Session(final UUID uuid) { this.uuid = uuid; }
	}

	private static final class ExternalValidationException extends RuntimeException {
		private final List<ExternalDataIssue> issues;
		private ExternalValidationException(final String message, final List<ExternalDataIssue> issues) { super(message); this.issues = List.copyOf(issues); }
	}
	private static final class ExternalApplyException extends RuntimeException {
		private ExternalApplyException(final String message, final Throwable cause) { super(message, cause); }
	}
	private static final class SupersededException extends RuntimeException {
		private SupersededException(final String message) { super(message); }
	}
}
