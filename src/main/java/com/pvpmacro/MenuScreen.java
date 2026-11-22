package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * NEW (1.1.0). In-game menu (key: Open Menu, default Right Shift). Plain vanilla buttons only, no custom drawing.
 * Every click saves config/pvpmacro.json and takes effect straight away. Tabs: Macros, Timing, Bow, Inventory, General.
 * Only the Screen class itself is used directly; the buttons are built in Ui.java through reflection.
 */
public class MenuScreen extends Screen {
    static MenuScreen current;

    boolean built = false;
    int age = 0;
    private int tab = 0, page = 0;
    private static final String[] TABS = {"Macros", "Timing", "Bow", "Inventory", "General"};

    private interface Row { void add(MenuScreen screen, int x, int y); }

    public MenuScreen() {
        super(Component.literal("PvP Macro"));
        current = this;
    }

    /** Normally called by the game when the screen opens. If that name changed, tickFallback() builds the menu instead. */
    protected void init() { build(); }

    static void tickFallback(Minecraft mc) {
        if (current == null) return;
        Object shown;
        try { shown = Reflect.field(mc, "screen"); } catch (ReflectiveOperationException e) { shown = null; }
        if (shown != current) { current = null; return; }
        if (!current.built && ++current.age > 2) current.build();
    }

