package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/**
 * NEW FILE. Hotbar selection that is also sent to the server immediately.
 *
 * Mirrors com.attributeswap.SlotSwapper.switchTo (verified from that mod's bytecode):
 *   1. reject slots outside 0..8
 *   2. Inventory.setSelectedSlot(slot)               (client side)
 *   3. ClientPacketListener.send(new ServerboundSetCarriedItemPacket(slot))   (server side)
 *
 * The original PvP Macro only did step 2 and relied on the game sending the change later.
 * Doing step 3 here puts the slot change on the wire *in order* with the attack / use packets
 * that the macro sends right after it, in the same tick.
 */
final class SlotSync {
    private SlotSync() {}

    /** Selects a hotbar slot on the client and tells the server. Returns false if the slot was invalid. */
    static boolean select(Minecraft mc, int slot) {
        if (mc.player == null || slot < 0 || slot > 8) return false;
        mc.player.getInventory().setSelectedSlot(slot);
        PvpLog.info("select hotbar slot " + slot + " = " + mc.player.getInventory().getItem(slot));
        if (Config.get().explicitSlotPackets) {
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
        return true;
    }
}
