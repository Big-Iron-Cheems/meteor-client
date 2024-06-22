/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.world.EnchantmentList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class EnchantmentListSetting extends Setting<EnchantmentList> {
    public EnchantmentListSetting(String name, String description, EnchantmentList defaultValue, Consumer<EnchantmentList> onChanged, Consumer<Setting<EnchantmentList>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    public void resetImpl() {
        value = new EnchantmentList(defaultValue);
    }

    @Override
    protected EnchantmentList parseImpl(String str) {
        String[] values = str.split(",");
        EnchantmentList enchs = new EnchantmentList();

        for (String value : values) {
            enchs.addGeneric(value.trim());
        }

        return enchs;
    }

    @Override
    protected boolean isValueValid(EnchantmentList value) {
        return true;
    }

    @Override
    public Iterable<Identifier> getIdentifierSuggestions() {
        return Optional.ofNullable(MinecraftClient.getInstance().getNetworkHandler())
            .flatMap(networkHandler -> networkHandler.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT))
            .map(Registry::getIds).orElse(Set.of());
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        NbtList literals = new NbtList();
        for (RegistryKey<Enchantment> literal : get().literals) {
            literals.add(NbtString.of(literal.getValue().toString()));
        }
        tag.put("literals", literals);


        NbtList tags = new NbtList();
        for (TagKey<Enchantment> tagKey : get().tags) {
            tags.add(NbtString.of(tagKey.id().toString()));
        }
        tag.put("tags", tags);

        NbtList effects = new NbtList();
        for (ComponentType<?> effect : get().effects) {
            @Nullable Identifier id = Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getId(effect);
            if (id != null) effects.add(NbtString.of(id.toString()));
        }
        tag.put("effects", effects);

        return tag;
    }

    @Override
    public EnchantmentList load(NbtCompound tag) {
        get().clear();

        for (NbtElement tagI : tag.getList("literals", 8)) {
            get().addLiteral(tagI.asString());
        }

        for (NbtElement tagI : tag.getList("tags", 8)) {
            get().addTag(tagI.asString());
        }

        for (NbtElement tagI : tag.getList("effects", 8)) {
            get().addEffect(tagI.asString());
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, EnchantmentList, EnchantmentListSetting> {
        public Builder() {
            super(new EnchantmentList());
        }

        public Builder vanillaDefaults() {
            defaultValue.literals.addAll(EnchantmentList.DEFAULT_ENCHANTMENT_KEYS);
            return this;
        }

        @SafeVarargs
        public final Builder defaultValue(RegistryKey<Enchantment>... defaults) {
            defaultValue.literals.addAll(List.of(defaults));
            return this;
        }

        @SafeVarargs
        public final Builder defaultValue(TagKey<Enchantment>... defaults) {
            defaultValue.tags.addAll(List.of(defaults));
            return this;
        }

        public final Builder defaultValue(ComponentType<?>... defaults) {
            defaultValue.effects.addAll(List.of(defaults));
            return this;
        }

        @Override
        public EnchantmentListSetting build() {
            return new EnchantmentListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
