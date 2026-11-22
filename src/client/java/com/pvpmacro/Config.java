package com.pvpmacro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to config/pvpmacro.json. Edit and restart (or rejoin) to apply. */
public class Config {
    public boolean restoreSlot = true;          // go back to the slot you started on after a combo
    public int bowMinChargeTicks = 5;           // insta cart: ticks the bow is drawn before release (was 3, too short to fire reliably)
    public int stunSlamGapTicks = 0;            // extra ticks between axe hit and mace hit (0 = mace hit on the very next tick)
    public boolean expoUseLava = false;         // Xbow: false = flint and steel placed in front of the cart (default), true = lava bucket
    public int expoAheadBlocks = 1;             // Xbow: how many blocks in front of the rail to place the fire/lava
    public boolean lungeSwapPrefersWindCharge = true; // else food first
    public boolean elytraGuard = true;          // swap back to chestplate before elytra durability ticks when low
    public double lowElytraFraction = 0.10;     // "low" = under this fraction of durability left
    public int elytraGuardTick = 17;            // swap before 20 ticks (1 second) of gliding

    // ---- added in 1.0.1 (existing config files keep working; missing keys take these defaults) ----
    public int attributeSettleTicks = 1;        // sword/breach swap: extra ticks to wait after selecting the base weapon so the server has ticked with it
    public int lungeClickWaitTicks = 0;         // lunge swap: EXTRA ticks to keep the spear after the click tick (raise to 1 if the lunge fires with the wrong item)
    public boolean explicitSlotPackets = true;  // send the hotbar change to the server immediately (what Attribute Swap does)
    public boolean attributeSwapCompat = true;  // pause the Attribute Swap mod while a macro runs so it cannot re-route macro hits

    // ---- added in 1.0.2 ----
    public boolean logSteps = true;             // write every macro step to logs/latest.log
    public int bowAfterReleaseTicks = 1;        // (unused since 1.0.3: insta cart now shoots the bow last)
    public int expoGapTicks = 1;                // Xbow: ticks between each placement / use
    public int pearlCatchDelayTicks = 6;        // pearl catch: ticks between the pearl and the wind charge (6 = about a third of a second)

    // ---- added in 1.0.4 ----
    public String elytraMaceMode = "packet";    // "packet" = exit glide by packet, mace hit, resume glide (all one tick); "chestplate" = old hotbar chestplate swap

    // ---- added in 1.0.5 ----
    public int configVersion = 0;               // used once to move old saved values to new defaults
    public String pearlCatchMode = "aim";       // "aim" = look at the thrown pearl and throw the wind charge at where it is going (any direction); "straight" = old: wind charge along the same look direction
    public int pearlFindTicks = 6;              // aim mode: how long to wait for the pearl to show up before throwing straight
    public boolean progressInChat = false;      // also print the progress line in the chat window (the action bar disappears after a few seconds)
    public boolean showProgress = false;        // action-bar line after Xbow / Bow Boost showing which steps ran (rail > cart > fire > ...)
    public int chainQueueSize = 2;              // key presses queued while a macro runs (0 = ignore presses like before); slot restore only after the last one
    public int aimSyncTicks = 2;                // after turning the camera, ticks to wait so the server has the new angle
    public String bowBoostMode = "auto";        // "auto" = macro sprints, jumps, waits for the top of the jump, looks up, shoots, restores (like Bow Boostin); "manual" = you jump and press the key at the top
    public int bowBoostDrawTicks = 4;           // auto mode: ticks the bow stays drawn (a bow only fires from about 4 here; a LONGER draw = a faster arrow that lands behind you)
    public int bowBoostChargeTicks = 5;         // bow boost: ticks the bow is drawn before release
    public double bowBoostPitch = -78.0;        // bow boost: camera pitch while shooting (negative = up; 78 is the Bow Boostin default)
    public boolean bowBoostRestoreLook = true;  // bow boost: put the camera back afterwards
    public boolean autoTotemReplaceOffhand = false; // auto totem: false = only fill an EMPTY offhand (keeps a shield)
    public int autoTotemDelayTicks = 2;         // auto totem: ticks to wait before moving a totem

    // ---- added in 1.0.12 ----
    public int lungeSwitchWaitTicks = 3;        // lunge swap: ticks to wait after it had to SELECT the wind charge (skipped when it is already held)
    public boolean flightMaceMacros = true;     // stun slam / sword swap / breach swap while gliding in an elytra: leave the glide by packet, hit, resume it (same trick as elytra mace)

    // ---- added in 1.1.0 ----
    public int bowBoostSprintHoldTicks = 20;    // bow boost: keep the sprint key held this long after the shot so you keep pace with the falling arrow
    public boolean autoTotemEnabled = false;    // auto totem on/off (also toggled by its key or the menu)
    public boolean restockEnabled = false;      // restock on/off
    public int restockBelowPercent = 50;        // restock when a stackable hotbar/offhand stack is below this percent of its max stack (50 = below half)
    public boolean restockHotbar = true;        // restock hotbar stacks
    public boolean restockOffhand = true;       // restock the offhand stack
    public int restockDelayTicks = 6;           // pause after each top-up
    public int restockCheckTicks = 10;          // how often the hotbar is scanned
    public java.util.List<String> restockIgnore = new java.util.ArrayList<>();   // item ids never restocked, e.g. "minecraft:cobblestone" or "cobblestone"
    public java.util.Map<String, Boolean> macroEnabled = new java.util.HashMap<>(); // menu: macro id -> on/off (missing = on)

