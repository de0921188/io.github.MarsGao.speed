package io.github.MarsGao.speed;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Resolves only the callback player or an unambiguous player owned by its receiver. */
final class CurrentPlayer {
    Object resolve(Object callback, Class<?> playerClass) {
        if (callback == null) return null;
        if (playerClass.isInstance(callback)) return callback;
        Object player = null;
        for (Class<?> type = callback.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object candidate = field.get(callback);
                    if (!playerClass.isInstance(candidate)) continue;
                    if (player != null && player != candidate) return null;
                    player = candidate;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Inaccessible or unknown owners fail closed; prepare/resume/setSpeed remain active.
                }
            }
        }
        return player;
    }
}
