package com.pvpmacro;

import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NEW (1.0.13). Clicks on the player's own inventory menu (swap with offhand, pick up / place stacks).
 * 26.x renamed the click method and its enum (handleInventoryMouseClick/ClickType became handleContainerInput/ContainerInput),
 * and none of those names could be confirmed here, so the method is FOUND BY ITS SHAPE: five parameters
 * (int, int, int, an enum that has the wanted constant, the player). Renames cannot break it. Everything is logged.
 * Menu slot numbers of the player inventory: 0 result, 1-4 crafting, 5-8 armour, 9-35 main inventory, 36-44 hotbar, 45 offhand.
 */
final class ContainerClicks {
    private static boolean logged = false;

    private ContainerClicks() {}

    /** Inventory index (0-8 hotbar, 9-35 main) to the menu slot number. */
    static int menuSlot(int inventoryIndex) { return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex; }
    static final int OFFHAND_MENU_SLOT = 45;

    private static Object menu(Minecraft mc) throws ReflectiveOperationException {
        try {
            return Reflect.field(mc.player, "inventoryMenu");
        } catch (NoSuchFieldException e) {
            for (Field f : mc.player.getClass().getFields()) {
                if (f.getType().getSimpleName().equals("InventoryMenu")) return f.get(mc.player);
            }
            throw e;
        }
    }

    /** One click. input is the enum constant name: "SWAP" (button 40 = offhand) or "PICKUP" (button 0 = left click). */
    static boolean click(Minecraft mc, int menuSlot, int button, String input) {
        try {
            Object menu = menu(mc);
            int containerId = ((Number) Reflect.field(menu, "containerId")).intValue();
            StringBuilder candidates = new StringBuilder();
            for (Method m : mc.gameMode.getClass().getMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (t.length != 5) continue;
                candidates.append(' ').append(m.getName());
                if (t[0] != int.class || t[1] != int.class || t[2] != int.class) continue;
                if (!t[3].isEnum() || !t[4].isInstance(mc.player)) continue;
                Object constant = Reflect.enumConstant(t[3], input);
                if (constant == null) continue;
                m.invoke(mc.gameMode, containerId, menuSlot, button, constant, mc.player);
                if (!logged) { logged = true; PvpLog.info("container clicks go through " + m.getName() + " (enum " + t[3].getSimpleName() + ")"); }
                return true;
            }
            PvpLog.info("no container-click method found on " + mc.gameMode.getClass().getName()
                    + " for " + input + " (5-parameter methods:" + (candidates.length() == 0 ? " none" : candidates) + ")");
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("container click " + input + " failed: " + e);
        }
        return false;
    }

    /** true = the cursor holds nothing, false = it holds an item, null = cannot tell. */
    static Boolean cursorEmpty(Minecraft mc) {
        try {
            Object carried = Reflect.invoke(menu(mc), "getCarried");
            return (Boolean) Reflect.invoke(carried, "isEmpty");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
