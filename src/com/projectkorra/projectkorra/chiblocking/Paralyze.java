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
	private LivingEntity target;

	public Paralyze(final LivingEntity caster) {
		this(caster, ChiPassive.findTarget(caster));
	}

	public Paralyze(final LivingEntity caster, final Entity targetentity) {
		super(caster);
		if (!this.bender.canBend(this)) {
			return;
		}
		if (!(targetentity instanceof LivingEntity le)) {
			return;
		}
		this.target = le;
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

	private void paralyze(final LivingEntity entity) {
		if (entity instanceof Creature creature) {
			creature.setTarget(null);
		}
		if (Suffocate.isChannelingSphere(entity)) {
			Suffocate.remove(entity);
		}
		final MovementHandler mh = new MovementHandler(entity, CoreAbility.getAbility(Paralyze.class));
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
