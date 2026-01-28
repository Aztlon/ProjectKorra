package com.projectkorra.projectkorra.earthbending;

import java.util.Random;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.ability.AbstractSkill;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.ParticleEffect;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Catapult extends EarthAbility {

	private double stageTimeMult;
	@Attribute(Attribute.COOLDOWN)
	@Setter private long cooldown;
	@Setter private Location origin;
	@Setter private Location target;

	private int stage;
	private long stageStart;
	private boolean charging;
	private boolean activationHandled;
	private Vector up;
	private double angle;
	private boolean cancelWithAngle;
	private BlockData bentBlockData;

	public Catapult(final LivingEntity caster, final boolean sneak) {
		super(caster);
		if (sneak && player != null && AbstractSkill.isLocked("ChargedCatapult", player)) return;
		this.setFields();
		final Block b = caster.getLocation().getBlock().getRelative(BlockFace.DOWN, 1);
		if (!(isEarth(b) || isSand(b) || isMetal(b))) {
			return;
		}

		this.bentBlockData = b.getBlockData();

		if (!this.bender.canBend(this)) {
			return;
		}

		if (this.bender.isAvatarState()) {
			this.cooldown = getConfig().getLong("Abilities.Avatar.AvatarState.Earth.Catapult.Cooldown");
		}

		this.charging = sneak;
		this.start();
	}

	private void setFields() {
		this.stageTimeMult = getConfig().getDouble("Abilities.Earth.Catapult.StageTimeMult");
		this.cooldown = getConfig().getLong("Abilities.Earth.Catapult.Cooldown");
		this.angle = Math.toRadians(getConfig().getDouble("Abilities.Earth.Catapult.Angle"));
		this.cancelWithAngle = getConfig().getBoolean("Abilities.Earth.Catapult.CancelWithAngle");
		this.activationHandled = false;
		this.stage = 1;
		this.stageStart = System.currentTimeMillis();
		this.up = new Vector(0, 1, 0);
	}

	private void moveEarth(final Vector apply, final Vector direction) {
		for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(this.origin, 2)) {
			if (entity.getEntityId() != this.caster.getEntityId()) {
				if (RegionProtection.isRegionProtected(this, entity.getLocation()) || ((entity instanceof Player) && Commands.invincible.contains(entity.getName()))) {
					continue;
				}
				GeneralMethods.setVelocity(this, entity, apply);
			}
		}
		this.moveEarth(this.origin.clone().subtract(direction), direction, 3, false);
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		}

		final Block b = this.caster.getLocation().getBlock().getRelative(BlockFace.DOWN, 1);
		if (!(isEarth(b) || isSand(b) || isMetal(b))) {
			this.remove();
			return;
		}

		this.bentBlockData = b.getBlockData();

		if (this.charging) {
			if (this.stage == 4 || !this.bender.isSneaking()) {
				this.charging = false;
			} else {
				if ((System.currentTimeMillis() - this.stageStart) >= ((Math.max(0, this.stageTimeMult * (this.stage - 1))) * 1000)) {
					this.stage++;
					this.stageStart = System.currentTimeMillis();
					final Random random = new Random();
					ParticleEffect.BLOCK_DUST.display(this.caster.getLocation(), 15, random.nextFloat(), random.nextFloat(), random.nextFloat(), this.bentBlockData);
					ParticleEffect.BLOCK_DUST.display(this.caster.getLocation().add(0, 0.5, 0), 10, random.nextFloat(), random.nextFloat(), random.nextFloat(), this.bentBlockData);
					this.caster.getWorld().playEffect(this.caster.getLocation(), Effect.GHAST_SHOOT, 0, 10);
				}
			}
			return;
		}

		Vector direction = null;
		if (!this.activationHandled) {
			this.origin = this.caster.getLocation().clone();
			direction = this.caster.getEyeLocation().getDirection().clone().normalize();

			if (!this.bender.canBendIgnoreBinds(this)) {
				this.activationHandled = true;
				this.remove();
				return;
			}
			this.activationHandled = true;
			this.bender.addCooldown(this);
		}

		if (this.up.angle(this.caster.getEyeLocation().getDirection()) > this.angle) {
			if (this.cancelWithAngle) {
				this.remove();
				return;
			}
			direction = this.up;
		}

		this.target = this.origin.clone().add(direction.clone().normalize().multiply(this.stage + 0.5));
		final Vector apply = this.target.clone().toVector().subtract(this.origin.clone().toVector());
		GeneralMethods.setVelocity(this, this.caster, apply);
		if (!isBendableEarthTempBlock(origin.getBlock().getRelative(BlockFace.DOWN))) {
			this.moveEarth(apply, direction);
		}
		this.remove();
	}

	@Override
	public String getName() {
		return "Catapult";
	}

	@Override
	public Location getLocation() {
		if (this.caster != null) {
			return this.caster.getLocation();
		}
		return null;
	}

	@Override
	public long getCooldown() {
		return this.cooldown;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}
}
