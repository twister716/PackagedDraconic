package thelm.packageddraconic.tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.brandon3055.brandonscore.client.particle.BCEffectHandler;
import com.brandon3055.brandonscore.lib.Vec3D;
import com.brandon3055.draconicevolution.api.fusioncrafting.ICraftingInjector;
import com.brandon3055.draconicevolution.api.fusioncrafting.IFusionCraftingInventory;
import com.brandon3055.draconicevolution.api.fusioncrafting.IFusionRecipe;
import com.brandon3055.draconicevolution.client.DEParticles;
import com.brandon3055.draconicevolution.client.render.effect.EffectTrackerFusionCrafting;
import com.brandon3055.draconicevolution.lib.DESoundHandler;
import com.brandon3055.draconicevolution.lib.RecipeManager;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import thelm.packagedauto.api.IPackageCraftingMachine;
import thelm.packagedauto.api.IRecipeInfo;
import thelm.packagedauto.api.MiscUtil;
import thelm.packagedauto.energy.EnergyStorage;
import thelm.packagedauto.tile.TileBase;
import thelm.packagedauto.tile.TilePackager;
import thelm.packagedauto.tile.TilePackagerExtension;
import thelm.packagedauto.tile.TileUnpackager;
import thelm.packageddraconic.client.gui.GuiFusionCrafter;
import thelm.packageddraconic.client.sound.FusionCrafterRotationSound;
import thelm.packageddraconic.container.ContainerFusionCrafter;
import thelm.packageddraconic.integration.appeng.networking.HostHelperTileFusionCrafter;
import thelm.packageddraconic.inventory.InventoryFusionCrafter;
import thelm.packageddraconic.packet.PacketSyncCrafter;
import thelm.packageddraconic.recipe.IRecipeInfoFusion;

@Optional.InterfaceList({
	@Optional.Interface(iface="appeng.api.networking.IGridHost", modid="appliedenergistics2"),
	@Optional.Interface(iface="appeng.api.networking.security.IActionHost", modid="appliedenergistics2"),
})
public class TileFusionCrafter extends TileBase implements ITickable, IPackageCraftingMachine, IFusionCraftingInventory, IGridHost, IActionHost {

	public static int energyCapacity = 5000;
	public static int energyUsage = 5;
	public static boolean drawMEEnergy = true;

	public IFusionRecipe effectRecipe;
	@SideOnly(Side.CLIENT)
	public LinkedList<EffectTrackerFusionCrafting> effects;
	public double effectRotation;
	public boolean allLocked;
	public int[] requiredInjectors = {0, 0, 0, 0};
	public boolean isWorking = false;
	public short progress = 0;
	public int craftRate = 0;
	public IRecipeInfoFusion currentRecipe;
	public List<BlockPos> injectors = new ArrayList<>();

	public TileFusionCrafter() {
		setInventory(new InventoryFusionCrafter(this));
		setEnergyStorage(new EnergyStorage(this, energyCapacity));
		if(Loader.isModLoaded("appliedenergistics2")) {
			hostHelper = new HostHelperTileFusionCrafter(this);
		}
	}

	@Override
	protected String getLocalizedName() {
		return I18n.translateToLocal("tile.packageddraconic.fusion_crafter.name");
	}

