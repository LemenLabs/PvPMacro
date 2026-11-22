package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.entity.player.Player;

/**
 * NEW (1.1.0). Keeps a totem of undying in the offhand while switched on (keybind, off by default).
 * Works with totems in any inventory slot (hotbar or main inventory).
 * The offhand move uses inventory clicks with ClickType.SWAP (button 40),
 * with a fallback for hotbar totems using the swap-hands packet.
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
            boolean ok = mc.player.getItemInHand(net.minecraft.hand.Hand.OFF_HAND).is(net.minecraft.item.Items.TOTEM_OF_UNDYING);
            Steps.msg(mc, ok ? "PvP Macro: auto totem equipped" : "PvP Macro: auto totem sent a swap but the offhand is still not a totem");
        }
        if (!Config.get().autoTotemEnabled || macroRunning) return;

        // Check if offhand already has a totem
        ItemStack off = mc.player.getItemInHand(net.minecraft.hand.Hand.OFF_HAND);
        if (off.is(net.minecraft.item.Items.TOTEM_OF_UNDYING)) { waited = 0; reportedNone = false; return; }

        // If offhand is not empty and not set to replace, don't swap
        if (!off.isEmpty() && !Config.get().autoTotemReplaceOffhand) { waited = 0; return; }

        int slot = findTotem(mc);
        if (slot < 0) {
            waited = 0;
            if (!reportedNone) {
                reportedNone = true;
                Steps.msg(mc, "PvP Macro: auto totem on, but no totem in your inventory");
            }
            return;
        }
        reportedNone = false;

        // Throttle the swap to once every few ticks to avoid spamming
        if (++waited <= Config.get().autoTotemDelayTicks) return;
        waited = 0;

        moveToOffhand(mc, slot);
        check = 8;  // wait 8 ticks before checking again
    }

    /**
     * Verify that the offhand still has a totem after a tick.
     * This helps detect cases where the totem was consumed and we need to re-equip.
     */
    static void verifyOffhand(Minecraft mc) {
        if (mc.player == null) return;
        ItemStack off = mc.player.getItemInHand(net.minecraft.hand.Hand.OFF_HAND);
        if (!off.is(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
            // Offhand no longer has a totem - the macro will re-equip on next tick
        }
    }

    /**
     * Inventory index 0-8 = hotbar, 9-35 = main inventory.
     * Searches all 36 slots for a totem.
     */
    private static int findTotem(Minecraft mc) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && stack.is(net.minecraft.item.Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    /**
     * Move a totem from the given inventory slot to the offhand.
     * 1. First choice: one click in the inventory menu with ClickType.SWAP (button 40),
     *    works for both hotbar and inventory totems.
     * 2. Fallback for hotbar totems only: select the slot, send the swap-hands packet,
     *    select the old slot again.
     */
    private static void moveToOffhand(Minecraft mc, int invSlot) {
        int menuSlot = Containers.menuSlotOfInventoryIndex(invSlot);

        // First choice: inventory click with SWAP
        if (Containers.click(mc, menuSlot, 40, "SWAP")) {
            Steps.log("auto totem: swapped inventory slot " + invSlot + " (menu slot " + menuSlot + ") with the offhand");
            return;
        }

        // Fallback for hotbar totems only
        if (invSlot < 9) {
            int prev = mc.player.getInventory().getSelectedSlot();
            // Select the totem slot
            boolean slotSelected = Containers.select(mc, invSlot);
            // Send swap-hands packet
            boolean packetSent = sendSwapHands(mc);
            // Restore previous selection
            Containers.select(mc, prev);

            if (packetSent) {
                Steps.log("auto totem: swap-hands packet sent for hotbar slot " + invSlot);
            } else if (!warned) {
                warned = true;
                Steps.msg(mc, "PvP Macro: auto totem cannot move items on this version (see the log)");
            }
        } else {
            // Inventory totems: if SWAP click didn't work, suggest putting totem in hotbar
            Steps.log("auto totem: no container click available for an inventory totem; put a totem in your hotbar for best results");
            if (!warned) {
                warned = true;
                Steps.msg(mc, "PvP Macro: auto totem needs the totem in your hotbar on this version (see the log)");
            }
        }
    }

    /** The swap-hands packet fallback for hotbar totems. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean sendSwapHands(Minecraft mc) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket");
            Class<?> action = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");
            Object swap = Enum.valueOf((Class<Enum>) action, "SWAP_ITEM_WITH_OFFHAND");
            Object packet = null;

            // Try different constructors
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
            Steps.log("auto totem: swap-hands packet failed: " + e);
            return false;
        }
    }

    /** Called from Steps.releaseUseKey() when any macro ends or is cancelled, so these keys are never left held. */
    static void releaseKeys() {
        // No keys to release for auto totem
    }
}