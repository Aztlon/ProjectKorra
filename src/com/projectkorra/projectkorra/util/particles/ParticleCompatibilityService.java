package com.projectkorra.projectkorra.util.particles;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.projectkorra.projectkorra.ProjectKorra;

public final class ParticleCompatibilityService {

	private static final float BEDROCK_DUST_SIZE = 1.0F;

	/*
	 * Bedrock clients can translate Java block/data particles into very heavy or
	 * visually wrong effects. Bedrock viewers get simple low-count substitutes
	 * while Java viewers keep the original particle payload.
	 */
	private static final Set<Particle> DATA_PARTICLES = EnumSet.of(
			Particle.BLOCK,
			Particle.BLOCK_CRUMBLE,
			Particle.BLOCK_MARKER,
			Particle.FALLING_DUST,
			Particle.DUST_PILLAR,
			Particle.ITEM);
	private static final Set<Particle> HEAVY_PARTICLES = EnumSet.of(
			Particle.CLOUD,
			Particle.ASH,
			Particle.WHITE_ASH,
			Particle.CAMPFIRE_COSY_SMOKE,
			Particle.CAMPFIRE_SIGNAL_SMOKE,
			Particle.LARGE_SMOKE);

	private static BedrockParticlePacketListener packetListener;

	private ParticleCompatibilityService() {
	}

	public static void reload(final ProjectKorra plugin) {
		BedrockParticleProfile.reload();
		BedrockPlayerDetector.reload();
		reloadPacketListener(plugin);
	}

	public static void shutdown() {
		if (packetListener != null) {
			packetListener.unregister();
			packetListener = null;
		}
	}

	public static void spawn(final Player viewer, final ParticleSpawnRequest request) {
		if (viewer == null || request == null || request.getParticle() == null || request.getLocation() == null) {
			return;
		}

		if (!BedrockParticleProfile.isEnabled() || !BedrockPlayerDetector.isBedrockPlayer(viewer)) {
			spawnOriginal(viewer, request);
			return;
		}

		final ParticleSpawnRequest replacement = createBedrockRequest(request);
		spawnOriginal(viewer, replacement);
		if (BedrockParticleProfile.isDebug() && replacement != request) {
			ProjectKorra.log.info("Replaced particle " + request.getParticle() + " with " + replacement.getParticle()
					+ " for Bedrock player " + viewer.getName());
		}
	}

	public static boolean isUnsafeForBedrock(final Particle particle) {
		return DATA_PARTICLES.contains(particle)
				|| HEAVY_PARTICLES.contains(particle)
				|| particle == Particle.DUST
				|| particle == Particle.DUST_COLOR_TRANSITION
				|| particle == Particle.SMOKE;
	}

	static ParticleSpawnRequest createBedrockRequest(final ParticleSpawnRequest request) {
		final Particle particle = request.getParticle();
		final int cappedAmount = BedrockParticleProfile.capAmount(request.getAmount());

		if (DATA_PARTICLES.contains(particle)) {
			if (particle == Particle.ITEM) {
				return itemReplacement(request, cappedAmount);
			}
			return blockReplacement(request, cappedAmount);
		}

		if (particle == Particle.CLOUD) {
			return airReplacement(request, cappedAmount);
		}

		if (particle == Particle.ASH || particle == Particle.WHITE_ASH || particle == Particle.CAMPFIRE_COSY_SMOKE
				|| particle == Particle.CAMPFIRE_SIGNAL_SMOKE || particle == Particle.LARGE_SMOKE || particle == Particle.SMOKE) {
			return new ParticleSpawnRequest(Particle.WHITE_SMOKE, request.getLocation(), cappedAmount, request.getOffsetX(),
					request.getOffsetY(), request.getOffsetZ(), Math.min(request.getExtra(), 0.02), null);
		}

		if (particle == Particle.DUST || particle == Particle.DUST_COLOR_TRANSITION) {
			return dustReplacement(request, cappedAmount);
		}

		if (request.getAmount() > BedrockParticleProfile.getMaxCount()) {
			return new ParticleSpawnRequest(particle, request.getLocation(), cappedAmount, request.getOffsetX(), request.getOffsetY(),
					request.getOffsetZ(), request.getExtra(), request.getData());
		}
		return request;
	}

