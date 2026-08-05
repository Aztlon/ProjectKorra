package com.projectkorra.projectkorra.persistence.external;

public sealed interface MutationDecision permits MutationDecision.Accepted, MutationDecision.Rejected, MutationDecision.Conflict, MutationDecision.Failed {
	record Accepted(String contextToken, long revision, BendingPlayerMutation canonicalMutation,
			ExternalBendingPlayerData canonicalSnapshot) implements MutationDecision {
		public Accepted {
			if (contextToken == null || contextToken.isBlank()) throw new IllegalArgumentException("contextToken may not be blank");
			if (revision < 0) throw new IllegalArgumentException("revision may not be negative");
			if ((canonicalMutation == null) == (canonicalSnapshot == null)) throw new IllegalArgumentException("exactly one canonical result is required");
		}
		public static Accepted mutation(final String contextToken, final long revision, final BendingPlayerMutation mutation) {
			return new Accepted(contextToken, revision, mutation, null);
		}
		public static Accepted snapshot(final ExternalBendingPlayerData snapshot) {
			return new Accepted(snapshot.contextToken(), snapshot.revision(), null, snapshot);
		}
	}
	record Rejected(String code, String message, ExternalBendingPlayerData authoritativeSnapshot) implements MutationDecision {}
	record Conflict(String code, String message, long currentRevision, ExternalBendingPlayerData authoritativeSnapshot) implements MutationDecision {}
	record Failed(String code, String message, Throwable cause) implements MutationDecision {}
}
