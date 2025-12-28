/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;

public class NoRotate extends Module {
    public NoRotate() {
        super(Categories.Player, "no-rotate", "Attempts to block rotations sent from server to client.");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
            PositionMoveRotation oldPosition = packet.change();
            PositionMoveRotation newPosition = new PositionMoveRotation(
                oldPosition.position(),
                oldPosition.deltaMovement(),
                mc.player.getYRot(),
                mc.player.getXRot()
            );
            event.packet = ClientboundPlayerPositionPacket.of(
                packet.id(),
                newPosition,
                packet.relatives()
            );
        }
    }
}
