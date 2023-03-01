package thelm.packageddraconic.inventory;

import net.minecraft.item.ItemStack;
import thelm.packagedauto.inventory.BaseItemHandler;
import thelm.packageddraconic.tile.MarkedInjectorTile;

public class MarkedInjectorItemHandler extends BaseItemHandler<MarkedInjectorTile> {

	public MarkedInjectorItemHandler(MarkedInjectorTile tile) {
		super(tile, 1);
	}

	@Override
	protected void onContentsChanged(int slot) {
		syncTile(false);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		return stack;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return ItemStack.EMPTY;
	}
}