    void build() {
        // Prevent double-build or build before screen dimensions are ready
        if (built && age < 3) return;
        built = true;
        try {
            Ui.clear(this);
            // Use screen dimensions with safe defaults
            int w = this.width > 0 ? this.width : 300;
            int h = this.height > 0 ? this.height : 200;
            int cx = w / 2;
            int tw = 78, gap = 4, total = Math.min(TABS.length * tw + (TABS.length - 1) * gap, w - 20), tx = cx - total / 2;
            for (int i = 0; i < TABS.length; i++) {
                final int idx = i;
                // Safe button creation with null check
                try {
                    Object b = Ui.button(this, TABS[i], tx + i * (tw + gap), 8, tw, 20, () -> { tab = idx; page = 0; build(); });
                    if (i == tab) Ui.setActive(b, false);
                } catch (Exception e) {
                    PvpLog.info("menu tab button failed: " + e);
                }
            }
            List<Row> rows = rowsFor(tab);
            // Safe page calculation with minimum height
            int perPage = Math.max(3, (h > 80 ? h - 80 : 0) / 24);
            int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
            page = Math.min(page, Math.max(0, pages - 1));
            int y = 40;
            // Safe row rendering with bounds checking
            for (int i = page * perPage; i < Math.min(rows.size(), Math.max(0, (page + 1) * perPage)); i++) {
                try {
                    rows.get(i).add(this, cx - 100, y);
                } catch (Exception e) {
                    PvpLog.info("menu row render failed: " + e);
                }
                y += 24;
            }
            // Safe Done button with null-safe Minecraft instance
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    int fy = Math.max(26, h - 26);
                    Ui.button(this, "Done", cx - 40, fy, 80, 20, () -> Ui.setScreen(mc, null));
                }
            } catch (Exception e) {
                PvpLog.info("menu Done button failed: " + e);
            }
            // Safe navigation buttons
            if (pages > 1) {
                try {
                    Object prev = Ui.button(this, "< Prev", cx - 130, fy, 80, 20, () -> { page--; build(); });
                    Ui.setActive(prev, page > 0);
                    Object next = Ui.button(this, "> Next", cx + 50, fy, 80, 20, () -> { page++; build(); });
                    Ui.setActive(next, page < pages - 1);
                } catch (Exception e) {
                    PvpLog.info("menu nav buttons failed: " + e);
                }
            }
        } catch (RuntimeException e) {
            PvpLog.info("menu build failed: " + e);
            // Reset build state so it can be retried
            built = false;
            age = 0;
        }
    }

    // ------------------------------------------------------------------ pages
    private static List<Row> rowsFor(int tab) {
        Config c = Config.get();
        List<Row> r = new ArrayList<>();
        switch (tab) {
            case 0:  // Macros: on/off per macro (the key is still set in Controls)
                macro(r, "stun_slam", "Stun Slam"); macro(r, "sword_swap", "Sword Swap"); macro(r, "breach_swap", "Breach Swap");
                macro(r, "lunge_swap", "Lunge Swap"); macro(r, "elytra_mace", "Elytra Mace"); macro(r, "insta_cart", "Insta Cart");
                macro(r, "expo", "Xbow Cart"); macro(r, "pearl_catch", "Pearl Catch"); macro(r, "bow_boost", "Bow Boost");
                break;
            case 1:  // Timing
                r.add(ints("Swap settle (ticks)", () -> c.attributeSettleTicks, v -> c.attributeSettleTicks = v, 0, 5, 1));
                r.add(ints("Stun slam gap", () -> c.stunSlamGapTicks, v -> c.stunSlamGapTicks = v, 0, 10, 1));
                r.add(ints("Lunge select wait", () -> c.lungeSwitchWaitTicks, v -> c.lungeSwitchWaitTicks = v, 0, 10, 1));
                r.add(ints("Lunge click wait", () -> c.lungeClickWaitTicks, v -> c.lungeClickWaitTicks = v, 0, 5, 1));
                r.add(ints("Bow after release", () -> c.bowAfterReleaseTicks, v -> c.bowAfterReleaseTicks = v, 0, 5, 1));
                r.add(ints("Camera sync", () -> c.aimSyncTicks, v -> c.aimSyncTicks = v, 1, 10, 1));
                r.add(ints("Xbow step gap", () -> c.expoGapTicks, v -> c.expoGapTicks = v, 0, 10, 1));
                r.add(ints("Xbow fire distance", () -> c.expoAheadBlocks, v -> c.expoAheadBlocks = v, 1, 4, 1));
                r.add(toggle("Xbow uses lava", () -> c.expoUseLava, v -> c.expoUseLava = v));
                r.add(ints("Insta cart draw", () -> c.bowMinChargeTicks, v -> c.bowMinChargeTicks = v, 3, 20, 1));
                r.add(cycle("Pearl mode", () -> c.pearlCatchMode, v -> c.pearlCatchMode = v, "aim", "straight"));
                r.add(ints("Pearl delay (straight)", () -> c.pearlCatchDelayTicks, v -> c.pearlCatchDelayTicks = v, 0, 40, 1));
                r.add(ints("Pearl find wait", () -> c.pearlFindTicks, v -> c.pearlFindTicks = v, 1, 20, 1));
                r.add(cycle("Elytra mace mode", () -> c.elytraMaceMode, v -> c.elytraMaceMode = v, "packet", "chestplate"));
                break;
            case 2:  // Bow boost
                r.add(cycle("Mode", () -> c.bowBoostMode, v -> c.bowBoostMode = v, "auto", "manual"));
                r.add(ints("Angle up (degrees)", () -> (int) Math.round(-c.bowBoostPitch), v -> c.bowBoostPitch = -v, 10, 89, 1));
                r.add(ints("Draw (ticks)", () -> c.bowBoostDrawTicks, v -> c.bowBoostDrawTicks = v, 3, 20, 1));
                r.add(ints("Keep sprint (ticks)", () -> c.bowBoostSprintHoldTicks, v -> c.bowBoostSprintHoldTicks = v, 0, 60, 2));
                r.add(toggle("Restore camera", () -> c.bowBoostRestoreLook, v -> c.bowBoostRestoreLook = v));
                break;
            case 3:  // Inventory: auto totem + restock
                r.add(toggle("Auto totem", () -> c.autoTotemEnabled, v -> c.autoTotemEnabled = v));
                r.add(toggle("Totem replaces offhand", () -> c.autoTotemReplaceOffhand, v -> c.autoTotemReplaceOffhand = v));
                r.add(ints("Totem delay (ticks)", () -> c.autoTotemDelayTicks, v -> c.autoTotemDelayTicks = v, 0, 20, 1));
                r.add(toggle("Restock", () -> c.restockEnabled, v -> c.restockEnabled = v));
                r.add(ints("Restock below (%)", () -> c.restockBelowPercent, v -> c.restockBelowPercent = v, 5, 95, 5));
                r.add(toggle("Restock hotbar", () -> c.restockHotbar, v -> c.restockHotbar = v));
                r.add(toggle("Restock offhand", () -> c.restockOffhand, v -> c.restockOffhand = v));
                r.add(ints("Restock delay (ticks)", () -> c.restockDelayTicks, v -> c.restockDelayTicks = v, 1, 40, 1));
                r.add(ints("Restock scan (ticks)", () -> c.restockCheckTicks, v -> c.restockCheckTicks = v, 2, 60, 2));
                break;
            default: // General
                r.add(toggle("Restore hotbar slot", () -> c.restoreSlot, v -> c.restoreSlot = v));
                r.add(ints("Queued key presses", () -> c.chainQueueSize, v -> c.chainQueueSize = v, 0, 5, 1));
                r.add(toggle("Elytra guard", () -> c.elytraGuard, v -> c.elytraGuard = v));
                r.add(toggle("Mace macros in flight", () -> c.flightMaceMacros, v -> c.flightMaceMacros = v));
                r.add(toggle("Pause Attribute Swap", () -> c.attributeSwapCompat, v -> c.attributeSwapCompat = v));
                r.add(toggle("Log every step", () -> c.logSteps, v -> c.logSteps = v));
                r.add(toggle("Progress line", () -> c.showProgress, v -> c.showProgress = v));
                r.add(action("Reset ALL settings", () -> { Config.resetToDefaults(); if (current != null) current.build(); }));
                break;
        }
        return r;
    }

    private static void macro(List<Row> rows, String id, String label) {
        rows.add(toggle(label, () -> Config.get().isMacroEnabled(id), v -> Config.get().macroEnabled.put(id, v)));
    }

    // ------------------------------------------------------------------ row builders
    private static Row toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
        return (s, x, y) -> {
            Object[] ref = new Object[1];
            ref[0] = Ui.button(s, name + ": " + (get.getAsBoolean() ? "ON" : "OFF"), x, y, 200, 20, () -> {
                set.accept(!get.getAsBoolean());
                Config.save();
                Ui.setLabel(ref[0], name + ": " + (get.getAsBoolean() ? "ON" : "OFF"));
            });
        };
    }

    private static Row ints(String name, IntSupplier get, IntConsumer set, int min, int max, int step) {
        return (s, x, y) -> {
            Object[] ref = new Object[1];
            Runnable refresh = () -> Ui.setLabel(ref[0], name + ": " + get.getAsInt());
            Ui.button(s, "-", x, y, 24, 20, () -> { set.accept(Math.max(min, get.getAsInt() - step)); Config.save(); refresh.run(); });
            ref[0] = Ui.button(s, name + ": " + get.getAsInt(), x + 26, y, 148, 20, () -> { });
            Ui.button(s, "+", x + 176, y, 24, 20, () -> { set.accept(Math.min(max, get.getAsInt() + step)); Config.save(); refresh.run(); });
        };
    }

    private static Row cycle(String name, Supplier<String> get, Consumer<String> set, String... options) {
        return (s, x, y) -> {
            Object[] ref = new Object[1];
            ref[0] = Ui.button(s, name + ": " + get.get(), x, y, 200, 20, () -> {
                int at = 0;
                for (int i = 0; i < options.length; i++) if (options[i].equalsIgnoreCase(get.get())) at = i;
                set.accept(options[(at + 1) % options.length]);
                Config.save();
                Ui.setLabel(ref[0], name + ": " + get.get());
            });
        };
    }

    private static Row action(String name, Runnable run) {
        return (s, x, y) -> Ui.button(s, name, x, y, 200, 20, run);
    }
}
