package com.projectkorra.projectkorra.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerDataSqlGuardTest {

	@Test
	void recognizesEveryLegacyPlayerTableCaseInsensitively() {
		assertTrue(PlayerDataSqlGuard.isPlayerDataReference("SELECT * FROM pk_players"));
		assertTrue(PlayerDataSqlGuard.isPlayerDataReference("delete FROM PK_PRESETS"));
		assertTrue(PlayerDataSqlGuard.isPlayerDataReference("pk_cooldowns"));
		assertTrue(PlayerDataSqlGuard.isPlayerDataReference("ALTER TABLE pk_board ADD enabled BOOL"));
	}

	@Test
	void permitsUnrelatedGlobalStorage() {
		assertFalse(PlayerDataSqlGuard.isPlayerDataReference("SELECT * FROM pk_statKeys"));
		assertFalse(PlayerDataSqlGuard.isPlayerDataReference("CREATE TABLE pk_players_stats"));
		assertFalse(PlayerDataSqlGuard.isPlayerDataReference(null));
	}
}
