package com.projectkorra.projectkorra.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

public enum ParticleEffect {
	
	ASH (Particle.ASH),
	
	/**
	 * Applicable data: {@link BlockData}
	 */
	BLOCK (Particle.BLOCK),
	BLOCK_CRACK (Particle.BLOCK_CRUMBLE),
	BLOCK_CRUMBLE (Particle.BLOCK_CRUMBLE),
	BLOCK_MARKER (Particle.BLOCK_MARKER),
	/**
	 * Applicable data: {@link BlockData}
	 */
	BLOCK_DUST (Particle.BLOCK),
	BUBBLE_COLUMN_UP (Particle.BUBBLE_COLUMN_UP),
	BUBBLE_POP (Particle.BUBBLE_POP),
	CAMPFIRE_COSY_SMOKE (Particle.CAMPFIRE_COSY_SMOKE),
	CAMPFIRE_SIGNAL_SMOKE (Particle.CAMPFIRE_SIGNAL_SMOKE),
	CHERRY_LEAVES (Particle.CHERRY_LEAVES),
	CLOUD (Particle.CLOUD),
	COMPOSTER (Particle.COMPOSTER),
	CRIMSON_SPORE (Particle.CRIMSON_SPORE),
	CRIT (Particle.CRIT),
	CRIT_MAGIC (Particle.ENCHANTED_HIT), MAGIC_CRIT (Particle.ENCHANTED_HIT), ENCHANTED_HIT (Particle.ENCHANTED_HIT),
	CURRENT_DOWN (Particle.CURRENT_DOWN),
	DAMAGE_INDICATOR (Particle.DAMAGE_INDICATOR),
	DOLPHIN (Particle.DOLPHIN),
	DRAGON_BREATH (Particle.DRAGON_BREATH),
	DRIPPING_DRIPSTONE_LAVA (Particle.DRIPPING_DRIPSTONE_LAVA),
	DRIPPING_DRIPSTONE_WATER (Particle.DRIPPING_DRIPSTONE_WATER),
	DRIP_LAVA (Particle.DRIPPING_LAVA),
	DRIP_WATER (Particle.DRIPPING_WATER),
	DRIPPING_HONEY (Particle.DRIPPING_HONEY),
	DRIPPING_OBSIDIAN_TEAR (Particle.DRIPPING_OBSIDIAN_TEAR),
	DUST (Particle.DUST),
	DUST_COLOR_TRANSITION (Particle.DUST_COLOR_TRANSITION),
	DUST_PILLAR (Particle.DUST_PILLAR),
	DUST_PLUME (Particle.DUST_PLUME),
	EGG_CRACK (Particle.EGG_CRACK),
	ELECTRIC_SPARK(Particle.ELECTRIC_SPARK),
	ENCHANTMENT_TABLE (Particle.ENCHANT), ENCHANT (Particle.ENCHANT),
	END_ROD (Particle.END_ROD),
	EXPLOSION_HUGE (Particle.EXPLOSION_EMITTER), HUGE_EXPLOSION (Particle.EXPLOSION_EMITTER), EXPLOSION_EMITTER (Particle.EXPLOSION_EMITTER),
	EXPLOSION_LARGE (Particle.EXPLOSION), LARGE_EXPLODE (Particle.EXPLOSION),
	EXPLOSION_NORMAL (Particle.POOF), EXPLODE (Particle.POOF), POOF (Particle.POOF),
	FALLING_DRIPSTONE_LAVA (Particle.FALLING_DRIPSTONE_LAVA),
	FALLING_DRIPSTONE_WATER (Particle.FALLING_DRIPSTONE_WATER),
	/**
	 * Applicable data: {@link BlockData}
	 */
	FALLING_DUST (Particle.FALLING_DUST),
	FALLING_HONEY (Particle.FALLING_HONEY),
	FALLING_LAVA (Particle.FALLING_LAVA),
	FALLING_NECTAR (Particle.FALLING_NECTAR),
	FALLING_OBSIDIAN_TEAR (Particle.FALLING_OBSIDIAN_TEAR),
	FALLING_SPORE_BLOSSOM (Particle.FALLING_SPORE_BLOSSOM),
	FALLING_WATER (Particle.FALLING_WATER),
	FIREWORKS_SPARK (Particle.FIREWORK), FIREWORK (Particle.FIREWORK),
	FLAME (Particle.FLAME),
	FLASH (Particle.FLASH),
	GLOW (Particle.GLOW),
	GLOW_SQUID_INK (Particle.GLOW_SQUID_INK),
	GUST (Particle.GUST),
	GUST_EMITTER_LARGE (Particle.GUST_EMITTER_LARGE),
	GUST_EMITTER_SMALL (Particle.GUST_EMITTER_SMALL),
	HEART (Particle.HEART),
	/**
	 * Applicable data: {@link ItemStack}
	 */
	ITEM_CRACK (Particle.ITEM), ITEM (Particle.ITEM),
	ITEM_COBWEB (Particle.ITEM_COBWEB),
	LANDING_HONEY (Particle.LANDING_HONEY),
	LANDING_LAVA (Particle.LANDING_LAVA),
	LANDING_OBSIDIAN_TEAR (Particle.LANDING_OBSIDIAN_TEAR),
	LAVA (Particle.LAVA),
	MOB_APPEARANCE (Particle.ELDER_GUARDIAN), ELDER_GUARDIAN (Particle.ELDER_GUARDIAN),
	NAUTILUS (Particle.NAUTILUS),
	NOTE (Particle.NOTE),
	OMINOUS_SPAWNING (Particle.OMINOUS_SPAWNING),
	PALE_OAK_LEAVES (Particle.PALE_OAK_LEAVES),
	PORTAL (Particle.PORTAL),
	
