package com.projectkorra.projectkorra.airbending;

import java.util.ArrayList;
import java.util.Random;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.attribute.Attribute;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirScooter extends AirAbility {

	@Attribute(Attribute.SPEED)
	private double speed;
	private double interval;
	@Attribute(Attribute.RADIUS)
	private double radius;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	@Attribute(Attribute.HEIGHT)
	private double maxHeightFromGround;
	@Attribute("DamageThreshold")
	private double damageTreshold;
	private Block floorblock;
	private Random random;
	private ArrayList<Double> angles;
	private Slime slime;
	private Boolean useslime;
	private double health;
	private double phi = 0;
	private boolean cancelOnSwapSlot;
	private boolean cancelOnClick;
	private boolean cancelOnSneak;

	public AirScooter(final LivingEntity caster) {
		super(caster);

		if (check(caster)) {
			return;
		} else if ((bPlayer != null && !player.isSprinting()) || GeneralMethods.isSolid(caster.getEyeLocation().getBlock()) || isWater(caster.getEyeLocation().getBlock())) {
			return;
		} else if (GeneralMethods.isSolid(caster.getLocation().add(0, -.5, 0).getBlock())) {
			return;
		} else if (this.bender.isOnCooldown(this)) {
			return;
		}

		this.speed = getConfig().getDouble("Abilities.Air.AirScooter.Speed");
		this.interval = getConfig().getDouble("Abilities.Air.AirScooter.Interval");
		this.radius = getConfig().getDouble("Abilities.Air.AirScooter.Radius");
		this.cooldown = getConfig().getLong("Abilities.Air.AirScooter.Cooldown");
		this.duration = getConfig().getLong("Abilities.Air.AirScooter.Duration");
		this.maxHeightFromGround = getConfig().getDouble("Abilities.Air.AirScooter.MaxHeightFromGround");
		this.useslime = getConfig().getBoolean("Abilities.Air.AirScooter.ShowSitting");
		this.damageTreshold = getConfig().getDouble("Abilities.Air.AirScooter.DamageThreshold");
		this.cancelOnSwapSlot = getConfig().getBoolean("Abilities.Air.AirScooter.Cancel.OnSwapSlot");
		this.cancelOnClick = getConfig().getBoolean("Abilities.Air.AirScooter.Cancel.OnClick");
		this.cancelOnSneak = getConfig().getBoolean("Abilities.Air.AirScooter.Cancel.OnSneak");
		this.random = new Random();
		this.angles = new ArrayList<>();
		this.health = caster.getHealth();

		for (int i = 0; i < 5; i++) {
			this.angles.add((double) (60 * i));
		}
		if (caster.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
			this.useslime = false;
		}
		if (this.useslime) {
			this.slime = (Slime) caster.getWorld().spawnEntity(caster.getLocation(), EntityType.SLIME);
			this.slime.setSize(1);
			this.slime.setSilent(true);
			this.slime.setInvulnerable(true);
			this.slime.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, true, false));
			this.slime.addPassenger(caster);
		}

		this.getFloor();
		if (floorblock == null) return;

		this.start();
		if (!isRemoved() && bPlayer != null) {
			this.flightHandler.createInstance(player, this.getName());
			player.setAllowFlight(true);
			player.setFlying(true);

			player.setSprinting(false);
			player.setSneaking(false);
		}
	}

	/**
	 * Checks if caster has an instance already and removes if they do
	 *
	 * @param caster The caster to check
	 * @return true if an instance was removed
	 */
	public static boolean check(final LivingEntity caster) {
		AirScooter scooter = getAbility(caster, AirScooter.class);
		if (scooter != null) {
			scooter.remove();
			return true;
		}
		return false;
	}

	public static void tryRemoveOnClick(final LivingEntity caster) {
		AirScooter scooter = getAbility(caster, AirScooter.class);
		if (scooter != null && scooter.cancelOnClick) {
			scooter.remove();
		}
	}

	/*
	 * Looks for a block under the player and sets "floorBlock" to a block under
	 * the player if it within the maximum height
	 */
	private void getFloor() {
		this.floorblock = null;
		for (int i = 0; i <= this.maxHeightFromGround; i++) {
			final Block block = this.caster.getEyeLocation().getBlock().getRelative(BlockFace.DOWN, i);
			if (GeneralMethods.isSolid(block) || ElementalAbility.isWater(block)) {
				this.floorblock = block;
				return;
			}
		}
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		} else if (this.duration > 0 && System.currentTimeMillis() > this.getStartTime() + this.duration) {
			this.bender.addCooldown(this);
			this.remove();
			return;
		} else if (this.health - this.caster.getHealth() >= this.damageTreshold) {
			this.bender.addCooldown(this);
			this.remove();
			return;
		}

		this.getFloor();
		if (this.floorblock == null) {
			this.remove();
			return;
		}

		if (bender.isSneaking() && this.cancelOnSneak) {
			this.bender.addCooldown(this);
			this.remove();
			return;
		}

		if (this.useslime && (this.slime == null || !this.slime.getPassengers().contains(this.caster))) {
			this.bender.addCooldown(this);
			this.remove();
			return;
		}

		Vector velocity = this.caster.getEyeLocation().getDirection().setY(0).normalize().multiply(this.speed);
		/*
		 * checks the players speed and ends the move if they are going too slow
		 */
		if (System.currentTimeMillis() > this.getStartTime() + this.interval) {
//			if (this.useslime) {
//				if (this.slime.getVelocity().length() < this.speed * 0.3) {
//					this.remove();
//					return;
//				}
//			} else {
//				if (this.caster.getVelocity().length() < this.speed * 0.3) {
//					this.remove();
//					return;
//				}
//			}
			this.spinScooter();
		}
		/*
		 * Checks for how far the ground is away from the player it elevates or
		 * lowers the player based on their distance from the ground.
		 */
		final double distance = this.caster.getLocation().getY() - this.floorblock.getY();
		Math.abs(distance - 2.4); // TODO ?
		if (distance > 2.75) {
			velocity.setY(-.25);
		} else if (distance < 2) {
			velocity.setY(.25);
		} else {
			velocity.setY(0);
		}

		final Vector v = velocity.clone().setY(0);
		final Block b = this.floorblock.getLocation().clone().add(v.multiply(1.2)).getBlock();
		if (!GeneralMethods.isSolid(b) && !ElementalAbility.isWater(b)) {
			velocity.add(new Vector(0, -0.1, 0));
		} else if (GeneralMethods.isSolid(b.getRelative(BlockFace.UP)) || ElementalAbility.isWater(b.getRelative(BlockFace.UP))) {
			velocity.add(new Vector(0, 0.7, 0));
		}

		final Location loc = this.caster.getLocation();
		if (!ElementalAbility.isWater(this.caster.getLocation().add(0, 2, 0).getBlock())) {
			loc.setY(this.floorblock.getY() + 1.5);
		} else {
			return;
		}

		if (bPlayer != null)
			this.player.setSprinting(false);
		this.caster.removePotionEffect(PotionEffectType.SPEED);
		if (this.useslime) {
			GeneralMethods.setVelocity(this, this.slime, velocity);
		} else {
			GeneralMethods.setVelocity(this, this.caster, velocity);
		}

		if (this.random.nextInt(4) == 0) {
			playAirbendingSound(this.caster.getLocation());
		}
	}

	/*
	 * Updates the players flight, also adds the cooldown.
	 */
	@Override
	public void remove() {
		super.remove();
		if (this.slime != null) {
			this.slime.remove();
		}
		if (bPlayer != null)
			this.flightHandler.removeInstance(this.player, this.getName());
		this.bender.addCooldown(this);
	}

	/*
	 * The particles used for AirScooter phi = how many rings of particles the
	 * sphere has. theta = how dense the rings are. r = Radius of the sphere
	 */
	private void spinScooter() {
		final Location origin = this.caster.getLocation();
		final Location origin2 = this.caster.getLocation();
		this.phi += Math.PI / 10 * 4;
		for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / 10) {
			final double r = 0.6;
			final double x = r * Math.cos(theta) * Math.sin(this.phi);
			final double y = r * Math.cos(this.phi);
			final double z = r * Math.sin(theta) * Math.sin(this.phi);
			origin.add(x, y, z);
			playAirbendingParticles(origin, 1, 0F, 0F, 0F);
			origin.subtract(x, y, z);
		}
		for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / 10) {
			final double r = 0.6;
			final double x = r * Math.cos(theta) * Math.sin(this.phi);
			final double y = r * Math.cos(this.phi);
			final double z = r * Math.sin(theta) * Math.sin(this.phi);
			origin2.subtract(x, y, z);
			playAirbendingParticles(origin2, 1, 0F, 0F, 0F);
			origin2.add(x, y, z);
		}
	}

	@Override
	public String getName() {
		return "AirScooter";
	}

	@Override
	public Location getLocation() {
		return this.caster != null ? this.caster.getLocation() : null;
	}

	@Override
	public long getCooldown() {
		return this.cooldown;
	}

	@Override
	public boolean isSneakAbility() {
		return false;
	}

	@Override
	public boolean isHarmlessAbility() {
		return true;
	}

	@Override
	public double getCollisionRadius() {
		return this.getRadius();
	}
}
