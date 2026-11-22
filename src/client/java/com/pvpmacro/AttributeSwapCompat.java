package com.pvpmacro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NEW FILE. Keeps the Attribute Swap mod (id "attributeswap") from hijacking PvP Macro's attacks.
 *
 * Why this is needed: Attribute Swap mixes into MultiPlayerGameMode.attack(Player, Entity) at HEAD and,
 * whenever its config says enabled, cancels the call and replaces it with
 * "select secondary slot -> attack -> select primary slot". PvP Macro calls that same method for its own
 * hits, so with both mods installed every macro hit was re-routed through Attribute Swap's slots and the
 * hotbar was left on Attribute Swap's primary slot instead of where the macro put it.
 *
 * How it stays compatible: only public members that exist in the Attribute Swap 1.0.0 bytecode are used,
 * via reflection, so there is NO compile-time dependency and nothing happens if that mod is absent:
 *     public static AttributeSwapMod.getConfig()    -> AttributeSwapConfig
 *     public boolean AttributeSwapConfig.enabled
 * While a macro runs, enabled is set to false in memory only (save() is never called, nothing is written
 * to attributeswap.json) and put back when the macro finishes, is cancelled, or the world is left.
 */
final class AttributeSwapCompat {
    private static final Logger LOG = LoggerFactory.getLogger("PvpMacro");

    private static Object suppressedConfig = null;   // the config object we switched off, null if we did not

    private AttributeSwapCompat() {}

    static void suppress() {
        if (suppressedConfig != null || !Config.get().attributeSwapCompat) return;
        try {
            Object cfg = config();
            Field enabled = cfg.getClass().getField("enabled");
            if (enabled.getBoolean(cfg)) {
                enabled.setBoolean(cfg, false);
                suppressedConfig = cfg;
            }
        } catch (ClassNotFoundException absent) {
            // Attribute Swap is not installed: nothing to do.
        } catch (Throwable t) {
            LOG.warn("Could not pause Attribute Swap while a macro runs; macro hits may be re-routed by it", t);
        }
    }

    static void release() {
        Object cfg = suppressedConfig;
        suppressedConfig = null;
        if (cfg == null) return;
        try {
            Field enabled = cfg.getClass().getField("enabled");
            // If the player toggled Attribute Swap on themselves mid-macro it is already true; leave it alone.
            if (!enabled.getBoolean(cfg)) enabled.setBoolean(cfg, true);
        } catch (Throwable t) {
            LOG.warn("Could not re-enable Attribute Swap after a macro", t);
        }
    }

    private static Object config() throws ReflectiveOperationException {   // ClassNotFoundException when the mod is absent
        Class<?> mod = Class.forName("com.attributeswap.AttributeSwapMod");
        Method getConfig = mod.getMethod("getConfig");
        return getConfig.invoke(null);
    }
}
