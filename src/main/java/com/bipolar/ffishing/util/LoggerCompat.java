package com.bipolar.ffishing.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.util.Locale;

public final class LoggerCompat {
    private LoggerCompat() {
    }

    public static void info(HytaleLogger logger, String message, Object... args) {
        log(logger, "atInfo", null, message, args);
    }

    public static void warn(HytaleLogger logger, String message, Object... args) {
        log(logger, "atWarning", null, message, args);
    }

    public static void warn(HytaleLogger logger, Throwable cause, String message, Object... args) {
        log(logger, "atWarning", cause, message, args);
    }

    private static void log(HytaleLogger logger, String levelMethod, Throwable cause, String message, Object... args) {
        if (logger == null) {
            fallbackPrint(levelMethod, cause, message, args);
            return;
        }

        Object target = logger;
        try {
            Method method = logger.getClass().getMethod(levelMethod);
            Object maybeTarget = method.invoke(logger);
            if (maybeTarget != null) {
                target = maybeTarget;
            }
        } catch (Throwable ignored) {
        }

        if (cause != null) {
            try {
                Method withCause = target.getClass().getMethod("withCause", Throwable.class);
                Object maybeTarget = withCause.invoke(target, cause);
                if (maybeTarget != null) {
                    target = maybeTarget;
                }
            } catch (Throwable ignored) {
            }
        }

        if (invokeLog(target, message, args)) {
            return;
        }

        if (target != logger && invokeLog(logger, message, args)) {
            return;
        }

        fallbackPrint(levelMethod, cause, message, args);
    }

    private static boolean invokeLog(Object target, String message, Object... args) {
        if (target == null) {
            return false;
        }

        try {
            Method method = target.getClass().getMethod("log", String.class, Object[].class);
            method.invoke(target, message, args == null ? new Object[0] : args);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
            return false;
        }

        try {
            Method method = target.getClass().getMethod("log", String.class);
            method.invoke(target, format(message, args));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void fallbackPrint(String levelMethod, Throwable cause, String message, Object... args) {
        String prefix = "atWarning".equals(levelMethod) ? "WARN" : "INFO";
        String rendered = format(message, args);
        if (cause != null) {
            System.out.println("[FallenFishing/" + prefix + "] " + rendered + " - " + cause);
        } else {
            System.out.println("[FallenFishing/" + prefix + "] " + rendered);
        }
    }

    private static String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(Locale.ROOT, message, args);
        } catch (Throwable ignored) {
            StringBuilder builder = new StringBuilder(message);
            builder.append(" [args=");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) builder.append(", ");
                builder.append(args[i]);
            }
            builder.append(']');
            return builder.toString();
        }
    }
}
