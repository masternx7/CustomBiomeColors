package me.arthed.custombiomecolors.nms;

import me.arthed.custombiomecolors.utils.objects.BiomeColors;
import me.arthed.custombiomecolors.utils.objects.BiomeKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public class NmsServer_26_3 implements NmsServer {

    private final Registry<Biome> biomeRegistry = ((CraftServer) Bukkit.getServer()).getServer()
            .registryAccess()
            .lookupOrThrow(Registries.BIOME);

    @Override
    public NmsBiome getBiomeFromBiomeKey(BiomeKey biomeKey) {
        Biome biome = this.biomeRegistry.getValue(createBiomeKey(biomeKey));
        return biome == null ? null : new NmsBiome_26_3(biome);
    }

    @Override
    public NmsBiome getBiomeFromBiomeBase(Object biomeBase) {
        return new NmsBiome_26_3(((Holder<Biome>) biomeBase).value());
    }

    @Override
    public boolean doesBiomeExist(BiomeKey biomeKey) {
        return this.biomeRegistry.containsKey(createBiomeKey(biomeKey));
    }

    @Override
    public void loadBiome(BiomeKey biomeKey, BiomeColors biomeColors) {
        Biome baseBiome = this.biomeRegistry.getValue(ResourceKey.create(
                Registries.BIOME,
                Identifier.withDefaultNamespace("plains")
        ));
        if (baseBiome == null) {
            return;
        }

        Biome customBiome = NmsBiome_26_3.createBiome(baseBiome, biomeColors);
        this.registerBiome(customBiome, createBiomeKey(biomeKey));
    }

    @Override
    public void setBlocksBiome(Block block, NmsBiome nmsBiome) {
        ServerLevel nmsWorld = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos blockPosition = new BlockPos(block.getX(), block.getY(), block.getZ());

        LevelChunk chunk = nmsWorld.getChunkAt(blockPosition);
        if (chunk != null) {
            chunk.setBiome(
                    block.getX() >> 2,
                    block.getY() >> 2,
                    block.getZ() >> 2,
                    this.biomeRegistry.wrapAsHolder((Biome) nmsBiome.getBiomeBase())
            );
        }
    }

    @Override
    public Object getBlocksBiomeBase(Block block) {
        ServerLevel nmsWorld = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos blockPosition = new BlockPos(block.getX(), block.getY(), block.getZ());

        LevelChunk chunk = nmsWorld.getChunkAt(blockPosition);
        if (chunk != null) {
            return chunk.getNoiseBiome(block.getX() >> 2, block.getY() >> 2, block.getZ() >> 2);
        }
        return null;
    }

    @Override
    public void registerBiome(Object biomeBase, Object biomeMinecraftKey) {
        Field frozenField = null;
        Field unregisteredIntrusiveHoldersField = null;

        try {
            MappedRegistry<Biome> registry = (MappedRegistry<Biome>) this.biomeRegistry;

            frozenField = MappedRegistry.class.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            frozenField.set(registry, false);

            unregisteredIntrusiveHoldersField = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
            unregisteredIntrusiveHoldersField.setAccessible(true);
            unregisteredIntrusiveHoldersField.set(registry, new IdentityHashMap<>());

            registry.createIntrusiveHolder((Biome) biomeBase);
            registry.register(
                    (ResourceKey<Biome>) biomeMinecraftKey,
                    (Biome) biomeBase,
                    RegistrationInfo.BUILT_IN
            );
        } catch (Exception error) {
            error.printStackTrace();
        } finally {
            try {
                MappedRegistry<Biome> registry = (MappedRegistry<Biome>) this.biomeRegistry;
                if (unregisteredIntrusiveHoldersField != null) {
                    unregisteredIntrusiveHoldersField.set(registry, null);
                }
                if (frozenField != null) {
                    frozenField.set(registry, true);
                }
            } catch (Exception error) {
                error.printStackTrace();
            }
        }
    }

    @Override
    public String getBiomeString(NmsBiome nmsBiome) {
        Identifier identifier = this.biomeRegistry.getKey((Biome) nmsBiome.getBiomeBase());
        if (identifier != null) {
            return identifier.toString();
        }
        return "minecraft:forest";
    }

    private static ResourceKey<Biome> createBiomeKey(BiomeKey biomeKey) {
        return ResourceKey.create(
                Registries.BIOME,
                Identifier.fromNamespaceAndPath(biomeKey.key, biomeKey.value)
        );
    }

}
