package me.arthed.custombiomecolors;

import me.arthed.custombiomecolors.data.DataManager;
import me.arthed.custombiomecolors.nms.NmsBiome;
import me.arthed.custombiomecolors.nms.NmsServer;
import me.arthed.custombiomecolors.utils.StringUtils;
import me.arthed.custombiomecolors.utils.objects.BiomeColorType;
import me.arthed.custombiomecolors.utils.objects.BiomeColors;
import me.arthed.custombiomecolors.utils.objects.BiomeKey;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BiomeManager {

    private final NmsServer nmsServer = CustomBiomeColors.getInstance().getNmsServer();
    private final DataManager dataManager = CustomBiomeColors.getInstance().getDataManager();
    private final Object biomeLock = new Object();

    public void changeBiomeColor(Block[] blocks, BiomeColorType colorType, int color, Runnable runWhenDone) {
        this.changeBiomeColor(blocks, colorType, color, new BiomeKey("cbc", StringUtils.randomString(8)), runWhenDone);
    }

    public void changeBiomeColor(Block[] blocks, BiomeColorType colorType, int color, BiomeKey biomeKey, Runnable runWhenDone) {
        Map<String, ChunkBlocks> chunks = this.groupBlocks(blocks);
        if (chunks.isEmpty()) {
            CustomBiomeColors.getScheduler().runTask(runWhenDone);
            return;
        }

        List<ChunkBiomes> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(chunks.size());

        for (ChunkBlocks chunk : chunks.values()) {
            CustomBiomeColors.getScheduler().runTask(chunk.location, () -> {
                try {
                    ChunkBiomes result = this.readChunk(chunk, colorType, color);
                    if (!result.groups.isEmpty()) {
                        results.add(result);
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        CustomBiomeColors.getScheduler().runTask(() -> this.createAndApplyBiomes(results, biomeKey, runWhenDone));
                    }
                }
            });
        }
    }

    private Map<String, ChunkBlocks> groupBlocks(Block[] blocks) {
        Map<String, ChunkBlocks> chunks = new HashMap<>();

        for (Block block : blocks) {
            World world = block.getWorld();
            if (world == null) {
                continue;
            }

            UUID worldId = world.getUID();
            int chunkX = block.getX() >> 4;
            int chunkZ = block.getZ() >> 4;
            String key = worldId + ":" + chunkX + ":" + chunkZ;

            ChunkBlocks chunk = chunks.get(key);
            if (chunk == null) {
                chunk = new ChunkBlocks(new Location(world, block.getX(), block.getY(), block.getZ()));
                chunks.put(key, chunk);
            }
            chunk.blocks.add(block);
        }

        return chunks;
    }

    /**
     * Reads the biome data of one chunk. This must run on the thread that owns the chunk,
     * because on Folia chunks may only be accessed from their region thread.
     */
    private ChunkBiomes readChunk(ChunkBlocks chunk, BiomeColorType colorType, int color) {
        ChunkBiomes result = new ChunkBiomes(chunk);
        Map<Object, BiomeGroup> groups = new HashMap<>();

        for (Block block : chunk.blocks) {
            Object biomeBase = this.nmsServer.getBlocksBiomeBase(block);
            if (biomeBase == null) {
                continue;
            }

            BiomeGroup group = groups.get(biomeBase);
            if (group == null) {
                NmsBiome biome = this.nmsServer.getBiomeFromBiomeBase(biomeBase);
                if (biome == null) {
                    continue;
                }

                BiomeColors biomeColors = biome.getBiomeColors();
                if (biomeColors == null) {
                    continue;
                }

                biomeColors.setColor(colorType, color);
                group = new BiomeGroup(biome, biomeColors);
                groups.put(biomeBase, group);
            }
            group.blocks.add(block);
        }

        result.groups.addAll(groups.values());
        return result;
    }

    /**
     * Creates the new custom biomes on the global thread, then applies them per chunk.
     * The biome registry is global state, so it must not be modified from region threads.
     */
    private void createAndApplyBiomes(List<ChunkBiomes> results, BiomeKey biomeKey, Runnable runWhenDone) {
        AtomicInteger biomeIndex = new AtomicInteger();

        for (ChunkBiomes result : results) {
            for (BiomeGroup group : result.groups) {
                synchronized (this.biomeLock) {
                    try {
                        NmsBiome newBiome = this.dataManager.getBiomeWithSpecificColors(group.colors);
                        if (newBiome == null) {
                            int index = biomeIndex.getAndIncrement();
                            BiomeKey individualBiomeKey = index == 0
                                    ? biomeKey
                                    : new BiomeKey(biomeKey.key, biomeKey.value + "." + (index - 1));

                            newBiome = group.biome.cloneWithDifferentColors(this.nmsServer, individualBiomeKey, group.colors);
                            this.dataManager.saveBiome(individualBiomeKey, group.colors);
                        }
                        group.newBiome = newBiome;
                    } catch (Exception error) {
                        error.printStackTrace();
                    }
                }
            }
        }

        if (results.isEmpty()) {
            CustomBiomeColors.getScheduler().runTask(runWhenDone);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(results.size());
        for (ChunkBiomes result : results) {
            CustomBiomeColors.getScheduler().runTask(result.chunk.location, () -> {
                try {
                    for (BiomeGroup group : result.groups) {
                        if (group.newBiome == null) {
                            continue;
                        }
                        for (Block block : group.blocks) {
                            this.nmsServer.setBlocksBiome(block, group.newBiome);
                        }
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        CustomBiomeColors.getScheduler().runTask(runWhenDone);
                    }
                }
            });
        }
    }

    private static class ChunkBlocks {
        private final Location location;
        private final List<Block> blocks = new ArrayList<>();

        private ChunkBlocks(Location location) {
            this.location = location;
        }
    }

    private static class ChunkBiomes {
        private final ChunkBlocks chunk;
        private final List<BiomeGroup> groups = new ArrayList<>();

        private ChunkBiomes(ChunkBlocks chunk) {
            this.chunk = chunk;
        }
    }

    private static class BiomeGroup {
        private final NmsBiome biome;
        private final BiomeColors colors;
        private final List<Block> blocks = new ArrayList<>();
        private volatile NmsBiome newBiome;

        private BiomeGroup(NmsBiome biome, BiomeColors colors) {
            this.biome = biome;
            this.colors = colors;
        }
    }

}
