package com.projectkorra.projectkorra.persistence.external;

import java.util.Objects;

/**
 * A provider-mediated durable player mutation.
 *
 * <p>The typed {@link #intent()} is the authority signal. {@link #source()} is
 * diagnostic metadata only and must never be parsed to infer authority.</p>
 */
public record BendingPlayerMutation(PlayerIdentity player, String contextToken, long expectedRevision,
		MutationSource source, MutationIntent intent, BendingPlayerMutationOperation operation) {
	public BendingPlayerMutation {
		Objects.requireNonNull(player, "player");
		contextToken = Objects.requireNonNull(contextToken, "contextToken");
		if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision may not be negative");
		source = Objects.requireNonNull(source, "source");
		intent = Objects.requireNonNull(intent, "intent");
		operation = Objects.requireNonNull(operation, "operation");
	}

	/** Source-compatible constructor for ordinary gameplay, API, and addon mutations. */
	public BendingPlayerMutation(final PlayerIdentity player, final String contextToken, final long expectedRevision,
			final MutationSource source, final BendingPlayerMutationOperation operation) {
		this(player, contextToken, expectedRevision, source, MutationIntent.STANDARD, operation);
	}
}
