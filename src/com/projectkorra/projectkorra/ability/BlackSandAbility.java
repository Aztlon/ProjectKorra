package com.projectkorra.projectkorra.ability;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.phasing.PhasedEntityEffectManager;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public abstract class BlackSandAbility extends SandAbility implements SubAbility {

    public BlackSandAbility(final LivingEntity caster) {
        super(caster);
    }

    @Override
    public Class<? extends Ability> getParentAbility() {
        return EarthAbility.class;
    }

    @Override
    public Element getElement() {
        return Element.BLACK_SAND;
    }

    public static void applyEffects(LivingEntity target, Player player) {
        boolean effectsEnabled = getConfig().getBoolean("Properties.Earth.BlackSand.EffectsEnabled");
        if (getSandBlock(player) == Material.BLACK_CONCRETE_POWDER && effectsEnabled && target != player) {
            int durationWith = getConfig().getInt("Properties.Earth.BlackSand.WitherEffectDuration");
            int amplifierWith = getConfig().getInt("Properties.Earth.BlackSand.WitherEffectStrength");
            PhasedEntityEffectManager.addPotionEffect(player, "BlackSand", target, new PotionEffect(PotionEffectType.WITHER, durationWith, amplifierWith));
            int durationDark = getConfig().getInt("Properties.Earth.BlackSand.DarknessEffectDuration");
            int amplifierDark = getConfig().getInt("Properties.Earth.BlackSand.DarknessEffectStrength");
            PhasedEntityEffectManager.addPotionEffect(player, "BlackSand", target, new PotionEffect(PotionEffectType.DARKNESS, durationDark, amplifierDark));
        }
    }
}
