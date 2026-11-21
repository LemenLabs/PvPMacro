package com.pvpmacro;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.function.Predicate;

public final class Steps {
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private static BlockPos lastPlaced = null;

    /** Action bar message. If this line ever stops compiling, it is the only place to change. */
    static void msg(Minecraft mc, String text) {
        Component c = Component.literal(text);
        PvpLog.info("message: " + text);
        // 1.0.10: LocalPlayer.sendOverlayMessage(Component) is confirmed in 26.2 (the Attribute Swap mod calls it). The old lookup below
        // may not have found any action-bar method on this version, which would mean NO "PvP Macro: ..." message was ever shown.
        try {
            mc.player.sendOverlayMessage(c);
            return;
        } catch (Throwable t) {
            PvpLog.info("sendOverlayMessage failed: " + t);
        }
        // Uses reflection so a renamed method can never break the build: tries the action bar, then chat.
        try {
            for (java.lang.reflect.Method m : mc.gui.getClass().getMethods()) {
                Class<?>[] t = m.getParameterTypes();
                String n = m.getName().toLowerCase();
                if (t.length == 2 && t[0] == Component.class && t[1] == boolean.class
                        && (n.contains("overlay") || n.contains("actionbar"))) {
                    m.invoke(mc.gui, c, false);
                    return;
                }
            }
            Object chat = mc.gui.getClass().getMethod("getChat").invoke(mc.gui);
            for (java.lang.reflect.Method m : chat.getClass().getMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (t.length == 1 && t[0] == Component.class && m.getName().toLowerCase().contains("message")) {
                    m.invoke(chat, c);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        System.out.println("[PvP Macro] " + text);
    }

    // ---------- item matchers ----------
    public static Predicate<ItemStack> item(Item i) { return s -> s.is(i); }
    public static Predicate<ItemStack> food() { return s -> s.has(DataComponents.FOOD); }

    // 1.0.12: carting works with ANY rail in the hotbar. The names are looked up at runtime so a wrong name can never break the
    // build; Items.RAIL itself is confirmed in 26.2. Whichever rail is already held is kept, otherwise the first one in the hotbar.
    private static java.util.List<Item> railItems = null;
    private static java.util.List<Item> railItems() {
        if (railItems == null) {
            java.util.List<Item> l = new java.util.ArrayList<>();
            l.add(Items.RAIL);
            for (String n : new String[]{"POWERED_RAIL", "DETECTOR_RAIL", "ACTIVATOR_RAIL"}) {
                try { l.add((Item) Items.class.getField(n).get(null)); }
                catch (ReflectiveOperationException | RuntimeException e) { PvpLog.info("rail item " + n + " not found: " + e); }
            }
            railItems = l;
        }
        return railItems;
    }
    public static Predicate<ItemStack> rails() {
        return s -> { for (Item i : railItems()) if (s.is(i)) return true; return false; };
    }

    /** Wait n ticks, written the way a returned delay works: 0 = same tick, 1 = next tick (NEXT_TICK), n = (n-1). */
    static int ticksToDelay(int n) { return n <= 0 ? 0 : n == 1 ? Step.NEXT_TICK : n - 1; }
    public static Predicate<ItemStack> chestplate() { return s -> s.is(ItemTags.CHEST_ARMOR); }
    public static Predicate<ItemStack> withEnchant(Predicate<ItemStack> base, ResourceKey<Enchantment> key) {
        return s -> base.test(s) && hasEnchant(s, key);
    }
    public static boolean hasEnchant(ItemStack s, ResourceKey<Enchantment> key) {
        for (Holder<Enchantment> h : s.getEnchantments().keySet()) if (h.is(key)) return true;
        return false;
    }

    // ---------- slot selection (hotbar only) ----------
    /** First hotbar slot matching the first matcher that matches anything, or -1. */
    @SafeVarargs
    private static int findHotbar(Minecraft mc, Predicate<ItemStack>... prefs) {
        int held = mc.player.getInventory().getSelectedSlot();
        for (Predicate<ItemStack> p : prefs) {
            if (p.test(mc.player.getInventory().getItem(held))) return held;   // already holding it: keep it
            for (int i = 0; i < 9; i++) {
                if (p.test(mc.player.getInventory().getItem(i))) return i;
            }
        }
        return -1;
    }

    /**
     * Tries each matcher in order; aborts the macro with a message if none is in the hotbar.
     * CHANGED: selection now goes through SlotSync, so the server is told immediately (see SlotSync).
     */
    @SafeVarargs
    public static Step slot(String name, Predicate<ItemStack>... prefs) {
        return mc -> {
            int found = findHotbar(mc, prefs);
            if (found >= 0) {
                if (found == mc.player.getInventory().getSelectedSlot()) {   // 1.0.8: already holding it, no switch, no packet
                    PvpLog.info("already holding " + name + " (slot " + found + ")");
                    return 0;
                }
                SlotSync.select(mc, found);
                return 0;
            }
            msg(mc, "PvP Macro: no " + name + " in hotbar");
            return Step.ABORT;
        };
    }

    /** NEW. Remembers which hotbar slot the base-damage weapon of an attribute swap lives in. */
    public static final class SlotMemo { int slot = -1; }

    /**
     * NEW. First half of an attribute swap: select the base-damage weapon (the sword) and remember its slot.
     * If that changed the selection, wait settleTicks so the server has ticked with the sword in hand
     * (that tick is when the server refreshes the attack-damage attribute from the held item).
     * If the sword is already held no wait is needed.
     */
    @SafeVarargs
    public static Step baseSlot(String name, SlotMemo memo, java.util.function.IntSupplier settleTicks,
                                Predicate<ItemStack>... prefs) {
        return mc -> {
            int found = findHotbar(mc, prefs);
            if (found < 0) {
                msg(mc, "PvP Macro: no " + name + " in hotbar");
                return Step.ABORT;
            }
            memo.slot = found;
            if (mc.player.getInventory().getSelectedSlot() == found) return 0;
            SlotSync.select(mc, found);
            int wait = settleTicks.getAsInt();
            return wait == Step.NEXT_TICK ? wait : Math.max(0, wait);
        };
    }

    /** NEW. Second half of an attribute swap: go back to the base weapon in the same tick as the hit. */
    public static Step backToBase(SlotMemo memo) {
        return mc -> {
            if (memo.slot >= 0) SlotSync.select(mc, memo.slot);
            return 0;
        };
    }

    // ---------- actions ----------
    /** Attack whatever entity is under the crosshair. */
    public static Step attack() {
        return mc -> {
            if (mc.hitResult instanceof EntityHitResult hit) {
                PvpLog.info("attack " + hit.getEntity());
                mc.gameMode.attack(mc.player, hit.getEntity());
                mc.player.swing(HAND);
            } else {
                PvpLog.info("attack SKIPPED: no entity under the crosshair");
            }
            return 0;
        };
    }

    /**
     * One left-click made RIGHT NOW (same tick), so a swap + click + swap back is a real attribute swap.
     * It calls the game's own Minecraft.startAttack() by reflection (that is what a mouse click runs; it is private).
     * That name is NOT confirmed against the 26.2 classes I had; if it is missing or fails, this falls back to the
     * old queued KeyMapping.click, which the game only processes on the next tick (then the next step runs next tick).
     */
    public static Step clickAttack() {
        return mc -> {
            Boolean done = startAttackNow(mc);
            if (done != null) {
                PvpLog.info("left click via startAttack() -> " + done);
                return 0;
            }
            PvpLog.info("queue left click (startAttack() not usable, using KeyMapping.click)");
            KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
            return Step.NEXT_TICK;
        };
    }

    private static Boolean startAttackNow(Minecraft mc) {
        try {
            // startAttack() refuses to do anything while the game's own click delay (missTime, 10 ticks after a miss) is
            // running, which would make the lunge silently do nothing, so clear it first (reflection; logged).
            try {
                java.lang.reflect.Field delay = Minecraft.class.getDeclaredField("missTime");
                delay.setAccessible(true);
                PvpLog.info("click delay (missTime) was " + delay.getInt(mc) + ", clearing it");
                delay.setInt(mc, 0);
            } catch (ReflectiveOperationException | RuntimeException e) {
                PvpLog.info("could not read/clear missTime: " + e);
            }
            java.lang.reflect.Method m = Minecraft.class.getDeclaredMethod("startAttack");
            m.setAccessible(true);
            return (Boolean) m.invoke(mc);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("startAttack() unavailable: " + e);
            return null;
        }
    }

    /** Right-click with the held item (throw, fire loaded crossbow, equip armour, start drawing a bow). */
    public static Step use() {
        return mc -> {
            Object result = mc.gameMode.useItem(mc.player, HAND);
            PvpLog.info("use item (holding " + held(mc) + ") -> " + result);
            return 0;
        };
    }

    /** Release a held-use item (bow, trident). */
    public static Step release() {
        return mc -> {
            releaseUseKey();
            PvpLog.info("release item");
            mc.gameMode.releaseUsingItem(mc.player);
            return 0;
        };
    }

    // ---------- holding the use key while charging (bow, trident) ----------
    // Vanilla: while you are using an item and the use key is NOT held, the client releases the item on the very next
    // tick. A macro does not hold right-click, so a bow or trident used by use() was let go after ~1 tick however long
    // the macro waited (too little charge, so the bow never fired). The key is therefore held here until release().
    private static boolean useKeyHeld = false;

    private static boolean setUseKey(Minecraft mc, boolean down) {
        try {
            Object keyUse = mc.options.getClass().getField("keyUse").get(mc.options);
            KeyMapping.class.getMethod("setDown", boolean.class).invoke(keyUse, down);
            useKeyHeld = down;
            PvpLog.info((down ? "holding" : "letting go of") + " the use key");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("could not " + (down ? "hold" : "release") + " the use key: " + e);
            return false;
        }
    }

    /** Always call when a macro ends / is cancelled / the world is left, so the use key is never left held. */
    static void releaseUseKey() {
        BowBoost.releaseKeys();      // 1.0.8: also lets go of the sprint / jump keys the automatic bow boost may hold
        if (useKeyHeld) {
            useKeyHeld = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) setUseKey(mc, false);
        }
    }

    /** NEW. Right-click and keep the use key held (bow, trident charge). Pair with release(). */
    public static Step useAndHold() {
        return mc -> {
            setUseKey(mc, true);
            Object result = mc.gameMode.useItem(mc.player, HAND);
            PvpLog.info("use item and hold (holding " + held(mc) + ") -> " + result);
            return 0;
        };
    }

    // ---------- hunger (Lunge needs 6+) ----------
    private static Integer foodLevel(Minecraft mc) {
        try {
            Object food = mc.player.getClass().getMethod("getFoodData").invoke(mc.player);
            return (Integer) food.getClass().getMethod("getFoodLevel").invoke(food);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** NEW. Cancel with a message when hunger is below min. If hunger cannot be read it only logs and carries on. */
    public static Step requireHunger(int min, String what) {
        return mc -> {
            Integer food = foodLevel(mc);
            PvpLog.info("hunger = " + (food == null ? "unknown" : food));
            if (food != null && food < min) {
                msg(mc, "PvP Macro: " + what + " needs at least " + min + " hunger");
                return Step.ABORT;
            }
            return 0;
        };
    }

    /**
     * NEW. Wait so the NEXT step runs exactly n ticks after this one (n=1: next tick, n=2: the tick after, ...).
     * 0 or less: no wait. (pause() keeps its old meaning, where a value of n means n+1 ticks.)
     */
    public static Step waitTicks(java.util.function.IntSupplier n) {
        return mc -> { int v = n.getAsInt(); return v <= 0 ? 0 : v == 1 ? Step.NEXT_TICK : v - 1; };
    }

    /** NEW. Show a message and cancel the macro. */
    public static Step abortWith(String text) {
        return mc -> { msg(mc, text); return Step.ABORT; };
    }

    public static Step pause(int ticks) { return mc -> ticks; }
    public static Step pause(java.util.function.IntSupplier ticks) { return mc -> ticks.getAsInt(); }

    private static ItemStack held(Minecraft mc) {
        return mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot());
    }

    private static void useOn(Minecraft mc, BlockHitResult hit) {
        Object result = mc.gameMode.useItemOn(mc.player, HAND, hit);
        PvpLog.info("use on block " + hit.getBlockPos() + " (holding " + held(mc) + ") -> " + result);
    }

    /** Place the held block against the block under the crosshair, remembering where it landed. */
    public static Step placeAtCrosshair() {
        return mc -> {
            if (mc.hitResult instanceof BlockHitResult hit) {
                useOn(mc, hit);
                lastPlaced = hit.getBlockPos().relative(hit.getDirection());
                mc.player.swing(HAND);
                return 0;
            }
            msg(mc, "PvP Macro: aim at a block");
            return Step.ABORT;
        };
    }

    /** Right-click the block placed by the last placeAtCrosshair (puts a minecart on the rail). */
    public static Step useOnLastPlaced() {
        return mc -> {
            if (lastPlaced == null) return Step.ABORT;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(lastPlaced), Direction.UP, lastPlaced, false);
            useOn(mc, hit);
            mc.player.swing(HAND);
            return 0;
        };
    }

    /** Use the held item (lava bucket / flint and steel) on the ground N blocks ahead of the placed rail. */
    public static Step useOnGroundAhead(java.util.function.IntSupplier blocksAhead) {
        return mc -> {
            if (lastPlaced == null) return Step.ABORT;
            BlockPos ground = lastPlaced.below().relative(mc.player.getDirection(), blocksAhead.getAsInt());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
            useOn(mc, hit);
            mc.player.swing(HAND);
            return 0;
        };
    }

    /** Right-click the TNT minecart near the placed rail (flint and steel ignites it). */
    public static Step useOnNearbyCart() {
        return mc -> {
            if (lastPlaced == null) return Step.ABORT;
            for (var e : mc.level.getEntitiesOfClass(MinecartTNT.class, new AABB(lastPlaced).inflate(1.5))) {
                mc.gameMode.interact(mc.player, e, new EntityHitResult(e), HAND);
                break;
            }
            return 0;
        };
    }

    // ---------- elytra mace: leave the glide by packet, hit, resume ----------
    /** Remembers whether exitGlide() really left the glide, so resumeGlide() only runs after a successful exit. */
    public static final class GlideMemo { boolean exited = false; }

    /**
     * NEW. While gliding the server refuses mace smash hits. The vanilla server treats START_FALL_FLYING from a
     * player who is ALREADY gliding as "stop gliding" (it only starts a glide when the player is not gliding), so
     * sending that one packet takes the server out of the glide immediately, in order with the attack packet that
     * follows in the same tick. Technique as described publicly for the Elytra Mace Attack mod; code is my own.
     * Packet class, constructor and Action.START_FALL_FLYING confirmed present in 26.2 (that mod's jar references them).
     */
    public static Step exitGlide(GlideMemo memo) {
        return mc -> {
            memo.exited = false;
            var conn = mc.getConnection();
            if (conn == null || !mc.player.isFallFlying()) {
                PvpLog.info("exitGlide skipped: not gliding (or no connection)");
                return 0;
            }
            conn.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            memo.exited = true;
            PvpLog.info("exitGlide: sent START_FALL_FLYING while gliding (server leaves the glide)");
            return 0;
        };
    }

    /** NEW. Second START_FALL_FLYING, same tick, after the hit: the server starts the glide again (needs an airborne player in an elytra). */
    public static Step resumeGlide(GlideMemo memo) {
        return mc -> {
            var conn = mc.getConnection();
            if (memo.exited && conn != null) {
                conn.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                PvpLog.info("resumeGlide: sent START_FALL_FLYING");
            } else {
                PvpLog.info("resumeGlide skipped (did not exit the glide)");
            }
            memo.exited = false;
            return 0;
        };
    }

    // =====================================================================================
    // 1.0.5: camera, fire placement toward the player, bow boost helpers.
    // Names that could not be confirmed for 26.2 go through Reflect (runtime, logged).
    // =====================================================================================
    private static final java.util.Map<String, java.lang.reflect.Method> CAMERA = new java.util.HashMap<>();

    /**
     * Finds the camera getter/setter by SIGNATURE and a name hint instead of one fixed name (public float setter / getter whose
     * name mentions XRot/pitch or YRot/yaw). Cached. Throws if nothing fits; the log line then lists what exists (Diagnostics).
     */
    private static java.lang.reflect.Method cam(Object player, boolean setter, boolean pitch) throws NoSuchMethodException {
        String key = (setter ? "set" : "get") + (pitch ? "P" : "Y");
        java.lang.reflect.Method cached = CAMERA.get(key);
        if (cached != null) return cached;
        for (java.lang.reflect.Method m : player.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (setter) { if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != float.class || !n.startsWith("set")) continue; }
            else { if (m.getParameterCount() != 0 || m.getReturnType() != float.class || !n.startsWith("get")) continue; }
            if (n.contains("head") || n.contains("body") || n.contains("old") || n.contains("eye")) continue;
            boolean isPitch = n.contains("xrot") || n.contains("pitch");
            boolean isYaw = n.contains("yrot") || n.contains("yaw");
            if (pitch ? isPitch : isYaw) { CAMERA.put(key, m); PvpLog.info("camera method for " + key + ": " + m.getName()); return m; }
        }
        throw new NoSuchMethodException("no camera " + key + " method found on " + player.getClass().getName());
    }

    /** Camera angle saved at the start of a macro. */
    public static final class LookMemo { float yaw, pitch; boolean saved = false, restore = true; }
    private static LookMemo activeLook = null;

    private static float num(Object o) { return ((Number) o).floatValue(); }

    /** x / y / z of an entity, BlockPos or Vec3 (getX() or x() or field x). */
    static double comp(Object o, String axis) throws ReflectiveOperationException {
        try {
            return ((Number) Reflect.invokeAny(o, new String[]{"get" + axis.toUpperCase(), axis})).doubleValue();
        } catch (NoSuchMethodException e) {
            return ((Number) Reflect.field(o, axis)).doubleValue();
        }
    }

    private static void setLook(Minecraft mc, Float yaw, Float pitch) {
        try {
            if (yaw != null) cam(mc.player, true, false).invoke(mc.player, yaw);
            if (pitch != null) cam(mc.player, true, true).invoke(mc.player, pitch);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set the camera angle: " + e, e);
        }
    }

    public static Step rememberLook(LookMemo memo, java.util.function.BooleanSupplier restore) {
        return mc -> {
            try {
                memo.yaw = num(cam(mc.player, false, false).invoke(mc.player));
                memo.pitch = num(cam(mc.player, false, true).invoke(mc.player));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot read the camera angle: " + e, e);
            }
            memo.saved = true;
            memo.restore = restore.getAsBoolean();
            activeLook = memo;
            PvpLog.info("camera saved: yaw " + memo.yaw + " pitch " + memo.pitch);
            return 0;
        };
    }

    public static Step restoreLook(LookMemo memo) {
        return mc -> {
            if (memo.saved && memo.restore) { setLook(mc, memo.yaw, memo.pitch); PvpLog.info("camera restored"); }
            memo.saved = false;
            if (activeLook == memo) activeLook = null;
            return 0;
        };
    }

    /** Called by the runner when a macro ends or is cancelled early, so the camera is never left tilted. */
    static void restoreLookIfPending(Minecraft mc) {
        LookMemo m = activeLook;
        activeLook = null;
        if (m != null && m.saved && m.restore && mc.player != null) {
            try { setLook(mc, m.yaw, m.pitch); PvpLog.info("camera restored (macro ended early)"); }
            catch (RuntimeException e) { PvpLog.info("could not restore the camera: " + e); }
        }
        if (m != null) m.saved = false;
    }

    /** Keep the yaw, set the pitch (degrees, negative = up). */
    public static Step setPitch(java.util.function.DoubleSupplier pitch) {
        return mc -> {
            float p = (float) Math.max(-90.0, Math.min(90.0, pitch.getAsDouble()));
            setLook(mc, null, p);
            PvpLog.info("camera pitch set to " + p);
            return 0;
        };
    }

    /** Turn the camera to look at a point {x, y, z}. Follow with waitTicks(aimSyncTicks) so the server gets the angle. */
    public static Step lookAt(java.util.function.Supplier<double[]> target) {
        return mc -> {
            double[] t = target.get();
            if (t == null) return Step.ABORT;
            try {
                double ex = comp(mc.player, "x");
                double ey = comp(mc.player, "y") + num(Reflect.invokeAny(mc.player, new String[]{"getEyeHeight"}));
                double ez = comp(mc.player, "z");
                double dx = t[0] - ex, dy = t[1] - ey, dz = t[2] - ez;
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                setLook(mc, yaw, pitch);
                PvpLog.info("aiming at " + t[0] + ", " + t[1] + ", " + t[2] + " (yaw " + yaw + ", pitch " + pitch + ")");
                return 0;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot aim: " + e, e);
            }
        };
    }

    private static double[] pointAt(BlockPos p, double yOffset) throws ReflectiveOperationException {
        return new double[]{comp(p, "x") + 0.5, comp(p, "y") + yOffset, comp(p, "z") + 0.5};
    }

    /** Aim at the TNT minecart standing on the last placed rail. */
    public static Step lookAtCart() {
        return lookAt(() -> {
            try { return lastPlaced == null ? null : pointAt(lastPlaced, 0.4); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    /**
     * The ground block N blocks from the rail, on the side the PLAYER is on (so the fire sits between you and the
     * cart and the arrow flies through it). The old code went the other way, past the cart.
     */
    private static BlockPos fireGround(Minecraft mc, int n) throws ReflectiveOperationException {
        double dx = comp(mc.player, "x") - (comp(lastPlaced, "x") + 0.5);
        double dz = comp(mc.player, "z") - (comp(lastPlaced, "z") + 0.5);
        Direction d = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                                                   : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        return lastPlaced.below().relative(d, Math.max(1, n));
    }

    /** Use the held flint and steel on the top of that ground block (fire appears above it). */
    public static Step useOnGroundTowardPlayer(java.util.function.IntSupplier blocks) {
        return mc -> {
            if (lastPlaced == null) return Step.ABORT;
            try {
                BlockPos ground = fireGround(mc, blocks.getAsInt());
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
                useOn(mc, hit);
                mc.player.swing(HAND);
                return 0;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot place the fire: " + e, e);
            }
        };
    }

    /** Aim at the top of that ground block (for the lava bucket, which is placed where you look). */
    public static Step lookAtFireSpot(java.util.function.IntSupplier blocks) {
        return lookAt(() -> {
            try {
                if (lastPlaced == null) return null;
                return pointAt(fireGround(Minecraft.getInstance(), blocks.getAsInt()), 1.0);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    // ---------- progress trail (shown in the action bar when a macro ends, so a failure shows WHERE it stopped) ----------
    static final java.util.ArrayList<String> TRAIL = new java.util.ArrayList<>();
    static void clearTrail() { TRAIL.clear(); }

    /** Runs the inner step and records its name in the trail if it did not abort. */
    public static Step label(String name, Step inner) {
        return mc -> {
            int r = inner.run(mc);
            if (r != Step.ABORT && r != Step.REPEAT) TRAIL.add(name);
            return r;
        };
    }

    // ---------- wrappers ----------
    /** Run a step but carry on if it throws (used for the camera aim in Xbow: no aim is better than no shot). */
    public static Step optional(Step inner) {
        return mc -> {
            try { return inner.run(mc); }
            catch (RuntimeException e) { PvpLog.info("optional step skipped: " + e.getMessage()); TRAIL.add("(a step was skipped)"); return 0; }
        };
    }

    /** Run a step; if it throws, tell the player and cancel the macro. */
    public static Step orAbort(Step inner, String message) {
        return mc -> {
            try { return inner.run(mc); }
            catch (RuntimeException e) {
                PvpLog.info("step failed: " + e.getMessage());
                msg(mc, message);
                return Step.ABORT;
            }
        };
    }

    // ---------- pearl catch in any direction (1.0.9) ----------
    /** The pearl found by findPearl(). */
    public static final class PearlMemo { Object pearl; }

    /** The ender pearl nearest to the player (client side), or null. Uses ThrowableProjectile + the entity type id. */
    private static Object nearestPearl(Minecraft mc) {
        try {
            double px = comp(mc.player, "x"), py = comp(mc.player, "y"), pz = comp(mc.player, "z");
            AABB box = new AABB(px - 14, py - 14, pz - 14, px + 14, py + 14, pz + 14);
            Object best = null;
            double bestDist = Double.MAX_VALUE;
            for (ThrowableProjectile e : mc.level.getEntitiesOfClass(ThrowableProjectile.class, box)) {
                String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                if (!path.contains("ender_pearl")) continue;
                double dx = comp(e, "x") - px, dy = comp(e, "y") - py, dz = comp(e, "z") - pz;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestDist) { bestDist = d; best = e; }
            }
            return best;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            PvpLog.info("pearl search failed: " + ex);
            return null;
        }
    }

    /** Waits (repeating each tick) until the thrown pearl shows up on the client, at most maxTicks, then carries on either way. */
    public static Step findPearl(PearlMemo memo, java.util.function.IntSupplier maxTicks) {
        return new Step() {
            int waited = 0;
            public int run(Minecraft mc) {
                waited++;
                memo.pearl = nearestPearl(mc);
                if (memo.pearl != null) { PvpLog.info("pearl found after " + waited + " tick(s)"); TRAIL.add("pearl found"); return 0; }
                if (waited >= maxTicks.getAsInt()) { PvpLog.info("pearl not found, wind charge goes straight"); TRAIL.add("pearl not found"); return 0; }
                return Step.REPEAT;
            }
        };
    }

    /**
     * Aim at where the pearl will be when the wind charge (speed 1.5) reaches it: the pearl's position, pushed forward
     * by its velocity with its drag (0.99) and gravity (0.03 per tick), plus latencyTicks for the camera to sync.
     * If no pearl was found this does nothing (the wind charge then goes along your current look direction).
     */
    public static Step lookAtPearl(PearlMemo memo, java.util.function.IntSupplier latencyTicks) {
        Step aim = lookAt(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                Object p = memo.pearl;
                if (p == null) return null;
                double px = comp(p, "x"), py = comp(p, "y") + 0.125, pz = comp(p, "z");
                Object vel = Reflect.invokeAny(p, new String[]{"getDeltaMovement", "getVelocity"});
                double vx = comp(vel, "x"), vy = comp(vel, "y"), vz = comp(vel, "z");
                double ex = comp(mc.player, "x");
                double ey = comp(mc.player, "y") + num(Reflect.invokeAny(mc.player, new String[]{"getEyeHeight"}));
                double ez = comp(mc.player, "z");
                double tx = px, ty = py, tz = pz, t = 0;
                for (int i = 0; i < 3; i++) {
                    double dx = tx - ex, dy = ty - ey, dz = tz - ez;
                    t = Math.sqrt(dx * dx + dy * dy + dz * dz) / 1.5 + latencyTicks.getAsInt();
                    double travel = (1.0 - Math.pow(0.99, t)) / 0.01;     // distance factor with 1% drag per tick
                    tx = px + vx * travel;
                    ty = py + vy * travel - 0.015 * t * t;
                    tz = pz + vz * travel;
                }
                return new double[]{tx, ty, tz};
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot aim at the pearl: " + e, e);
            }
        });
        return mc -> memo.pearl == null ? 0 : aim.run(mc);
    }

    /** A line in the chat window (stays visible, unlike the action bar). Reflection: sendSystemMessage is not confirmed for 26.2. */
    static void chatLine(Minecraft mc, String text) {
        try {
            Reflect.invokeAny(mc.player, new String[]{"sendSystemMessage"}, Component.literal(text));
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("chat line failed (" + e + "): " + text);
        }
    }

    /** True if the hotbar holds an item matching the predicate (used to pick lava or flint and steel when the combo is built). */
    static boolean hotbarHas(Predicate<ItemStack> p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        for (int i = 0; i < 9; i++) if (p.test(mc.player.getInventory().getItem(i))) return true;
        return false;
    }

    /**
     * Use the held flint and steel on the fire spot. After lookAtFireSpot + a wait, the game's own crosshair hit is that block's
     * top face, so it is used as is (exactly what a real right-click sends, the same way the lava bucket works). If the crosshair is
     * somewhere else, or the camera could not be turned, a computed hit on the top face is used instead.
     */
    public static Step useAtFireSpot(java.util.function.IntSupplier blocks) {
        return mc -> {
            if (lastPlaced == null) return Step.ABORT;
            try {
                BlockPos ground = fireGround(mc, blocks.getAsInt());
                BlockHitResult hit;
                if (mc.hitResult instanceof BlockHitResult h && h.getBlockPos().equals(ground)) {
                    hit = h;
                    PvpLog.info("fire: using the crosshair hit on " + ground + " face " + h.getDirection());
                } else {
                    hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
                    PvpLog.info("fire: crosshair is not on " + ground + ", using a computed hit on its top face");
                }
                useOn(mc, hit);
                mc.player.swing(HAND);
                return 0;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot place the fire: " + e, e);
            }
        };
    }

    // ---------- bow boost: pick the bow with the most Punch (1.1.0) ----------
    /** Punch level of a bow (99 if the level cannot be read but Punch is there, 0 if none). Reflection: Enchantments.PUNCH / getLevel are unconfirmed for 26.2. */
    private static int punchLevel(ItemStack s) {
        try {
            @SuppressWarnings("unchecked")
            ResourceKey<Enchantment> punch = (ResourceKey<Enchantment>) Enchantments.class.getField("PUNCH").get(null);
            var ench = s.getEnchantments();
            for (Holder<Enchantment> h : ench.keySet()) {
                if (h.is(punch)) {
                    try { return ((Number) Reflect.invoke(ench, "getLevel", h)).intValue(); }
                    catch (ReflectiveOperationException e) { return 99; }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("Punch check failed: " + e);
        }
        return 0;
    }

    /**
     * Select the hotbar bow with the highest Punch level (the self-hit knockback IS the boost; without Punch an arrow that lands on you
     * barely moves you). Any bow is used if none has Punch. The bow you already hold wins ties.
     */
    public static Step bestBow() {
        return mc -> {
            int held = mc.player.getInventory().getSelectedSlot();
            int best = -1, bestLevel = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (!s.is(Items.BOW)) continue;
                int level = punchLevel(s);
                if (level > bestLevel || (level == bestLevel && i == held)) { bestLevel = level; best = i; }
            }
            if (best < 0) { msg(mc, "PvP Macro: no bow in hotbar"); return Step.ABORT; }
            PvpLog.info("bow boost: bow in slot " + best + " (Punch " + (bestLevel == 99 ? "?" : bestLevel) + ")");
            if (bestLevel == 0) PvpLog.info("this bow has no Punch: an arrow that lands on you will barely move you");
            if (best != held) SlotSync.select(mc, best);
            return 0;
        };
    }
}
