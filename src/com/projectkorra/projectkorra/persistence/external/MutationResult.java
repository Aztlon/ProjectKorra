package com.projectkorra.projectkorra.persistence.external;

public record MutationResult(Status status, String code, String message, long revision, Throwable cause) {
	public enum Status { ACCEPTED, REJECTED, CONFLICT, FAILED, SUPERSEDED }

	public boolean accepted() { return this.status == Status.ACCEPTED; }
	public static MutationResult accepted(final long revision) { return new MutationResult(Status.ACCEPTED, "accepted", "", revision, null); }
	public static MutationResult failed(final String code, final String message, final Throwable cause) { return new MutationResult(Status.FAILED, code, message, -1, cause); }
}
