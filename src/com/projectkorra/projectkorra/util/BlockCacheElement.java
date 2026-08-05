package com.projectkorra.projectkorra.util;

import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class BlockCacheElement {

	private LivingEntity caster;
	private Block block;
	private CoreAbility ability;
	private boolean allowed;
	private long time;

	public BlockCacheElement(final LivingEntity caster, final Block block, final CoreAbility ability, final boolean allowed, final long time) {
		this.caster = caster;
		this.block = block;
		this.ability = ability;
		this.allowed = allowed;
		this.time = time;
	}

	public CoreAbility getAbility() {
		return this.ability;
	}

	public Block getBlock() {
		return this.block;
	}

	public LivingEntity getCaster() {
		return this.caster;
	}

	public long getTime() {
		return this.time;
	}

	public boolean isAllowed() {
		return this.allowed;
	}

	public void setAbility(final CoreAbility ability) {
		this.ability = ability;
	}

	public void setAllowed(final boolean allowed) {
		this.allowed = allowed;
	}

	public void setBlock(final Block block) {
		this.block = block;
	}

	public void setCaster(final Player caster) {
		this.caster = caster;
	}

	public void setTime(final long time) {
		this.time = time;
	}

}
