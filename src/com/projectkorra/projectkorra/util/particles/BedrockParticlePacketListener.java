package com.projectkorra.projectkorra.util.particles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleType;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.projectkorra.projectkorra.ProjectKorra;

final class BedrockParticlePacketListener extends PacketListenerAbstract {

	private PacketListenerCommon registeredListener;

	private BedrockParticlePacketListener() {
		super(PacketListenerPriority.NORMAL);
	}

	static BedrockParticlePacketListener tryRegister(final ProjectKorra plugin) {
		if (!isPacketEventsEnabled()) {
			return null;
		}

		try {
			final BedrockParticlePacketListener listener = new BedrockParticlePacketListener();
			listener.registeredListener = PacketEvents.getAPI().getEventManager().registerListener(listener);
			ProjectKorra.log.info("Registered Bedrock particle PacketEvents safety net");
			return listener;
		} catch (final LinkageError | RuntimeException ex) {
			ProjectKorra.log.warning("Unable to register Bedrock particle PacketEvents safety net: " + ex.getMessage());
			return null;
		}
	}

	void unregister() {
		if (this.registeredListener == null) {
			return;
		}
		try {
			PacketEvents.getAPI().getEventManager().unregisterListener(this.registeredListener);
		} catch (final LinkageError | RuntimeException ignored) {
		} finally {
			this.registeredListener = null;
		}
	}

	@Override
	public void onPacketSend(final PacketSendEvent event) {
		if (event.getPacketType() != PacketType.Play.Server.PARTICLE) {
			return;
		}

		final Player player = event.getPlayer();
		if (!BedrockPlayerDetector.isBedrockPlayer(player)) {
			return;
		}

		final WrapperPlayServerParticle packet = new WrapperPlayServerParticle(event);
		final ParticleType<?> particleType = packet.getParticle().getType();
		if (particleType == ParticleTypes.DUST) {
			if (packet.getParticleCount() > BedrockParticleProfile.getMaxCount()) {
				packet.setParticleCount(BedrockParticleProfile.capAmount(packet.getParticleCount()));
				event.markForReEncode(true);
			}
			return;
		}
		if (!isUnsafe(particleType)) {
			return;
		}

		packet.setParticle(new Particle<>(ParticleTypes.WHITE_SMOKE));
		packet.setParticleCount(BedrockParticleProfile.capAmount(packet.getParticleCount()));
		packet.setMaxSpeed(Math.min(packet.getMaxSpeed(), 0.02F));
		event.markForReEncode(true);

		if (BedrockParticleProfile.isDebug()) {
			final Vector3d position = packet.getPosition();
			ProjectKorra.log.info("PacketEvents replaced unsafe particle packet for Bedrock player " + player.getName()
					+ " at " + describeLocation(player.getWorld(), position));
		}
	}

	private static boolean isUnsafe(final ParticleType<?> type) {
		return type == ParticleTypes.BLOCK
				|| type == ParticleTypes.BLOCK_CRUMBLE
				|| type == ParticleTypes.BLOCK_MARKER
				|| type == ParticleTypes.FALLING_DUST
				|| type == ParticleTypes.DUST_PILLAR
				|| type == ParticleTypes.ITEM
				|| type == ParticleTypes.CLOUD
				|| type == ParticleTypes.ASH
				|| type == ParticleTypes.WHITE_ASH
				|| type == ParticleTypes.CAMPFIRE_COSY_SMOKE
				|| type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
				|| type == ParticleTypes.LARGE_SMOKE
				|| type == ParticleTypes.SMOKE
				|| type == ParticleTypes.DUST_COLOR_TRANSITION;
	}

	private static boolean isPacketEventsEnabled() {
		return Bukkit.getPluginManager().isPluginEnabled("packetevents")
				|| Bukkit.getPluginManager().isPluginEnabled("PacketEvents");
	}

	private static String describeLocation(final World world, final Vector3d position) {
		if (world == null || position == null) {
			return "unknown";
		}
		final Location location = new Location(world, position.x, position.y, position.z);
		return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
	}
}
