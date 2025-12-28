package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.utils.render.CustomOutlineVertexConsumerProvider;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.world.entity.Entity;

import java.nio.ByteBuffer;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public abstract class PostProcessShader {
    public CustomOutlineVertexConsumerProvider vertexConsumerProvider;
    public RenderTarget framebuffer;
    protected RenderPipeline pipeline;

    public void init(RenderPipeline pipeline) {
        if (vertexConsumerProvider == null) vertexConsumerProvider = new CustomOutlineVertexConsumerProvider();
        if (framebuffer == null)
            framebuffer = new TextureTarget(MeteorClient.NAME + " PostProcessShader", mc.getWindow().getWidth(), mc.getWindow().getHeight(), true);

        this.pipeline = pipeline;
    }

    protected abstract boolean shouldDraw();

    public abstract boolean shouldDraw(Entity entity);

    protected void preDraw() {
    }

    protected void postDraw() {
    }

    protected abstract void setupPass(MeshRenderer renderer);

    public boolean beginRender() {
        return shouldDraw();
    }

    public void endRender(Runnable draw) {
        if (!shouldDraw()) return;

        preDraw();
        draw.run();
        postDraw();

        var renderer = MeshRenderer.begin()
            .attachments(mc.getMainRenderTarget())
            .pipeline(pipeline)
            .fullscreen()
            .uniform("PostData", UNIFORM_STORAGE.writeUniform(new UniformData(
                (float) mc.getWindow().getWidth(), (float) mc.getWindow().getHeight(),
                (float) glfwGetTime()
            )))
            .sampler("u_Texture", framebuffer.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));

        setupPass(renderer);

        renderer.end();
    }

    public void onResized(int width, int height) {
        if (framebuffer == null) return;
        framebuffer.resize(width, height);
    }

    // Uniforms

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get();

    private static final DynamicUniformStorage<UniformData> UNIFORM_STORAGE = new DynamicUniformStorage<>("Meteor - Post UBO", UNIFORM_SIZE, 16);

    public static void flipFrame() {
        UNIFORM_STORAGE.endFrame();
    }

    private record UniformData(float sizeX, float sizeY, float time) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(sizeX, sizeY)
                .putFloat(time);
        }
    }
}
