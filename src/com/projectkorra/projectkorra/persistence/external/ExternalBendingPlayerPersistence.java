package com.projectkorra.projectkorra.persistence.external;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.projectkorra.projectkorra.ExternalPersistenceCoordinator;

/** Public, Avatarverse-neutral entry point for externally persisted bending data. */
public final class ExternalBendingPlayerPersistence {
	public static final int CONTRACT_VERSION = 2;

	private static final AtomicReference<Registration> PROVIDER = new AtomicReference<>();
	private static volatile PlayerDataMode mode = PlayerDataMode.INTERNAL;
	private static volatile boolean bootstrapped;
	private static volatile boolean registrationClosed;

	private ExternalBendingPlayerPersistence() {}

	/** Called by ProjectKorra once, after its configuration has loaded. */
	public static synchronized void bootstrap(final PlayerDataMode configuredMode) {
		if (bootstrapped) return;
		mode = Objects.requireNonNull(configuredMode, "configuredMode");
		bootstrapped = true;
		registrationClosed = false;
	}

	public static PlayerDataMode getMode() { return mode; }
	public static boolean isExternalMode() { return mode == PlayerDataMode.EXTERNAL; }
	public static boolean isRegistrationClosed() { return registrationClosed; }

	public static synchronized void closeRegistration() {
		registrationClosed = true;
	}

	public static void registerProvider(final Plugin owner, final ExternalBendingPlayerProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (registrationClosed) throw new IllegalStateException("External bending-player provider registration is closed; restart the server to change providers");
		if (!owner.isEnabled()) throw new IllegalStateException("The provider owner plugin must be enabled");
		if (provider.contractVersion() != CONTRACT_VERSION) {
			throw new IllegalArgumentException("Unsupported external persistence contract " + provider.contractVersion() + "; expected " + CONTRACT_VERSION);
		}
		if (provider.providerName() == null || provider.providerName().isBlank()) throw new IllegalArgumentException("providerName may not be blank");
		if (provider.providerVersion() == null || provider.providerVersion().isBlank()) throw new IllegalArgumentException("providerVersion may not be blank");
		if (!PROVIDER.compareAndSet(null, new Registration(owner, provider))) throw new IllegalStateException("An external bending-player provider is already registered");
	}

	public static boolean unregisterProvider(final Plugin owner) {
		final Registration registration = PROVIDER.get();
		if (registration == null || registration.owner() != owner) return false;
		if (!PROVIDER.compareAndSet(registration, null)) return false;
		ExternalPersistenceCoordinator.providerUnavailable("Provider plugin " + owner.getName() + " was disabled");
		return true;
	}

	public static Optional<ExternalBendingPlayerProvider> getProvider() {
		return Optional.ofNullable(PROVIDER.get()).map(Registration::provider);
	}

	public static Optional<Plugin> getProviderOwner() {
		return Optional.ofNullable(PROVIDER.get()).map(Registration::owner);
	}

	public static CompletionStage<ApplyResult> applyExternalSnapshot(final Player player,
			final ExternalBendingPlayerData data, final ApplyReason reason) {
		if (!isExternalMode()) throw new IllegalStateException("External snapshots require Storage.PlayerDataMode=EXTERNAL");
		return ExternalPersistenceCoordinator.applySnapshot(player, data, reason);
	}

	public static ExternalPersistenceDiagnostics getExternalPersistenceDiagnostics(final UUID uuid) {
		return ExternalPersistenceCoordinator.diagnostics(uuid);
	}

	private record Registration(Plugin owner, ExternalBendingPlayerProvider provider) {}
}
