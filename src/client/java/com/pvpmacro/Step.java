package com.pvpmacro;

import net.minecraft.client.Minecraft;

/** One action. Returns ticks to wait afterwards, or ABORT to cancel the whole macro. */
@FunctionalInterface
public interface Step {
    int ABORT = -1;
    int NEXT_TICK = -3; // run the next step at the very next client tick (0 = same tick, 1 = two ticks later)
    int REPEAT = -2;   // run this same step again next tick (used to wait for a condition)
    int run(Minecraft mc);
}
