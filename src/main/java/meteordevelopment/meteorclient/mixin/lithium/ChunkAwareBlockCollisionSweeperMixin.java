/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin.lithium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ChunkAwareBlockCollisionSweeper.class)
public abstract class ChunkAwareBlockCollisionSweeperMixin {
    @Shadow
    @Final
    private Level world;

    @Final
    @Shadow
    private BlockPos.MutableBlockPos pos;

    @ModifyExpressionValue(method = "computeNext()Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape modifyCollisionShape(VoxelShape original, @Local BlockState state) {
        if (world != Minecraft.getInstance().level) return original;

        CollisionShapeEvent event = MeteorClient.EVENT_BUS.post(CollisionShapeEvent.get(state, pos, original));
        return event.isCancelled() ? Shapes.empty() : event.shape;
    }
}
