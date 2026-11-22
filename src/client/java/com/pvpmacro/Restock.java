package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * NEW (1.0.13). Restock: when a stackable item in your hotbar (and offhand) drops below a fraction of its maximum stack
 * (default: below half), it is topped up from another stack of the same item in your inventory.
 * How: pick up the donor stack, put it on the low stack (it merges), put any left-over back where it came from. Done with
 * clicks on the inventory menu, only when no macro runs and no screen is open. Everything is configurable (menu: Restock).
 */
final class Restock {
    private static int waited = 0, cooldown = 0;
    private static boolean warned = false;

    private Restock() {}

    static boolean toggle(Minecraft mc) {
        Config c = Config.get();
        c.restock = !c.restock;
        Config.save();
        waited = 0; cooldown = 0; warned = false;
        Steps.msg(mc, "PvP Macro: restock " + (c.restock ? "ON" : "OFF"));
        return c.restock;
    }

    /** Does the item filter (all stackables / only listed ones) allow this stack? */
    static boolean allowed(ItemStack s) {
        Config c = Config.get();
        if (!"list".equalsIgnoreCase(c.restockMode)) return true;
        String id = Stacks.id(s);
        return id != null && c.restockItems != null && c.restockItems.contains(id);
    }

    private static boolean low(ItemStack s) {
        if (s.isEmpty()) return false;
        int max = Stacks.max(s), n = Stacks.count(s);
        if (max < 2 || n < 1) return false;                         // not stackable, or unknown
        return n < max * Config.get().restockThreshold && allowed(s);
    }

    /** One planned refill: pull from donor menu slot into target menu slot. */
    private record Plan(int target, int donor, String what, int have, int max) {}

    private static Plan plan(Minecraft mc) {
        Config c = Config.get();
        boolean all = "all".equalsIgnoreCase(c.restockScope);
        // targets: hotbar 0-8, optionally the offhand, optionally the main inventory
        int[] order = new int[all ? 36 : 9];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int t : order) {
            ItemStack s = mc.player.getInventory().getItem(t);
            if (!low(s)) continue;
            int donor = donorFor(mc, s, t);
            if (donor >= 0) return new Plan(ContainerClicks.menuSlot(t), ContainerClicks.menuSlot(donor), Stacks.id(s), Stacks.count(s), Stacks.max(s));
        }
        if (c.restockOffhand) {
            ItemStack s = mc.player.getItemInHand(InteractionHand.OFF_HAND);
            if (low(s)) {
                int donor = donorFor(mc, s, -1);
                if (donor >= 0) return new Plan(ContainerClicks.OFFHAND_MENU_SLOT, ContainerClicks.menuSlot(donor), Stacks.id(s), Stacks.count(s), Stacks.max(s));
            }
        }
        return null;
    }

    /** A main-inventory slot (9-35) holding the same item, other than the target; the one with the most items. */
    private static int donorFor(Minecraft mc, ItemStack target, int targetIndex) {
        int best = -1, bestN = 0;
        for (int i = 9; i < 36; i++) {
            if (i == targetIndex) continue;
            ItemStack d = mc.player.getInventory().getItem(i);
            if (d.isEmpty() || !Stacks.same(target, d)) continue;
            int n = Stacks.count(d);
            if (n > bestN) { bestN = n; best = i; }
        }
        return best;
    }

    private static boolean creative(Minecraft mc) {
        try {
            Object abilities = Reflect.invoke(mc.player, "getAbilities");
            return Boolean.TRUE.equals(Reflect.field(abilities, "instabuild"));
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }

    static void tick(Minecraft mc, boolean macroRunning) {
        Config c = Config.get();
        if (!c.restock || macroRunning || mc.player == null || mc.gameMode == null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (AutoTotem.screenOpen(mc) || creative(mc)) return;
        Plan p = plan(mc);
        if (p == null) { waited = 0; return; }
        if (++waited <= c.restockDelayTicks) return;
        waited = 0;
        int result = execute(mc, p);
        cooldown = result == OK ? 8 : result == BUSY ? 20 : 100;
        if (result == FAILED && !warned) {
            warned = true;
            Steps.msg(mc, "PvP Macro: restock could not move items (details in logs/latest.log)");
        }
    }

    private static final int OK = 0, BUSY = 1, FAILED = 2;

    /** Clears the internal counters (used by the tests and when toggled). */
    static void reset() { waited = 0; cooldown = 0; warned = false; }

    private static int execute(Minecraft mc, Plan p) {
        Boolean empty = ContainerClicks.cursorEmpty(mc);
        if (empty == null || !empty) { PvpLog.info("restock: cursor not empty or unknown, trying again shortly"); return BUSY; }
        PvpLog.info("restock: " + p.what + " " + p.have + "/" + p.max + ", topping up from menu slot " + p.donor);
        if (!ContainerClicks.click(mc, p.donor, 0, "PICKUP")) return FAILED;          // donor stack onto the cursor
        if (!ContainerClicks.click(mc, p.target, 0, "PICKUP")) {                      // could not place it: put it back
            ContainerClicks.click(mc, p.donor, 0, "PICKUP");
            return FAILED;
        }
        Boolean left = ContainerClicks.cursorEmpty(mc);
        if (left != null && !left) {                                                  // left-over: back where it came from
            ContainerClicks.click(mc, p.donor, 0, "PICKUP");
            PvpLog.info("restock: put the left-over back");
        }
        return OK;
    }
}
