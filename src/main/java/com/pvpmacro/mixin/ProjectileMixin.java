package com.pvpmacro.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SERVER SIDE. Rewritten from InstantPearlCatch by lyosha (Unlicense / public domain) for use inside PvP Macro.
 * A thrown projectile (ender pearl) younger than 5 ticks cannot hit another projectile (the wind charge thrown with it).
 * Only active on a dedicated server that has this jar installed.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    private static final int MIN_PEARL_VS_PROJECTILE_TICKS = 5;

    @Inject(method = "canHitEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void pvpmacro$blockEarlyProjectileVsProjectile(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ThrowableProjectile throwable)) return;
        if (!(target instanceof Projectile)) return;
        if (throwable.tickCount < MIN_PEARL_VS_PROJECTILE_TICKS) cir.setReturnValue(false);
    }
}