	public ITextComponent getMessage() {
		if(isWorking) {
			return null;
		}
		ITextComponent message = new TextComponentTranslation("tile.packageddraconic.fusion_crafter.injectors.usable");
		ITextComponent usable = new TextComponentString(" ");
		for(int i = 0; i <= 3; ++i) {
			int usableInjectors = getEmptyInjectorsForTier(i).size();
			if(usableInjectors > 0) {
				if(!usable.getSiblings().isEmpty()) {
					usable.appendText(", ");
				}
				usable.appendSibling(new TextComponentTranslation("tile.packageddraconic.fusion_crafter.injectors."+i, usableInjectors));
			}
		}
		if(usable.getSiblings().isEmpty()) {
			message.appendText(" 0");
		}
		else {
			message.appendText("\n");
			message.appendSibling(usable);
		}
		if(Arrays.stream(requiredInjectors).anyMatch(i->i > 0)) {
			message.appendText("\n");
			message.appendSibling(new TextComponentTranslation("tile.packageddraconic.fusion_crafter.injectors.required"));
			int[] actualRequiredInjectors = {
					requiredInjectors[0]-requiredInjectors[1]-requiredInjectors[2]-requiredInjectors[3],
					requiredInjectors[1]-requiredInjectors[2]-requiredInjectors[3],
					requiredInjectors[2]-requiredInjectors[3],
					requiredInjectors[3]
			};
			ITextComponent required = new TextComponentString(" ");
			for(int i = 0; i <= 3; ++i) {
				int requiredInjectors = actualRequiredInjectors[i];
				if(requiredInjectors > 0) {
					if(!required.getSiblings().isEmpty()) {
						required.appendText(", ");
					}
					required.appendSibling(new TextComponentTranslation("tile.packageddraconic.fusion_crafter.injectors."+i, requiredInjectors));
				}
			}
			message.appendText("\n");
			message.appendSibling(required);
		}
		return message;
	}

	@Override
	public void update() {
		if(!world.isRemote) {
			if(isWorking) {
				tickProcess();
				if(progress >= 2000) {
					finishProcess();
					if(hostHelper != null && hostHelper.isActive()) {
						hostHelper.ejectItem();
					}
					else {
						ejectItems();
					}
				}
			}
			chargeEnergy();
			if(world.getTotalWorldTime() % 8 == 0) {
				if(hostHelper != null && hostHelper.isActive()) {
					hostHelper.ejectItem();
					if(drawMEEnergy) {
						hostHelper.chargeEnergy();
					}
				}
				else {
					ejectItems();
				}
			}
		}
		else {
			clientTick();
		}
	}

	@SideOnly(Side.CLIENT)
	protected void clientTick() {
		if(effects == null) {
			if(isWorking) {
				initializeEffects();
				effectRotation = 0;
				allLocked = false;
			}
			return;
		}
		double distFromCore = 1.2;
		if(progress > 1600) {
			distFromCore *= 1-(progress-1600)/400D;
		}
		if(allLocked) {
			effectRotation -= Math.min(((progress-1100)/900D)*0.8, 0.5);
			if(effectRotation > 0) {
				effectRotation = 0;
			}
		}
		int index = 0;
		int count = effects.size();
		boolean flag = true;
		boolean isMoving = progress > 1000;
		for(EffectTrackerFusionCrafting effect : effects) {
			effect.onUpdate(isMoving);
			if(!effect.positionLocked) {
				flag = false;
			}
			if(isMoving) {
				effect.scale = 0.7F+((float)(distFromCore/1.2)*0.3F);
				effect.green = effect.blue = (float)(distFromCore-0.2);
				effect.red = 1-(float)(distFromCore-0.2);
			}
			double indexPos = (double)index/(double)count;
			double offset = indexPos*Math.PI*2;
			double offsetX = Math.sin(effectRotation+offset)*distFromCore;
			double offsetZ = Math.cos(effectRotation+offset)*distFromCore;
			double mix = effectRotation/5;
			double xAdditive = offsetX * Math.sin(-mix);
			double zAdditive = offsetZ * Math.cos(-mix);
			double offsetY = (xAdditive+zAdditive)*0.2*distFromCore/1.2;
			effect.circlePosition.set(pos.getX()+0.5+offsetX, pos.getY()+0.5+offsetY, pos.getZ()+0.5+ offsetZ);
			index++;
		}
		SoundHandler soundManager = FMLClientHandler.instance().getClient().getSoundHandler();
		if(!allLocked && flag) {
			soundManager.playSound(new FusionCrafterRotationSound(this));
		}
		allLocked = flag;
		if(!isWorking) {
			for(int i = 0; i < 100; i++) {
				BCEffectHandler.spawnFXDirect(DEParticles.DE_SHEET, new EffectTrackerFusionCrafting.SubParticle(world, new Vec3D(pos).add(0.5, 0.5, 0.5)));
			}
			world.playSound(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, DESoundHandler.fusionComplete, SoundCategory.BLOCKS, 2F, 1F, false);
			effects = null;
		}
	}

