package com.bipolar.ffishing.util;

import com.hypixel.hytale.server.core.universe.world.World;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Safe, reflection-based biome probe cache.
 *
 * This intentionally avoids hard compile-time links to worldgen classes so the mod can
 * keep booting even if generator internals change. PlayerBiomeProbeSystem updates this
 * cache while players move around; fishing then consumes the cached exact biome/zone data.
 */
public final class BiomeProbeCache {
    private static final Map<ChunkKey, ProbeSnapshot> CHUNK_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, ProbeSnapshot> PLAYER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> WORLD_ERROR_COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private static final long ERROR_COOLDOWN_NANOS = 5_000_000_000L;

    private BiomeProbeCache() {
    }

    public static ProbeSnapshot getForBlock(World world, int blockX, int blockZ) {
        if (world == null) return null;
        String worldKey = worldKey(world);
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        ProbeSnapshot exact = CHUNK_CACHE.get(new ChunkKey(worldKey, chunkX, chunkZ));
        if (exact != null) return exact;

        // Fishing can happen near a chunk edge. Search adjacent cached chunks as a small safety net.
        ProbeSnapshot nearest = null;
        long newest = Long.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ProbeSnapshot probe = CHUNK_CACHE.get(new ChunkKey(worldKey, chunkX + dx, chunkZ + dz));
                if (probe != null && probe.probedAtNanos() > newest) {
                    nearest = probe;
                    newest = probe.probedAtNanos();
                }
            }
        }
        return nearest;
    }

    public static ProbeSnapshot getForPlayer(UUID uuid) {
        return uuid == null ? null : PLAYER_CACHE.get(uuid);
    }

    public static boolean probeAndCache(World world, int blockX, int blockZ, UUID playerUuid) {
        if (world == null) return false;
        String worldKey = worldKey(world);
        long now = System.nanoTime();
        Long cooldownUntil = WORLD_ERROR_COOLDOWN_UNTIL.get(worldKey);
        if (cooldownUntil != null && cooldownUntil > now) {
            return false;
        }

        try {
            Object chunkStore = invokeNoArg(world, "getChunkStore");
            if (chunkStore == null) return false;

            Object generator = invokeNoArg(chunkStore, "getGenerator");
            if (generator == null || findMethod(generator.getClass(), "getZoneBiomeResultAt", int.class, int.class, int.class) == null) {
                return false;
            }

            Object worldConfig = invokeNoArg(world, "getWorldConfig");
            int seed = 0;
            if (worldConfig != null) {
                Object seedObj = invokeNoArg(worldConfig, "getSeed");
                if (seedObj instanceof Number number) {
                    seed = number.intValue();
                }
            }

            Object zoneBiomeResult = invoke(generator, "getZoneBiomeResultAt", new Class<?>[]{int.class, int.class, int.class}, seed, blockX, blockZ);
            if (zoneBiomeResult == null) {
                return false;
            }

            String biomeName = null;
            Object biome = invokeNoArg(zoneBiomeResult, "getBiome");
            if (biome != null) {
                Object name = invokeNoArg(biome, "getName");
                if (name instanceof String s && !s.isBlank()) {
                    biomeName = s;
                }
            }

            String zoneName = null;
            Object zoneResult = invokeNoArg(zoneBiomeResult, "getZoneResult");
            if (zoneResult != null) {
                Object zone = invokeNoArg(zoneResult, "getZone");
                if (zone != null) {
                    Object discoveryConfig = invokeNoArg(zone, "discoveryConfig");
                    if (discoveryConfig != null) {
                        Object zoneId = invokeNoArg(discoveryConfig, "zone");
                        if (zoneId instanceof String s && !s.isBlank()) {
                            zoneName = s;
                        }
                    }
                    if (zoneName == null || zoneName.isBlank()) {
                        Object zoneRecordName = invokeNoArg(zone, "name");
                        if (zoneRecordName instanceof String s && !s.isBlank()) {
                            zoneName = s;
                        }
                    }
                }
            }

            if ((biomeName == null || biomeName.isBlank()) && (zoneName == null || zoneName.isBlank())) {
                return false;
            }

            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            ProbeSnapshot snapshot = new ProbeSnapshot(worldKey, chunkX, chunkZ, blankToNull(biomeName), blankToNull(zoneName), now);
            CHUNK_CACHE.put(new ChunkKey(worldKey, chunkX, chunkZ), snapshot);
            if (playerUuid != null) {
                PLAYER_CACHE.put(playerUuid, snapshot);
            }
            return true;
        } catch (Throwable throwable) {
            WORLD_ERROR_COOLDOWN_UNTIL.put(worldKey, now + ERROR_COOLDOWN_NANOS);
            LoggerCompat.warn(com.bipolar.ffishing.FallenFishingPlugin.LOGGER, "Biome probe failed for world %s: %s", worldKey, throwable.toString());
            return false;
        }
    }

    public static FishingHabitat habitatFromSnapshot(ProbeSnapshot snapshot, int yLevel, int fluidDepth, int fluidSpread) {
        if (snapshot == null) return null;
        String biome = normalize(snapshot.biomeName());
        String zone = normalize(snapshot.zoneName());
        String combined = biome + " " + zone;

        if (containsAny(combined, "volcan", "lava", "ash", "ember", "magma", "basalt")) {
            return FishingHabitat.VOLCANIC;
        }
        if (containsAny(combined, "swamp", "bog", "fen", "marsh", "mangrove", "reed")) {
            return FishingHabitat.SWAMP;
        }
        if (containsAny(combined, "reef", "coral", "tropical reef")) {
            return FishingHabitat.TROPICAL_REEF;
        }
        if (containsAny(combined, "ancient", "fossil", "ruin")) {
            return FishingHabitat.ANCIENT_COAST;
        }
        if (containsAny(combined, "glacier", "frozen", "ice", "snow", "tundra", "arctic")) {
            return yLevel >= 95 ? FishingHabitat.MOUNTAIN_COLD : FishingHabitat.FROZEN_WATERS;
        }
        if (containsAny(combined, "mountain", "alpine", "aspen", "highland")) {
            return FishingHabitat.MOUNTAIN_COLD;
        }
        if (containsAny(combined, "ocean", "sea", "coast", "shore", "beach")) {
            if (containsAny(combined, "reef", "coral", "lagoon")) {
                return FishingHabitat.TROPICAL_REEF;
            }
            return fluidDepth >= 12 || fluidSpread >= 90 ? FishingHabitat.DEEP_OCEAN : FishingHabitat.OPEN_OCEAN;
        }
        if (containsAny(combined, "river", "lake", "plains", "forest", "meadow", "jungle")) {
            return FishingHabitat.PLAINS_FRESHWATER;
        }
        return null;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }

    private static String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String worldKey(World world) {
        try {
            Object name = invokeNoArg(world, "getName");
            if (name instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {
        }
        try {
            Object worldConfig = invokeNoArg(world, "getWorldConfig");
            if (worldConfig != null) {
                Object displayName = invokeNoArg(worldConfig, "getDisplayName");
                if (displayName instanceof String s && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {
        }
        return world.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(world));
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object instance, String name) throws Exception {
        if (instance == null) return null;
        Method method = findMethod(instance.getClass(), name);
        if (method == null) return null;
        return method.invoke(instance);
    }

    private static Object invoke(Object instance, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        if (instance == null) return null;
        Method method = findMethod(instance.getClass(), name, parameterTypes);
        if (method == null) return null;
        return method.invoke(instance, args);
    }

    private record ChunkKey(String worldKey, int chunkX, int chunkZ) {
        private ChunkKey {
            Objects.requireNonNull(worldKey, "worldKey");
        }
    }

    public record ProbeSnapshot(String worldKey, int chunkX, int chunkZ, String biomeName, String zoneName, long probedAtNanos) {
    }
}
