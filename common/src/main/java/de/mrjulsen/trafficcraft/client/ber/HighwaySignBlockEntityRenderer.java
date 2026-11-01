package de.mrjulsen.trafficcraft.client.ber;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import de.mrjulsen.mcdragonlib.util.DLUtils;
import de.mrjulsen.trafficcraft.TrafficCraft;
import de.mrjulsen.trafficcraft.block.HighwaySignBlock;
import de.mrjulsen.trafficcraft.block.entity.HighwaySignBlockEntity;
import de.mrjulsen.trafficcraft.config.ModCommonConfig;
import de.mrjulsen.trafficcraft.data.HighwaySignLayout;
import de.mrjulsen.trafficcraft.data.HighwaySignLayout.LineLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders text and arrows of a highway sign. The panel itself (background and border) is part of the baked
 * block model, so only the dynamic content is drawn here. The layout is cached in the block entity, so this
 * renderer does no text measuring per frame.
 */
public class HighwaySignBlockEntityRenderer implements BlockEntityRenderer<HighwaySignBlockEntity> {

    public static final ResourceLocation ARROWS_TEXTURE = DLUtils.resourceLocation(TrafficCraft.MOD_ID, "textures/block/highway_sign/arrows.png");

    /** Distance of the front face from the block center. */
    private static final float FACE_OFFSET = 0.5f - (float)HighwaySignBlock.PANEL_THICKNESS / 16.0f;
    private static final float TEXT_Z = 0.002f;
    private static final float ARROW_Z = 0.003f;
    private static final int DEFAULT_VIEW_DISTANCE = 96;

    private final Font font;

    public HighwaySignBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(HighwaySignBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof HighwaySignBlock)) {
            return;
        }

        HighwaySignLayout layout = blockEntity.getLayout(font::width, font.lineHeight);
        if (layout.isEmpty()) {
            return;
        }

        Direction facing = state.getValue(HighwaySignBlock.FACING);
        int color = blockEntity.getData().getTextColor();

        poseStack.pushPose();
        // Local space: +X = right side of the sign (seen from the front), +Y = up, +Z = towards the viewer.
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Origin: top left corner of the front face. Layout coordinates point down, so y is negated below.
        poseStack.translate(-0.5f, layout.getHeight() - 0.5f, -FACE_OFFSET);

        for (LineLayout line : layout.getLines()) {
            if (line.hasText()) {
                poseStack.pushPose();
                poseStack.translate(line.textX(), -line.textY(), TEXT_Z);
                poseStack.scale(line.textScale(), -line.textScale(), line.textScale());
                font.drawInBatch(line.text(), 0, 0, color, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
                poseStack.popPose();
            }

            if (line.hasArrow()) {
                renderArrow(poseStack, bufferSource, line, color, packedLight);
            }
        }

        poseStack.popPose();
    }

    private void renderArrow(PoseStack poseStack, MultiBufferSource bufferSource, LineLayout line, int color, int packedLight) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(ARROWS_TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float x0 = line.arrowX();
        float x1 = line.arrowX() + line.arrowSize();
        float yTop = -line.arrowY();
        float yBottom = -(line.arrowY() + line.arrowSize());
        float u0 = line.arrow().getU0();
        float u1 = line.arrow().getU1();
        float v0 = line.arrow().getV0();
        float v1 = line.arrow().getV1();

        vertex(consumer, pose, normal, x0, yTop, u0, v0, r, g, b, packedLight);
        vertex(consumer, pose, normal, x0, yBottom, u0, v1, r, g, b, packedLight);
        vertex(consumer, pose, normal, x1, yBottom, u1, v1, r, g, b, packedLight);
        vertex(consumer, pose, normal, x1, yTop, u1, v0, r, g, b, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v, float r, float g, float b, int light) {
        consumer.vertex(pose, x, y, ARROW_Z)
            .color(r, g, b, 1.0f)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(normal, 0, 0, 1)
            .endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(HighwaySignBlockEntity blockEntity) {
        // Large signs reach into neighbouring chunk sections. Without this the content disappears when the anchor's section is culled.
        return true;
    }

    @Override
    public int getViewDistance() {
        try {
            return ModCommonConfig.HIGHWAY_SIGN_TEXT_RENDER_DISTANCE.get();
        } catch (Exception e) {
            return DEFAULT_VIEW_DISTANCE;
        }
    }
}
