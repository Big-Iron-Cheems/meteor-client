/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

/**
 * An extension of {@link FormattedText.StyledContentConsumer} with access to the underlying {@link Component} objects.
 *
 * @param <T> the optional short circuit return type, to match the semantics of {@link FormattedText.ContentConsumer} and {@link FormattedText.StyledContentConsumer}.
 * @author Crosby
 */
@FunctionalInterface
public interface TextVisitor<T> {
    Optional<T> accept(Component text, Style style, String string);

    static <T> Optional<T> visit(Component text, TextVisitor<T> visitor, Style baseStyle) {
        Queue<Component> queue = collectSiblings(text);
        return text.visit((style, string) -> visitor.accept(queue.remove(), style, string), baseStyle);
    }

    /**
     * Collapses the tree of {@link Component} siblings into a one dimensional FIFO {@link Queue}. To match the behaviours of
     * the {@link Component#visit(FormattedText.ContentConsumer)} and {@link Component#visit(FormattedText.StyledContentConsumer, Style)}
     * methods, texts with empty contents (created from {@link Component#empty()}) are ignored but their siblings are still
     * processed.
     *
     * @param text the text
     * @return the text and its siblings in the order they appear when rendered.
     */
    static ArrayDeque<Component> collectSiblings(Component text) {
        ArrayDeque<Component> queue = new ArrayDeque<>();
        collectSiblings(text, queue);
        return queue;
    }

    private static void collectSiblings(Component text, Queue<Component> queue) {
        if (!(text.getContents() instanceof PlainTextContents ptc) || !ptc.text().isEmpty()) queue.add(text);
        for (Component sibling : text.getSiblings()) {
            collectSiblings(sibling, queue);
        }
    }
}
