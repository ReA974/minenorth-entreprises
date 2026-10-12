package fr.minenorth.entreprises.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.minenorth.entreprises.vending.VendingMachineBE;
import fr.minenorth.entreprises.vending.VendingMachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dessine les articles en stock sur les trois étagères du distributeur (6 colonnes, comme les emplacements du menu). */
public class VendingRenderer implements BlockEntityRenderer<VendingMachineBE> {
    private static final float COL0 = 1.5f, COL_W = 10f / 6f, SCALE = 0.1f, FRONT_Z = 13.5f;
    /** Hauteur (en pixels de modèle) du centre de chaque étagère, de la plus haute à la plus basse. */
    private static final float[] ROW_Y = {27.5f, 21.5f, 15.5f};

    public VendingRenderer(BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(VendingMachineBE be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(VendingMachineBlock.FACING)) return;
        float yaw = be.getBlockState().getValue(VendingMachineBlock.FACING).toYRot();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(-0.5, 0, -0.5);
        for (int i = 0; i < VendingMachineBE.SLOTS; i++) {
            ItemStack st = be.inv.getItem(i);
            if (st.isEmpty()) continue;
            pose.pushPose();
            pose.translate((COL0 + ((i % 6) + 0.5f) * COL_W) / 16f, ROW_Y[i / 6] / 16f, FRONT_Z / 16f);
            pose.scale(SCALE, SCALE, 0.02f);
            Minecraft.getInstance().getItemRenderer().renderStatic(st, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong() + i);
            pose.popPose();
        }
        pose.popPose();
    }
}
