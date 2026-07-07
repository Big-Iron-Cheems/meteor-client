/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer.text;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Vanilla-font text renderer ported to the 26.2 deferred text pipeline.
 *
 * 26.2 removed {@code MultiBufferSource}/immediate-mode {@code Font.drawInBatch}. Text is now produced by
 * {@code Font.prepareText(...) -> PreparedText.visit(GlyphVisitor)}, where each {@link TextRenderable} renders
 * itself into a {@link VertexConsumer} and carries its own atlas texture ({@link TextRenderable#textureView()}).
 *
 * We collect glyph quads (position + uv + color) into one {@link MeshBuilder} per atlas texture, then draw each
 * with Meteor's migrated {@link MeshRenderer} using the UI_TEXT pipeline (same path {@link CustomTextRenderer} uses).
 *
 * ponytail: geometry/positions are exact; glyph texturing follows vanilla's atlas uvs. Cannot be runtime-verified
 * on a headless host - if vanilla-font rendering looks off, this class is the place to check first. CustomTextRenderer
 * (the default font) is unaffected.
 */
public class VanillaTextRenderer implements TextRenderer {
    public static final VanillaTextRenderer INSTANCE = new VanillaTextRenderer();

    // One mesh per atlas texture, reused across draws to avoid re-allocating native buffers.
    private final Map<GpuTextureView, MeshBuilder> meshes = new Object2ObjectOpenHashMap<>();
    private final Map<GpuTextureView, Boolean> touched = new Object2ObjectOpenHashMap<>();

    private final GlyphCollector collector = new GlyphCollector();
    private final Matrix4f identity = new Matrix4f();

    private GpuSampler sampler;

    public double scale = 2;
    public boolean scaleIndividually;

    private boolean building;
    private double alpha = 1;

    private VanillaTextRenderer() {
        // Use INSTANCE
    }

    private GpuSampler sampler() {
        // Vanilla font atlases: clamp + nearest.
        if (sampler == null) {
            sampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, false);
        }
        return sampler;
    }

    @Override
    public void setAlpha(double a) {
        alpha = a;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text.isEmpty()) return 0;

        if (length != text.length()) text = text.substring(0, length);
        return (mc.font.width(text) + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public double getHeight(boolean shadow) {
        return (mc.font.lineHeight + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new RuntimeException("VanillaTextRenderer.begin() called twice");

        this.scale = scale * 2;
        this.building = true;
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();

        x += 0.5 * scale;
        y += 0.5 * scale;

        int preA = color.a;
        color.a = (int) (((double) color.a / 255 * alpha) * 255);
        int packed = color.getPacked();
        color.a = preA;

        // Glyph coords are produced unscaled here; the scale is applied to the modelview in end().
        Font.PreparedText prepared = mc.font.prepareText(text, (float) (x / scale), (float) (y / scale), packed, shadow, LightCoordsUtil.FULL_BRIGHT);
        prepared.visit(collector);

        double x2 = (x / scale) + mc.font.width(text);
        if (!wasBuilding) end();
        return (x2 - 1) * scale;
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new RuntimeException("VanillaTextRenderer.end() called without calling begin()");

        collector.flush();

        Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.pushMatrix();
        if (!scaleIndividually) matrixStack.scale((float) scale, (float) scale, 1);

        for (GpuTextureView texture : touched.keySet()) {
            MeshBuilder mesh = meshes.get(texture);
            if (mesh == null || !mesh.isBuilding()) continue;
            mesh.end();

            if (mesh.getIndicesCount() > 0) {
                MeshRenderer.begin()
                    .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                    .pipeline(MeteorRenderPipelines.UI_TEXT)
                    .mesh(mesh)
                    .sampler("u_Texture", texture, sampler())
                    .end();
            }
        }

        matrixStack.popMatrix();
        touched.clear();

        this.scale = 2;
        this.building = false;
    }

    /** Lazily starts (and returns) the mesh for a given atlas texture, marking it drawn this batch. */
    private MeshBuilder meshFor(GpuTextureView texture) {
        MeshBuilder mesh = meshes.computeIfAbsent(texture, t -> new MeshBuilder(MeteorRenderPipelines.UI_TEXT));
        if (!mesh.isBuilding()) mesh.begin();
        touched.put(texture, Boolean.TRUE);
        return mesh;
    }

    /**
     * Visits prepared glyphs/effects and renders each into the mesh belonging to its atlas texture via a
     * position+uv+color {@link VertexConsumer}.
     */
    private final class GlyphCollector implements Font.GlyphVisitor {
        private final QuadConsumer consumer = new QuadConsumer();

        private void render(TextRenderable renderable) {
            GpuTextureView texture = renderable.textureView();
            if (texture == null) return; // effects without a texture (e.g. shadow-only markers) - skip
            consumer.mesh = meshFor(texture);
            renderable.render(identity, consumer, LightCoordsUtil.FULL_BRIGHT, false);
            consumer.flush();
        }

        @Override
        public void acceptGlyph(TextRenderable.Styled glyph) {
            render(glyph);
        }

        @Override
        public void acceptRenderable(TextRenderable renderable) {
            render(renderable);
        }

        @Override
        public void acceptEffect(TextRenderable effect) {
            render(effect);
        }

        @Override
        public void acceptEmptyArea(EmptyArea area) {
            // Nothing to draw.
        }

        void flush() {
            consumer.flush();
        }
    }

    /**
     * Buffers one vertex at a time (pos, uv, color) and emits a {@link MeshBuilder} quad every 4 vertices, matching
     * the UI_TEXT vertex layout (vec3 position, vec2 uv, 4-byte color).
     */
    private static final class QuadConsumer implements VertexConsumer {
        private MeshBuilder mesh;

        private final Color color = new Color();
        private float x, y, z, u, v;
        private boolean pending;
        private final int[] quad = new int[4];
        private int count;

        private void flush() {
            if (!pending || mesh == null) return;
            pending = false;

            mesh.ensureQuadCapacity();
            quad[count++] = mesh.vec3(x, y, z).vec2(u, v).color(color).next();

            if (count == 4) {
                mesh.quad(quad[0], quad[1], quad[2], quad[3]);
                count = 0;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flush(); // finalize the previous vertex before starting a new one
            this.x = x;
            this.y = y;
            this.z = z;
            pending = true;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            color.set(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            color.set(
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                (argb >> 24) & 0xFF);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
