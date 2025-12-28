/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.entity;

import meteordevelopment.meteorclient.mixin.CrossbowItemAccessor;
import meteordevelopment.meteorclient.mixin.ProjectileInGroundAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MissHitResult;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ProjectileEntitySimulator {
    private static final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

    private static final Vec3 pos3d = new Vec3(0, 0, 0);
    private static final Vec3 prevPos3d = new Vec3(0, 0, 0);

    public final Vector3d pos = new Vector3d();
    private final Vector3d velocity = new Vector3d();

    private Entity simulatingEntity;
    private double gravity;
    private double airDrag, waterDrag;
    private float height, width;


    // held items

    public boolean set(Entity user, ItemStack itemStack, double simulated, boolean accurate, float tickDelta) {
        Item item = itemStack.getItem();

        switch (item) {
            case BowItem ignored -> {
                double charge = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
                if (charge <= 0.1) return false;

                set(user, 0, charge * 3, simulated, 0.05, 0.6, accurate, tickDelta, EntityType.ARROW);
            }
            case CrossbowItem ignored -> {
                ChargedProjectiles projectilesComponent = itemStack.get(DataComponents.CHARGED_PROJECTILES);
                if (projectilesComponent == null) return false;

                if (projectilesComponent.contains(Items.FIREWORK_ROCKET)) {
                    set(user, 0, CrossbowItemAccessor.meteor$getSpeed(projectilesComponent), simulated, 0, 0.6, accurate, tickDelta, EntityType.FIREWORK_ROCKET);
                } else
                    set(user, 0, CrossbowItemAccessor.meteor$getSpeed(projectilesComponent), simulated, 0.05, 0.6, accurate, tickDelta, EntityType.ARROW);
            }
            case WindChargeItem ignored -> {
                set(user, 0, 1.5, simulated, 0, 1.0, accurate, tickDelta, EntityType.WIND_CHARGE);
                this.airDrag = 1.0;
            }
            case FishingRodItem ignored -> setFishingBobber(user, tickDelta);
            case TridentItem ignored ->
                set(user, 0, 2.5, simulated, 0.05, 0.99, accurate, tickDelta, EntityType.TRIDENT);
            case SnowballItem ignored ->
                set(user, 0, 1.5, simulated, 0.03, 0.8, accurate, tickDelta, EntityType.SNOWBALL);
            case EggItem ignored -> set(user, 0, 1.5, simulated, 0.03, 0.8, accurate, tickDelta, EntityType.EGG);
            case EnderpearlItem ignored ->
                set(user, 0, 1.5, simulated, 0.03, 0.8, accurate, tickDelta, EntityType.ENDER_PEARL);
            case ExperienceBottleItem ignored ->
                set(user, -20, 0.7, simulated, 0.07, 0.8, accurate, tickDelta, EntityType.EXPERIENCE_BOTTLE);
            case SplashPotionItem ignored ->
                set(user, -20, 0.5, simulated, 0.05, 0.8, accurate, tickDelta, EntityType.SPLASH_POTION);
            case LingeringPotionItem ignored ->
                set(user, -20, 0.5, simulated, 0.05, 0.8, accurate, tickDelta, EntityType.LINGERING_POTION);
            default -> {
                return false;
            }
        }

        return true;
    }

    public void set(Entity user, double roll, double speed, double simulated, double gravity, double waterDrag, boolean accurate, float tickDelta, EntityType<?> type) {
        Utils.set(pos, user, tickDelta).add(0, user.getEyeHeight(user.getPose()), 0);

        double yaw;
        double pitch;

        if (user == mc.player && Rotations.rotating) {
            yaw = Rotations.serverYaw;
            pitch = Rotations.serverPitch;
        } else {
            yaw = user.getViewYRot(tickDelta);
            pitch = user.getViewXRot(tickDelta);
        }

        double x, y, z;

        if (simulated == 0) {
            x = -Math.sin(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
            y = -Math.sin((pitch + roll) * 0.017453292);
            z = Math.cos(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
        } else {
            Vec3 vec3d = user.getUpVector(1.0F);
            Quaterniond quaternion = new Quaterniond().setAngleAxis(simulated, vec3d.x, vec3d.y, vec3d.z);
            Vec3 vec3d2 = user.getViewVector(1.0F);
            Vector3d vector3f = new Vector3d(vec3d2.x, vec3d2.y, vec3d2.z);
            vector3f.rotate(quaternion);

            x = vector3f.x;
            y = vector3f.y;
            z = vector3f.z;
        }

        velocity.set(x, y, z).normalize().mul(speed);

        if (accurate) {
            Vec3 vel = user.getDeltaMovement();
            velocity.add(vel.x, user.onGround() ? 0.0D : vel.y, vel.z);
        }

        this.simulatingEntity = type.create(mc.level, null);
        this.gravity = gravity;
        this.airDrag = 0.99;
        this.waterDrag = waterDrag;
        this.width = type.getWidth();
        this.height = type.getHeight();
    }


    // fired projectiles

    public boolean set(Entity entity, boolean accurate) {
        // skip entities in ground
        if (entity instanceof ProjectileInGroundAccessor ppe && ppe.meteor$invokeIsInGround()) return false;

        if (entity instanceof Arrow) {
            set(entity, 0.05, 0.6, accurate);
        } else if (entity instanceof ThrownTrident) {
            set(entity, 0.05, 0.99, accurate);
        } else if (entity instanceof ThrownEnderpearl || entity instanceof Snowball || entity instanceof ThrownEgg) {
            set(entity, 0.03, 0.8, accurate);
        } else if (entity instanceof ThrownExperienceBottle) {
            set(entity, 0.07, 0.8, accurate);
        } else if (entity instanceof AbstractThrownPotion) {
            set(entity, 0.05, 0.8, accurate);
        } else if (entity instanceof WitherSkull || entity instanceof LargeFireball || entity instanceof DragonFireball || entity instanceof WindCharge) {
            // drag isn't actually 1, but this provides accurate results in 99.9% in of real situations.
            set(entity, 0, 1.0, accurate);
            this.airDrag = 1.0;
        } else {
            return false;
        }

        if (entity.isNoGravity()) {
            this.gravity = 0;
        }

        return true;
    }

    public void set(Entity entity, double gravity, double waterDrag, boolean accurate) {
        pos.set(entity.getX(), entity.getY(), entity.getZ());

        double speed = entity.getDeltaMovement().length();
        velocity.set(entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z).normalize().mul(speed);

        if (accurate) {
            Vec3 vel = entity.getDeltaMovement();
            velocity.add(vel.x, entity.onGround() ? 0.0D : vel.y, vel.z);
        }

        this.simulatingEntity = entity;
        this.gravity = gravity;
        this.airDrag = 0.99;
        this.waterDrag = waterDrag;
        this.width = entity.getBbWidth();
        this.height = entity.getBbHeight();
    }

    public void setFishingBobber(Entity user, float tickDelta) {
        double yaw;
        double pitch;

        if (user == mc.player && Rotations.rotating) {
            yaw = Rotations.serverYaw;
            pitch = Rotations.serverPitch;
        } else {
            yaw = user.getViewYRot(tickDelta);
            pitch = user.getViewXRot(tickDelta);
        }

        double h = Math.cos(-yaw * 0.017453292F - 3.1415927F);
        double i = Math.sin(-yaw * 0.017453292F - 3.1415927F);
        double j = -Math.cos(-pitch * 0.017453292F);
        double k = Math.sin(-pitch * 0.017453292F);

        Utils.set(pos, user, tickDelta).sub(i * 0.3, 0, h * 0.3).add(0, user.getEyeHeight(user.getPose()), 0);

        velocity.set(-i, Mth.clamp(-(k / j), -5, 5), -h);

        double l = velocity.length();
        velocity.mul(0.6 / l + 0.5, 0.6 / l + 0.5, 0.6 / l + 0.5);

        simulatingEntity = EntityType.FISHING_BOBBER.create(mc.level, null);
        gravity = 0.03;
        airDrag = 0.92;
        waterDrag = 0;
        width = EntityType.FISHING_BOBBER.getWidth();
        height = EntityType.FISHING_BOBBER.getHeight();
    }

    public HitResult tick() {
        // Apply velocity
        ((IVec3d) prevPos3d).meteor$set(pos);
        pos.add(velocity);

        // Update velocity
        velocity.mul(isTouchingWater() ? waterDrag : airDrag);
        velocity.sub(0, gravity, 0);

        // Check if below world
        if (pos.y < mc.level.getMinY()) return MissHitResult.INSTANCE;

        // Check if chunk is loaded
        int chunkX = SectionPos.posToSectionCoord(pos.x);
        int chunkZ = SectionPos.posToSectionCoord(pos.z);
        if (!mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) return MissHitResult.INSTANCE;

        // Check for collision
        ((IVec3d) pos3d).meteor$set(pos);
        if (pos3d.equals(prevPos3d)) return MissHitResult.INSTANCE;

        HitResult hitResult = getCollision();

        return hitResult.getType() == HitResult.Type.MISS ? null : hitResult;
    }

    private boolean isTouchingWater() {
        blockPos.set(pos.x, pos.y, pos.z);

        FluidState fluidState = mc.level.getFluidState(blockPos);
        if (fluidState.getType() != Fluids.WATER && fluidState.getType() != Fluids.FLOWING_WATER) return false;

        return pos.y - (int) pos.y <= fluidState.getOwnHeight();
    }

    private HitResult getCollision() {
        HitResult hitResult = mc.level.clip(new ClipContext(prevPos3d, pos3d, ClipContext.Block.COLLIDER, waterDrag == 0 ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, simulatingEntity));
        if (hitResult.getType() != HitResult.Type.MISS) {
            ((IVec3d) pos3d).meteor$set(hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z);
        }

        // Vanilla uses the current and next positions to check collisions, we use the previous and current positions
        AABB box = new AABB(prevPos3d.x - (width / 2f), prevPos3d.y, prevPos3d.z - (width / 2f), prevPos3d.x + (width / 2f), prevPos3d.y + height, prevPos3d.z + (width / 2f))
            .expandTowards(velocity.x, velocity.y, velocity.z).inflate(1.0D);
        HitResult hitResult2 = ProjectileUtil.getEntityHitResult(
            mc.level,
            simulatingEntity,
            prevPos3d,
            pos3d,
            box,
            entity -> !entity.isSpectator() && entity.isAlive() && entity.isPickable(),
            ProjectileUtil.computeMargin(simulatingEntity)
        );
        if (hitResult2 != null) {
            hitResult = hitResult2;
        }

        return hitResult;
    }
}
