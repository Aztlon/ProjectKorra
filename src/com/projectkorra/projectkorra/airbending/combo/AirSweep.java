package com.projectkorra.projectkorra.airbending.combo;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.ability.util.ComboUtil;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.firebending.combo.FireComboStream;
import com.projectkorra.projectkorra.object.HorizontalVelocityTracker;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirSweep extends AirAbility implements ComboAbility {

	private int progressCounter;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.KNOCKBACK)
	private double knockback;
	private Location origin;
	private Location currentLoc;
	private Location destination;
	private Vector direction;
	private ArrayList<Entity> affectedEntities;
	private ArrayList<BukkitRunnable> tasks;
	private double radius;

	public AirSweep(final LivingEntity caster) {
		super(caster);

		this.affectedEntities = new ArrayList<>();
		this.tasks = new ArrayList<>();

		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			return;
		}

		if (this.bender.isOnCooldown(this)) {
			return;
		}

		this.damage = getConfig().getDouble("Abilities.Air.AirSweep.Damage");
		this.range = getConfig().getDouble("Abilities.Air.AirSweep.Range");
		this.speed = getConfig().getDouble("Abilities.Air.AirSweep.Speed");
		this.knockback = getConfig().getDouble("Abilities.Air.AirSweep.Knockback");
		this.cooldown = getConfig().getLong("Abilities.Air.AirSweep.Cooldown");
		this.radius = getConfig().getDouble("Abilities.Air.AirSweep.Radius");

		if (this.bender.isAvatarState()) {
			this.cooldown = 0;
			this.damage = getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirSweep.Damage");
			this.range = getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirSweep.Range");
			this.knockback = getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirSweep.Knockback");
		}

		this.start();
		if (!isRemoved())
			this.bender.addCooldown(this);
	}

	@Override
	public String getName() {
		return "AirSweep";
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public void handleCollision(final Collision collision) {
		if (collision.isRemovingFirst()) {
			final ArrayList<BukkitRunnable> newTasks = new ArrayList<>();
			final double collisionDistanceSquared = Math.pow(this.getCollisionRadius() + collision.getAbilitySecond().getCollisionRadius(), 2);
			// Remove all of the streams that are by this specific ourLocation.
			// Don't just do a single stream at a time or this algorithm becomes O(n^2) with Collision's detection algorithm.
			for (final BukkitRunnable task : this.getTasks()) {
				if (task instanceof FireComboStream stream) {
					if (stream.getLocation().distanceSquared(collision.getLocationSecond()) > collisionDistanceSquared) {
						newTasks.add(stream);
					} else {
						stream.cancel();
					}
				} else {
					newTasks.add(task);
				}
			}
			this.setTasks(newTasks);
		}
	}

	@Override
	public List<Location> getLocations() {
		final ArrayList<Location> locations = new ArrayList<>();
		for (final BukkitRunnable task : this.getTasks()) {
			if (task instanceof FireComboStream stream) {
				locations.add(stream.getLocation());
			}
		}
		return locations;
	}

	@Override
	public void progress() {
		this.progressCounter++;
		if (this.caster.isDead()) {
			this.remove();
			return;
		} else if (this.currentLoc != null && RegionProtection.isRegionProtected(this, this.currentLoc)) {
			this.remove();
			return;
		}

		if (this.origin == null) {
			this.direction = this.caster.getEyeLocation().getDirection().normalize();
			this.origin = GeneralMethods.getMainHandLocation(player).add(this.direction.clone().multiply(10));
		}
		if (this.progressCounter < 8) {
			return;
		}
		if (this.destination == null) {
			this.destination = GeneralMethods.getMainHandLocation(player).add(GeneralMethods.getMainHandLocation(player).getDirection().normalize().multiply(10));
			final Vector origToDest = GeneralMethods.getDirection(this.origin, this.destination);
			final Location hand = GeneralMethods.getMainHandLocation(player);
			for (double i = 0; i < 30; i++) {
				final Location endLoc = this.origin.clone().add(origToDest.clone().multiply(i / 30));
				if (GeneralMethods.locationEqualsIgnoreDirection(hand, endLoc)) {
					continue;
				}
				final Vector vec = GeneralMethods.getDirection(hand, endLoc);

				String hexVal = particleColor(player);
				final FireComboStream fs = new FireComboStream(this.caster, this, vec, hand, this.range, this.speed);
				fs.setDensity(1);
				fs.setAir(true);
				fs.setXSpread(Integer.valueOf(hexVal.substring(0, 2), 16) / 255D);
				fs.setYSpread(Integer.valueOf(hexVal.substring(2, 4), 16) / 255D);
				fs.setZSpread(Integer.valueOf(hexVal.substring(4, 6), 16) / 255D);
				fs.setParticleAmount(1);
				fs.setCollides(false);
				fs.runTaskTimer(ProjectKorra.plugin, (long) (i / 2.5), 1L);
				this.tasks.add(fs);
			}
		}
		this.manageAirVectors();
	}

	public void manageAirVectors() {
		for (int i = 0; i < this.tasks.size(); i++) {
			if (this.tasks.get(i).isCancelled()) {
				this.tasks.remove(i);
				i--;
			}
		}
		if (this.tasks.isEmpty()) {
			this.remove();
			return;
		}
		for (int i = 0; i < this.tasks.size(); i++) {
			final FireComboStream fstream = (FireComboStream) this.tasks.get(i);
			final Location loc = fstream.getLocation();

			if (RegionProtection.isRegionProtected(this, loc)) {
				fstream.remove();
				return;
			}

			if (!this.isTransparent(loc.getBlock())) {
				if (!this.isTransparent(loc.clone().add(0, 0.2, 0).getBlock())) {
					fstream.remove();
					return;
				}
			}
			if (i % 3 == 0) {
				for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(loc, radius)) {
					if (RegionProtection.isRegionProtected(this, entity.getLocation())) {
						this.remove();
						return;
					}
					if (!entity.equals(this.caster) && !(entity instanceof Player && Commands.invincible.contains(entity.getName()))) {
						if (this.knockback != 0) {
							final Vector force = fstream.getLocation().getDirection();
							GeneralMethods.setVelocity(this, entity, force.clone().multiply(this.knockback));
							new HorizontalVelocityTracker(entity, this.caster, 200L, this);
							entity.setFallDistance(0);
						}
						if (!this.affectedEntities.contains(entity)) {
							this.affectedEntities.add(entity);
							if (this.damage != 0) {
								if (entity instanceof LivingEntity) {
									DamageHandler.damageEntity(entity, this.damage, this);
								}
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void remove() {
		super.remove();
		for (final BukkitRunnable task : this.tasks) {
			task.cancel();
		}
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public long getCooldown() {
		return this.cooldown;
	}

	@Override
	public Location getLocation() {
		return this.origin;
	}

	@NotNull
	@Override
	public Object createNewComboInstance(final Player player) {
		return new AirSweep(player);
	}

	@Override
	public ArrayList<AbilityInformation> getCombination() {
		return ComboUtil.generateCombinationFromList(this, ConfigManager.defaultConfig.get().getStringList("Abilities.Air.AirSweep.Combination"));
	}
}
