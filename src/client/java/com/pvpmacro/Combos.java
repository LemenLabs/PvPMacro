package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.List;
import java.util.function.Predicate;
import static com.pvpmacro.Steps.*;

public final class Combos {
    private static final Predicate<ItemStack> MACE = item(Items.MACE);
    private static final Predicate<ItemStack> SWORD = s -> s.is(ItemTags.SWORDS);
    private static final Predicate<ItemStack> AXE = s -> s.is(ItemTags.AXES);
    private static final Predicate<ItemStack> SPEAR = s -> s.is(ItemTags.SPEARS);
    private static Config c() { return Config.get(); }

    /**
     * 1.0.12: true when you are gliding in an elytra. Then the mace macros (stun slam, sword swap, breach swap) do the elytra mace
     * trick around their hits: leave the glide by packet right before the first hit, resume it right after the last one, because the
     * server refuses mace smash hits while you glide. Switch off with flightMaceMacros=false. Not used in elytraMaceMode "chestplate".
     */
    private static boolean flying() {
        Minecraft mc = Minecraft.getInstance();
        return c().flightMaceMacros && mc != null && mc.player != null && mc.player.isFallFlying()
                && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                && !"chestplate".equalsIgnoreCase(c().elytraMaceMode);
    }

    /** Axe swap + click on one tick, mace swap + click on the next tick (stunSlamGapTicks adds extra ticks between). While gliding: with the glide exit/resume. */
    public static List<Step> stunSlam() {
        boolean fly = flying();
        GlideMemo glide = new GlideMemo();
        java.util.ArrayList<Step> steps = new java.util.ArrayList<>();
        steps.add(slot("axe", AXE));
        if (fly) steps.add(exitGlide(glide));
        steps.add(attack());
        steps.add(waitTicks(() -> 1 + c().stunSlamGapTicks));
        steps.add(slot("mace", MACE));
        steps.add(attack());
        if (fly) steps.add(resumeGlide(glide));
        return steps;
    }

    /**
     * Attribute swap: sword hit damage, Density mace enchants.
     * CHANGED: now follows the same order as the Attribute Swap mod: hold the base weapon (sword), then in ONE tick
     * select the enchant weapon (mace) -> attack -> select the base weapon again. The old order was
     * sword -> attack -> mace, which hit with the sword and only picked the mace up afterwards.
     */
    public static List<Step> swordSwap() {
        Steps.SlotMemo base = new Steps.SlotMemo();
        boolean fly = flying();
        GlideMemo glide = new GlideMemo();
        java.util.ArrayList<Step> steps = new java.util.ArrayList<>();
        steps.add(baseSlot("sword", base, () -> c().attributeSettleTicks, SWORD));
        if (fly) steps.add(exitGlide(glide));
        steps.add(slot("mace", withEnchant(MACE, Enchantments.DENSITY), MACE));
        steps.add(attack());
        steps.add(backToBase(base));
        if (fly) steps.add(resumeGlide(glide));
        return steps;
    }

    /** Attribute swap: sword hit that applies the Breach mace's armour bypass. CHANGED: same order fix as swordSwap. */
    public static List<Step> breachSwap() {
        Steps.SlotMemo base = new Steps.SlotMemo();
        boolean fly = flying();
        GlideMemo glide = new GlideMemo();
        java.util.ArrayList<Step> steps = new java.util.ArrayList<>();
        steps.add(baseSlot("sword", base, () -> c().attributeSettleTicks, SWORD));
        if (fly) steps.add(exitGlide(glide));
        steps.add(slot("Breach mace", withEnchant(MACE, Enchantments.BREACH)));
        steps.add(attack());
        steps.add(backToBase(base));
        if (fly) steps.add(resumeGlide(glide));
        return steps;
    }

