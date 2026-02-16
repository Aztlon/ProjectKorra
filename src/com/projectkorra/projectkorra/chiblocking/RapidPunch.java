package com.projectkorra.projectkorra.chiblocking;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.airbending.Suffocate;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.chiblocking.passive.ChiPassive;
import com.projectkorra.projectkorra.util.DamageHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RapidPunch extends ChiAbility {

	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute("Hits")
	private int punches;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private int numPunches;
	private long interval;
	private final long last = 0;
	private Entity target;

	public RapidPunch(final LivingEntity caster) {
		this(caster, ChiPassive.findTarget(caster));
	}

	public RapidPunch(final LivingEntity caster, final Entity targetentity) {
		super(caster);
		if (!this.bender.canBend(this)) {
			return;
		}

		this.damage = getConfig().getDouble("Abilities.Chi.RapidPunch.Damage");
		this.punches = getConfig().getInt("Abilities.Chi.RapidPunch.Punches");
		this.cooldown = getConfig().getLong("Abilities.Chi.RapidPunch.Cooldown");
		this.interval = getConfig().getLong("Abilities.Chi.RapidPunch.Interval");
		this.target = targetentity;
		this.start();
		if (!isRemoved()) {
			this.bender.addCooldown(this);
		}
	}

	@Override
	public void progress() {
		if (this.numPunches >= this.punches || this.target == null || !(this.target instanceof LivingEntity lt)) {
			this.remove();
			return;
		}

		if (System.currentTimeMillis() >= this.last + this.interval) {
			DamageHandler.damageEntity(this.target, this.damage, this);

			if (this.target instanceof Player) {
				if (ChiPassive.willChiBlock(this.caster, (Player) this.target)) {
					ChiPassive.blockChi((Player) this.target);
				}
				if (Suffocate.isChannelingSphere((Player) this.target)) {
					Suffocate.remove((Player) this.target);
				}
			}

			lt.setNoDamageTicks(0);
			this.numPunches++;
		}
	}

	@Override
	public String getName() {
		return "RapidPunch";
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
