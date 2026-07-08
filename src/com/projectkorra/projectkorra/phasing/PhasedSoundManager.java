package com.projectkorra.projectkorra.phasing;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.ability.Ability;

public final class PhasedSoundManager {

	private static final double BASE_SOUND_RANGE = 16.0D;

	private PhasedSoundManager() {
	}

	public static void playSound(final Location location, final Sound sound, final float volume, final float pitch) {
		playSound(PhasedIntegrationManager.getCurrentAbilityContext(), location, sound, volume, pitch);
	}

	public static void playSound(@Nullable final Ability ability, final Location location, final Sound sound, final float volume, final float pitch) {
		if (!isValidSound(location, sound)) {
			return;
		}

		final Ability sourceAbility = ability == null ? PhasedIntegrationManager.getCurrentAbilityContext() : ability;
		playSound(sourceAbility, location, volume, viewer -> viewer.playSound(location, sound, volume, pitch));
	}

	public static void playSound(final Location location, final String sound, final float volume, final float pitch) {
		playSound(PhasedIntegrationManager.getCurrentAbilityContext(), location, sound, volume, pitch);
	}

	public static void playSound(@Nullable final Ability ability, final Location location, final String sound, final float volume, final float pitch) {
		if (!isValidSound(location, sound)) {
			return;
		}

		final Ability sourceAbility = ability == null ? PhasedIntegrationManager.getCurrentAbilityContext() : ability;
		playSound(sourceAbility, location, volume, viewer -> viewer.playSound(location, sound, volume, pitch));
	}

	public static void playSoundFromEntity(@Nullable final LivingEntity source, @Nullable final String abilityId, final Location location,
			final Sound sound, final float volume, final float pitch) {
		if (!isValidSound(location, sound)) {
			return;
		}

		playSoundFromEntity(source, abilityId, location, volume, viewer -> viewer.playSound(location, sound, volume, pitch));
	}

	public static void playSoundFromEntity(@Nullable final LivingEntity source, @Nullable final String abilityId, final Location location,
			final String sound, final float volume, final float pitch) {
		if (!isValidSound(location, sound)) {
			return;
		}

		playSoundFromEntity(source, abilityId, location, volume, viewer -> viewer.playSound(location, sound, volume, pitch));
	}

	public static void playSoundToViewer(final Player viewer, final Location location, final Sound sound, final float volume, final float pitch) {
		playSoundToViewer(PhasedIntegrationManager.getCurrentAbilityContext(), viewer, location, sound, volume, pitch);
	}

	public static void playSoundToViewer(@Nullable final Ability ability, final Player viewer, final Location location, final Sound sound,
			final float volume, final float pitch) {
		if (viewer == null || !isValidSound(location, sound)) {
			return;
		}

		final Ability sourceAbility = ability == null ? PhasedIntegrationManager.getCurrentAbilityContext() : ability;
		playSoundToViewer(sourceAbility, viewer, location, target -> target.playSound(location, sound, volume, pitch));
	}

	public static void playSoundToViewer(final Player viewer, final Location location, final String sound, final float volume, final float pitch) {
		playSoundToViewer(PhasedIntegrationManager.getCurrentAbilityContext(), viewer, location, sound, volume, pitch);
	}

	public static void playSoundToViewer(@Nullable final Ability ability, final Player viewer, final Location location, final String sound,
			final float volume, final float pitch) {
		if (viewer == null || !isValidSound(location, sound)) {
			return;
		}

		final Ability sourceAbility = ability == null ? PhasedIntegrationManager.getCurrentAbilityContext() : ability;
		playSoundToViewer(sourceAbility, viewer, location, target -> target.playSound(location, sound, volume, pitch));
	}

	private static void playSound(@Nullable final Ability sourceAbility, final Location location, final float volume, final SoundEmitter emitter) {
		for (final Player viewer : location.getWorld().getPlayers()) {
			if (!canHear(viewer, location, volume)) {
				continue;
			}
			if (!PhasedIntegrationManager.shouldAllow(
					PhasedIntegrationManager.requestFromAbility(sourceAbility, null, GateStage.SOUND, location, viewer.getUniqueId()))) {
				continue;
			}
			emitter.play(viewer);
		}
	}

	private static void playSoundFromEntity(@Nullable final LivingEntity source, @Nullable final String abilityId, final Location location,
			final float volume, final SoundEmitter emitter) {
		for (final Player viewer : location.getWorld().getPlayers()) {
			if (!canHear(viewer, location, volume)) {
				continue;
			}
			if (!PhasedIntegrationManager.shouldAllow(
					PhasedIntegrationManager.requestFromSource(source, abilityId, GateStage.SOUND, location, viewer.getUniqueId()))) {
				continue;
			}
			emitter.play(viewer);
		}
	}

	private static void playSoundToViewer(@Nullable final Ability sourceAbility, final Player viewer, final Location location, final SoundEmitter emitter) {
		if (PhasedIntegrationManager.shouldAllow(
				PhasedIntegrationManager.requestFromAbility(sourceAbility, null, GateStage.SOUND, location, viewer.getUniqueId()))) {
			emitter.play(viewer);
		}
	}

	private static boolean isValidSound(@Nullable final Location location, @Nullable final Sound sound) {
		return location != null && location.getWorld() != null && sound != null;
	}

	private static boolean isValidSound(@Nullable final Location location, @Nullable final String sound) {
		return location != null && location.getWorld() != null && sound != null && !sound.isEmpty();
	}

	private static boolean canHear(final Player viewer, final Location location, final float volume) {
		final World world = location.getWorld();
		if (viewer == null || world == null || !world.equals(viewer.getWorld())) {
			return false;
		}

		final double range = BASE_SOUND_RANGE * Math.max(1.0F, volume);
		return viewer.getLocation().distanceSquared(location) <= range * range;
	}

	private interface SoundEmitter {
		void play(Player viewer);
	}
}
