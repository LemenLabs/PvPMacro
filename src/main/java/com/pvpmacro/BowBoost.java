package com.pvpmacro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;

/**
 * NEW (1.0.8). Fully automatic bow boost, following the sequence of the "Bow Boostin" mod (CC0 / public domain):
 * check bow + arrows + on the ground, save the camera, sprint, jump, wait for the top of the jump (vertical speed near 0),
 * look up, draw for a few ticks, release, restore the camera. One state per tick, like that mod.
 * Names that could not be confirmed for 26.2 go through reflection and fail with a message, not a build error.
 * Bow Boostin itself targets 1.21.11 with different (intermediary) names, so only the sequence and numbers were reused.
 */
final class BowBoost implements Step {
    private enum S { START, JUMP, APEX, AIM, DRAW, RESTORE, HOLD }

    private S state = S.START;
    private int ticks = 0;
    private final Steps.LookMemo look = new Steps.LookMemo();
    private static boolean sprintHeld = false, jumpHeld = false;

    @Override
    public int run(Minecraft mc) {
        if (AutoTotem.screenOpen(mc)) {
            return fail(mc, "a screen opened");
        }
        try {
            switch (state) {
                case START:   return start(mc);
                case JUMP:    setKey(mc, "keyJump", true); Steps.TRAIL.add("jump"); state = S.APEX; ticks = 0; return Step.REPEAT;
                case APEX:    return apex(mc);
                case AIM:     return aim(mc);
                case DRAW:    return draw(mc);
                case RESTORE: return restore(mc);
                case HOLD:    return hold(mc);
                default:      return Step.ABORT;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("bow boost failed in state " + state + ": " + e);
            return fail(mc, "cannot run on this version in state " + state + " (see the log)");
        }
    }

    private int start(Minecraft mc) throws ReflectiveOperationException {
        if (!hasArrows(mc)) return fail(mc, "no arrows in your inventory");
        if (!onGround(mc)) return fail(mc, "you must be on the ground");
        Steps.rememberLook(look, () -> Config.get().bowBoostRestoreLook).run(mc);
        Reflect.invoke(mc.player, "setSprinting", true);
        setKey(mc, "keySprint", true);
        Steps.TRAIL.add("sprint");
        state = S.JUMP;
        return Step.REPEAT;
    }

    private int apex(Minecraft mc) throws ReflectiveOperationException {
        if (ticks == 1) setKey(mc, "keyJump", false);       // jump key is only tapped
        ticks++;
        double vy = velocityY(mc);
        if (ticks >= 4 && vy <= 0.05) { Steps.TRAIL.add("top of jump"); state = S.AIM; ticks = 0; return Step.REPEAT; }
        if (ticks > 15) return fail(mc, "never reached the top of the jump");
        return Step.REPEAT;
    }

    private int aim(Minecraft mc) {
        Steps.setPitch(() -> Config.get().bowBoostPitch).run(mc);
        snapOldPitch(mc, (float) Math.max(-90.0, Math.min(90.0, Config.get().bowBoostPitch)));
        Steps.useAndHold().run(mc);
        Steps.TRAIL.add("aim+draw");
        state = S.DRAW; ticks = 0;
        return Step.REPEAT;
    }

    private int draw(Minecraft mc) {
        ticks++;
        // A bow only fires once it has a minimum pull, and the server counts the draw a tick or so shorter than the
        // client, so releasing after 3 ticks fired nothing. 4 is the floor; the default is 5 (same as insta cart).
        if (ticks >= Math.max(3, Config.get().bowBoostDrawTicks)) {
            Steps.release().run(mc);
            Steps.TRAIL.add("shot");
            state = S.RESTORE;
        }
        return Step.REPEAT;
    }

    private int restore(Minecraft mc) throws ReflectiveOperationException {
        boolean doRestore = look.saved && look.restore;
        float pitch = look.pitch;
        Steps.restoreLook(look).run(mc);
        if (doRestore) snapOldPitch(mc, pitch);
        Steps.TRAIL.add("look back");
        state = S.HOLD; ticks = 0;                           // keep sprinting a little so you keep pace with the falling arrow
        return Step.REPEAT;
    }

    private int hold(Minecraft mc) throws ReflectiveOperationException {
        ticks++;
        if (ticks >= Config.get().bowBoostSprintHoldTicks) {
            setKey(mc, "keySprint", false);
            return 0;                                        // finished
        }
        return Step.REPEAT;
    }

    // ---------------- helpers ----------------
    /** Puts the reason in the progress line too (that line replaces the plain message when the macro ends). */
    private static int fail(Minecraft mc, String reason) {
        Steps.TRAIL.add("STOP: " + reason);
        Steps.msg(mc, "PvP Macro: bow boost: " + reason);
        return Step.ABORT;
    }

    private static boolean hasArrows(Minecraft mc) {
        try {
            Object abilities = Reflect.invoke(mc.player, "getAbilities");
            if (Boolean.TRUE.equals(Reflect.field(abilities, "instabuild"))) return true;   // creative needs no arrows
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        if (mc.player.getItemInHand(InteractionHand.OFF_HAND).is(ItemTags.ARROWS)) return true;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(ItemTags.ARROWS)) return true;
        }
        return false;
    }

    private static boolean onGround(Minecraft mc) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(Reflect.invokeAny(mc.player, new String[]{"onGround", "isOnGround"}));
    }

    private static double velocityY(Minecraft mc) throws ReflectiveOperationException {
        Object v = Reflect.invokeAny(mc.player, new String[]{"getDeltaMovement", "getVelocity"});
        return Steps.comp(v, "y");
    }

    /** Previous-tick pitch too, so the camera does not slide between the old and new angle on screen. */
    private static void snapOldPitch(Minecraft mc, float pitch) {
        try { mc.player.getClass().getField("xRotO").setFloat(mc.player, pitch); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static void setKey(Minecraft mc, String field, boolean down) throws ReflectiveOperationException {
        Object key = mc.options.getClass().getField(field).get(mc.options);
        KeyMapping.class.getMethod("setDown", boolean.class).invoke(key, down);
        if (field.equals("keySprint")) sprintHeld = down;
        else if (field.equals("keyJump")) jumpHeld = down;
    }

    /** Called from Steps.releaseUseKey() when any macro ends or is cancelled, so these keys are never left held. */
    static void releaseKeys() {
        if (!sprintHeld && !jumpHeld) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc != null && mc.options != null) {
                if (sprintHeld) setKey(mc, "keySprint", false);
                if (jumpHeld) setKey(mc, "keyJump", false);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        sprintHeld = false;
        jumpHeld = false;
    }
}
