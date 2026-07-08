package com.projectkorra.projectkorra.firebending.combo;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.ability.util.ComboUtil;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.phasing.PhasedSoundManager;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FireKick extends FireAbility implements ComboAbility {

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.RANGE)
	private double range;
	private Location location;
	private Location destination;
	private ArrayList<LivingEntity> affectedEntities;
	private ArrayList<BukkitRunnable> tasks;

	public FireKick(final LivingEntity caster) {
		super(caster);

		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			return;
		}

		this.affectedEntities = new ArrayList<>();
		this.tasks = new ArrayList<>();

		this.damage = applyModifiersDamage(getConfig().getDouble("Abilities.Fire.FireKick.Damage"));
		this.range = applyModifiersRange(getConfig().getDouble("Abilities.Fire.FireKick.Range"));
		this.cooldown = applyModifiersCooldown(getConfig().getLong("Abilities.Fire.FireKick.Cooldown"));
		this.speed = getConfig().getLong("Abilities.Fire.FireKick.Speed");

		if (this.bender.isAvatarState()) {
			this.cooldown = 0;
			this.damage = getConfig().getDouble("Abilities.Avatar.AvatarState.Fire.FireKick.Damage");
			this.range = getConfig().getDouble("Abilities.Avatar.AvatarState.Fire.FireKick.Range");
		}

		this.start();
	}

	@Override
	public String getName() {
		return "FireKick";
	}

	@Override
	public void progress() {
		for (int i = 0; i < this.tasks.size(); i++) {
			final BukkitRunnable br = this.tasks.get(i);
			if (br instanceof FireComboStream fs) {
				if (fs.isCancelled()) {
					this.tasks.remove(fs);
				}
			}
		}

		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		}

		if (this.destination == null) {
			if (this.bender.isOnCooldown("FireKick") && !this.bender.isAvatarState()) {
				this.remove();
				return;
			}

			this.bender.addCooldown("FireKick", this.cooldown);
			final Vector eyeDir = this.caster.getEyeLocation().getDirection().normalize().multiply(this.range);
			this.destination = this.caster.getEyeLocation().add(eyeDir);

			PhasedSoundManager.playSound(this, this.caster.getLocation(), Sound.ENTITY_HORSE_JUMP, 0.5f, 0f);
			PhasedSoundManager.playSound(this, this.caster.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.5f, 1f);
			for (int i = -30; i <= 30; i += 5) {
				this.location = this.caster.getLocation().clone().add(0, 0.1, 0); // to account for dirt paths and other low blocks
				Vector vec = GeneralMethods.getDirection(this.location, this.destination.clone());
				vec = GeneralMethods.rotateXZ(vec, i);

				final FireComboStream fs = new FireComboStream(this.caster, this, vec, this.location, this.range, this.speed);
				fs.setSpread(0.2F);
				fs.setDensity(5);
				fs.setUseNewParticles(true);
				fs.setDamage(this.damage);
				if (this.tasks.size() % 3 != 0) {
					fs.setCollides(false);
				}
				fs.runTaskTimer(ProjectKorra.plugin, 0, 1L);
				this.tasks.add(fs);
				PhasedSoundManager.playSound(this, this.caster.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 0.5f, 1f);
			}
		} else if (this.tasks.isEmpty()) {
			this.remove();
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
		return this.location;
	}

	@NotNull
	@Override
	public Object createNewComboInstance(final Player player) {
		return new FireKick(player);
	}

	@Override
	public ArrayList<AbilityInformation> getCombination() {
		return ComboUtil.generateCombinationFromList(this, ConfigManager.defaultConfig.get().getStringList("Abilities.Fire.FireKick.Combination"));
	}
}
