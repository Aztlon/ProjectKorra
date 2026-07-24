package com.projectkorra.projectkorra.persistence.external;

import java.time.Instant;
import java.util.UUID;

public record ExternalPersistenceDiagnostics(UUID playerUuid, PlayerDataMode mode, String providerName, String providerVersion,
		int contractVersion, String initializationState, String initializationPhase, String contextToken,
		long revision, Instant lastSuccessfulLoad, Instant lastSuccessfulMutation, int pendingRequests,
		String lastFailure, boolean projectionGuardActive) {}
