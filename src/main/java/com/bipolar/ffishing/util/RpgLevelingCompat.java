package com.bipolar.ffishing.util;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.bipolar.ffishing.FallenFishingPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RpgLevelingCompat {
    private static final String API_CLASS_NAME = "org.zuxaw.plugin.api.RPGLevelingAPI";
    private static final String XP_SOURCE_CLASS_NAME = "org.zuxaw.plugin.api.XPSource";
    private static final String SOURCE_NAME = "FISHING";

    private static boolean initialized = false;
    private static boolean available = false;
    private static Method apiGetMethod;
    private static Method apiAvailableMethod;
    private static Method[] apiAddXpMethods = new Method[0];
    private static Method xpSourceCreateMethod;

    private RpgLevelingCompat() {}

    public static void awardCatchXp(PlayerRef playerRef, FishCatchInfo catchInfo) {
        if (playerRef == null || catchInfo == null) return;
        if (!ensureInitialized()) return;

        try {
            Object api = apiGetMethod.invoke(null);
            if (api == null || !Boolean.TRUE.equals(apiAvailableMethod.invoke(null))) {
                return;
            }

            Object xpSource = createXpSource(catchInfo);
            double xp = computeCatchXp(catchInfo);
            boolean awarded = invokeAddXp(api, playerRef, xp, xpSource, catchInfo);
            if (!awarded) {
                FallenFishingPlugin.LOGGER.atWarning().log("RPGLeveling rejected FFishing XP award for %s (%s, %.1f XP)",
                        catchInfo.getSpeciesName(), catchInfo.getRarityName(), xp);
            }
        } catch (Throwable throwable) {
            FallenFishingPlugin.LOGGER.atWarning().withCause(throwable).log("Failed to award RPGLeveling XP for fishing catch");
        }
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) return available;
        initialized = true;
        try {
            Class<?> apiClass = loadClass(API_CLASS_NAME);
            Class<?> xpSourceClass = loadClass(XP_SOURCE_CLASS_NAME);
            apiGetMethod = apiClass.getMethod("get");
            apiAvailableMethod = apiClass.getMethod("isAvailable");

            xpSourceCreateMethod = findCreateMethod(xpSourceClass,
                    new Class<?>[]{String.class, Object.class},
                    new Class<?>[]{String.class}
            );
            if (xpSourceCreateMethod == null) {
                throw new NoSuchMethodException("XPSource.create overload not found");
            }

            apiAddXpMethods = findAddXpMethods(apiClass, xpSourceClass);
            if (apiAddXpMethods.length == 0) {
                throw new NoSuchMethodException("RPGLevelingAPI.addXP compatible overload not found");
            }

            available = true;
        } catch (Throwable throwable) {
            FallenFishingPlugin.LOGGER.atWarning().withCause(throwable).log("RPGLeveling API unavailable for FFishing integration");
            available = false;
        }
        return available;
    }

    private static Object createXpSource(FishCatchInfo catchInfo) throws Exception {
        if (xpSourceCreateMethod.getParameterCount() >= 2) {
            return xpSourceCreateMethod.invoke(null, SOURCE_NAME, catchInfo.getSpeciesName() + ":" + catchInfo.getRarityName());
        }
        return xpSourceCreateMethod.invoke(null, SOURCE_NAME);
    }

    private static boolean invokeAddXp(Object api, PlayerRef playerRef, double xp, Object xpSource, FishCatchInfo catchInfo) throws Exception {
        Throwable lastThrowable = null;
        for (Method method : apiAddXpMethods) {
            try {
                Object[] args = buildAddXpArguments(method, playerRef, xp, xpSource, catchInfo);
                if (args == null) {
                    continue;
                }
                Object result = method.invoke(api, args);
                if (!(result instanceof Boolean success)) {
                    return true;
                }
                if (success) {
                    return true;
                }
            } catch (Throwable throwable) {
                lastThrowable = throwable;
            }
        }

        if (lastThrowable != null) {
            if (lastThrowable instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(lastThrowable);
        }
        return false;
    }

    private static Object[] buildAddXpArguments(Method method, PlayerRef playerRef, double xp, Object xpSource, FishCatchInfo catchInfo) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];

        if (UUID.class.equals(parameterTypes[0])) {
            UUID playerUuid = extractPlayerUuid(playerRef);
            if (playerUuid == null) {
                return null;
            }
            args[0] = playerUuid;
        } else {
            args[0] = playerRef;
        }

        args[1] = xp;

        if (parameterTypes.length >= 3) {
            args[2] = xpSource;
        }
        if (parameterTypes.length >= 4) {
            args[3] = catchInfo.getSpeciesName();
        }
        return args;
    }

    private static UUID extractPlayerUuid(PlayerRef playerRef) {
        if (playerRef == null) return null;

        UUID fromPlayer = tryExtractUuid(playerRef,
                "getUuid",
                "getUUID",
                "uuid",
                "getId",
                "getUniqueId"
        );
        if (fromPlayer != null) {
            return fromPlayer;
        }

        Object reference = tryInvoke(playerRef, "getReference");
        if (reference != null) {
            UUID fromReference = tryExtractUuid(reference,
                    "getUuid",
                    "getUUID",
                    "uuid",
                    "getId",
                    "getUniqueId"
            );
            if (fromReference != null) {
                return fromReference;
            }
        }

        return null;
    }

    private static UUID tryExtractUuid(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = tryInvoke(target, methodName);
            if (value instanceof UUID uuid) {
                return uuid;
            }
            if (value instanceof String text) {
                try {
                    return UUID.fromString(text);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    private static Object tryInvoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method[] findAddXpMethods(Class<?> owner, Class<?> xpSourceClass) {
        List<Method> methods = new ArrayList<>();
        addIfPresent(methods, owner, new Class<?>[]{PlayerRef.class, double.class, xpSourceClass, Object.class});
        addIfPresent(methods, owner, new Class<?>[]{PlayerRef.class, double.class, xpSourceClass});
        addIfPresent(methods, owner, new Class<?>[]{PlayerRef.class, double.class});
        addIfPresent(methods, owner, new Class<?>[]{UUID.class, double.class, xpSourceClass, Object.class});
        addIfPresent(methods, owner, new Class<?>[]{UUID.class, double.class, xpSourceClass});
        addIfPresent(methods, owner, new Class<?>[]{UUID.class, double.class});
        return methods.toArray(Method[]::new);
    }

    private static void addIfPresent(List<Method> methods, Class<?> owner, Class<?>[] signature) {
        try {
            methods.add(owner.getMethod("addXP", signature));
        } catch (NoSuchMethodException ignored) {
        }
    }

    private static Method findCreateMethod(Class<?> owner, Class<?>[] firstSignature, Class<?>[] secondSignature) {
        try {
            return owner.getMethod("create", firstSignature);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return owner.getMethod("create", secondSignature);
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader[] classLoaders = new ClassLoader[] {
                FallenFishingPlugin.class.getClassLoader(),
                RpgLevelingCompat.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        ClassNotFoundException last = null;
        for (ClassLoader loader : classLoaders) {
            if (loader == null) continue;
            try {
                return Class.forName(className, true, loader);
            } catch (ClassNotFoundException exception) {
                last = exception;
            }
        }
        if (last != null) throw last;
        throw new ClassNotFoundException(className);
    }

    private static double computeCatchXp(FishCatchInfo catchInfo) {
        double rarityBase = switch (catchInfo.getRarityName() == null ? "common" : catchInfo.getRarityName().toLowerCase()) {
            case "legendary" -> 42.0D;
            case "epic" -> 28.0D;
            case "rare" -> 18.0D;
            case "uncommon" -> 12.0D;
            default -> 8.0D;
        };

        double chance = catchInfo.getEncounterChance();
        double oddsBonus = 0.0D;
        if (chance > 0.0D) {
            oddsBonus = Math.max(0.0D, Math.min(16.0D, (-Math.log10(chance) - 1.0D) * 3.2D));
        }

        return Math.round((rarityBase + oddsBonus) * 10.0D) / 10.0D;
    }
}
