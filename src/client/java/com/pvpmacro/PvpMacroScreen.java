package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The PvP Macro menu (NEW 1.0.13). Open it with the "Open Menu" key (default Right Shift; rebind it in Controls).
 * Built only from widgets and calls that exist in real 26.2 classes (Button.builder, Screen.init/onClose/extractRenderState,
 * Gui.setScreen); no extra mods needed. Every change is saved to config/pvpmacro.json at once.
 * Keybinds themselves stay in Options > Controls > PvP Macro.
 */
final class PvpMacroScreen extends Screen {
    private static final String[] PAGES = {
        "General", "Macros", "Swaps", "Elytra", "Pearl Catch", "Insta Cart and Xbow", "Bow Boost", "Auto Totem", "Restock", "Restock Item List"
    };
    private static final String[][] MACROS = {            // {internal id, shown name}
        {"stun_slam", "Stun Slam"}, {"sword_swap", "Sword Swap"}, {"breach_swap", "Breach Swap"},
        {"lunge_swap", "Lunge Swap"}, {"elytra_mace", "Elytra Mace"}, {"insta_cart", "Insta Cart"},
        {"expo", "Xbow"}, {"pearl_catch", "Pearl Catch"}, {"bow_boost", "Bow Boost"}
    };
    private static final int ROW = 24, COL_W = 248;
    private static boolean resetArmed = false;

    private final Screen parent;
    private final int page;

    PvpMacroScreen(Screen parent, int page) {
        super(Component.literal("PvP Macro: " + PAGES[Math.floorMod(page, PAGES.length)]));
        this.parent = parent;
        this.page = Math.floorMod(page, PAGES.length);
    }

    private int left() { return this.width / 2 - COL_W / 2; }
    private static Component text(String label, String value) { return Component.literal(label + ": " + value); }
    private static Tooltip tip(String t) { return Tooltip.create(Component.literal(t)); }

