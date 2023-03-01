package thelm.packageddraconic.slot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import thelm.packageddraconic.tile.FusionCrafterTile;

//Code from CoFHCore
public class FusionCrafterRemoveOnlySlot extends SlotItemHandler {

	public final FusionCrafterTile tile;

	public FusionCrafterRemoveOnlySlot(FusionCrafterTile tile, int index, int x, int y) {
		super(tile.getItemHandler(), index, x, y);
		this.tile = tile;
	}

	@Override
	public boolean mayPickup(PlayerEntity playerIn) {
		return !tile.isWorking;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return false;
	}
}