	private static ParticleSpawnRequest blockReplacement(final ParticleSpawnRequest request, final int amount) {
		if ("CLOUD".equals(BedrockParticleProfile.getBlockReplacementMode())) {
			return new ParticleSpawnRequest(Particle.WHITE_SMOKE, request.getLocation(), amount, request.getOffsetX(),
					request.getOffsetY(), request.getOffsetZ(), Math.min(request.getExtra(), 0.02), null);
		}
		return coloredDust(request, amount, colorFromData(request.getData()));
	}

	private static ParticleSpawnRequest itemReplacement(final ParticleSpawnRequest request, final int amount) {
		final Color color = colorFromData(request.getData());
		if (color.equals(BedrockParticleProfile.getIceColor())) {
			return new ParticleSpawnRequest(Particle.SNOWFLAKE, request.getLocation(), amount, request.getOffsetX(),
					request.getOffsetY(), request.getOffsetZ(), 0, null);
		}
		return coloredDust(request, amount, color);
	}

	private static ParticleSpawnRequest airReplacement(final ParticleSpawnRequest request, final int amount) {
		final Particle airParticle = BedrockParticleProfile.getAirParticle();
		if (airParticle == Particle.DUST) {
			return coloredDust(request, amount, BedrockParticleProfile.getAirColor());
		}
		return new ParticleSpawnRequest(airParticle, request.getLocation(), amount, request.getOffsetX(), request.getOffsetY(),
				request.getOffsetZ(), Math.min(request.getExtra(), 0.02), defaultDataFor(airParticle, BedrockParticleProfile.getAirColor()));
	}

	private static ParticleSpawnRequest dustReplacement(final ParticleSpawnRequest request, final int amount) {
		Color color = BedrockParticleProfile.getAirColor();
		if (request.getData() instanceof DustOptions dust) {
			color = dust.getColor();
		}
		return coloredDust(request, amount, color);
	}

	private static ParticleSpawnRequest coloredDust(final ParticleSpawnRequest request, final int amount, final Color color) {
		return new ParticleSpawnRequest(Particle.DUST, request.getLocation(), amount, request.getOffsetX(), request.getOffsetY(),
				request.getOffsetZ(), Math.min(request.getExtra(), 0.02), new DustOptions(color, BEDROCK_DUST_SIZE));
	}

	private static Object defaultDataFor(final Particle particle, final Color color) {
		if (particle.getDataType() == DustOptions.class) {
			return new DustOptions(color, BEDROCK_DUST_SIZE);
		}
		if (particle.getDataType() == Color.class) {
			return color;
		}
		return null;
	}

	private static Color colorFromData(final Object data) {
		Material material = null;
		if (data instanceof BlockData blockData) {
			material = blockData.getMaterial();
		} else if (data instanceof ItemStack itemStack) {
			material = itemStack.getType();
		}

		if (material == null) {
			return BedrockParticleProfile.getEarthColor();
		}

		final String name = material.name().toUpperCase(Locale.ROOT);
		if (name.contains("LEAVES") || name.contains("GRASS") || name.contains("VINE") || name.contains("PLANT")) {
			return BedrockParticleProfile.getPlantColor();
		}
		if (name.contains("ICE") || name.contains("SNOW")) {
			return BedrockParticleProfile.getIceColor();
		}
		if (name.contains("SAND")) {
			return BedrockParticleProfile.getSandColor();
		}
		if (name.contains("IRON") || name.contains("GOLD") || name.contains("COPPER") || name.contains("METAL")) {
			return BedrockParticleProfile.getMetalColor();
		}
		if (name.contains("WATER")) {
			return BedrockParticleProfile.getWaterColor();
		}
		return BedrockParticleProfile.getEarthColor();
	}

	private static void spawnOriginal(final Player viewer, final ParticleSpawnRequest request) {
		viewer.spawnParticle(request.getParticle(), request.getLocation(), request.getAmount(), request.getOffsetX(),
				request.getOffsetY(), request.getOffsetZ(), request.getExtra(), request.getData());
	}

	private static void reloadPacketListener(final ProjectKorra plugin) {
		shutdown();
		if (plugin == null || !BedrockParticleProfile.isEnabled() || !BedrockParticleProfile.usePacketEventsSafetyNet()) {
			return;
		}
		packetListener = BedrockParticlePacketListener.tryRegister(plugin);
	}
}