	@SideOnly(Side.CLIENT)
	public void initializeEffects() {
		injectors.clear();
		int range = 16;
		Streams.stream(Iterables.concat(
				BlockPos.getAllInBoxMutable(pos.add(-range, -1, -1), pos.add(range, 1, 1)),
				BlockPos.getAllInBoxMutable(pos.add(-1, -range, -1), pos.add(1, range, 1)),
				BlockPos.getAllInBoxMutable(pos.add(-1, -1, -range), pos.add(1, 1, range)))).
		forEach(checkPos->{
			TileEntity tile = world.getTileEntity(checkPos);
			if(tile instanceof TileMarkedInjector) {
				TileMarkedInjector injector = (TileMarkedInjector)tile;
				Vec3i dirVec = checkPos.subtract(pos);
				int dist = Ints.max(Math.abs(dirVec.getX()), Math.abs(dirVec.getY()), Math.abs(dirVec.getZ()));
				if(dist >= 2 && EnumFacing.getFacingFromVector(dirVec.getX(), dirVec.getY(), dirVec.getZ()) == injector.getDirection().getOpposite() &&
						injector.setCraftingInventory(this)) {
					EnumFacing facing = injector.getDirection();
					for(BlockPos bp : BlockPos.getAllInBox(
							checkPos.offset(facing),
							checkPos.offset(facing, distanceInDirection(checkPos, pos, facing)-1))) {
						if(!world.isAirBlock(bp) && (world.getBlockState(bp).isFullCube() || world.getTileEntity(bp) instanceof TileMarkedInjector)) {
							injector.setCraftingInventory(null);
							return;
						}
					}
					injectors.add(checkPos.toImmutable());
				}
			}
		});
		effectRecipe = RecipeManager.FUSION_REGISTRY.findRecipe(this, world, pos);
		if(effectRecipe == null) {
			effects = null;
			return;
		}
		effects = new LinkedList<>();
		for(ICraftingInjector injector : getInjectors()) {
			if(injector.getStackInPedestal().isEmpty()) {
				continue;
			}
			injector.setCraftingInventory(this);
			Vec3D spawn = new Vec3D(((TileEntity)injector).getPos());
			spawn.add(0.5+injector.getDirection().getXOffset()*0.45, 0.5+injector.getDirection().getYOffset()*0.45, 0.5+injector.getDirection().getZOffset()*0.45);
			effects.add(new EffectTrackerFusionCrafting(world, spawn, new Vec3D(pos), this, effectRecipe.getRecipeIngredients().size()));
		}
	}

