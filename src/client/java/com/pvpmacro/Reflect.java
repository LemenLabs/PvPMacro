package com.pvpmacro;

import java.lang.reflect.Method;

/**
 * NEW (1.0.5). Calls Minecraft methods/fields by name, so a method that is named differently in 26.2 fails at
 * RUNTIME with a log line (the macro is cancelled) instead of breaking the whole build.
 */
final class Reflect {
    private Reflect() {}

    static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            if (!fits(m.getParameterTypes(), args)) continue;
            return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "/" + args.length);
    }

    /** Tries each name in turn (for methods that were renamed between versions). */
    static Object invokeAny(Object target, String[] names, Object... args) throws ReflectiveOperationException {
        NoSuchMethodException last = null;
        for (String n : names) {
            try { return invoke(target, n, args); } catch (NoSuchMethodException e) { last = e; }
        }
        throw last != null ? last : new NoSuchMethodException("no method names given");
    }

    /** The constant called name of an enum class, or null. (Finds SWAP / PICKUP without knowing what the enum is called.) */
    static Object enumConstant(Class<?> enumClass, String name) {
        Object[] all = enumClass.getEnumConstants();
        if (all == null) return null;
        for (Object o : all) if (((Enum<?>) o).name().equals(name)) return o;
        return null;
    }

    static Object field(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getField(name).get(target);
    }

    private static boolean fits(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Object a = args[i];
            Class<?> t = types[i];
            if (a == null) { if (t.isPrimitive()) return false; continue; }
            if (!wrap(t).isAssignableFrom(a.getClass())) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        if (t == boolean.class) return Boolean.class;
        if (t == long.class) return Long.class;
        if (t == short.class) return Short.class;
        if (t == byte.class) return Byte.class;
        if (t == char.class) return Character.class;
        return t;
    }
}
