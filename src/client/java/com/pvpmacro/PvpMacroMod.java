package com.pvpmacro;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** All keybinds are unbound-by-default friendly: rebind them in Options > Controls > PvP Macro. */
public class PvpMacroMod implements ClientModInitializer {
    private final MacroRunner runner = new MacroRunner();
    private final List<KeyMapping> keys = new ArrayList<>();
    private final List<Function<Minecraft, List<Step>>> actions = new ArrayList<>();
    private KeyMapping cancelKey;
    private KeyMapping autoTotemKey;
    private KeyMapping restockKey;
    private KeyMapping menuKey;
    private final List<String> ids = new ArrayList<>();
    private KeyMapping.Category category;
    private int glideTicks = 0;

    private void add(String id, int defaultKey, Function<Minecraft, List<Step>> action) {
        keys.add(KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvpmacro." + id, defaultKey, category)));
        actions.add(action);
        ids.add(id);
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pvpmacro", "main"));

        add("stun_slam",    GLFW.GLFW_KEY_Z,         mc -> Combos.stunSlam());
        add("sword_swap",   GLFW.GLFW_KEY_X,         mc -> Combos.swordSwap());
        add("breach_swap",  GLFW.GLFW_KEY_B,         mc -> Combos.breachSwap());
        add("lunge_swap",   GLFW.GLFW_KEY_N,         mc -> Combos.lungeSwap());
        add("elytra_mace",  GLFW.GLFW_KEY_G,         mc -> Combos.elytraMace(mc));
        add("insta_cart",   GLFW.GLFW_KEY_C,         mc -> Combos.instaCart());
        add("expo",         GLFW.GLFW_KEY_V,         mc -> Combos.expo());
        add("pearl_catch",  GLFW.GLFW_KEY_J,         mc -> Combos.pearlCatch());
        add("bow_boost",    GLFW.GLFW_KEY_K,         mc -> Combos.bowBoost());
        cancelKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.pvpmacro.cancel", GLFW.GLFW_KEY_BACKSPACE, category));

        // Auto totem toggle: unbound by default (rebind it in Controls).
        autoTotemKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.pvpmacro.auto_totem", GLFW.GLFW_KEY_UNKNOWN, category));

        // Restock toggle: unbound by default. Menu key: Right Shift.
        restockKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.pvpmacro.restock", GLFW.GLFW_KEY_UNKNOWN, category));

        // Inventory pull toggle: unbound by default. Menu key: Left Control.
        inventoryPullKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.pvpmacro.inventory_pull", GLFW.GLFW_KEY_UNKNOWN, category));

        menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.pvpmacro.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (mc.player == null) { runner.reset(); glideTicks = 0; return; }   // left the world: drop any half-run macro

        if (cancelKey.consumeClick()) runner.cancel(mc);
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).consumeClick()) {
                if (Config.get().isMacroEnabled(ids.get(i))) runner.press(mc, actions.get(i));
                else Steps.msg(mc, "PvP Macro: " + ids.get(i) + " is switched off in the menu");
            }
        }
        Diagnostics.runOnce(mc);
        runner.tick(mc);
        if (autoTotemKey.consumeClick()) AutoTotem.toggle(mc);
        AutoTotem.tick(mc, runner.running());
        if (restockKey.consumeClick()) Restock.toggle(mc);
        Restock.tick(mc, runner.running());
        if (inventoryPullKey.consumeClick()) InventoryPull.toggle(mc);
        InventoryPull.tick(mc, runner.running());
        if (menuKey.consumeClick() && !AutoTotem.screenOpen(mc)) mc.setScreen(new MenuScreen());
        MenuScreen.tickFallback(mc);

        // Elytra guard: when low, swap back to the chestplate before the 1 second durability tick.
        Config cfg = Config.get();
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (cfg.elytraGuard && mc.player.isFallFlying() && chest.is(Items.ELYTRA)) {
            glideTicks++;
            double left = 1.0 - (double) chest.getDamageValue() / Math.max(1, chest.getMaxDamage());
            if (left < cfg.lowElytraFraction && glideTicks >= cfg.elytraGuardTick && !runner.running()) {
                runner.start(mc, Combos.elytraGuard());
                glideTicks = 0;
            }
        } else {
            glideTicks = 0;
        }
    }
}