    @Override
    protected void init() {
        Config c = Config.get();
        int y = 44;
        switch (page) {
            case 0 -> {
                addBool(y, "Return to start slot", "Go back to the hotbar slot you started on after a macro.", () -> c.restoreSlot, v -> c.restoreSlot = v); y += ROW;
                addInt(y, "Queued key presses", "Key presses remembered while a macro runs (0 = ignore them).", () -> c.chainQueueSize, v -> c.chainQueueSize = v, 0, 5, 1); y += ROW;
                addBool(y, "Send slot packets", "Send every hotbar change to the server at once (what Attribute Swap does). Turn off if an anti-cheat objects.", () -> c.explicitSlotPackets, v -> c.explicitSlotPackets = v); y += ROW;
                addBool(y, "Pause Attribute Swap in macros", "Stops the Attribute Swap mod from re-routing macro hits while a macro runs.", () -> c.attributeSwapCompat, v -> c.attributeSwapCompat = v); y += ROW;
                addBool(y, "Log every step", "Write every macro step to logs/latest.log (useful when something does not work).", () -> c.logSteps, v -> c.logSteps = v); y += ROW;
                addResetButton(y);
            }
            case 1 -> {
                int half = COL_W / 2 - 2;
                for (int i = 0; i < MACROS.length; i++) {
                    String id = MACROS[i][0], name = MACROS[i][1];
                    int x = left() + (i % 2) * (half + 4), yy = y + (i / 2) * ROW;
                    addBoolAt(x, half, yy, name, "Switch this macro on or off. When off, its key does nothing.",
                            () -> c.macroEnabled(id), v -> c.setMacroEnabled(id, v));
                }
                y += ((MACROS.length + 1) / 2) * ROW;
                addBool(y, "Show progress line", "Action-bar line after Xbow / Bow Boost showing which steps ran.", () -> c.showProgress, v -> c.showProgress = v); y += ROW;
                addBool(y, "Progress line in chat", "Also print that line in the chat window.", () -> c.progressInChat, v -> c.progressInChat = v);
            }
            case 2 -> {
                addInt(y, "Sword/Breach settle ticks", "Ticks to wait after selecting the sword before the swap hit (only when the sword was not already held).", () -> c.attributeSettleTicks, v -> c.attributeSettleTicks = v, 0, 5, 1); y += ROW;
                addInt(y, "Stun slam extra gap", "Extra ticks between the axe hit and the mace hit (0 = the very next tick).", () -> c.stunSlamGapTicks, v -> c.stunSlamGapTicks = v, 0, 10, 1); y += ROW;
                addBool(y, "Lunge holds wind charge first", "ON: hold wind charge before the lunge (food if none). OFF: food first.", () -> c.lungeSwapPrefersWindCharge, v -> c.lungeSwapPrefersWindCharge = v); y += ROW;
                addInt(y, "Lunge switch wait ticks", "Ticks to wait after the wind charge had to be selected, before the spear swap. Not used if it was already held.", () -> c.lungeSwitchWaitTicks, v -> c.lungeSwitchWaitTicks = v, 0, 10, 1); y += ROW;
                addInt(y, "Lunge extra wait ticks", "Extra ticks to keep the spear after the click. Raise to 1 if the lunge fires with the wrong item.", () -> c.lungeClickWaitTicks, v -> c.lungeClickWaitTicks = v, 0, 5, 1); y += ROW;
                addBool(y, "Mace macros while gliding", "Stun slam / sword swap / breach swap leave the glide, hit, and resume it when you are gliding in an elytra.", () -> c.flightMaceMacros, v -> c.flightMaceMacros = v);
            }
            case 3 -> {
                addChoice(y, "Elytra mace mode", "packet = leave the glide by packet, hit, resume (one tick). chestplate = swap the hotbar chestplate.", () -> c.elytraMaceMode, v -> c.elytraMaceMode = v, "packet", "chestplate"); y += ROW;
                addBool(y, "Elytra guard", "Swap back to the chestplate when the elytra is nearly broken.", () -> c.elytraGuard, v -> c.elytraGuard = v); y += ROW;
                addDouble(y, "Guard below durability", "Fraction of durability left that counts as low.", () -> c.lowElytraFraction, v -> c.lowElytraFraction = v, 0.01, 0.5, 0.01); y += ROW;
                addInt(y, "Guard swap tick", "Gliding tick at which the guard swaps (17 is just before the 1 second durability tick).", () -> c.elytraGuardTick, v -> c.elytraGuardTick = v, 1, 19, 1);
            }
            case 4 -> {
                addChoice(y, "Pearl catch mode", "aim = look at the thrown pearl and throw the wind charge at where it is going (any direction). straight = along the same look direction.", () -> c.pearlCatchMode, v -> c.pearlCatchMode = v, "aim", "straight"); y += ROW;
                addInt(y, "Pearl to wind charge ticks", "Ticks between the pearl and the wind charge. 0 = same tick (use this on a server that runs InstantPearlCatch).", () -> c.pearlCatchDelayTicks, v -> c.pearlCatchDelayTicks = v, 0, 40, 1); y += ROW;
                addInt(y, "Wait for the pearl (ticks)", "Aim mode: how long to wait for the pearl to appear before throwing straight.", () -> c.pearlFindTicks, v -> c.pearlFindTicks = v, 1, 20, 1);
            }
            case 5 -> {
                addInt(y, "Insta cart bow draw ticks", "Insta cart: ticks the bow is drawn before it is released.", () -> c.bowMinChargeTicks, v -> c.bowMinChargeTicks = v, 1, 20, 1); y += ROW;
                addBool(y, "Xbow uses lava", "OFF = flint and steel placed in front of the cart. ON = lava bucket.", () -> c.expoUseLava, v -> c.expoUseLava = v); y += ROW;
                addInt(y, "Xbow fire distance", "Blocks from the cart, toward you, where the fire goes.", () -> c.expoAheadBlocks, v -> c.expoAheadBlocks = v, 1, 5, 1); y += ROW;
                addInt(y, "Xbow gap ticks", "Ticks between each placement.", () -> c.expoGapTicks, v -> c.expoGapTicks = v, 0, 10, 1); y += ROW;
                addInt(y, "Aim sync ticks", "After turning the camera, ticks to wait so the server has the new angle.", () -> c.aimSyncTicks, v -> c.aimSyncTicks = v, 1, 10, 1);
            }
            case 6 -> {
                addChoice(y, "Mode", "auto = the macro sprints, jumps, waits for the top of the jump and shoots. manual = you jump and press the key at the top.", () -> c.bowBoostMode, v -> c.bowBoostMode = v, "auto", "manual"); y += ROW;
                addDouble(y, "Camera pitch", "Degrees. Negative = up. -78 is almost straight up (Bow Boostin default). Try a few degrees either way if the arrow misses you.", () -> c.bowBoostPitch, v -> c.bowBoostPitch = v, -90, 0, 1); y += ROW;
                addInt(y, "Draw ticks", "Ticks the bow is drawn before release (4 is the minimum that fires). The arrow lands in a narrow window, so +-1 matters.", () -> c.bowBoostDrawTicks, v -> c.bowBoostDrawTicks = v, 4, 20, 1); y += ROW;
                addBool(y, "Chase the arrow", "Hold forward/sprint and steer by where YOUR arrow is, so you run into it. Turn off to stop running right after the shot.", () -> c.bowBoostChaseArrow, v -> c.bowBoostChaseArrow = v); y += ROW;
                addInt(y, "Longest run (ticks)", "Longest time to keep running after the shot.", () -> c.bowBoostRunTicks, v -> c.bowBoostRunTicks = v, 5, 80, 1); y += ROW;
                addBool(y, "Restore camera", "Put the camera back where it was afterwards.", () -> c.bowBoostRestoreLook, v -> c.bowBoostRestoreLook = v);
            }
            case 7 -> {
                addBool(y, "Auto totem", "Keep a totem in the offhand (also from the main inventory). Also toggled by the Toggle Auto Totem key.", () -> c.autoTotem, v -> { c.autoTotem = v; AutoTotem.enabled = v; }); y += ROW;
                addBool(y, "Replace non-empty offhand", "OFF: only fill an EMPTY offhand (keeps a shield).", () -> c.autoTotemReplaceOffhand, v -> c.autoTotemReplaceOffhand = v); y += ROW;
                addInt(y, "Delay ticks", "Ticks to wait before moving a totem.", () -> c.autoTotemDelayTicks, v -> c.autoTotemDelayTicks = v, 0, 20, 1);
            }
            case 8 -> {
                addBool(y, "Restock", "Top up a stack that gets low from another stack of the same item in your inventory. Also toggled by the Toggle Restock key.", () -> c.restock, v -> c.restock = v); y += ROW;
                addInt(y, "Restock below (% of a stack)", "Refill when a stack is below this share of its maximum. 50 = below half (pearls: under 8 of 16).", () -> (int) Math.round(c.restockThreshold * 100), v -> c.restockThreshold = v / 100.0, 10, 95, 5); y += ROW;
                addChoice(y, "Where", "hotbar = hotbar stacks (and offhand). all = also stacks inside the main inventory.", () -> c.restockScope, v -> c.restockScope = v, "hotbar", "all"); y += ROW;
                addChoice(y, "Which items", "all = every stackable item. list = only the items on the Restock Item List page.", () -> c.restockMode, v -> c.restockMode = v, "all", "list"); y += ROW;
                addBool(y, "Include offhand", "Also restock the offhand stack.", () -> c.restockOffhand, v -> c.restockOffhand = v); y += ROW;
                addInt(y, "Delay (ticks)", "How long a stack must stay low before it is refilled.", () -> c.restockDelayTicks, v -> c.restockDelayTicks = v, 0, 20, 1);
            }
            default -> {
                addActionButton(y, "Add the item in your hand", "Adds the item you are holding to the list (used when 'Which items' is set to list).", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    String id = mc.player == null ? null : Stacks.id(mc.player.getItemInHand(InteractionHand.MAIN_HAND));
                    if (id == null || mc.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) { Steps.msg(mc, "PvP Macro: hold the item first"); return; }
                    c.restockItems.add(id);
                    Config.save();
                    open(page);
                }); y += ROW;
                List<String> items = new ArrayList<>(new java.util.TreeSet<>(c.restockItems));
                for (int i = 0; i < Math.min(5, items.size()); i++) {
                    String id = items.get(i);
                    addActionButton(y, id + "   (click to remove)", "Remove this item from the list.", () -> { c.restockItems.remove(id); Config.save(); open(page); });
                    y += ROW;
                }
                if (items.size() > 5) { addLabel(y, "+ " + (items.size() - 5) + " more (use Clear to empty the list)"); y += ROW; }
                if (items.isEmpty()) { addLabel(y, "The list is empty"); y += ROW; }
                addActionButton(y, "Clear the list (" + items.size() + ")", "Remove every item from the list.", () -> { c.restockItems.clear(); Config.save(); open(page); });
            }
        }
        // navigation
        int by = this.height - 28, cx = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> open(page - 1)).pos(cx - 124, by).size(80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).pos(cx - 40, by).size(80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> open(page + 1)).pos(cx + 44, by).size(80, 20).build());
    }

    private void open(int newPage) {
        Config.save();
        Minecraft.getInstance().gui.setScreen(new PvpMacroScreen(parent, newPage));
    }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("Page " + (page + 1) + " of " + PAGES.length
                + "   |   keybinds: Options > Controls > PvP Macro"), this.width / 2, 26, 0xFFAAAAAA);
    }

    // ------------------------------------------------------------------ row builders

    private void addBool(int y, String label, String tip, BooleanSupplier get, Consumer<Boolean> set) {
        addBoolAt(left(), COL_W, y, label, tip, get, set);
    }

    private void addBoolAt(int x, int w, int y, String label, String tip, BooleanSupplier get, Consumer<Boolean> set) {
        Button button = Button.builder(text(label, get.getAsBoolean() ? "ON" : "OFF"), b -> {
            set.accept(!get.getAsBoolean());
            Config.save();
            b.setMessage(text(label, get.getAsBoolean() ? "ON" : "OFF"));
        }).pos(x, y).size(w, 20).build();
        button.setTooltip(tip(tip));
        this.addRenderableWidget(button);
    }

    private void addActionButton(int y, String label, String tip, Runnable action) {
        Button button = Button.builder(Component.literal(label), b -> action.run()).pos(left(), y).size(COL_W, 20).build();
        button.setTooltip(tip(tip));
        this.addRenderableWidget(button);
    }

    private void addLabel(int y, String label) {
        Button button = Button.builder(Component.literal(label), b -> { }).pos(left(), y).size(COL_W, 20).build();
        button.active = false;
        this.addRenderableWidget(button);
    }

    private void addChoice(int y, String label, String tip, Supplier<String> get, Consumer<String> set, String... options) {
        Button button = Button.builder(text(label, get.get()), b -> {
            int at = 0;
            for (int i = 0; i < options.length; i++) if (options[i].equalsIgnoreCase(get.get())) at = i;
            set.accept(options[(at + 1) % options.length]);
            Config.save();
            b.setMessage(text(label, get.get()));
        }).pos(left(), y).size(COL_W, 20).build();
        button.setTooltip(tip(tip));
        this.addRenderableWidget(button);
    }

    /** value button (click = + step, wraps) plus [-] and [+]. */
    private void addInt(int y, String label, String tip, IntSupplier get, IntConsumer set, int min, int max, int step) {
        Button value = Button.builder(text(label, String.valueOf(get.getAsInt())), b -> {
            set.accept(get.getAsInt() + step > max ? min : get.getAsInt() + step);
            Config.save();
            b.setMessage(text(label, String.valueOf(get.getAsInt())));
        }).pos(left(), y).size(200, 20).build();
        value.setTooltip(tip(tip));
        this.addRenderableWidget(value);
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            set.accept(Math.max(min, get.getAsInt() - step));
            Config.save();
            value.setMessage(text(label, String.valueOf(get.getAsInt())));
        }).pos(left() + 204, y).size(20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            set.accept(Math.min(max, get.getAsInt() + step));
            Config.save();
            value.setMessage(text(label, String.valueOf(get.getAsInt())));
        }).pos(left() + 228, y).size(20, 20).build());
    }

    private void addDouble(int y, String label, String tip, DoubleSupplier get, DoubleConsumer set, double min, double max, double step) {
        Button value = Button.builder(text(label, fmt(get.getAsDouble())), b -> {
            double next = get.getAsDouble() + step;
            set.accept(next > max + 1e-9 ? min : round(next));
            Config.save();
            b.setMessage(text(label, fmt(get.getAsDouble())));
        }).pos(left(), y).size(200, 20).build();
        value.setTooltip(tip(tip));
        this.addRenderableWidget(value);
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            set.accept(round(Math.max(min, get.getAsDouble() - step)));
            Config.save();
            value.setMessage(text(label, fmt(get.getAsDouble())));
        }).pos(left() + 204, y).size(20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            set.accept(round(Math.min(max, get.getAsDouble() + step)));
            Config.save();
            value.setMessage(text(label, fmt(get.getAsDouble())));
        }).pos(left() + 228, y).size(20, 20).build());
    }

    private static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(round(v)); }

    private void addResetButton(int y) {
        Button button = Button.builder(Component.literal(resetArmed ? "Click again: reset ALL settings" : "Reset all settings to defaults"), b -> {
            if (!resetArmed) {
                resetArmed = true;
                b.setMessage(Component.literal("Click again: reset ALL settings"));
                return;
            }
            resetArmed = false;
            Config.reset();
            AutoTotem.enabled = Config.get().autoTotem;
            open(page);
        }).pos(left(), y).size(COL_W, 20).build();
        button.setTooltip(tip("Puts every setting back to its default."));
        this.addRenderableWidget(button);
    }
}
