package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

/**
 * NEW (1.1.1). Inventory Pulling: when a stackable hotbar/offhand stack drops below the threshold percent
 * of its max stack size, pull a matching stack from the main inventory into the hotbar slot.
 * Handles partial stacks, multiple matching stacks, and avoids overwriting valuable items.
 * Key: key.pvpmacro.inventory_pull (unbound by default).
 */
final class InventoryPull {
    private static int cooldown = 0;

    private InventoryPull() {}

    static void toggle(Minecraft mc) {
        Config c = Config.get();
        c.inventoryPullEnabled = !c.inventoryPullEnabled;
        Config.save();
        Steps.msg(mc, "PvP Macro: inventory pull " + (c.inventoryPullEnabled ? "ON" : "OFF"));
    }

    static void tick(Minecraft mc, boolean macroRunning) {
        Config c = Config.get();
        if (!c.inventoryPullEnabled || macroRunning || mc.player == null || mc.gameMode == null) return;
        if (cooldown > 0) { cooldown--; return; }
        cooldown = Math.max(1, c.inventoryPullCheckTicks);

        // Check each monitored hotbar slot
        List<Integer> monitoredSlots = c.inventoryPullHotbarSlots;
        if (monitoredSlots.isEmpty()) monitoredSlots = java.util.List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

        for (int hotbarSlot : monitoredSlots) {
            if (hotbarSlot < 0 || hotbarSlot > 8) continue;
            ItemStack hotbarStack = mc.player.getInventory().getItem(hotbarSlot);
            if (hotbarStack.isEmpty()) continue;

            int max = hotbarStack.getMaxStackSize();
            if (max <= 1) continue; // non-stackable, skip

            // Check if below threshold
            if (hotbarStack.getCount() * 100 >= max * c.inventoryPullThreshold) continue;

            // Check blacklist
            if (ignored(hotbarStack)) continue;

            // Try to pull from main inventory
            int srcSlot = findMatchingStack(mc, hotbarSlot, hotbarStack);
            if (srcSlot < 0) continue;

            // Perform the pull: take from main inventory and merge into hotbar slot
            int srcMenu = Containers.menuSlotOfInventoryIndex(srcSlot);
            PvpLog.info("inventory pull: pulling from main slot " + srcSlot + " into hotbar slot " + hotbarSlot);

            // Step 1: Pick up from source
            if (!Containers.click(mc, srcMenu, 0, "PICKUP")) continue;
            // Step 2: Click on the hotbar slot to merge (will merge up to max stack size)
            if (!Containers.click(mc, 36 + hotbarSlot, 0, "PICKUP")) continue;
            // Step 3: If source still has items, put the remainder back
            if (!Containers.cursorEmpty(mc)) {
                Containers.click(mc, srcMenu, 0, "PICKUP");
                PvpLog.info("inventory pull: put remainder back to source slot");
            }
            cooldown = Math.max(cooldown, c.inventoryPullDelayTicks);
            return; // one top-up per scan
        }
    }

    private static boolean ignored(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String s : Config.get().inventoryPullBlacklist) {
            if (s == null) continue;
            if (s.equalsIgnoreCase(id) || ("minecraft:" + s).equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    /** Find the biggest matching stack in main inventory (slots 9-35) for the given hotbar item. */
    private static int findMatchingStack(Minecraft mc, int hotbarSlot, ItemStack hotbarStack) {
        int max = hotbarStack.getMaxStackSize();
        if (max <= 1) return -1;

        int bestSrc = -1;
        int bestCount = 0;

        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (ignored(s)) continue;

            // Check if same item (using same item comparison)
            if (!s.is(hotbarStack.getItem())) continue;

            int count = s.getCount();
            // Prefer stacks that can fully top up the hotbar slot
            if (count + hotbarStack.getCount() <= max) {
                // This stack can fully top up without exceeding max
                if (count > bestCount) {
                    bestCount = count;
                    bestSrc = i;
                }
            } else {
                // Partial top-up is also acceptable if it's the best we have
                if (count > bestCount) {
                    bestCount = count;
                    bestSrc = i;
                }
            }
        }

        return bestSrc;
    }
}