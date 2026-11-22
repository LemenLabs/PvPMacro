package com.pvpmacro;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

/**
 * NEW (1.0.13). Stack size helpers. The ItemStack methods used here (getCount, getMaxStackSize, getItem, same-item test)
 * could not be confirmed in 26.2 classes, so they are looked up at runtime; if one is missing the answer is "unknown"
 * and the restock macro simply does nothing (and says why in the log). BuiltInRegistries.ITEM is confirmed.
 */
final class Stacks {
    private static Method sameItem = null;
    private static boolean sameItemSearched = false;

    private Stacks() {}

    /** Number of items in the stack, or -1 if it cannot be read. */
    static int count(ItemStack s) {
        try { return ((Number) Reflect.invoke(s, "getCount")).intValue(); } catch (ReflectiveOperationException | RuntimeException e) { return -1; }
    }

    /** Largest stack size of this item (1 = not stackable), or -1 if it cannot be read. */
    static int max(ItemStack s) {
        try { return ((Number) Reflect.invoke(s, "getMaxStackSize")).intValue(); } catch (ReflectiveOperationException | RuntimeException e) { return -1; }
    }

    /** "minecraft:ender_pearl", or null if it cannot be read. */
    static String id(ItemStack s) {
        try {
            Item item = (Item) Reflect.invoke(s, "getItem");           // the registry only accepts an Item
            return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    /** Same item AND same components (name, enchantments, ...), so different potions or enchanted books are never merged. */
    static boolean same(ItemStack a, ItemStack b) {
        try {
            if (!sameItemSearched) {
                sameItemSearched = true;
                Method best = null;
                for (Method m : ItemStack.class.getMethods()) {
                    Class<?>[] t = m.getParameterTypes();
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || t.length != 2 || t[0] != ItemStack.class || t[1] != ItemStack.class || m.getReturnType() != boolean.class) continue;
                    if (m.getName().equals("isSameItemSameComponents")) { best = m; break; }
                    if (m.getName().equals("isSameItemSameTags") || m.getName().equals("isSameItem")) best = best == null ? m : best;
                }
                sameItem = best;
                PvpLog.info("stack comparison method: " + (best == null ? "none found, comparing ids" : best.getName()));
            }
            if (sameItem != null) return (Boolean) sameItem.invoke(null, a, b);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("stack comparison failed: " + e);
        }
        String x = id(a), y = id(b);
        return x != null && x.equals(y);
    }
}
