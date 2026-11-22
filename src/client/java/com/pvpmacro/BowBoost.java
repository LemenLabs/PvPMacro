package com.pvpmacro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * NEW (1.0.8). Fully automatic bow boost, following the sequence of the "Bow Boostin" mod (CC0 / public domain):
 * check bow + arrows + on the ground, save the camera, sprint, jump, wait for the top of the jump (vertical speed near 0),
 * look up, draw for a few ticks, release, restore the camera. One state per tick, like that mod.
 * Names that could not be confirmed for 26.2 go through reflection and fail with a message, not a build error.
 * Bow Boostin itself targets 1.21.11 with different (intermediary) names, so only the sequence and numbers were reused.
 */
final class BowBoost implements Step {
    private enum S { START, JUMP, APEX, AIM, DRAW, RESTORE, CHASE }

    private S state = S.START;
    private int ticks = 0;
    private final Steps.LookMemo look = new Steps.LookMemo();
    private static boolean sprintHeld = false, jumpHeld = false, forwardHeld = false;
    private Object arrow = null;          // 1.0.13: your own arrow, once it has been seen
    private boolean sawArrow = false;
    private double closest = Double.MAX_VALUE;     // closest the arrow got to you (blocks), logged so you can tune draw ticks / pitch

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
                case CHASE:   return chase(mc);
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
        // 1.0.13: sprinting only lasts while you move forward, and the macro never pressed forward, so without W held the
        // sprint was dropped at once. Forward is held for the whole boost (and let go again at the end).
        if (Config.get().bowBoostChaseArrow) setKey(mc, "keyUp", true);
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
        if (ticks >= Math.max(4, Config.get().bowBoostDrawTicks)) {
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
        if (Config.get().bowBoostChaseArrow) {               // keep running, steered by the arrow (see chase)
            state = S.CHASE; ticks = 0; arrow = null; sawArrow = false; closest = Double.MAX_VALUE;
            return Step.REPEAT;
        }
        setKey(mc, "keySprint", false);
        return 0;                                            // finished
    }

    /**
     * NEW (1.0.13). After the shot the arrow flies on with the speed you had, while drawing slowed you down and the old code
     * stopped sprinting at once, so you fell behind and the arrow landed ahead of you. Now your own arrow is tracked and
     * forward + sprint are held while the arrow is ahead of you (or level), and let go when you have clearly passed it.
     * Ends when the arrow is gone (it hit you or landed), if it never shows up, or after bowBoostRunTicks.
     */
    private int chase(Minecraft mc) throws ReflectiveOperationException {
        ticks++;
        Object found = findArrow(mc);
        if (found != null) {
            arrow = found;
            sawArrow = true;
            double ahead = aheadOfPlayer(mc, found), dist = distanceTo(mc, found);
            closest = Math.min(closest, dist);
            PvpLog.info(String.format("bow boost chase tick %d: arrow %.1f blocks %s you, %.1f away", ticks, Math.abs(ahead), ahead >= 0 ? "AHEAD of" : "BEHIND", dist));
            boolean forward = ahead > -0.3;
            setKey(mc, "keyUp", forward);
            setKey(mc, "keySprint", forward);
        } else if (sawArrow || ticks > 8) {
            return endChase(mc, sawArrow ? "arrow gone" : "arrow not seen");
        }
        if (ticks >= Config.get().bowBoostRunTicks) return endChase(mc, "run time over");
        return Step.REPEAT;
    }

    private int endChase(Minecraft mc, String why) throws ReflectiveOperationException {
        setKey(mc, "keyUp", false);
        setKey(mc, "keySprint", false);
        String result = sawArrow ? String.format("closest approach %.1f blocks (%s)", closest, closest < 1.2 ? "should have hit" : "missed: change Draw ticks or Camera pitch in the menu") : "arrow not seen";
        PvpLog.info("bow boost chase over: " + why + ", " + result);
        Steps.TRAIL.add(why);
        return 0;
    }

    private static double distanceTo(Minecraft mc, Object arrow) throws ReflectiveOperationException {
        double dx = Steps.comp(arrow, "x") - Steps.comp(mc.player, "x");
        double dy = Steps.comp(arrow, "y") - (Steps.comp(mc.player, "y") + 0.9);
        double dz = Steps.comp(arrow, "z") - Steps.comp(mc.player, "z");
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Your arrow: the one already tracked if it is still there, otherwise the arrow nearest to your head. */
    private Object findArrow(Minecraft mc) throws ReflectiveOperationException {
        double px = Steps.comp(mc.player, "x"), py = Steps.comp(mc.player, "y"), pz = Steps.comp(mc.player, "z");
        AABB box = new AABB(px - 8, py - 4, pz - 8, px + 8, py + 14, pz + 8);
        Object best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if (!type.endsWith("arrow")) continue;           // arrow, spectral_arrow
            if (arrow != null) { if (e == arrow) return e; continue; }
            double dx = Steps.comp(e, "x") - px, dy = Steps.comp(e, "y") - (py + 1.5), dz = Steps.comp(e, "z") - pz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD && d < 36) { bestD = d; best = e; }
        }
        return best;
    }

    /** Positive = the arrow is in front of you (along where you face), negative = behind you. */
    private static double aheadOfPlayer(Minecraft mc, Object arrow) throws ReflectiveOperationException {
        double yaw = Math.toRadians(((Number) Reflect.invokeAny(mc.player, new String[]{"getYRot", "getYaw"})).doubleValue());
        double dx = Steps.comp(arrow, "x") - Steps.comp(mc.player, "x");
        double dz = Steps.comp(arrow, "z") - Steps.comp(mc.player, "z");
        return dx * -Math.sin(yaw) + dz * Math.cos(yaw);
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
        else if (field.equals("keyUp")) forwardHeld = down;
    }

    /** Called from Steps.releaseUseKey() when any macro ends or is cancelled, so these keys are never left held. */
    static void releaseKeys() {
        if (!sprintHeld && !jumpHeld && !forwardHeld) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc != null && mc.options != null) {
                if (sprintHeld) setKey(mc, "keySprint", false);
                if (jumpHeld) setKey(mc, "keyJump", false);
                if (forwardHeld) setKey(mc, "keyUp", false);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        sprintHeld = false;
        jumpHeld = false;
        forwardHeld = false;
    }
}