    // ---- added in 1.1.1 ----
    public boolean inventoryPullEnabled = false;    // inventory pulling on/off (key: key.pvpmacro.inventory_pull, default unbound)
    public int inventoryPullThreshold = 50;         // pull from main inventory when hotbar stack is below this percent of max
    public java.util.List<String> inventoryPullBlacklist = new java.util.ArrayList<>(); // item ids never pulled, e.g. "minecraft:cobblestone"
    public java.util.List<Integer> inventoryPullHotbarSlots = new java.util.ArrayList<>(); // hotbar slots to monitor (empty = all 9)

    public boolean isMacroEnabled(String id) { return macroEnabled == null || macroEnabled.getOrDefault(id, true); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public static Config get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("pvpmacro.json");
        try {
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), Config.class);
        } catch (Exception ignored) {}
        if (instance == null) instance = new Config();
        if (instance.configVersion < 105) {          // 1.0.5: faster pearl catch (an old saved 20 would otherwise stay)
            instance.pearlCatchDelayTicks = 6;
            instance.configVersion = 105;
        }
        if (instance.configVersion < 106) {          // 1.0.7: bow boost angle 75 degrees up (an old saved -45 would otherwise stay)
            instance.bowBoostPitch = -75.0;
            instance.configVersion = 106;
        }
        if (instance.configVersion < 107) {          // 1.0.8: 78 degrees like Bow Boostin
            instance.bowBoostPitch = -78.0;
            instance.configVersion = 107;
        }
        if (instance.bowBoostMode == null || !instance.bowBoostMode.equalsIgnoreCase("manual")) instance.bowBoostMode = "auto";
        instance.bowBoostDrawTicks = Math.max(3, Math.min(20, instance.bowBoostDrawTicks));
        if (instance.configVersion < 108) {          // 1.0.9: the old default of 3 draw ticks never fired the bow
            instance.bowBoostDrawTicks = 5;
            instance.configVersion = 108;
        }
        if (instance.configVersion < 109) {          // 1.0.11: no "sprint > done" progress lines unless you switch them on
            instance.showProgress = false;
            instance.progressInChat = false;
            instance.configVersion = 109;
        }
        if (instance.pearlCatchMode == null || !instance.pearlCatchMode.equalsIgnoreCase("straight")) instance.pearlCatchMode = "aim";
        instance.pearlFindTicks = Math.max(1, instance.pearlFindTicks);
        if (instance.configVersion < 110) {          // 1.1.0: shorter draw so the arrow comes back down on you
            instance.bowBoostDrawTicks = 4;
            instance.configVersion = 110;
        }
        instance.bowBoostDrawTicks = Math.max(3, Math.min(20, instance.bowBoostDrawTicks));
        instance.bowBoostSprintHoldTicks = Math.max(0, instance.bowBoostSprintHoldTicks);
        instance.restockBelowPercent = Math.max(5, Math.min(95, instance.restockBelowPercent));
        instance.restockDelayTicks = Math.max(1, instance.restockDelayTicks);
        instance.restockCheckTicks = Math.max(2, instance.restockCheckTicks);
        if (instance.restockIgnore == null) instance.restockIgnore = new java.util.ArrayList<>();
        if (instance.macroEnabled == null) instance.macroEnabled = new java.util.HashMap<>();
        instance.chainQueueSize = Math.max(0, instance.chainQueueSize);
        instance.inventoryPullBelowPercent = Math.max(5, Math.min(95, instance.inventoryPullBelowPercent));
        instance.inventoryPullDelayTicks = Math.max(1, instance.inventoryPullDelayTicks);
        instance.inventoryPullCheckTicks = Math.max(2, instance.inventoryPullCheckTicks);
        if (instance.inventoryPullBlacklist == null) instance.inventoryPullBlacklist = new java.util.ArrayList<>();
        if (instance.inventoryPullHotbarSlots == null) instance.inventoryPullHotbarSlots = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) if (!instance.inventoryPullHotbarSlots.contains(i)) instance.inventoryPullHotbarSlots.add(i);
        instance.aimSyncTicks = Math.max(1, instance.aimSyncTicks);
        instance.bowBoostChargeTicks = Math.max(1, instance.bowBoostChargeTicks);
        instance.autoTotemDelayTicks = Math.max(0, instance.autoTotemDelayTicks);
        instance.lungeSwitchWaitTicks = Math.max(0, instance.lungeSwitchWaitTicks);
        instance.attributeSettleTicks = Math.max(0, instance.attributeSettleTicks);
        instance.lungeClickWaitTicks = Math.max(0, instance.lungeClickWaitTicks);
        instance.bowAfterReleaseTicks = Math.max(0, instance.bowAfterReleaseTicks);
        instance.expoGapTicks = Math.max(0, instance.expoGapTicks);
        instance.pearlCatchDelayTicks = Math.max(0, instance.pearlCatchDelayTicks);
        if (instance.elytraMaceMode == null || !instance.elytraMaceMode.equalsIgnoreCase("chestplate")) instance.elytraMaceMode = "packet";
        try { Files.writeString(p, GSON.toJson(instance)); } catch (Exception ignored) {}
    }

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("pvpmacro.json"); }

    /** Writes the current settings to config/pvpmacro.json (the menu calls this after every change). */
    public static void save() {
        try { Files.writeString(path(), GSON.toJson(get())); } catch (Exception ignored) {}
    }

    public static void resetToDefaults() {
        Config fresh = new Config();
        fresh.configVersion = 110;
        instance = fresh;
        save();
    }
}