    /**
     * Attribute swap, like Sword Swap: hold the wind charge (food if configured / no wind charge), then in ONE tick
     * swap to the Lunge spear, click, and swap back. 1.0.12: if the wind charge is not already held it is selected
     * automatically and the macro waits lungeSwitchWaitTicks (default 3) so the server really has it in hand before the
     * spear swap; nothing is waited if it was already held. (If the game's startAttack() cannot be called, the click is
     * queued instead and the swap back happens on the next tick; see Steps.clickAttack.)
     */
    public static List<Step> lungeSwap() {
        Predicate<ItemStack> wind = item(Items.WIND_CHARGE);
        Steps.SlotMemo base = new Steps.SlotMemo();
        Step hold = c().lungeSwapPrefersWindCharge
                ? baseSlot("wind charge / food", base, () -> Steps.ticksToDelay(c().lungeSwitchWaitTicks), wind, food())
                : baseSlot("food / wind charge", base, () -> Steps.ticksToDelay(c().lungeSwitchWaitTicks), food(), wind);
        return List.of(requireHunger(6, "Lunge"), hold,
                       slot("spear", withEnchant(SPEAR, Enchantments.LUNGE), SPEAR), clickAttack(),
                       waitTicks(() -> c().lungeClickWaitTicks),
                       backToBase(base));
    }

