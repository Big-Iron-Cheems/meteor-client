/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.Utils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class DynamicRegistryListSettingScreen<S> extends WindowScreen {
    protected final Setting<S> setting;
    private final boolean manualEntry;

    private WTextBox filter;
    private String filterText = "";

    private WTable table;

    public DynamicRegistryListSettingScreen(GuiTheme theme, String title, Setting<S> setting, boolean manualEntry) {
        super(theme, title);
        this.setting = setting;
        this.manualEntry = manualEntry;
    }

    @Override
    public void initWidgets() {
        // Filter
        filter = add(theme.textBox("")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            generateWidgets();
        };

        table = add(theme.table()).expandX().widget();

        generateWidgets();
    }

    private void generateWidgets() {
        // Left (all)
        WTable left = abc(true, getLeftSections());

        if (manualEntry) {
            if (!left.cells.isEmpty()) {
                left.add(theme.horizontalSeparator("Manual Entry")).expandX();
                left.row();
            }

            WHorizontalList wManualEntry = left.add(theme.horizontalList()).expandX().widget();
            WTextBox textBox = wManualEntry.add(theme.textBox("")).expandX().minWidth(120).widget();
            wManualEntry.add(theme.plus()).expandCellX().right().widget().action = () -> {
                if (manualEntry(textBox.get())) {
                    regenerateWidgets();
                }
            };
        }

        table.add(theme.verticalSeparator()).expandWidgetY();

        // Right (selected)
        abc(false, getRightSections());
    }

    protected void regenerateWidgets() {
        setting.onChanged();
        table.clear();
        generateWidgets();
    }

    private WTable abc(boolean isLeft, Section<?>... sections) {
        // Create
        Cell<WTable> cell = this.table.add(theme.table()).top();
        WTable table = cell.widget();

        for (Section<?> section : sections) {
            section(table, section, isLeft);
        }

        if (!table.cells.isEmpty()) cell.expandX();

        return table;
    }

    private <T> void section(WTable table, Section<T> section, boolean isLeft) {
        if (!section.collection.isEmpty()) {
            WSection wSection = table.add(theme.section(section.theName)).expandX().widget();
            List<ObjectIntPair<WHorizontalList>> entries = new ObjectArrayList<>();

            for (T object : section.collection) {
                WHorizontalList horizontalList = theme.horizontalList();

                String name = section.theNamer.apply(object);

                horizontalList.add(theme.label(name));
                WPressable button = horizontalList.add(isLeft ? theme.plus() : theme.minus()).expandCellX().right().widget();

                button.action = () -> section.theOnClicker.accept(object);

                if (!filterText.isBlank()) {
                    boolean inWord = Utils.searchInWords(name, filterText) > 0;
                    int diff = Utils.searchLevenshteinDefault(name, filterText, false);
                    for (String alias : section.theAliaser.apply(object)) {
                        diff = Math.min(diff, Utils.searchLevenshteinDefault(alias, filterText, false));
                        inWord |= Utils.searchInWords(alias, filterText) > 0;
                    }
                    if (inWord || diff <= name.length() / 2) entries.add(new ObjectIntImmutablePair<>(horizontalList, -diff));
                } else entries.add(new ObjectIntImmutablePair<>(horizontalList, 0));
            }

            if (!filterText.isBlank()) entries.sort(Comparator.comparingInt(value -> -value.rightInt()));
            for (ObjectIntPair<WHorizontalList> entry : entries) wSection.add(entry.left()).expandX();

            table.row();
        }
    }

    protected abstract Section<?>[] getLeftSections();
    protected abstract Section<?>[] getRightSections();
    protected abstract boolean manualEntry(String textBox);

    protected record Section<T>(String theName, Collection<T> collection, Function<T, String> theNamer, Consumer<T> theOnClicker, Function<T, String[]> theAliaser) {
        public Section(String theName, Collection<T> collection, Function<T, String> theNamer, Consumer<T> theOnClicker) {
            this(theName, collection, theNamer, theOnClicker, t -> new String[0]);
        }
    }
}