	/**
	 * Applicable data: {@link DustOptions}
	 */
	REDSTONE (Particle.DUST), RED_DUST (Particle.DUST),
	REVERSE_PORTAL (Particle.REVERSE_PORTAL),
	SCRAPE (Particle.SCRAPE),
	/**
	 * Applicable data: {@link Float}
	 */
	SCULK_CHARGE (Particle.SCULK_CHARGE),
	SCULK_CHARGE_POP (Particle.SCULK_CHARGE_POP),
	SCULK_SOUL (Particle.SCULK_SOUL),
	/**
	 * Applicable data: {@link Integer}
	 */
	SHRIEK (Particle.SHRIEK),
	SLIME (Particle.ITEM_SLIME), ITEM_SLIME (Particle.ITEM_SLIME),
	SMALL_FLAME (Particle.SMALL_FLAME),
	SMALL_GUST (Particle.SMALL_GUST),
	SMOKE_NORMAL (Particle.SMOKE), SMOKE (Particle.SMOKE),
	SMOKE_LARGE (Particle.LARGE_SMOKE), LARGE_SMOKE (Particle.LARGE_SMOKE),
	SNEEZE (Particle.SNEEZE),
	SNOWBALL (Particle.ITEM_SNOWBALL), SNOWBALL_PROOF (Particle.ITEM_SNOWBALL), SNOW_SHOVEL (Particle.ITEM_SNOWBALL), ITEM_SNOWBALL (Particle.ITEM_SNOWBALL),
	SNOWFLAKE (Particle.SNOWFLAKE),
	SONIC_BOOM (Particle.SONIC_BOOM),
	SOUL (Particle.SOUL),
	SOUL_FIRE_FLAME (Particle.SOUL_FIRE_FLAME),
	/**
	 * Applicable data: {@link org.bukkit.Color}
	 */
	SPELL (Particle.EFFECT), EFFECT (Particle.EFFECT),
	/**
	 * Applicable data: {@link org.bukkit.Color}
	 */
	SPELL_INSTANT (Particle.INSTANT_EFFECT), INSTANT_SPELL (Particle.INSTANT_EFFECT), INSTANT_EFFECT (Particle.INSTANT_EFFECT),
	/**
	 * Applicable data: {@link org.bukkit.Color}
	 */
	SPELL_MOB (Particle.ENTITY_EFFECT), MOB_SPELL (Particle.ENTITY_EFFECT),
	/**
	 * Applicable data: {@link org.bukkit.Color}
	 */
	SPELL_MOB_AMBIENT (Particle.ENTITY_EFFECT), MOB_SPELL_AMBIENT (Particle.ENTITY_EFFECT), ENTITY_EFFECT (Particle.ENTITY_EFFECT),
	SPELL_WITCH (Particle.WITCH), WITCH_SPELL (Particle.WITCH), WITCH (Particle.WITCH),
	SPIT (Particle.SPIT),
	SPORE_BLOSSOM_AIR (Particle.SPORE_BLOSSOM_AIR),
	SQUID_INK (Particle.SQUID_INK),
	SUSPENDED (Particle.UNDERWATER), SUSPEND (Particle.UNDERWATER), UNDERWATER (Particle.UNDERWATER),
	SUSPENDED_DEPTH (Particle.UNDERWATER), DEPTH_SUSPEND (Particle.UNDERWATER),
	SWEEP_ATTACK (Particle.SWEEP_ATTACK),
	TOTEM (Particle.TOTEM_OF_UNDYING), TOTEM_UNDYING (Particle.TOTEM_OF_UNDYING),
	TOWN_AURA (Particle.MYCELIUM), MYCELIUM (Particle.MYCELIUM),
	/**
	 * Applicable data: {@link org.bukkit.Particle.Trail}
	 */
	TRAIL (Particle.TRAIL),
	TRIAL_OMEN (Particle.TRIAL_OMEN),
	TRIAL_SPAWNER_DETECTION (Particle.TRIAL_SPAWNER_DETECTION),
	TRIAL_SPAWNER_DETECTION_OMINOUS (Particle.TRIAL_SPAWNER_DETECTION_OMINOUS),
	VAULT_CONNECTION (Particle.VAULT_CONNECTION),
	/**
	 * Applicable data: {@link org.bukkit.Vibration}
	 */
	VIBRATION (Particle.VIBRATION),
	VILLAGER_ANGRY (Particle.ANGRY_VILLAGER), ANGRY_VILLAGER (Particle.ANGRY_VILLAGER),
	VILLAGER_HAPPY (Particle.HAPPY_VILLAGER), HAPPY_VILLAGER (Particle.HAPPY_VILLAGER),
	WARPED_SPORE (Particle.WARPED_SPORE),
	WATER_BUBBLE (Particle.BUBBLE), BUBBLE (Particle.BUBBLE),
	WATER_DROP (Particle.RAIN), RAIN (Particle.RAIN),
	WATER_SPLASH (Particle.SPLASH), SPLASH (Particle.SPLASH),
	WATER_WAKE (Particle.FISHING), WAKE (Particle.FISHING), FISHING (Particle.FISHING),
	WAX_OFF (Particle.WAX_OFF),
	WAX_ON (Particle.WAX_ON),
	WHITE_ASH (Particle.WHITE_ASH),
	WHITE_SMOKE (Particle.WHITE_SMOKE);
	