    /**
     * Elytra mace. You equip the elytra and rocket down yourself, then press this key.
     * Default mode "packet" (CHANGED in 1.0.4): swap to the mace, take the server out of the glide with one packet,
     * mace hit, put the server back into the glide, all in ONE tick, so the elytra stays on and no chestplate swap is needed.
     * Mode "chestplate" (config elytraMaceMode) keeps the old behaviour: equip the hotbar chestplate, swap to the mace, hit.
     */
    public static List<Step> elytraMace(Minecraft mc) {
        if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            return List.of(abortWith("PvP Macro: elytra mace needs the elytra equipped"));
        }
        if ("chestplate".equalsIgnoreCase(c().elytraMaceMode)) {
            return List.of(slot("chestplate", chestplate()), use(),
                           slot("mace", MACE), attack());
        }
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("elytramaceattack")) {
            PvpLog.info("WARNING: the Elytra Mace Attack mod is installed; if its toggle is ON it also sends glide packets "
                    + "around this hit and they can cancel each other. Turn its toggle OFF (its default key is Z).");
        }
        GlideMemo glide = new GlideMemo();
        return List.of(slot("mace", MACE), exitGlide(glide), attack(), resumeGlide(glide));
    }

    /** Place the rail (ANY rail in the hotbar), place the TNT minecart, THEN draw the bow minimally and shoot it at the cart. */
    public static List<Step> instaCart() {
        return List.of(slot("rail", rails()), placeAtCrosshair(),
                       slot("TNT minecart", item(Items.TNT_MINECART)), useOnLastPlaced(),
                       slot("bow", item(Items.BOW)), useAndHold(), pause(() -> c().bowMinChargeTicks), release());
    }

    /**
     * Xbow (crossbow cart; internal id "expo"), reworked in 1.0.5:
     * rail, TNT minecart, flint and steel (or lava) on the ground between YOU and the cart, camera aimed at the cart,
     * loaded crossbow, camera back. expoAheadBlocks = how many blocks from the cart toward you the fire goes.
     */
    public static List<Step> expo() {
        Steps.LookMemo look = new Steps.LookMemo();
        Step gap = waitTicks(() -> c().expoGapTicks);
        Step aimWait = waitTicks(() -> Math.max(1, c().aimSyncTicks));
        java.util.ArrayList<Step> steps = new java.util.ArrayList<>();
        steps.add(optional(rememberLook(look, () -> true)));
        steps.add(slot("rail", rails())); steps.add(label("rail", placeAtCrosshair())); steps.add(gap);
        steps.add(slot("TNT minecart", item(Items.TNT_MINECART))); steps.add(label("cart", useOnLastPlaced())); steps.add(gap);
        // 1.0.11: use what is actually in the hotbar. The config only says which one you prefer.
        boolean lava = c().expoUseLava;
        boolean haveLava = hotbarHas(item(Items.LAVA_BUCKET)), haveFlint = hotbarHas(item(Items.FLINT_AND_STEEL));
        if (lava && !haveLava && haveFlint) lava = false;
        else if (!lava && !haveFlint && haveLava) lava = true;
        if (lava) {
            steps.add(slot("lava bucket", item(Items.LAVA_BUCKET)));
            steps.add(optional(lookAtFireSpot(() -> c().expoAheadBlocks))); steps.add(aimWait);
            steps.add(label("lava", use())); steps.add(gap);
        } else {
            steps.add(slot("flint and steel", item(Items.FLINT_AND_STEEL)));
            steps.add(optional(lookAtFireSpot(() -> c().expoAheadBlocks))); steps.add(aimWait);   // same aiming as the lava path
            steps.add(label("fire", useAtFireSpot(() -> c().expoAheadBlocks))); steps.add(gap);
        }
        steps.add(optional(label("aim", lookAtCart()))); steps.add(aimWait);
        steps.add(slot("loaded crossbow", item(Items.CROSSBOW))); steps.add(label("shot", use()));
        steps.add(optional(restoreLook(look)));
        return steps;
    }

    /**
     * Bow boost (key K): you sprint jump and press the key at the top of the jump. Takes ANY bow from the hotbar and uses
     * whatever arrow the game picks (simplified in 1.0.6), looks up (bowBoostPitch, default -75), draws bowBoostChargeTicks,
     * releases, puts the camera back.
     */
    public static List<Step> bowBoost() {
        if (!"manual".equalsIgnoreCase(c().bowBoostMode)) {   // 1.0.8 default: fully automatic, see BowBoost.java
            return List.of(slot("bow", item(Items.BOW)), label("bow boost", new BowBoost()));
        }
        Steps.LookMemo look = new Steps.LookMemo();
        String noCamera = "PvP Macro: cannot turn the camera on this version (see the log)";
        return List.of(label("bow", slot("bow", item(Items.BOW))),
                       orAbort(rememberLook(look, () -> c().bowBoostRestoreLook), noCamera),
                       orAbort(label("look up", setPitch(() -> c().bowBoostPitch)), noCamera),
                       label("draw", useAndHold()), waitTicks(() -> c().bowBoostChargeTicks), label("shot", release()),
                       label("look back", restoreLook(look)));
    }

    /** Pearl straight up (you aim), then the wind charge after pearlCatchDelayTicks (default 20 = one second). */
    public static List<Step> pearlCatch() {
        if ("straight".equalsIgnoreCase(c().pearlCatchMode)) {            // old behaviour: both thrown along your look direction
            return List.of(slot("ender pearl", item(Items.ENDER_PEARL)), use(),
                           waitTicks(() -> c().pearlCatchDelayTicks),
                           slot("wind charge", item(Items.WIND_CHARGE)), use());
        }
        // 1.0.9 default "aim": throw the pearl in ANY direction, wait for it to appear, aim at where it is going, throw the wind charge.
        Steps.LookMemo look = new Steps.LookMemo();
        Steps.PearlMemo pearl = new Steps.PearlMemo();
        Step aimWait = waitTicks(() -> Math.max(1, c().aimSyncTicks));
        return List.of(optional(rememberLook(look, () -> true)),
                       slot("ender pearl", item(Items.ENDER_PEARL)), label("pearl", use()),
                       findPearl(pearl, () -> c().pearlFindTicks),
                       optional(label("aim", lookAtPearl(pearl, () -> Math.max(1, c().aimSyncTicks)))), aimWait,
                       slot("wind charge", item(Items.WIND_CHARGE)), label("wind", use()),
                       optional(restoreLook(look)));
    }

    /** Auto elytra guard: back to the chestplate before the 1 second durability tick. */
    public static List<Step> elytraGuard() {
        return List.of(slot("chestplate", chestplate()), use());
    }
}
