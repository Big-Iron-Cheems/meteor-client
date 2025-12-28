/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import com.google.common.collect.Streams;
import meteordevelopment.meteorclient.events.entity.player.CanWalkOnFluidEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.LivingEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Jesus extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgWater = settings.createGroup("Water");
    private final SettingGroup sgLava = settings.createGroup("Lava");

    // General

    private final Setting<Boolean> powderSnow = sgGeneral.add(new BoolSetting.Builder()
        .name("powder-snow")
        .description("Walk on powder snow.")
        .defaultValue(true)
        .build()
    );

    // Water

    private final Setting<Mode> waterMode = sgWater.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to treat the water.")
        .defaultValue(Mode.Solid)
        .build()
    );

    private final Setting<Boolean> dipIfBurning = sgWater.add(new BoolSetting.Builder()
        .name("dip-if-burning")
        .description("Lets you go into the water when you are burning.")
        .defaultValue(true)
        .visible(() -> waterMode.get() == Mode.Solid)
        .build()
    );

    private final Setting<Boolean> dipOnSneakWater = sgWater.add(new BoolSetting.Builder()
        .name("dip-on-sneak")
        .description("Lets you go into the water when your sneak key is held.")
        .defaultValue(true)
        .visible(() -> waterMode.get() == Mode.Solid)
        .build()
    );

    private final Setting<Boolean> dipOnFallWater = sgWater.add(new BoolSetting.Builder()
        .name("dip-on-fall")
        .description("Lets you go into the water when you fall over a certain height.")
        .defaultValue(true)
        .visible(() -> waterMode.get() == Mode.Solid)
        .build()
    );

    private final Setting<Integer> dipFallHeightWater = sgWater.add(new IntSetting.Builder()
        .name("dip-fall-height")
        .description("The fall height at which you will go into the water.")
        .defaultValue(4)
        .range(1, 255)
        .sliderRange(3, 20)
        .visible(() -> waterMode.get() == Mode.Solid && dipOnFallWater.get())
        .build()
    );

    // Lava

    private final Setting<Mode> lavaMode = sgLava.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to treat the lava.")
        .defaultValue(Mode.Solid)
        .build()
    );

    private final Setting<Boolean> dipIfFireResistant = sgLava.add(new BoolSetting.Builder()
        .name("dip-if-resistant")
        .description("Lets you go into the lava if you have Fire Resistance effect.")
        .defaultValue(true)
        .visible(() -> lavaMode.get() == Mode.Solid)
        .build()
    );

    private final Setting<Boolean> dipOnSneakLava = sgLava.add(new BoolSetting.Builder()
        .name("dip-on-sneak")
        .description("Lets you go into the lava when your sneak key is held.")
        .defaultValue(true)
        .visible(() -> lavaMode.get() == Mode.Solid)
        .build()
    );

    private final Setting<Boolean> dipOnFallLava = sgLava.add(new BoolSetting.Builder()
        .name("dip-on-fall")
        .description("Lets you go into the lava when you fall over a certain height.")
        .defaultValue(true)
        .visible(() -> lavaMode.get() == Mode.Solid)
        .build()
    );

    private final Setting<Integer> dipFallHeightLava = sgLava.add(new IntSetting.Builder()
        .name("dip-fall-height")
        .description("The fall height at which you will go into the lava.")
        .defaultValue(4)
        .range(1, 255)
        .sliderRange(3, 20)
        .visible(() -> lavaMode.get() == Mode.Solid && dipOnFallLava.get())
        .build()
    );

    // Other

    private final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

    private int tickTimer = 10;
    private int packetTimer = 0;

    private boolean prePathManagerWalkOnWater;
    private boolean prePathManagerWalkOnLava;

    public boolean isInBubbleColumn = false;

    public Jesus() {
        super(Categories.Movement, "jesus", "Walk on liquids and powder snow like Jesus.");
    }

    @Override
    public void onActivate() {
        prePathManagerWalkOnWater = PathManagers.get().getSettings().getWalkOnWater().get();
        prePathManagerWalkOnLava = PathManagers.get().getSettings().getWalkOnLava().get();

        PathManagers.get().getSettings().getWalkOnWater().set(waterMode.get() == Mode.Solid);
        PathManagers.get().getSettings().getWalkOnLava().set(lavaMode.get() == Mode.Solid);
    }

    @Override
    public void onDeactivate() {
        PathManagers.get().getSettings().getWalkOnWater().set(prePathManagerWalkOnWater);
        PathManagers.get().getSettings().getWalkOnLava().set(prePathManagerWalkOnLava);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean bubbleColumn = isInBubbleColumn;
        isInBubbleColumn = false;

        if ((waterMode.get() == Mode.Bob && mc.player.isInWater()) || (lavaMode.get() == Mode.Bob && mc.player.isInLava())) {
            double fluidHeight;
            if (mc.player.isInLava()) fluidHeight = mc.player.getFluidHeight(FluidTags.LAVA);
            else fluidHeight = mc.player.getFluidHeight(FluidTags.WATER);

            double swimHeight = mc.player.getFluidJumpThreshold();

            if (mc.player.isInWater() && fluidHeight > swimHeight) {
                ((LivingEntityAccessor) mc.player).meteor$swimUpwards(FluidTags.WATER);
            } else if (mc.player.onGround() && fluidHeight <= swimHeight && ((LivingEntityAccessor) mc.player).meteor$getJumpCooldown() == 0) {
                mc.player.jumpFromGround();
                ((LivingEntityAccessor) mc.player).meteor$setJumpCooldown(10);
            } else {
                ((LivingEntityAccessor) mc.player).meteor$swimUpwards(FluidTags.LAVA);
            }
        }

        if (mc.player.isInWater() && !waterShouldBeSolid()) return;
        if (mc.player.isVisuallySwimming()) return;
        if (mc.player.isInLava() && !lavaShouldBeSolid()) return;

        // Move up in bubble columns
        if (bubbleColumn) {
            if (mc.options.keyJump.isDown() && mc.player.getDeltaMovement().y() < 0.11)
                ((IVec3d) mc.player.getDeltaMovement()).meteor$setY(0.11);
            return;
        }

        // Move up
        if (mc.player.isInWater() || mc.player.isInLava()) {
            ((IVec3d) mc.player.getDeltaMovement()).meteor$setY(0.11);
            tickTimer = 0;
            return;
        }

        BlockState blockBelowState = mc.level.getBlockState(mc.player.blockPosition().below());
        boolean waterLogger = false;
        try {
            waterLogger = blockBelowState.getValue(BlockStateProperties.WATERLOGGED);
        } catch (Exception ignored) {
        }


        // Simulate jumping out of water
        if (tickTimer == 0) ((IVec3d) mc.player.getDeltaMovement()).meteor$setY(0.30);
        else if (tickTimer == 1 && (blockBelowState == Blocks.WATER.defaultBlockState() || blockBelowState == Blocks.LAVA.defaultBlockState() || waterLogger))
            ((IVec3d) mc.player.getDeltaMovement()).meteor$setY(0);

        tickTimer++;
    }

    @EventHandler
    private void onCanWalkOnFluid(CanWalkOnFluidEvent event) {
        if (mc.player != null && mc.player.isSwimming()) return;
        if ((event.fluidState.getType() == Fluids.WATER || event.fluidState.getType() == Fluids.FLOWING_WATER) && waterShouldBeSolid()) {
            event.walkOnFluid = true;
        } else if ((event.fluidState.getType() == Fluids.LAVA || event.fluidState.getType() == Fluids.FLOWING_LAVA) && lavaShouldBeSolid()) {
            event.walkOnFluid = true;
        }
    }

    @EventHandler
    private void onFluidCollisionShape(CollisionShapeEvent event) {
        if (event.state.getFluidState().isEmpty()) return;

        if ((event.state.getBlock() == Blocks.WATER | event.state.getFluidState().getType() == Fluids.WATER) && !mc.player.isInWater() && waterShouldBeSolid() && event.pos.getY() <= mc.player.getY() - 1) {
            event.shape = Shapes.block();
        } else if (event.state.getBlock() == Blocks.LAVA && !mc.player.isInLava() && lavaShouldBeSolid() && (!lavaIsSafe() || event.pos.getY() <= mc.player.getY() - 1)) {
            event.shape = Shapes.block();
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundMovePlayerPacket packet)) return;
        if (mc.player.isInWater() && !waterShouldBeSolid()) return;
        if (mc.player.isInLava() && !lavaShouldBeSolid()) return;

        // Check if packet contains a position
        if (!(packet instanceof ServerboundMovePlayerPacket.Pos || packet instanceof ServerboundMovePlayerPacket.PosRot))
            return;

        // Check inWater, fallDistance and if over liquid
        if (mc.player.isInWater() || mc.player.isInLava() || mc.player.fallDistance > 3f || !isOverLiquid()) return;

        // If not actually moving, cancel packet
        if (mc.player.input.getMoveVector().equals(Vec2.ZERO)) {
            event.cancel();
            return;
        }

        // Wait for timer
        if (packetTimer++ < 4) return;
        packetTimer = 0;

        // Cancel old packet
        event.cancel();

        // Get position
        double x = packet.getX(0);
        double y = packet.getY(0) + 0.05;
        double z = packet.getZ(0);

        // Create new packet
        Packet<?> newPacket;
        if (packet instanceof ServerboundMovePlayerPacket.Pos) {
            newPacket = new ServerboundMovePlayerPacket.Pos(x, y, z, true, mc.player.horizontalCollision);
        } else {
            newPacket = new ServerboundMovePlayerPacket.PosRot(x, y, z, packet.getYRot(0), packet.getXRot(0), true, mc.player.horizontalCollision);
        }

        // Send new packet
        mc.getConnection().getConnection().send(newPacket);
    }

    private boolean waterShouldBeSolid() {
        if (EntityUtils.getGameMode(mc.player) == GameType.SPECTATOR || mc.player.getAbilities().flying) return false;

        if (mc.player.getVehicle() != null) {
            Entity vehicle = mc.player.getVehicle();
            if (vehicle instanceof AbstractBoat) return false;
        }

        if (Modules.get().get(Flight.class).isActive()) return false;

        if (dipIfBurning.get() && mc.player.isOnFire()) return false;

        if (dipOnSneakWater.get() && mc.options.keyShift.isDown()) return false;
        if (dipOnFallWater.get() && mc.player.fallDistance > dipFallHeightWater.get()) return false;

        return waterMode.get() == Mode.Solid;
    }

    private boolean lavaShouldBeSolid() {
        if (EntityUtils.getGameMode(mc.player) == GameType.SPECTATOR || mc.player.getAbilities().flying) return false;

        if (!lavaIsSafe() && lavaMode.get() == Mode.Solid) return true;

        if (dipOnSneakLava.get() && mc.options.keyShift.isDown()) return false;
        if (dipOnFallLava.get() && mc.player.fallDistance > dipFallHeightLava.get()) return false;

        return lavaMode.get() == Mode.Solid;
    }

    private boolean lavaIsSafe() {
        if (!dipIfFireResistant.get()) return false;
        return mc.player.hasEffect(MobEffects.FIRE_RESISTANCE) && (mc.player.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() > (15 * 20 * mc.player.getAttributeValue(Attributes.BURNING_TIME)));
    }

    private boolean isOverLiquid() {
        boolean foundLiquid = false;
        boolean foundSolid = false;

        List<AABB> blockCollisions = Streams.stream(mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().move(0, -0.5, 0)))
            .map(VoxelShape::bounds)
            .collect(Collectors.toCollection(ArrayList::new));

        for (AABB bb : blockCollisions) {
            blockPos.set(Mth.lerp(0.5D, bb.minX, bb.maxX), Mth.lerp(0.5D, bb.minY, bb.maxY), Mth.lerp(0.5D, bb.minZ, bb.maxZ));
            BlockState blockState = mc.level.getBlockState(blockPos);

            if ((blockState.getBlock() == Blocks.WATER | blockState.getFluidState().getType() == Fluids.WATER) || blockState.getBlock() == Blocks.LAVA)
                foundLiquid = true;
            else if (!blockState.isAir()) foundSolid = true;
        }

        return foundLiquid && !foundSolid;
    }

    public enum Mode {
        Solid,
        Bob,
        Ignore
    }

    public boolean canWalkOnPowderSnow() {
        return isActive() && powderSnow.get();
    }
}
