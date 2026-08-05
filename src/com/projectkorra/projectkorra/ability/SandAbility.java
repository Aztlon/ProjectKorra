package com.projectkorra.projectkorra.ability;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;

public abstract class SandAbility extends EarthAbility implements SubAbility {

	public SandAbility(final LivingEntity caster) {
		super(caster);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return EarthAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.SAND;
	}

	public static Material getSandBlock(LivingEntity caster) {
		if (caster instanceof Player player) {
			Material material = SandMaterial.byName(materialType(player)).material;

			if (material == Material.BLACK_CONCRETE_POWDER)  {
				if (isDesert(player.getLocation())) return material;
				else return Material.SAND;
			}
			return material;
		}
		return Material.SAND;
	}

	public static Material getSolidSandBlock(LivingEntity caster) {
		if (caster instanceof Player player) {
			Material material = SandMaterial.byName(materialType(player)).solidMaterial;

			if (material == Material.BLACK_CONCRETE) {
				if (isDesert(player.getLocation())) return material;
				else return Material.SANDSTONE;
			}
			return material;
		}
		return Material.SAND;
	}

	public static String getParticleColor(Player player) {
		return SandMaterial.byName(materialType(player)).particleColor;
	}

	public static String materialType(final LivingEntity caster) {
		String material = "sand";
		if (caster instanceof Player player) {
			String placeholder = PlaceholderAPI.setPlaceholders(player, "%avatarverse_sandmaterial%");
			if (!placeholder.isEmpty())
				material = placeholder;
		}
		return material;
	}

	public static Boolean isDesert(Location location) {
		return !getConfig().getBoolean("Properties.Earth.BlackSand.DesertOnly") || location.getWorld().getBiome(location) == Biome.DESERT;
	}

	public enum SandMaterial {
		SAND(Material.SAND, Material.SANDSTONE, Material.SAND, "#E4C189", net.md_5.bungee.api.ChatColor.GOLD),
		RED(Material.RED_SAND, Material.RED_SANDSTONE, Material.RED_SAND, "#A75A22", net.md_5.bungee.api.ChatColor.RED),
		BLACK(Material.BLACK_CONCRETE_POWDER, Material.BLACK_CONCRETE, Material.BLACK_CONCRETE_POWDER, "#111213", net.md_5.bungee.api.ChatColor.DARK_GRAY),
		PINK(Material.PINK_CONCRETE_POWDER, Material.PINK_CONCRETE,  Material.PINK_CONCRETE_POWDER, "#F2A5BF", net.md_5.bungee.api.ChatColor.LIGHT_PURPLE),
		BLUE(Material.BLUE_CONCRETE_POWDER, Material.BLUE_CONCRETE,  Material.BLUE_CONCRETE_POWDER, "#61B1D5", net.md_5.bungee.api.ChatColor.BLUE),
		WHITE(Material.WHITE_CONCRETE_POWDER, Material.WHITE_CONCRETE,  Material.WHITE_CONCRETE_POWDER, "#EEF1F3", net.md_5.bungee.api.ChatColor.WHITE);

		public static SandMaterial byName(String name) {
			return switch (name.toLowerCase()) {
				case "black" -> BLACK;
				case "red" -> RED;
				case "white" -> WHITE;
				case "blue" -> BLUE;
				case "pink" -> PINK;
				default -> SAND;
			};
		}

		final Material material;
		final Material solidMaterial;
		final Material icon;
		final String particleColor;
		final net.md_5.bungee.api.ChatColor color;

		SandMaterial(Material material, Material solidMaterial, Material icon, String particleColor, net.md_5.bungee.api.ChatColor color) {
			this.material = material;
			this.solidMaterial = solidMaterial;
			this.icon = icon;
			this.particleColor = particleColor;
			this.color = color;
		}

		public Material getMaterial() {
			return material;
		}

		public Material getIcon() {
			return icon;
		}

		public net.md_5.bungee.api.ChatColor getColor() {
			return color;
		}
	}
}
