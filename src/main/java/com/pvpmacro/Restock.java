package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

/**
 * NEW (1.1.0). Restock: any STACKABLE item in the hotbar (and offhand) that drops below restockBelowPercent of its max stack
 * (default 50 = below half) is topped up from the biggest matching stack in the main inventory. One top-up per scan.
 * Done with three clicks in the player's inventory menu: pick up the source stack, put it on the low stack (that merges up
 * to the max), put whatever is left back where it came from. Toggle with the Restock key or the menu (config restockEnabled).
 */
final class Restock {
    private static int cooldown = 0;
    private static int lastSrcMenu = 0;
    private static Method sameStack;
    private static boolean sameSearched = false;

    private Restock() {}

    static void toggle(Minecraft mc) {
        Config c = Config.get();
        c.restockEnabled = !c.restockEnabled;
        Config.save();
        cooldown = 0;
        Steps.msg(mc, "PvP Macro: restock " + (c.restockEnabled ? "ON" : "OFF"));
    }

    static void tick(Minecraft mc, boolean macroRunning) {
        Config c = Config.get();
        if (!c.restockEnabled || macroRunning || mc.player == null || mc.gameMode == null || AutoTotem.screenOpen(mc)) return;
        if (cooldown > 0) { cooldown--; return; }
        cooldown = Math.max(1, c.restockCheckTicks);
        if (!Containers.cursorEmpty(mc)) {
            if (lastSrcMenu > 0) {      // a previous top-up left a stack on the cursor: put it back
                Containers.click(mc, lastSrcMenu, 0, "PICKUP");
                PvpLog.info("restock: put a leftover cursor stack back");
                lastSrcMenu = 0;
            }
            return;
        }
        if (c.restockHotbar) {
            for (int i = 0; i < 9; i++) {
                if (tryRestock(mc, mc.player.getInventory().getItem(i), 36 + i)) return;
            }
        }
        if (c.restockOffhand) {
            tryRestock(mc, mc.player.getItemInHand(InteractionHand.OFF_HAND), Containers.OFFHAND_MENU_SLOT);
        }
    }

    private static boolean tryRestock(Minecraft mc, ItemStack target, int dstMenu) {
        Config c = Config.get();
        if (target.isEmpty()) return false;
        int max = target.getMaxStackSize();
        if (max <= 1) return false;                                              // not stackable
        if (target.getCount() * 100 >= max * c.restockBelowPercent) return false; // not low enough
        if (ignored(target)) return false;

        int src = -1, best = 0;                                                  // biggest matching stack in the main inventory
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getCount() > best && same(s, target)) { best = s.getCount(); src = i; }
        }
        if (src < 0) return false;

        int srcMenu = Containers.menuSlotOfInventoryIndex(src);
        PvpLog.info("restock: " + target + " (" + target.getCount() + "/" + max + ") from inventory slot " + src);
        if (!Containers.click(mc, srcMenu, 0, "PICKUP")) return false;           // 1) pick up the whole source stack
        lastSrcMenu = srcMenu;
        Containers.click(mc, dstMenu, 0, "PICKUP");                              // 2) drop it on the low stack: merges up to the max
        Containers.click(mc, srcMenu, 0, "PICKUP");                              // 3) put the remainder back (no-op if nothing is left)
        if (Containers.cursorEmpty(mc)) lastSrcMenu = 0;
        cooldown = Math.max(cooldown, c.restockDelayTicks);
        return true;
    }

    private static boolean ignored(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String s : Config.get().restockIgnore) {
            if (s == null) continue;
            if (s.equalsIgnoreCase(id) || ("minecraft:" + s).equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    /** Same item AND same components (so different tipped arrows or enchanted books are not mixed); item-only if that call is missing. */
    private static boolean same(ItemStack a, ItemStack b) {
        if (!sameSearched) {
            sameSearched = true;
            try { sameStack = ItemStack.class.getMethod("isSameItemSameComponents", ItemStack.class, ItemStack.class); }
            catch (ReflectiveOperationException e) { PvpLog.info("isSameItemSameComponents not found, comparing items only"); }
        }
        if (sameStack != null) {
            try { return (Boolean) sameStack.invoke(null, a, b); } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return a.is(b.getItem());
    }
}
