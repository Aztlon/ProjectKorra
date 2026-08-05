package com.projectkorra.projectkorra.persistence.external;

public enum ApplyReason {
	INITIAL_LOAD(true, true),
	PROFILE_REPLACEMENT(true, true),
	ADMINISTRATIVE_REAPPLY(false, false),
	CONFLICT_RECONCILIATION(false, false),
	ACCEPTED_MUTATION(false, false);

	private final boolean supersedesPending;
	private final boolean clearsRuntimeCooldowns;

	ApplyReason(final boolean supersedesPending, final boolean clearsRuntimeCooldowns) {
		this.supersedesPending = supersedesPending;
		this.clearsRuntimeCooldowns = clearsRuntimeCooldowns;
	}

	public boolean supersedesPending() { return this.supersedesPending; }
	public boolean clearsRuntimeCooldowns() { return this.clearsRuntimeCooldowns; }
}
