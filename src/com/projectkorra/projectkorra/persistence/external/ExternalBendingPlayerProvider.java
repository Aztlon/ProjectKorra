package com.projectkorra.projectkorra.persistence.external;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface ExternalBendingPlayerProvider {
	String providerName();
	default String providerVersion() { return "unknown"; }
	int contractVersion();
	CompletionStage<ExternalBendingPlayerData> load(PlayerIdentity player);
	/**
	 * Decides whether to accept a mutation, including what an
	 * {@link MutationIntent#ADMINISTRATIVE} element change is allowed to bypass.
	 * Administrative intent is not unconditional acceptance; providers retain
	 * responsibility for context, revision, structural, lock, and persistence checks.
	 */
	CompletionStage<MutationDecision> requestMutation(BendingPlayerMutation mutation);
	CompletionStage<Void> flush(PlayerIdentity player, FlushReason reason);

	default CooldownPersistence classifyCooldown(final PlayerIdentity player, final String key, final boolean persistenceRequested) {
		return persistenceRequested ? CooldownPersistence.PROFILE_PERSISTENT : CooldownPersistence.RUNTIME;
	}

	default CompletionStage<Void> reportDataIssues(final PlayerIdentity player, final List<ExternalDataIssue> issues) {
		return CompletableFuture.completedFuture(null);
	}
}
