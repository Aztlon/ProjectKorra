package com.projectkorra.projectkorra.chiblocking;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.airbending.Suffocate;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.chiblocking.passive.ChiPassive;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.util.MovementHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Paralyze extends ChiAbility {

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	private Entity target;

	public Paralyze(final LivingEntity caster) {
		this(caster, ChiPassive.findTarget(caster));
	}

	public Paralyze(final LivingEntity caster, final Entity targetentity) {
		super(caster);
		if (!this.bender.canBend(this)) {
			return;
		}
		this.target = targetentity;
		if (!(this.target instanceof LivingEntity)) {
			return;
		}
		this.cooldown = getConfig().getLong("Abilities.Chi.Paralyze.Cooldown");
		this.duration = getConfig().getLong("Abilities.Chi.Paralyze.Duration");
		this.start();
	}

	@Override
	public void progress() {
		if (this.bender.canBend(this)) {
			if (this.target instanceof Player) {
				if (Commands.invincible.contains(this.target.getName())) {
					this.remove();
					return;
				}
			}
			this.paralyze(this.target);
			this.bender.addCooldown(this);
		}
		this.remove();
	}

	private void paralyze(final Entity entity) {
		if (entity instanceof Creature) {
			((Creature) entity).setTarget(null);
		}

		if (entity instanceof Player) {
			if (Suffocate.isChannelingSphere((Player) entity)) {
				Suffocate.remove((Player) entity);
			}
		}
		final MovementHandler mh = new MovementHandler((LivingEntity) entity, CoreAbility.getAbility(Paralyze.class));
		mh.stopWithDuration((long) (this.duration / 1000D * 20), Element.NON.getColor() + "* Paralyzed *");
		entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 2, 0);
	}

	@Override
	public String getName() {
		return "Paralyze";
	}

	@Override
	public Location getLocation() {
		return this.target != null ? this.target.getLocation() : null;
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
		return false;
	}
}
