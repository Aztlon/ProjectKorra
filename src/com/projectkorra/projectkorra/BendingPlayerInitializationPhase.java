package com.projectkorra.projectkorra;

/** The phase in which bending-player initialization failed. */
public enum BendingPlayerInitializationPhase {
	DATABASE_LOOKUP,
	ROW_CREATION,
	HYDRATION,
	ADDON_RESOLUTION,
	MAIN_THREAD_FINALIZATION
}
