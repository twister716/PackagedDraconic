package thelm.packageddraconic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import thelm.packagedauto.client.RenderTimer;
import thelm.packageddraconic.block.entity.MarkedInjectorBlockEntity;

// Code modified from RenderTileCraftingInjector
public class MarkedInjectorRenderer implements BlockEntityRenderer<MarkedInjectorBlockEntity> {

	public MarkedInjectorRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(MarkedInjectorBlockEntity blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
		if(blockEntity.getCrafter() != null && blockEntity.getCrafter().isWorking && blockEntity.getCrafter().getFusionState().ordinal() > 1) {
			return;
		}
		if(!blockEntity.getItemHandler().getStackInSlot(0).isEmpty()) {
			BlockState state = blockEntity.getLevel().getBlockState(blockEntity.getBlockPos());
			if(state.isAir()) {
				return;
			}
			Direction facing = state.getValue(DirectionalBlock.FACING);
			poseStack.translate(0.5+(facing.getStepX()*0.45), 0.5+(facing.getStepY()*0.45), 0.5+(facing.getStepZ()*0.45));
			poseStack.scale(0.5F, 0.5F, 0.5F);
			if(facing.getAxis() == Direction.Axis.Y) {
				if(facing == Direction.DOWN) {
					poseStack.mulPose(Axis.XP.rotationDegrees(180));
				}
			}
			else {
				poseStack.mulPose(Axis.XP.rotationDegrees(facing.getStepZ()*90));
				poseStack.mulPose(Axis.ZP.rotationDegrees(facing.getStepX()*-90));
			}
			poseStack.mulPose(Axis.YP.rotationDegrees((RenderTimer.INSTANCE.getTicks()+partialTicks)*-0.8F));
			ItemStack stack = blockEntity.getItemHandler().getStackInSlot(0);
			Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, combinedLight, combinedOverlay, poseStack, buffer, blockEntity.getLevel(), (int)blockEntity.getBlockPos().asLong());
		}
	}
}
