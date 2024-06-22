/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.world.EnchantmentList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class EnchantmentListSettingScreen extends DynamicRegistryListSettingScreen<EnchantmentList> {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<Registry<Enchantment>> registry;

    public EnchantmentListSettingScreen(GuiTheme theme, Setting<EnchantmentList> setting) {
        super(theme, "Select Enchantments", setting, true);

        this.registry = Optional.ofNullable(MinecraftClient.getInstance().getNetworkHandler())
            .flatMap(networkHandler -> networkHandler.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT));
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    protected Section<?>[] getLeftSections() {
        return new Section[]{
            new Section<>("Literals",
                registry
                    .map(registry -> registry.streamEntries()
                        .map(RegistryEntry.Reference::registryKey)
                    ).orElse(EnchantmentList.DEFAULT_ENCHANTMENT_KEYS.stream())
                    .filter(t -> !setting.get().literals.contains(t))
                    .collect(Collectors.toList()),
                Names::get,
                t -> addValue(setting.get().literals, t),
                t -> new String[]{t.getValue().toString()}
            ),
            new Section<>("Effects",
                Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.stream()
                    .filter(t -> !setting.get().effects.contains(t))
                    .collect(Collectors.toList()),
                t -> "@" + Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getId(t).toString(),
                t -> addValue(setting.get().effects, t)
            ),
            new Section<>("Tags",
                registry.map(Registry::streamTags)
                    .orElse(EnchantmentList.DEFAULT_ENCHANTMENT_TAGS.stream())
                    .filter(t -> !setting.get().tags.contains(t))
                    .collect(Collectors.toList()),
                t -> "#" + t.id().toString(),
                t -> addValue(setting.get().tags, t)
            )
        };
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    protected Section<?>[] getRightSections() {
        return new Section[]{
            new Section<>("Literals",
                setting.get().literals,
                Names::get,
                t -> removeValue(setting.get().literals, t),
                t -> new String[]{t.getValue().toString()}
            ),
            new Section<>("Effects",
                setting.get().effects,
                t -> "@" + Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getId(t).toString(),
                t -> removeValue(setting.get().effects, t)
            ),
            new Section<>("Tags",
                setting.get().tags,
                t -> "#" + t.id().toString(),
                t -> removeValue(setting.get().tags, t)
            )
        };
    }

    @Override
    protected boolean manualEntry(String textBox) {
        setting.get().addGeneric(textBox);
        return true;
    }

    private <T> void addValue(Set<T> set, T value) {
        if (set.add(value)) {
            regenerateWidgets();
        }
    }

    private <T> void removeValue(Set<T> set, T value) {
        if (set.remove(value)) {
            regenerateWidgets();
        }
    }
}
