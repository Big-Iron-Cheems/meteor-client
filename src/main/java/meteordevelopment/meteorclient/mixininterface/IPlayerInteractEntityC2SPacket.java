/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixininterface;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;

public interface IPlayerInteractEntityC2SPacket {
    ServerboundInteractPacket.ActionType meteor$getType();

    Entity meteor$getEntity();
}
