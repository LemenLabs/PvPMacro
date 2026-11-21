package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

/**
 * NEW (1.1.0). Clicks in the player's own inventory (menu id 0, only used while no screen is open), shared by Auto Totem and Restock.
 * The click method is found by its parameters (int, int, int, ClickType, Player) so its name does not matter.
 * Menu slots: 0-8 crafting/armor area, 9-35 main inventory, 36-44 hotbar, 45 offhand.
 */
final class Containers {
    static final int OFFHAND_MENU_SLOT = 45;
    private static Method click;
    private static Class<?> clickTypeClass;
    private static boolean searched = false;

    private Containers() {}

    /** Inventory index (0-8 hotbar, 9-35 main) to menu slot number. */
    static int menuSlotOfInventoryIndex(int invIndex) { return invIndex < 9 ? 36 + invIndex : invIndex; }

    private static void find(Minecraft mc) {
        if (searched || mc.gameMode == null || mc.player == null) return;
        searched = true;
        for (Method m : mc.gameMode.getClass().getMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 5 && t[0] == int.class && t[1] == int.class && t[2] == int.class
                    && t[3].isEnum() && t[3].getSimpleName().equals("ClickType") && t[4].isAssignableFrom(mc.player.getClass())) {
                click = m;
                clickTypeClass = t[3];
                PvpLog.info("container click method: " + m.getName());
                return;
            }
        }
        PvpLog.info("NO container click method (int,int,int,ClickType,Player) found on " + mc.gameMode.getClass().getName()
                + " - see the 'diag:' lines for what exists");
    }

    /** One click: type is a ClickType name (PICKUP, SWAP, QUICK_MOVE ...), button as in vanilla (SWAP with 40 = swap with the offhand). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean click(Minecraft mc, int menuSlot, int button, String type) {
        find(mc);
        if (click == null) return false;
        try {
            Object clickType = Enum.valueOf((Class) clickTypeClass, type);
            click.invoke(mc.gameMode, 0, menuSlot, button, clickType, mc.player);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("container click failed (" + type + " slot " + menuSlot + "): " + e);
            return false;
        }
    }

    /** True when nothing is held on the mouse cursor (or it cannot be read). */
    static boolean cursorEmpty(Minecraft mc) {
        try {
            Object menu = Reflect.field(mc.player, "containerMenu");
            Object carried = Reflect.invokeAny(menu, new String[]{"getCarried"});
            return ((ItemStack) carried).isEmpty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }
}
