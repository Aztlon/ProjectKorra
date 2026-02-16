package com.projectkorra.projectkorra.chiblocking;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.chiblocking.passive.ChiPassive;
import com.projectkorra.projectkorra.util.DamageHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwiftKick extends ChiAbility {

	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute("ChiBlockChance")
	private int blockChance;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private Entity target;

	public SwiftKick(final LivingEntity caster) {
		this(caster, ChiPassive.findTarget(caster));
	}

	public SwiftKick(final LivingEntity caster, final Entity targetentity) {
		super(caster);
		if (!this.bender.canBend(this)) {
			return;
		}
		this.damage = getConfig().getDouble("Abilities.Chi.SwiftKick.Damage");
		this.blockChance = getConfig().getInt("Abilities.Chi.SwiftKick.ChiBlockChance");
		this.cooldown = getConfig().getInt("Abilities.Chi.SwiftKick.Cooldown");
		this.target = targetentity;
		this.start();
	}

	@Override
	public void progress() {
		if (this.target == null) {
			this.remove();
			return;
		}
		if (!ElementalAbility.isAir(this.caster.getLocation().subtract(0, 0.5, 0).getBlock().getType())) {
			this.remove();
			return;
		}
		DamageHandler.damageEntity(this.target, this.damage, this);
		if (this.target instanceof Player p && ChiPassive.willChiBlock(this.caster, p)) {
			ChiPassive.blockChi(p);
		}
		this.bender.addCooldown(this);
		this.remove();
	}

	@Override
	public String getName() {
		return "SwiftKick";
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
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}
}
