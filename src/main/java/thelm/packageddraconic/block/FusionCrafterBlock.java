package thelm.packageddraconic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thelm.packagedauto.block.BaseBlock;
import thelm.packagedauto.block.entity.BaseBlockEntity;
import thelm.packageddraconic.block.entity.FusionCrafterBlockEntity;

public class FusionCrafterBlock extends BaseBlock {

	public static final FusionCrafterBlock INSTANCE = new FusionCrafterBlock();
	public static final Item ITEM_INSTANCE = new BlockItem(INSTANCE, new Item.Properties());
	public static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

	public FusionCrafterBlock() {
		super(BlockBehaviour.Properties.of().strength(15F, 25F).noOcclusion().mapColor(MapColor.METAL).sound(SoundType.METAL));
	}

	@Override
	public FusionCrafterBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return FusionCrafterBlockEntity.TYPE_INSTANCE.create(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return BaseBlockEntity::tick;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if(player.isShiftKeyDown()) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if(blockEntity instanceof FusionCrafterBlockEntity crafter && !crafter.isWorking) {
				if(!level.isClientSide) {
					Component message = crafter.getMessage();
					if(message != null) {
						player.sendSystemMessage(message);
					}
				}
				return InteractionResult.SUCCESS;
			}
		}
		return super.use(state, level, pos, player, hand, hitResult);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if(state.getBlock() == newState.getBlock()) {
			return;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if(blockEntity instanceof FusionCrafterBlockEntity crafter && crafter.isWorking) {
			crafter.cancelCraft();
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
