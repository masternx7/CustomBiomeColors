package me.arthed.custombiomecolors.nms;

import me.arthed.custombiomecolors.utils.objects.BiomeColors;
import me.arthed.custombiomecolors.utils.objects.BiomeKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.joml.Vector3fc;

import java.util.Optional;

public class NmsBiome_26_3 implements NmsBiome {

    private final Biome biomeBase;

    public NmsBiome_26_3(Biome biomeBase) {
        this.biomeBase = biomeBase;
    }

    public Biome getBiomeBase() {
        return this.biomeBase;
    }

    @Override
    public BiomeColors getBiomeColors() {
        BiomeSpecialEffects effects = this.biomeBase.getSpecialEffects();
        EnvironmentAttributeMap attributes = this.biomeBase.getAttributes();

        return new BiomeColors()
                .setGrassColor(effects.grassColorOverride().orElse(0))
                .setFoliageColor(effects.foliageColorOverride().orElse(0))
                .setWaterColor(effects.waterColor())
                .setWaterFogColor(getAttributeColor(attributes, EnvironmentAttributes.WATER_FOG_COLOR))
                .setSkyColor(getAttributeColor(attributes, EnvironmentAttributes.SKY_COLOR))
                .setFogColor(getAttributeColor(attributes, EnvironmentAttributes.FOG_COLOR));
    }

    private static int getAttributeColor(EnvironmentAttributeMap attributes, EnvironmentAttribute<Vector3fc> attribute) {
        return ARGB.colorFromVector3f(attributes.applyModifier(attribute, attribute.defaultValue())) & 0xFFFFFF;
    }

    @Override
    public NmsBiome cloneWithDifferentColors(NmsServer nmsServer, BiomeKey newBiomeKey, BiomeColors biomeColors) {
        Biome customBiome = createBiome(this.biomeBase, biomeColors);
        ResourceKey<Biome> customBiomeKey = ResourceKey.create(
                Registries.BIOME,
                Identifier.fromNamespaceAndPath(newBiomeKey.key, newBiomeKey.value)
        );

        nmsServer.registerBiome(customBiome, customBiomeKey);

        return new NmsBiome_26_3(customBiome);
    }

    static Biome createBiome(Biome baseBiome, BiomeColors biomeColors) {
        Biome.ClimateSettings climateSettings = baseBiome.climateSettings;
        BiomeSpecialEffects oldEffects = baseBiome.getSpecialEffects();

        BiomeSpecialEffects newEffects = new BiomeSpecialEffects(
                biomeColors.getWaterColor(),
                biomeColors.getFoliageColor() != 0
                        ? Optional.of(biomeColors.getFoliageColor())
                        : oldEffects.foliageColorOverride(),
                oldEffects.dryFoliageColorOverride(),
                biomeColors.getGrassColor() != 0
                        ? Optional.of(biomeColors.getGrassColor())
                        : oldEffects.grassColorOverride(),
                BiomeSpecialEffects.GrassColorModifier.NONE
        );

        EnvironmentAttributeMap.Builder attributes = EnvironmentAttributeMap.builder();
        attributes.putAll(baseBiome.getAttributes());
        attributes.set(EnvironmentAttributes.WATER_FOG_COLOR, ARGB.vector3fFromRGB24(biomeColors.getWaterFogColor()));
        attributes.set(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(biomeColors.getSkyColor()));
        attributes.set(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(biomeColors.getFogColor()));

        return new Biome.BiomeBuilder()
                .hasPrecipitation(climateSettings.hasPrecipitation())
                .temperature(climateSettings.temperature())
                .downfall(climateSettings.downfall())
                .temperatureAdjustment(climateSettings.temperatureModifier())
                .putAttributes(attributes)
                .specialEffects(newEffects)
                .generationSettings(baseBiome.getGenerationSettings())
                .build();
    }

    public boolean equals(Object object) {
        return object instanceof NmsBiome && ((NmsBiome) object).getBiomeBase().equals(this.biomeBase);
    }

}
