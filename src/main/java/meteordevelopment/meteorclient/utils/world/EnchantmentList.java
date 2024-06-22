/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.component.ComponentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class EnchantmentList {
    public static final Set<RegistryKey<Enchantment>> DEFAULT_ENCHANTMENT_KEYS;
    public static final Set<TagKey<Enchantment>> DEFAULT_ENCHANTMENT_TAGS;

    public final Set<RegistryKey<Enchantment>> literals;
    public final Set<TagKey<Enchantment>> tags;
    public final Set<ComponentType<?>> effects;

    public EnchantmentList() {
        literals = new ReferenceOpenHashSet<>();
        tags = new ReferenceOpenHashSet<>();
        effects = new ReferenceOpenHashSet<>();
    }

    public EnchantmentList(EnchantmentList other) {
        literals = new ReferenceOpenHashSet<>(other.literals);
        tags = new ReferenceOpenHashSet<>(other.tags);
        effects = new ReferenceOpenHashSet<>(other.effects);
    }

    public boolean contains(RegistryEntry<Enchantment> entry) {
        if (entry.matches(literals::contains)) return true;
        for (TagKey<Enchantment> tag : tags) {
            if (entry.isIn(tag)) return true;
        }
        for (ComponentType<?> effect : effects) {
            if (entry.value().effects().contains(effect)) return true;
        }
        return false;
    }

    public void clear() {
        literals.clear();
        tags.clear();
        effects.clear();
    }

    public void addGeneric(String id) {
        if (id.startsWith("#")) addTag(id.substring(1));
        else if (id.startsWith("@")) addEffect(id.substring(1));
        else addLiteral(id);
    }

    public void addLiteral(String id) {
        literals.add(RegistryKey.of(RegistryKeys.ENCHANTMENT, id(id)));
    }

    public void addEffect(String id) {
        @Nullable ComponentType<?> componentType = Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.get(id(id));
        if (componentType != null) effects.add(componentType);
    }

    public void addTag(String id) {
        tags.add(TagKey.of(RegistryKeys.ENCHANTMENT, id(id)));
    }

    private Identifier id(String id) {
        if (id.contains(":")) return Identifier.of(id);
        else return Identifier.ofVanilla(id);
    }

    static {
        //noinspection unchecked,rawtypes
        DEFAULT_ENCHANTMENT_KEYS = (Set) Arrays.stream(Enchantments.class.getDeclaredFields())
            //.filter(field -> field.getType() == RegistryKey.class)
            //.filter(field -> field.accessFlags().containsAll(List.of(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL)))
            .map(field -> {
                try {
                    return field.get(null);
                } catch (IllegalAccessException e) {
                    return null;
                }
            })//.filter(Objects::nonNull)
            .map(RegistryKey.class::cast)
            //.filter(registryKey -> registryKey.getRegistryRef().isOf(RegistryKeys.ENCHANTMENT))
            .collect(Collectors.toSet());

        //noinspection unchecked,rawtypes
        DEFAULT_ENCHANTMENT_TAGS = (Set) Arrays.stream(EnchantmentTags.class.getDeclaredFields())
            //.filter(field -> field.getType() == TagKey.class)
            //.filter(field -> field.accessFlags().containsAll(List.of(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL)))
            .map(field -> {
                try {
                    return field.get(null);
                } catch (IllegalAccessException e) {
                    return null;
                }
            })//.filter(Objects::nonNull)
            .map(TagKey.class::cast)
            //.filter(registryKey -> registryKey.registry().isOf(RegistryKeys.ENCHANTMENT))
            .collect(Collectors.toSet());
    }
}
