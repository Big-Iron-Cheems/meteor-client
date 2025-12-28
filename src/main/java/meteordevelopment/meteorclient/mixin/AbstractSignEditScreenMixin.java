/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {
    @ModifyExpressionValue(method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;ZZLnet/minecraft/network/chat/Component;)V", at = @At(value = "INVOKE", target = "Ljava/util/stream/IntStream;mapToObj(Ljava/util/function/IntFunction;)Ljava/util/stream/Stream;"))
    private Stream<Component> modifyTranslatableText(Stream<Component> original) {
        return original.map(this::modifyText);
    }

    // based on https://github.com/JustAlittleWolf/ModDetectionPreventer
    @Unique
    private Component modifyText(Component message) {
        MutableComponent modified = MutableComponent.create(message.getContents());

        if (message.getContents() instanceof KeybindContents content) {
            String key = content.getName();

            if (key.contains("meteor-client"))
                modified = MutableComponent.create(new PlainTextContents.LiteralContents(key));
        }
        if (message.getContents() instanceof TranslatableContents content) {
            String key = content.getKey();

            if (key.contains("meteor-client"))
                modified = MutableComponent.create(new PlainTextContents.LiteralContents(key));
        }

        modified.setStyle(message.getStyle());
        for (Component sibling : message.getSiblings()) {
            modified.append(modifyText(sibling));
        }

        return modified;
    }
}