	Particle particle;
	Class<?> dataClass;
	
	ParticleEffect(Particle particle) {
		this.particle = particle;
		this.dataClass = particle.getDataType();
	}
	
	public Particle getParticle() {
		return particle;
	}
	
	/**
	 * Displays the particle at the specified location without offsets
	 * @param loc Location to display the particle at
	 * @param amount how many of the particle to display
	 */
	public void display(Location loc, int amount) {
		display(loc, amount, 0, 0, 0);
	}
	
	/**
	 * Displays the particle at the specified location with no extra data
	 * @param loc Location to spawn the particle
	 * @param amount how many of the particle to spawn
	 * @param offsetX random offset on the x axis
	 * @param offsetY random offset on the y axis
	 * @param offsetZ random offset on the z axis
	 */
	public void display(Location loc, int amount, double offsetX, double offsetY, double offsetZ) {
		display(loc, amount, offsetX, offsetY, offsetZ, 0);
	}
	
	/**
	 * Displays the particle at the specified location with extra data
	 * @param loc Location to spawn the particle
	 * @param amount how many of the particle to spawn
	 * @param offsetX random offset on the x axis
	 * @param offsetY random offset on the y axis
	 * @param offsetZ random offset on the z axis
	 * @param extra extra data to affect the particle, usually affects speed or does nothing
	 */
	public void display(Location loc, int amount, double offsetX, double offsetY, double offsetZ, double extra) {
		if (particle == Particle.ENTITY_EFFECT) {
			display(loc, amount, 0, 0, 0, extra, Color.fromRGB((int) (offsetX * 255), (int) (offsetY * 255), (int) (offsetZ * 255)));
			return;
		}

		Object data = null;
		if (particle.getDataType() == Color.class) {
			data = Color.fromARGB(0xFFFFFFFF); // default white color
		}
		if (particle.getDataType() == Float.class) {
			data = 1.0f; // default size
		}

		loc.getWorld().spawnParticle(particle, loc, amount, offsetX, offsetY, offsetZ, extra, data, true);
	}
	
	/**
	 * Displays the particle at the specified location with data
	 * @param loc Location to spawn the particle
	 * @param amount how many of the particle to spawn
	 * @param offsetX random offset on the x axis
	 * @param offsetY random offset on the y axis
	 * @param offsetZ random offset on the z axis
	 * @param data data to display the particle with, only applicable on several particle types (check the enum)
	 */
	public void display(Location loc, int amount, double offsetX, double offsetY, double offsetZ, Object data) {
		display(loc, amount, offsetX, offsetY, offsetZ, 0, data);
	}
	
	/**
	 * Displays the particle at the specified location with regular and extra data
	 * @param loc Location to spawn the particle
	 * @param amount how many of the particle to spawn
	 * @param offsetX random offset on the x axis
	 * @param offsetY random offset on the y axis
	 * @param offsetZ random offset on the z axis
	 * @param extra extra data to affect the particle, usually affects speed or does nothing
	 * @param data data to display the particle with, only applicable on several particle types (check the enum)
	 */
	public void display(Location loc, int amount, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
		if (dataClass.isAssignableFrom(Void.class) || data == null || !dataClass.isAssignableFrom(data.getClass())) {
			display(loc, amount, offsetX, offsetY, offsetZ, extra);
		} else {
			loc.getWorld().spawnParticle(particle, loc, amount, offsetX, offsetY, offsetZ, extra, data, true);
		}
	}
}
