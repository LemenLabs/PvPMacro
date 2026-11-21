package com.pvpmacro;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Function;

/** Runs one macro at a time. Steps with delay 0 chain in the same tick. Key presses during a macro are queued (chainQueueSize). */
public class MacroRunner {
    private static final Logger LOG = LoggerFactory.getLogger("PvpMacro");
    private List<Step> steps = null;
    private int index = 0, delay = 0, startSlot = 0;
    private int repeats = 0;                       // consecutive ticks the current step asked to run again
    private final ArrayDeque<Function<Minecraft, List<Step>>> pending = new ArrayDeque<>();

    public boolean running() { return steps != null; }

    /** NEW. A macro key was pressed: run it now, or queue it behind the running one. */
    public void press(Minecraft mc, Function<Minecraft, List<Step>> action) {
        if (mc.player == null || mc.gameMode == null) return;
        if (!running()) { start(mc, action.apply(mc)); return; }
        if (pending.size() < Config.get().chainQueueSize) {
            pending.add(action);
            PvpLog.info("macro queued behind the running one (" + pending.size() + " waiting)");
        } else {
            PvpLog.info("macro key ignored: a macro is running and the queue is full");
        }
    }

    public void start(Minecraft mc, List<Step> list) {
        if (running() || mc.player == null || mc.gameMode == null) return;
        steps = list; index = 0; delay = 0; repeats = 0;
        startSlot = mc.player.getInventory().getSelectedSlot();
        Steps.clearTrail();
        AttributeSwapCompat.suppress();   // keep the Attribute Swap mod from re-routing this macro's hits
        PvpLog.info("macro start: " + list.size() + " steps, starting slot " + startSlot);
    }

    /** Drops a running macro without touching the hotbar (used when the world is left). */
    public void reset() {
        steps = null;
        pending.clear();
        Steps.releaseUseKey();
        AttributeSwapCompat.release();
    }

    public void cancel(Minecraft mc) { finish(mc, true); }

    public void tick(Minecraft mc) {
        if (steps == null) return;
        if (mc.player == null || mc.gameMode == null) { reset(); return; }
        if (delay > 0) { delay--; return; }
        while (steps != null && delay == 0) {
            int r;
            try {
                r = steps.get(index).run(mc);
            } catch (RuntimeException e) {          // never leave the runner stuck (or Attribute Swap paused)
                LOG.error("Macro step failed, cancelling macro", e);
                String why = String.valueOf(e.getMessage());
                Steps.TRAIL.add("ERROR " + (why.length() > 70 ? why.substring(0, 70) : why));
                finish(mc, true);
                return;
            }
            if (r == Step.REPEAT) {
                // 1.0.11 FIX: the runner never handled REPEAT. It stored -2 as the delay and the macro sat there forever, which is why
                // Bow Boost stopped after its first step. REPEAT means "run this same step again next tick".
                if (++repeats > 200) {
                    PvpLog.info("a step asked to repeat for 200 ticks, cancelling the macro");
                    Steps.TRAIL.add("STOP: a step waited too long");
                    Steps.msg(mc, "PvP Macro: a step waited too long, macro cancelled");
                    finish(mc, true);
                }
                return;
            }
            repeats = 0;
            PvpLog.info("step " + (index + 1) + "/" + steps.size() + " done, "
                    + (r == Step.ABORT ? "ABORT" : r == Step.NEXT_TICK ? "next step on the next tick"
                       : r == 0 ? "next step this tick" : "next step in " + (r + 1) + " ticks"));
            if (r == Step.ABORT || ++index >= steps.size()) { finish(mc, r == Step.ABORT); return; }
            if (r == Step.NEXT_TICK) return;      // delay stays 0, so the next tick's call continues straight away
            delay = r;
        }
    }

    private void finish(Minecraft mc, boolean aborted) {
        // Chain: a queued macro starts next; the hotbar is only restored after the LAST macro of the chain.
        if (!aborted && !pending.isEmpty()) {
            Function<Minecraft, List<Step>> next = pending.poll();
            try {
                List<Step> list = next.apply(mc);
                Steps.releaseUseKey();
                Steps.clearTrail();
                steps = list; index = 0; delay = 0; repeats = 0;
                PvpLog.info("macro finished; chaining the next queued macro (" + list.size() + " steps), slot restore skipped");
                return;
            } catch (RuntimeException e) {
                LOG.error("Could not start the queued macro", e);
            }
        }
        pending.clear();
        if (Config.get().showProgress && !Steps.TRAIL.isEmpty() && mc.player != null) {
            String line = "PvP Macro: " + String.join(" > ", Steps.TRAIL) + (aborted ? " > STOPPED" : " > done");
            Steps.msg(mc, line);
            if (Config.get().progressInChat) Steps.chatLine(mc, line);
        }
        PvpLog.info("macro finished, restoring slot " + startSlot + " (restoreSlot=" + Config.get().restoreSlot + ")");
        try {
            Steps.restoreLookIfPending(mc);
            // Synchronised restore: client slot AND server slot go back to where the macro started.
            if (mc.player != null && Config.get().restoreSlot) SlotSync.select(mc, startSlot);
        } finally {
            steps = null;
            Steps.releaseUseKey();
            AttributeSwapCompat.release();
        }
    }
}