	@Override
	public boolean acceptPackage(IRecipeInfo recipeInfo, List<ItemStack> stacks, EnumFacing facing) {
		if(!isBusy() && recipeInfo.isValid() && recipeInfo instanceof IRecipeInfoFusion) {
			IRecipeInfoFusion recipe = (IRecipeInfoFusion)recipeInfo;
			int tier = recipe.getTierRequired();
			List<ItemStack> injectorInputs = recipe.getInjectorInputs();
			List<BlockPos> emptyInjectors = getEmptyInjectors(tier);
			requiredInjectors[tier] = Math.max(requiredInjectors[tier], injectorInputs.size());
			if(emptyInjectors.size() >= injectorInputs.size()) {
				injectors.clear();
				injectors.addAll(emptyInjectors.subList(0, injectorInputs.size()));
				currentRecipe = recipe;
				isWorking = true;
				inventory.setInventorySlotContents(0, recipe.getCoreInput().copy());
				List<ICraftingInjector> craftInjectors = getInjectors();
				for(int i = 0; i < craftInjectors.size(); ++i) {
					ICraftingInjector injector = craftInjectors.get(i);
					injector.setStackInPedestal(injectorInputs.get(i).copy());
					injector.setCraftingInventory(this);
				}
				if(!recipe.getRecipe().matches(this, world, pos) || !"true".equals(recipe.getRecipe().canCraft(this, world, pos))) {
					injectors.clear();
					currentRecipe = null;
					isWorking = false;
					inventory.setInventorySlotContents(0, ItemStack.EMPTY);
					for(int i = 0; i < craftInjectors.size(); ++i) {
						ICraftingInjector injector = craftInjectors.get(i);
						injector.setStackInPedestal(ItemStack.EMPTY);
						injector.setCraftingInventory(null);
					}
					return false;
				}
				markDirty();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isBusy() {
		return isWorking || !inventory.stacks.subList(0, 2).stream().allMatch(ItemStack::isEmpty);
	}

	protected void tickProcess() {
		List<ICraftingInjector> craftInjectors = getInjectors();
		if(craftInjectors.size() != injectors.size()) {
			endProcess();
		}
		else {
			PacketSyncCrafter.sync(this);
			long totalCharge = getInjectors().stream().mapToLong(c->c.getInjectorCharge()).sum();
			long averageCharge = totalCharge / injectors.size();
			double percentage = averageCharge / (double)currentRecipe.getEnergyRequired();
			if(percentage <= 1 && progress < 1000) {
				progress = (short)(percentage*1000);
				if(progress == 0 && percentage > 0) {
					progress = 1;
				}
			}
			else if(progress < 2000 && energyStorage.extractEnergy(energyUsage, true) == energyUsage) {
				energyStorage.extractEnergy(energyUsage, false);
				if(craftRate <= 0) {
					craftRate = craftInjectors.stream().mapToInt(c->{
						if(c instanceof TileMarkedInjector) {
							return ((TileMarkedInjector)c).getCraftRate();
						}
						return 2 + Math.max(c.getPedestalTier()*2-1, 0);
					}).min().orElse(2);
				}
				progress += craftRate;
			}
		}
	}

	protected void finishProcess() {
		if(currentRecipe == null) {
			endProcess();
			return;
		}
		if(injectors.stream().map(world::getTileEntity).anyMatch(tile->!(tile instanceof TileMarkedInjector) || tile.isInvalid())) {
			endProcess();
			return;
		}
		currentRecipe.getRecipe().craft(this, world, pos);
		for(ICraftingInjector injector : getInjectors()) {
			injector.onCraft();
		}
		endProcess();
	}

	public void endProcess() {
		progress = 0;
		getInjectors().stream().
		forEach(tile->((TileMarkedInjector)tile).ejectItem());
		injectors.clear();
		isWorking = false;
		craftRate = 0;
		currentRecipe = null;
		markDirty();
	}

	protected List<BlockPos> getEmptyInjectors(int minTier) {
		List<BlockPos> positions = new ArrayList<>();
		for(int i = 3; i >= minTier; --i) {
			positions.addAll(getEmptyInjectorsForTier(i));
		}
		return positions;
	}

	protected List<BlockPos> getEmptyInjectorsForTier(int tier) {
		int range = 16;
		return Streams.stream(Iterables.concat(
				BlockPos.getAllInBoxMutable(pos.add(-range, -1, -1), pos.add(range, 1, 1)),
				BlockPos.getAllInBoxMutable(pos.add(-1, -range, -1), pos.add(1, range, 1)),
				BlockPos.getAllInBoxMutable(pos.add(-1, -1, -range), pos.add(1, 1, range)))).
				map(checkPos->{
					TileEntity tile = world.getTileEntity(checkPos);
					if(tile instanceof TileMarkedInjector) {
						TileMarkedInjector injector = (TileMarkedInjector)tile;
						Vec3i dirVec = checkPos.subtract(pos);
						int dist = Ints.max(Math.abs(dirVec.getX()), Math.abs(dirVec.getY()), Math.abs(dirVec.getZ()));
						if(dist >= 2 && injector.getPedestalTier() == tier && injector.getStackInPedestal().isEmpty() &&
								EnumFacing.getFacingFromVector(dirVec.getX(), dirVec.getY(), dirVec.getZ()) == injector.getDirection().getOpposite()) {
							EnumFacing facing = injector.getDirection();
							for(BlockPos bp : BlockPos.getAllInBox(
									checkPos.offset(facing),
									checkPos.offset(facing, distanceInDirection(checkPos, pos, facing)-1))) {
								IBlockState state = world.getBlockState(bp);
								TileEntity btile = world.getTileEntity(bp);
								if(!state.getBlock().isAir(state, world, bp) &&
										!(btile instanceof TilePackager) &&
										!(btile instanceof TilePackagerExtension) &&
										!(btile instanceof TileUnpackager) &&
										world.getBlockState(bp).isFullCube() ||
										btile instanceof TileMarkedInjector ||
										btile instanceof TileFusionCrafter) {
									return null;
								}
							}
							return checkPos.toImmutable();
						}
					}
					return null;
				}).filter(Objects::nonNull).collect(Collectors.toList());
	}

	@Override
	public List<ICraftingInjector> getInjectors() {
		return injectors.stream().map(world::getTileEntity).
				filter(tile->tile instanceof TileMarkedInjector && !tile.isInvalid()).
				map(tile->(ICraftingInjector)tile).collect(Collectors.toList());
	}

	public static int distanceInDirection(BlockPos fromPos, BlockPos toPos, EnumFacing direction) {
		switch(direction) {
		case DOWN: return fromPos.getY() - toPos.getY();
		case UP: return toPos.getY() - fromPos.getY();
		case NORTH: return fromPos.getZ() - toPos.getZ();
		case SOUTH: return toPos.getZ() - fromPos.getZ();
		case WEST: return fromPos.getX() - toPos.getX();
		case EAST: return toPos.getX() - fromPos.getX();
		}
		return 0;
	}

	protected void ejectItems() {
		int endIndex = isWorking ? 1 : 0;
		for(EnumFacing facing : EnumFacing.VALUES) {
			TileEntity tile = world.getTileEntity(pos.offset(facing));
			if(tile != null && !(tile instanceof TileUnpackager) && tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
				IItemHandler itemHandler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
				for(int i = 1; i >= endIndex; --i) {
					ItemStack stack = inventory.getStackInSlot(i);
					if(stack.isEmpty()) {
						continue;
					}
					ItemStack stackRem = ItemHandlerHelper.insertItem(itemHandler, stack, false);
					inventory.setInventorySlotContents(i, stackRem);
				}
			}
		}
	}

	protected void chargeEnergy() {
		ItemStack energyStack = inventory.getStackInSlot(2);
		if(energyStack.hasCapability(CapabilityEnergy.ENERGY, null)) {
			int energyRequest = Math.min(energyStorage.getMaxReceive(), energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());
			energyStorage.receiveEnergy(energyStack.getCapability(CapabilityEnergy.ENERGY, null).extractEnergy(energyRequest, false), false);
			if(energyStack.getCount() <= 0) {
				inventory.setInventorySlotContents(2, ItemStack.EMPTY);
			}
		}
	}

	@Override
	public ItemStack getStackInCore(int slot) {
		return inventory.getStackInSlot(slot);
	}

	@Override
	public void setStackInCore(int slot, ItemStack stack) {
		inventory.setInventorySlotContents(slot, stack);
	}

	@Override
	public long getIngredientEnergyCost() {
		return currentRecipe == null ? 0 : currentRecipe.getEnergyRequired();
	}

	@Override
	public boolean craftingInProgress() {
		return isWorking;
	}

	@Override
	public int getCraftingStage() {
		return progress;
	}

	@Override
	public int getComparatorSignal() {
		if(isWorking) {
			return 1;
		}
		if(!inventory.stacks.subList(0, 2).stream().allMatch(ItemStack::isEmpty)) {
			return 15;
		}
		return 0;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return new AxisAlignedBB(pos.add(-16, -16, -16), pos.add(17, 17, 17));
	}

	@Override
	public boolean shouldRenderInPass(int pass) {
		return true;
	}

	public HostHelperTileFusionCrafter hostHelper;

	@Override
	public void invalidate() {
		super.invalidate();
		if(hostHelper != null) {
			hostHelper.invalidate();
		}
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if(hostHelper != null) {
			hostHelper.invalidate();
		}
	}

	@Optional.Method(modid="appliedenergistics2")
	@Override
	public IGridNode getGridNode(AEPartLocation dir) {
		return getActionableNode();
	}

	@Optional.Method(modid="appliedenergistics2")
	@Override
	public AECableType getCableConnectionType(AEPartLocation dir) {
		return AECableType.SMART;
	}

	@Optional.Method(modid="appliedenergistics2")
	@Override
	public void securityBreak() {
		world.destroyBlock(pos, true);
	}

	@Optional.Method(modid="appliedenergistics2")
	@Override
	public IGridNode getActionableNode() {
		return hostHelper.getNode();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		if(hostHelper != null) {
			hostHelper.readFromNBT(nbt);
		}
		super.readFromNBT(nbt);
		progress = nbt.getShort("Progress");
		currentRecipe = null;
		if(nbt.hasKey("Recipe")) {
			NBTTagCompound tag = nbt.getCompoundTag("Recipe");
			IRecipeInfo recipe = MiscUtil.readRecipeFromNBT(tag);
			if(recipe instanceof IRecipeInfoFusion) {
				currentRecipe = (IRecipeInfoFusion)recipe;
			}
			injectors.clear();
			NBTTagList injectorsTag = nbt.getTagList("Injectors", 11);
			for(int i = 0; i < injectorsTag.tagCount(); ++i) {
				int[] posArray = injectorsTag.getIntArrayAt(i);
				BlockPos pos = new BlockPos(posArray[0], posArray[1], posArray[2]);
				injectors.add(pos);
			}
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setShort("Progress", progress);
		if(currentRecipe != null) {
			NBTTagCompound tag = MiscUtil.writeRecipeToNBT(new NBTTagCompound(), currentRecipe);
			nbt.setTag("Recipe", tag);
			NBTTagList injectorsTag = new NBTTagList();
			injectors.stream().map(pos->new int[] {pos.getX(), pos.getY(), pos.getZ()}).
			forEach(arr->injectorsTag.appendTag(new NBTTagIntArray(arr)));
			nbt.setTag("Injectors", injectorsTag);
		}
		if(hostHelper != null) {
			hostHelper.writeToNBT(nbt);
		}
		return nbt;
	}

	@Override
	public void readSyncNBT(NBTTagCompound nbt) {
		super.readSyncNBT(nbt);
		isWorking = nbt.getBoolean("Working");
		inventory.readFromNBT(nbt);
	}

	@Override
	public NBTTagCompound writeSyncNBT(NBTTagCompound nbt) {
		super.writeSyncNBT(nbt);
		nbt.setBoolean("Working", isWorking);
		inventory.writeToNBT(nbt);
		return nbt;
	}

	public int getScaledEnergy(int scale) {
		if(energyStorage.getMaxEnergyStored() <= 0) {
			return 0;
		}
		return scale * energyStorage.getEnergyStored() / energyStorage.getMaxEnergyStored();
	}

	public int getScaledProgress(int scale) {
		if(progress <= 0) {
			return 0;
		}
		return scale * progress / 2000;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public GuiContainer getClientGuiElement(EntityPlayer player, Object... args) {
		return new GuiFusionCrafter(new ContainerFusionCrafter(player.inventory, this));
	}

	@Override
	public Container getServerGuiElement(EntityPlayer player, Object... args) {
		return new ContainerFusionCrafter(player.inventory, this);
	}
}
