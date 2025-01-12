package thelm.packageddraconic.recipe;

import java.util.Collections;
import java.util.List;

import com.brandon3055.draconicevolution.api.fusioncrafting.IFusionRecipe;

import net.minecraft.item.ItemStack;
import thelm.packagedauto.api.IRecipeInfo;

public interface IRecipeInfoFusion extends IRecipeInfo {

	ItemStack getCoreInput();

	List<ItemStack> getInjectorInputs();

	ItemStack getOutput();

	int getTierRequired();

	long getEnergyRequired();

	IFusionRecipe getRecipe();

	@Override
	default List<ItemStack> getOutputs() {
		ItemStack output = getOutput();
		return output.isEmpty() ? Collections.emptyList() : Collections.singletonList(output);
	}
}
