package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * NEW (1.0.5). Keeps a totem of undying in the offhand while switched on (keybind, off by default).
 * The offhand move uses names that could not be confirmed for 26.2, so it is done by reflection and logged:
 *   1) inventory click "swap with offhand" (ClickType.SWAP, button 40) - works for hotbar and inventory totems
 *   2) fallback, hotbar totems only: select the slot, send the swap-hands packet, select the old slot again
 */
final class AutoTotem {
    private static int waited = 0;
    private static boolean warned = false;

    private AutoTotem() {}

    static void toggle(Minecraft mc) {
        Config c = Config.get();
        c.autoTotemEnabled = !c.autoTotemEnabled;
        Config.save();
        waited = 0;
        Steps.msg(mc, "PvP Macro: auto totem " + (c.autoTotemEnabled ? "ON" : "OFF"));
    }

    private static int check = 0;
    private static boolean reportedNone = false;

    static void tick(Minecraft mc, boolean macroRunning) {
        if (mc.player == null || mc.gameMode == null) return;
        if (check > 0 && --check == 0) {   // a few ticks after a move: tell the player what happened
            boolean ok = mc.player.getItemInHand(InteractionHand.OFF_HAND).is(Items.TOTEM_OF_UNDYING);
            Steps.msg(mc, ok ? "PvP Macro: auto totem equipped" : "PvP Macro: auto totem sent a swap but the offhand is still not a totem");
        }
        if (!Config.get().autoTotemEnabled || macroRunning || screenOpen(mc)) return;
        ItemStack off = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.is(Items.TOTEM_OF_UNDYING)) { waited = 0; reportedNone = false; return; }
        if (!off.isEmpty() && !Config.get().autoTotemReplaceOffhand) { waited = 0; return; }
        int slot = findTotem(mc);
        if (slot < 0) {
            waited = 0;
            if (!reportedNone) { reportedNone = true; Steps.msg(mc, "PvP Macro: auto totem on, but no totem in your inventory"); }
            return;
        }
        reportedNone = false;
        if (++waited <= Config.get().autoTotemDelayTicks) return;
        waited = 0;
        moveToOffhand(mc, slot);
        check = 8;
    }

    static boolean screenOpen(Minecraft mc) {
        try { return Reflect.field(mc, "screen") != null; } catch (ReflectiveOperationException e) { return false; }
    }

    /** Inventory index 0-8 = hotbar, 9-35 = main inventory. */
    private static int findTotem(Minecraft mc) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    /**
     * 1.1.0: inventory totems work via one inventory click (ClickType.SWAP, button 40 = swap with offhand).
     * This works for both hotbar and main inventory slots when the inventory menu is open,
     * and via reflection for hotbar slots when no screen is open.
     * Fallback: for hotbar totems without an open screen, use the swap-hands packet.
     * Main inventory totems rely on the inventory-click method (requires screen open).
     */
    private static void moveToOffhand(Minecraft mc, int invSlot) {
        int menuSlot = Containers.menuSlotOfInventoryIndex(invSlot);
        // First choice: one inventory click "swap with offhand" (works for hotbar and main inventory through the screen)
        if (Containers.click(mc, menuSlot, 40, "SWAP")) {
            PvpLog.info("auto totem: swapped inventory slot " + invSlot + " (menu slot " + menuSlot + ") with the offhand");
            return;
        }
        // Fallback for hotbar only: select slot, send swap-hands packet, select old slot again
        if (invSlot < 9) {
            int prev = mc.player.getInventory().getSelectedSlot();
            SlotSync.select(mc, invSlot);
            boolean sent = sendSwapHands(mc);
            SlotSync.select(mc, prev);
            PvpLog.info("auto totem: swap-hands packet " + (sent ? "sent" : "NOT sent"));
            if (!sent && !warned) { warned = true; Steps.msg(mc, "PvP Macro: auto totem cannot move items on this version (see the log)"); }
        } else {
            // Main inventory: inventory click is the primary method; if it failed above, inform the user
            if (!warned) { warned = true; Steps.msg(mc, "PvP Macro: auto totem found totem in inventory but swap failed (ensure inventory screen is not open)"); }
            PvpLog.info("auto totem: inventory click swap failed for main inventory slot " + invSlot);
        }
    }

    /** The container click method, found by its parameters (int, int, int, ClickType, Player) so its name does not matter. */
    private static Method findClick(Object gameMode, Object player) {
        for (Method m : gameMode.getClass().getMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 5 && t[0] == int.class && t[1] == int.class && t[2] == int.class
                    && t[3].getSimpleName().equals("ClickType") && t[4].isAssignableFrom(player.getClass())) return m;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean sendSwapHands(Minecraft mc) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket");
            Class<?> action = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");
            Object swap = Enum.valueOf((Class<Enum>) action, "SWAP_ITEM_WITH_OFFHAND");
            Object packet = null;
            for (Constructor<?> c : packetClass.getConstructors()) {
                Class<?>[] t = c.getParameterTypes();
                if (t.length == 3 && t[0] == action && t[1] == BlockPos.class && t[2] == Direction.class) {
                    packet = c.newInstance(swap, BlockPos.ZERO, Direction.DOWN);
                    break;
                }
                if (t.length == 4 && t[0] == action && t[1] == BlockPos.class && t[2] == Direction.class && t[3] == int.class) {
                    packet = c.newInstance(swap, BlockPos.ZERO, Direction.DOWN, 0);
                    break;
                }
            }
            var conn = mc.getConnection();
            if (packet == null || conn == null) return false;
            conn.send((Packet<?>) packet);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("auto totem: swap-hands packet failed: " + e);
            return false;
        }
    }
}
