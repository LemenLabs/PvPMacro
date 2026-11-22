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
    public int bowBoostDrawTicks = 5;           // auto mode: ticks the bow stays drawn (minimum 4: less than that does not fire; 5 = same as insta cart)
    public int bowBoostChargeTicks = 5;         // bow boost: ticks the bow is drawn before release
    public double bowBoostPitch = -78.0;        // bow boost: camera pitch while shooting (negative = up; 78 is the Bow Boostin default)
    public boolean bowBoostRestoreLook = true;  // bow boost: put the camera back afterwards
    public boolean autoTotemReplaceOffhand = false; // auto totem: false = only fill an EMPTY offhand (keeps a shield)
    public int autoTotemDelayTicks = 2;         // auto totem: ticks to wait before moving a totem

    // ---- added in 1.0.12 ----
    public int lungeSwitchWaitTicks = 3;        // lunge swap: ticks to wait after it had to SELECT the wind charge (skipped when it is already held)
    public boolean flightMaceMacros = true;     // stun slam / sword swap / breach swap while gliding in an elytra: leave the glide by packet, hit, resume it (same trick as elytra mace)

    // ---- added in 1.0.13 ----
    public boolean autoTotem = false;           // auto totem switched on (key or menu); remembered between launches
    public boolean bowBoostChaseArrow = true;   // bow boost: after the shot, steer forward/sprint by where YOUR arrow is so you run into it
    public int bowBoostRunTicks = 30;           // bow boost: longest time (ticks) the macro keeps running/steering after the shot
    public boolean restock = false;             // restock: top up low stacks from your inventory (menu: Restock page, or the Toggle Restock key)
    public double restockThreshold = 0.5;       // restock when a stack is below this fraction of its maximum (0.5 = below half)
    public String restockScope = "hotbar";      // "hotbar" = hotbar (+offhand) only, "all" = also the main inventory (consolidates)
    public String restockMode = "all";          // "all" = every stackable item, "list" = only the items in restockItems
    public java.util.Set<String> restockItems = new java.util.HashSet<>();   // ids like minecraft:ender_pearl (used when restockMode is "list")
    public int restockDelayTicks = 3;           // ticks a low stack must stay low before it is refilled
    public boolean restockOffhand = true;       // restock includes the offhand
    public java.util.Set<String> disabledMacros = new java.util.HashSet<>();   // macros switched off in the menu (internal ids)

    public boolean macroEnabled(String id) { return disabledMacros == null || !disabledMacros.contains(id); }
    public void setMacroEnabled(String id, boolean on) {
        if (disabledMacros == null) disabledMacros = new java.util.HashSet<>();
        if (on) disabledMacros.remove(id); else disabledMacros.add(id);
    }

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
        instance.bowBoostDrawTicks = Math.max(4, Math.min(20, instance.bowBoostDrawTicks));
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
        instance.chainQueueSize = Math.max(0, instance.chainQueueSize);
        instance.aimSyncTicks = Math.max(1, instance.aimSyncTicks);
        instance.bowBoostChargeTicks = Math.max(1, instance.bowBoostChargeTicks);
        instance.autoTotemDelayTicks = Math.max(0, instance.autoTotemDelayTicks);
        instance.lungeSwitchWaitTicks = Math.max(0, instance.lungeSwitchWaitTicks);
        instance.attributeSettleTicks = Math.max(0, instance.attributeSettleTicks);
        instance.lungeClickWaitTicks = Math.max(0, instance.lungeClickWaitTicks);
        instance.bowAfterReleaseTicks = Math.max(0, instance.bowAfterReleaseTicks);
        instance.expoGapTicks = Math.max(0, instance.expoGapTicks);
        instance.pearlCatchDelayTicks = Math.max(0, instance.pearlCatchDelayTicks);
        normalize();
        save();
    }

    private static void normalize() {
        if (instance.elytraMaceMode == null || !instance.elytraMaceMode.equalsIgnoreCase("chestplate")) instance.elytraMaceMode = "packet";
        if (instance.disabledMacros == null) instance.disabledMacros = new java.util.HashSet<>();
        if (instance.restockItems == null) instance.restockItems = new java.util.HashSet<>();
        if (instance.restockScope == null || !instance.restockScope.equalsIgnoreCase("all")) instance.restockScope = "hotbar";
        if (instance.restockMode == null || !instance.restockMode.equalsIgnoreCase("list")) instance.restockMode = "all";
        instance.restockThreshold = Math.max(0.1, Math.min(0.95, instance.restockThreshold));
        instance.restockDelayTicks = Math.max(0, instance.restockDelayTicks);
        instance.bowBoostRunTicks = Math.max(5, Math.min(80, instance.bowBoostRunTicks));
    }

    /** Writes the current settings to config/pvpmacro.json (the menu calls this after every change). */
    public static void save() {
        if (instance == null) return;
        Path p = FabricLoader.getInstance().getConfigDir().resolve("pvpmacro.json");
        try { Files.writeString(p, GSON.toJson(instance)); } catch (Exception ignored) {}
    }

    /** Puts every setting back to its default (menu: Reset). */
    public static void reset() {
        instance = new Config();
        instance.configVersion = 113;
        normalize();
        save();
    }
}
