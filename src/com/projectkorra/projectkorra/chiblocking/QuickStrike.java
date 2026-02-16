package com.projectkorra.projectkorra.chiblocking;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.chiblocking.passive.ChiPassive;
import com.projectkorra.projectkorra.util.DamageHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuickStrike extends ChiAbility {

	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute("ChiBlockChance")
	private int blockChance;
	private Entity target;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;

	public QuickStrike(final LivingEntity caster) {
		this(caster, ChiPassive.findTarget(caster));
	}

	public QuickStrike(final LivingEntity caster, final Entity targetentity) {
		super(caster);
		if (!this.bender.canBend(this)) {
			return;
		}
		this.damage = getConfig().getDouble("Abilities.Chi.QuickStrike.Damage");
		this.cooldown = getConfig().getLong("Abilities.Chi.QuickStrike.Cooldown");
		this.blockChance = getConfig().getInt("Abilities.Chi.QuickStrike.ChiBlockChance");
		this.target = targetentity;
		if (this.target == null) {
			return;
		}
		this.start();
	}

	@Override
	public void progress() {
		if (this.bender.isOnCooldown(this)) {
			this.remove();
			return;
		}

		if (this.target == null) {
			this.remove();
			return;
		}

		this.bender.addCooldown(this);
		DamageHandler.damageEntity(this.target, this.damage, this);

		if (this.target instanceof Player && ChiPassive.willChiBlock(this.caster, (Player) this.target)) {
			ChiPassive.blockChi((Player) this.target);
		}

		this.remove();
	}

	@Override
	public String getName() {
		return "QuickStrike";
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
		return false;
	}
}
