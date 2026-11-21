package com.pvpmacro;

import net.minecraft.client.Minecraft;
import java.lang.reflect.Method;

/** NEW (1.0.6). Writes ONE log block per game start listing the camera / container-click methods this Minecraft version really has. */
final class Diagnostics {
    private static boolean done = false;
    private Diagnostics() {}

    static void runOnce(Minecraft mc) {
        if (done || mc.player == null || mc.gameMode == null) return;
        done = true;
        PvpLog.info("diag: player methods with rot/yaw/pitch: " + names(mc.player.getClass(), "rot", "yaw", "pitch"));
        PvpLog.info("diag: game mode methods with click/container/inventory: " + names(mc.gameMode.getClass(), "click", "container", "inventory"));
    }

    private static String names(Class<?> c, String... needles) {
        StringBuilder b = new StringBuilder();
        for (Method m : c.getMethods()) {
            String n = m.getName().toLowerCase();
            for (String needle : needles) {
                if (n.contains(needle)) {
                    b.append(m.getName()).append('(');
                    Class<?>[] t = m.getParameterTypes();
                    for (int i = 0; i < t.length; i++) b.append(i > 0 ? "," : "").append(t[i].getSimpleName());
                    b.append(")->").append(m.getReturnType().getSimpleName()).append("; ");
                    break;
                }
            }
        }
        return b.toString();
    }
}
