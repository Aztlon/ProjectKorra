package com.projectkorra.projectkorra.persistence.external;

/**
 * Describes the authority ProjectKorra attests for a provider-mediated mutation.
 *
 * <p>{@link #ADMINISTRATIVE} only means that the mutation originated from a
 * ProjectKorra command path after its sender/target authorization checks
 * succeeded. The external provider remains responsible for deciding what that
 * authority permits and for every other validation and persistence check.</p>
 */
public enum MutationIntent {
	STANDARD,
	ADMINISTRATIVE
}
