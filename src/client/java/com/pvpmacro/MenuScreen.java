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
    private static final String[] TABS = {"Combat", "Timing", "Inventory", "General"};

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
        built = true;
        try {
            Ui.clear(this);
            int cx = this.width / 2;
            int tw = 78, gap = 4, total = TABS.length * tw + (TABS.length - 1) * gap, tx = cx - total / 2;
            for (int i = 0; i < TABS.length; i++) {
                final int idx = i;
                Object b = Ui.button(this, TABS[i], tx + i * (tw + gap), 8, tw, 20, () -> { tab = idx; page = 0; build(); });
                if (i == tab) Ui.setActive(b, false);
            }
            List<Row> rows = rowsFor(tab);
            int perPage = Math.max(3, (this.height - 80) / 24);
            int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
            page = Math.min(page, pages - 1);
            int y = 40;
            for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
                rows.get(i).add(this, cx - 100, y);
                y += 24;
            }
            int fy = this.height - 26;
            Ui.button(this, "Done", cx - 40, fy, 80, 20, () -> Minecraft.getInstance().setScreen(null));
            if (pages > 1) {
                Object prev = Ui.button(this, "< Prev", cx - 130, fy, 80, 20, () -> { page--; build(); });
                Ui.setActive(prev, page > 0);
                Object next = Ui.button(this, "Next >", cx + 50, fy, 80, 20, () -> { page++; build(); });
                Ui.setActive(next, page < pages - 1);
            }
        } catch (RuntimeException e) {
            PvpLog.info("menu build failed: " + e);
        }
    }

    // ------------------------------------------------------------------ pages
    private static List<Row> rowsFor(int tab) {
        Config c = Config.get();
        List<Row> r = new ArrayList<>();
        switch (tab) {
            case 0:  // Combat: Bow Boost, Auto Totem
                r.add(toggle("Bow Boost", () -> Config.get().isMacroEnabled("bow_boost"), v -> Config.get().macroEnabled.put("bow_boost", v)));
                r.add(toggle("Auto Totem", () -> Config.get().isMacroEnabled("auto_totem"), v -> Config.get().macroEnabled.put("auto_totem", v)));
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
            case 3:  // Inventory: auto totem, restock, inventory pulling
                r.add(toggle("Auto totem", () -> c.autoTotemEnabled, v -> c.autoTotemEnabled = v));
                r.add(toggle("Totem replaces offhand", () -> c.autoTotemReplaceOffhand, v -> c.autoTotemReplaceOffhand = v));
                r.add(ints("Totem delay (ticks)", () -> c.autoTotemDelayTicks, v -> c.autoTotemDelayTicks = v, 0, 20, 1));
                r.add(toggle("Restock", () -> c.restockEnabled, v -> c.restockEnabled = v));
                r.add(ints("Restock below (%)", () -> c.restockBelowPercent, v -> c.restockBelowPercent = v, 5, 95, 5));
                r.add(toggle("Restock hotbar", () -> c.restockHotbar, v -> c.restockHotbar = v));
                r.add(toggle("Restock offhand", () -> c.restockOffhand, v -> c.restockOffhand = v));
                r.add(ints("Restock delay (ticks)", () -> c.restockDelayTicks, v -> c.restockDelayTicks = v, 1, 40, 1));
                r.add(ints("Restock scan (ticks)", () -> c.restockCheckTicks, v -> c.restockCheckTicks = v, 2, 60, 2));
                r.add(toggle("Inventory pull", () -> c.inventoryPullEnabled, v -> c.inventoryPullEnabled = v));
                r.add(ints("Inventory pull threshold (%)", () -> c.inventoryPullThreshold, v -> c.inventoryPullThreshold = v, 5, 95, 5));
                r.add(toggle("Inventory pull hotbar", () -> c.inventoryPullHotbarSlots.contains(0) && c.inventoryPullHotbarSlots.size() == 9 ? "ON (all)" : "OFF", v -> {}));
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
