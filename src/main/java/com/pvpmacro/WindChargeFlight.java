package com.pvpmacro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/** Wind-charge launch when grounded; low-angle-independent ground bounce when already gliding. */
final class WindChargeFlight implements Step {
    private enum State { START, JUMP, WAIT_AIR, OPEN_ELYTRA, WAIT_GLIDE, AIM, THROW, RESTORE }
    private State state = State.START;
    private int ticks;
    private final Steps.LookMemo look = new Steps.LookMemo();
    private static boolean sprintHeld, jumpHeld;

    @Override public int run(Minecraft mc) {
        try {
            switch (state) {
                case START:
                    if (mc.player.isFallFlying()) {
                        Steps.rememberLook(look, () -> true).run(mc);
                        state = State.AIM;
                        return Step.REPEAT;
                    }
                    if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA))
                        return fail(mc, "equip an elytra first");
                    if (!mc.player.onGround()) return fail(mc, "not gliding; start from the ground");
                    Steps.rememberLook(look, () -> true).run(mc);
                    int selected = Steps.slot("wind charge", Steps.item(Items.WIND_CHARGE)).run(mc);
                    if (selected == Step.ABORT) return Step.ABORT;
                    key(mc, "keySprint", true);
                    key(mc, "keyJump", true);
                    state = State.JUMP;
                    Steps.TRAIL.add("jump");
                    return Step.REPEAT;
                case JUMP:
                    key(mc, "keyJump", false);
                    state = State.WAIT_AIR; ticks = 0;
                    return Step.REPEAT;
                case WAIT_AIR:
                    if (!mc.player.onGround()) { state = State.OPEN_ELYTRA; return Step.REPEAT; }
                    if (++ticks > 15) return fail(mc, "jump did not leave the ground");
                    return Step.REPEAT;
                case OPEN_ELYTRA:
                    key(mc, "keyJump", true);
                    state = State.WAIT_GLIDE; ticks = 0;
                    Steps.TRAIL.add("open elytra");
                    return Step.REPEAT;
                case WAIT_GLIDE:
                    key(mc, "keyJump", false);
                    if (mc.player.isFallFlying()) { state = State.AIM; return Step.REPEAT; }
                    if (++ticks > 20) return fail(mc, "elytra did not start gliding");
                    return Step.REPEAT;
                case AIM:
                    Steps.setPitch(() -> 80.0).run(mc);
                    state = State.THROW;
                    return Math.max(1, Config.get().aimSyncTicks);
                case THROW:
                    Steps.use().run(mc);
                    Steps.TRAIL.add("wind charge");
                    state = State.RESTORE;
                    return Step.REPEAT;
                case RESTORE:
                    Steps.restoreLook(look).run(mc);
                    key(mc, "keySprint", false);
                    key(mc, "keyJump", false);
                    return 0;
                default: return Step.ABORT;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("wind charge flight failed: " + e);
            return fail(mc, "could not run wind-charge sequence (see logs)");
        }
    }

    private static void key(Minecraft mc, String field, boolean down) throws ReflectiveOperationException {
        Object mapping = mc.options.getClass().getField(field).get(mc.options);
        KeyMapping.class.getMethod("setDown", boolean.class).invoke(mapping, down);
        if (field.equals("keySprint")) sprintHeld = down;
        if (field.equals("keyJump")) jumpHeld = down;
    }
    static void releaseKeys() {
        if (!sprintHeld && !jumpHeld) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc != null && mc.options != null) {
                if (sprintHeld) key(mc, "keySprint", false);
                if (jumpHeld) key(mc, "keyJump", false);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        sprintHeld = jumpHeld = false;
    }
    private static int fail(Minecraft mc, String why) {
        Steps.TRAIL.add("STOP: " + why);
        Steps.msg(mc, "PvP Macro: wind charge: " + why);
        return Step.ABORT;
    }
}
