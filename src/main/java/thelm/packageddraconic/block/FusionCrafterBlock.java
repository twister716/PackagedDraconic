package thelm.packageddraconic.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import thelm.packagedauto.block.BaseBlock;
import thelm.packageddraconic.PackagedDraconic;
import thelm.packageddraconic.tile.FusionCrafterTile;

public class FusionCrafterBlock extends BaseBlock {

	public static final FusionCrafterBlock INSTANCE = new FusionCrafterBlock();
	public static final Item ITEM_INSTANCE = new BlockItem(INSTANCE, new Item.Properties().tab(PackagedDraconic.ITEM_GROUP)).setRegistryName("packageddraconic:fusion_crafter");
	public static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

	public FusionCrafterBlock() {
		super(AbstractBlock.Properties.of(Material.METAL).strength(15F, 25F).noOcclusion().sound(SoundType.METAL));
		setRegistryName("packageddraconic:fusion_crafter");
	}

	@Override
	public FusionCrafterTile createTileEntity(BlockState state, IBlockReader worldIn) {
		return FusionCrafterTile.TYPE_INSTANCE.create();
	}

	@Override
	public ActionResultType use(BlockState state, World worldIn, BlockPos pos, PlayerEntity playerIn, Hand hand, BlockRayTraceResult rayTraceResult) {
		if(playerIn.isShiftKeyDown()) {
			TileEntity tileentity = worldIn.getBlockEntity(pos);
			if(tileentity instanceof FusionCrafterTile) {
				FusionCrafterTile crafter = (FusionCrafterTile)tileentity;
				if(!crafter.isWorking) {
					if(!worldIn.isClientSide) {
						ITextComponent message = crafter.getMessage();
						if(message != null) {
							playerIn.sendMessage(message, Util.NIL_UUID);
						}
					}
					return ActionResultType.SUCCESS;
				}
			}
		}
		return super.use(state, worldIn, pos, playerIn, hand, rayTraceResult);
	}

	@Override
	public void onRemove(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
		if(state.getBlock() == newState.getBlock()) {
			return;
		}
		TileEntity tileentity = worldIn.getBlockEntity(pos);
		if(tileentity instanceof FusionCrafterTile) {
			FusionCrafterTile crafter = (FusionCrafterTile)tileentity;
			if(crafter.isWorking) {
				crafter.cancelCraft();
			}
		}
		super.onRemove(state, worldIn, pos, newState, isMoving);
	}

	@Override
	public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
		return SHAPE;
	}
}
