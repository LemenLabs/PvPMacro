package com.pvpmacro.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SERVER SIDE. Rewritten from InstantPearlCatch by lyosha (Unlicense / public domain) for use inside PvP Macro.
 * Entities are ticked in a fixed order: thrown projectiles (ender pearls) first, then wind charges, then everything else
 * (ties broken by entity id), so a pearl and a wind charge thrown in the same tick are always processed in the same order.
 */
@Mixin(EntityTickList.class)
public abstract class EntityTickListMixin {
    @Redirect(method = "forEach",
              at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"))
    private ObjectCollection<Entity> pvpmacro$sortedValues(Int2ObjectMap<Entity> active) {
        ObjectArrayList<Entity> sorted = new ObjectArrayList<>(active.values());
        sorted.unstableSort((a, b) -> {
            int pa = priority(a);
            int pb = priority(b);
            return pa != pb ? Integer.compare(pa, pb) : Integer.compare(a.getId(), b.getId());
        });
        return sorted;
    }

    private static int priority(Entity e) {
        if (e instanceof ThrowableProjectile) return 0;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return path.contains("wind_charge") ? 1 : 2;
    }
}
