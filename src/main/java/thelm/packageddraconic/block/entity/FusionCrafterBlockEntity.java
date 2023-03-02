package thelm.packageddraconic.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.api.crafting.IFusionInjector;
import com.brandon3055.draconicevolution.api.crafting.IFusionInventory;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.IFusionStateMachine;
import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Runnables;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import thelm.packagedauto.api.IPackageCraftingMachine;
import thelm.packagedauto.api.IPackageRecipeInfo;
import thelm.packagedauto.block.entity.BaseBlockEntity;
import thelm.packagedauto.block.entity.UnpackagerBlockEntity;
import thelm.packagedauto.energy.EnergyStorage;
import thelm.packagedauto.util.MiscHelper;
import thelm.packageddraconic.block.FusionCrafterBlock;
import thelm.packageddraconic.client.fx.FusionCrafterFXHandler;
import thelm.packageddraconic.integration.appeng.blockentity.AEFusionCrafterBlockEntity;
import thelm.packageddraconic.inventory.FusionCrafterItemHandler;
import thelm.packageddraconic.menu.FusionCrafterMenu;
import thelm.packageddraconic.network.packet.FinishCraftEffectsPacket;
import thelm.packageddraconic.network.packet.SyncCrafterPacket;
import thelm.packageddraconic.recipe.IFusionPackageRecipeInfo;

public class FusionCrafterBlockEntity extends BaseBlockEntity implements IPackageCraftingMachine, IFusionInventory, IFusionStateMachine {

	public static final BlockEntityType<FusionCrafterBlockEntity> TYPE_INSTANCE = (BlockEntityType<FusionCrafterBlockEntity>)BlockEntityType.Builder.
			of(MiscHelper.INSTANCE.<BlockEntityType.BlockEntitySupplier<FusionCrafterBlockEntity>>conditionalSupplier(
					()->ModList.get().isLoaded("ae2"),
					()->()->AEFusionCrafterBlockEntity::new, ()->()->FusionCrafterBlockEntity::new).get(),
					FusionCrafterBlock.INSTANCE).
			build(null).setRegistryName("packageddraconic:fusion_crafter");

	public static int energyCapacity = 5000;
	public static int energyUsage = 5;
	public static boolean drawMEEnergy = true;

	public Runnable fxHandler = DistExecutor.runForDist(()->()->new FusionCrafterFXHandler(this), ()->()->Runnables.doNothing());
	public IFusionRecipe effectRecipe;
	public float animProgress = 0;
	public short animLength = 0;
	public boolean isWorking = false;	
	public FusionState fusionState = FusionState.START;
	public int fusionCounter = 0;
	public short progress = 0;
	public IFusionPackageRecipeInfo currentRecipe;
	public List<BlockPos> injectors = new ArrayList<>();

	public FusionCrafterBlockEntity(BlockPos pos, BlockState state) {
		super(TYPE_INSTANCE, pos, state);
		setItemHandler(new FusionCrafterItemHandler(this));
		setEnergyStorage(new EnergyStorage(this, energyCapacity));
	}

	@Override
	protected Component getDefaultName() {
		return new TranslatableComponent("block.packageddraconic.fusion_crafter");
	}

	@Override
	public void tick() {
		if(!level.isClientSide) {
			if(isWorking) {
				tickProcess();
			}
			chargeEnergy();
			if(level.getGameTime() % 8 == 0) {
				ejectItems();
			}
			energyStorage.updateIfChanged();
		}
		else {
			fxHandler.run();
		}
	}

	@Override
	public boolean acceptPackage(IPackageRecipeInfo recipeInfo, List<ItemStack> stacks, Direction direction) {
		if(!isBusy() && recipeInfo instanceof IFusionPackageRecipeInfo recipe) {
			List<ItemStack> injectorInputs = recipe.getInjectorInputs();
			List<BlockPos> emptyInjectors = getEmptyInjectors();
			if(emptyInjectors.size() >= injectorInputs.size()) {
				injectors.clear();
				injectors.addAll(emptyInjectors.subList(0, injectorInputs.size()));
				currentRecipe = recipe;
				effectRecipe = recipe.getRecipe();
				isWorking = true;
				fusionState = FusionState.START;
				itemHandler.setStackInSlot(0, recipe.getCoreInput().copy());
				List<IFusionInjector> craftInjectors = getInjectors();
				for(int i = 0; i < craftInjectors.size(); ++i) {
					MarkedInjectorBlockEntity injector = (MarkedInjectorBlockEntity)craftInjectors.get(i);
					injector.setInjectorStack(injectorInputs.get(i).copy());
					injector.setCrafter(this);
				}
				if(!recipe.getRecipe().matches(this, level) || !recipe.getRecipe().canStartCraft(this, level, t->{})) {
					injectors.clear();
					currentRecipe = null;
					effectRecipe = null;
					isWorking = false;
					itemHandler.setStackInSlot(0, ItemStack.EMPTY);
					for(int i = 0; i < craftInjectors.size(); ++i) {
						MarkedInjectorBlockEntity injector = (MarkedInjectorBlockEntity)craftInjectors.get(i);
						injector.setInjectorStack(ItemStack.EMPTY);
						injector.setCrafter(this);
					}
					return false;
				}
				sync(false);
				setChanged();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isBusy() {
		return isWorking || !itemHandler.getStacks().subList(0, 2).stream().allMatch(ItemStack::isEmpty);
	}

	protected void tickProcess() {
		if(injectors.stream().map(level::getBlockEntity).anyMatch(be->!(be instanceof MarkedInjectorBlockEntity) || be.isRemoved())) {
			cancelCraft();
		}
		else {
			SyncCrafterPacket.sync(this);
			if(fusionState.ordinal() < FusionState.CRAFTING.ordinal()) {
				currentRecipe.getRecipe().tickFusionState(this, this, level);
			}
			else if(energyStorage.extractEnergy(energyUsage, true) == energyUsage) {
				energyStorage.extractEnergy(energyUsage, false);
				currentRecipe.getRecipe().tickFusionState(this, this, level);
			}
		}
	}

	public void endProcess() {
		fusionCounter = 0;
		progress = 0;
		animProgress = 0;
		animLength = 0;
		injectors.stream().map(level::getBlockEntity).
		filter(be->be instanceof MarkedInjectorBlockEntity && !be.isRemoved()).
		forEach(be->((MarkedInjectorBlockEntity)be).spawnItem());
		injectors.clear();
		isWorking = false;
		effectRecipe = null;
		currentRecipe = null;
		sync(false);
		setChanged();
	}

	protected List<BlockPos> getEmptyInjectors() {
		List<BlockPos> positions = new ArrayList<>();
		int range = DEConfig.fusionInjectorRange;
		int radius = 1;
		List<MarkedInjectorBlockEntity> searchBlockEntities = Streams.concat(
				BlockPos.betweenClosedStream(worldPosition.offset(-range, -radius, -radius), worldPosition.offset(range, radius, radius)),
				BlockPos.betweenClosedStream(worldPosition.offset(-radius, -range, -radius), worldPosition.offset(radius, range, radius)),
				BlockPos.betweenClosedStream(worldPosition.offset(-radius, -radius, -range), worldPosition.offset(radius, radius, range))).
				map(level::getBlockEntity).
				filter(be->be instanceof MarkedInjectorBlockEntity).
				map(be->(MarkedInjectorBlockEntity)be).
				collect(Collectors.toList());
		for(MarkedInjectorBlockEntity be : searchBlockEntities) {
			Vec3i dirVec = be.getBlockPos().subtract(worldPosition);
			int dist = Ints.max(Math.abs(dirVec.getX()), Math.abs(dirVec.getY()), Math.abs(dirVec.getZ()));
			if(dist <= DEConfig.fusionInjectorMinDist) {
				positions.clear();
				return positions;
			}
			if(Direction.getNearest(dirVec.getX(), dirVec.getY(), dirVec.getZ()) == be.getDirection().getOpposite() && be.getInjectorStack().isEmpty()) {
				BlockPos pos = be.getBlockPos();
				Direction facing = be.getDirection();
				boolean obstructed = false;
				for(BlockPos bp : BlockPos.betweenClosed(pos.relative(facing), pos.relative(facing, distanceInDirection(pos, worldPosition, facing) - 1))) {
					if(!level.isEmptyBlock(bp) && level.getBlockState(bp).canOcclude() || level.getBlockEntity(bp) instanceof MarkedInjectorBlockEntity || level.getBlockEntity(bp) instanceof FusionCrafterBlockEntity) {
						obstructed = true;
						break;
					}
				}
				if(!obstructed) {
					positions.add(be.getBlockPos());
				}
			}
		}
		return positions;
	}

	@Override
	public List<IFusionInjector> getInjectors() {
		return injectors.stream().map(level::getBlockEntity).
				filter(be->be instanceof MarkedInjectorBlockEntity && !be.isRemoved()).
				map(be->(IFusionInjector)be).collect(Collectors.toList());
	}

	public static int distanceInDirection(BlockPos fromPos, BlockPos toPos, Direction direction) {
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
		for(Direction direction : Direction.values()) {
			BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(direction));
			if(blockEntity != null && !(blockEntity instanceof UnpackagerBlockEntity) && blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite()).isPresent()) {
				IItemHandler itemHandler = blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite()).resolve().get();
				boolean flag = true;
				for(int i = 1; i >= endIndex; --i) {
					ItemStack stack = this.itemHandler.getStackInSlot(i);
					if(stack.isEmpty()) {
						continue;
					}
					for(int slot = 0; slot < itemHandler.getSlots(); ++slot) {
						ItemStack stackRem = itemHandler.insertItem(slot, stack, false);
						if(stackRem.getCount() < stack.getCount()) {
							stack = stackRem;
							flag = false;
						}
						if(stack.isEmpty()) {
							break;
						}
					}
					this.itemHandler.setStackInSlot(i, stack);
					if(flag) {
						break;
					}
				}
			}
		}
	}

	protected void chargeEnergy() {
		int prevStored = energyStorage.getEnergyStored();
		ItemStack energyStack = itemHandler.getStackInSlot(2);
		if(energyStack.getCapability(CapabilityEnergy.ENERGY, null).isPresent()) {
			int energyRequest = Math.min(energyStorage.getMaxReceive(), energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());
			energyStorage.receiveEnergy(energyStack.getCapability(CapabilityEnergy.ENERGY).resolve().get().extractEnergy(energyRequest, false), false);
			if(energyStack.getCount() <= 0) {
				itemHandler.setStackInSlot(2, ItemStack.EMPTY);
			}
		}
	}

	@Override
	public ItemStack getCatalystStack() {
		return itemHandler.getStackInSlot(0);
	}

	@Override
	public ItemStack getOutputStack() {
		return itemHandler.getStackInSlot(1);
	}

	@Override
	public void setCatalystStack(ItemStack stack) {
		itemHandler.setStackInSlot(0, stack);
	}

	@Override
	public void setOutputStack(ItemStack stack) {
		itemHandler.setStackInSlot(1, stack);
	}

	@Override
	public TechLevel getMinimumTier() {
		return TechLevel.CHAOTIC;
	}

	@Override
	public FusionState getFusionState() {
		return fusionState;
	}

	@Override
	public void setFusionState(FusionState state) {
		fusionState = state;
		setChanged();
	}

	@Override
	public void completeCraft() {
		isWorking = false;
		getInjectors().forEach(e->e.setEnergyRequirement(0, 0));
		FinishCraftEffectsPacket.finishCraft(this, true);
		ejectItems();
		endProcess();
	}

	@Override
	public void cancelCraft() {
		isWorking = false;
		getInjectors().forEach(e->e.setEnergyRequirement(0, 0));
		FinishCraftEffectsPacket.finishCraft(this, false);
		ejectItems();
		endProcess();
	}

	@Override
	public int getCounter() {
		return fusionCounter;
	}

	@Override
	public void setCounter(int count) {
		fusionCounter = count;
		setChanged();
	}

	@Override
	public void setFusionStatus(double progress, Component stateText) {
		if(progress < 0) {
			this.progress = 0;
		}
		switch(fusionState) {
		case CHARGING:
			this.progress = (short)(progress*10000);
			break;
		case CRAFTING:
			this.progress = (short)(10000+progress*10000);
			break;
		default:
			this.progress = (short)(progress*20000);
			break;
		}
		setChanged();
	}

	@Override
	public void setCraftAnimation(float progress, int length) {
		animProgress = progress;
		animLength = (short)length;
		setChanged();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		endProcess();
	}

	@Override
	public void load(CompoundTag nbt) {
		super.load(nbt);
		fusionCounter = nbt.getInt("FusionCounter");
		currentRecipe = null;
		if(nbt.contains("Recipe")) {
			CompoundTag tag = nbt.getCompound("Recipe");
			IPackageRecipeInfo recipe = MiscHelper.INSTANCE.loadRecipe(tag);
			if(recipe instanceof IFusionPackageRecipeInfo fusionRecipe) {
				currentRecipe = fusionRecipe;
			}
		}
	}

	@Override
	public void saveAdditional(CompoundTag nbt) {
		super.saveAdditional(nbt);
		nbt.putInt("FusionCounter", fusionCounter);
		if(currentRecipe != null) {
			CompoundTag tag = MiscHelper.INSTANCE.saveRecipe(new CompoundTag(), currentRecipe);
			nbt.put("Recipe", tag);
		}
	}

	@Override
	public void loadSync(CompoundTag nbt) {
		super.loadSync(nbt);
		isWorking = nbt.getBoolean("Working");
		fusionState = FusionState.values()[nbt.getByte("FusionState")];
		progress = nbt.getShort("Progress");
		animProgress = nbt.getFloat("AnimProgress");
		animLength = nbt.getShort("AnimLength");
		itemHandler.load(nbt);
		injectors.clear();
		ListTag injectorsTag = nbt.getList("Injectors", 11);
		for(int i = 0; i < injectorsTag.size(); ++i) {
			int[] posArray = injectorsTag.getIntArray(i);
			BlockPos pos = new BlockPos(posArray[0], posArray[1], posArray[2]);
			injectors.add(pos);
		}
		if(nbt.contains("EffectRecipe")) {
			Recipe recipe = MiscHelper.INSTANCE.getRecipeManager().byKey(new ResourceLocation(nbt.getString("EffectRecipe"))).orElse(null);
			if(recipe instanceof IFusionRecipe fusionRecipe) {
				effectRecipe = fusionRecipe;
			}
		}
	}

	@Override
	public CompoundTag saveSync(CompoundTag nbt) {
		super.saveSync(nbt);
		nbt.putBoolean("Working", isWorking);
		nbt.putByte("FusionState", (byte)fusionState.ordinal());
		nbt.putShort("Progress", progress);
		nbt.putFloat("AnimProgress", animProgress);
		nbt.putShort("AnimLength", animLength);
		itemHandler.save(nbt);
		ListTag injectorsTag = new ListTag();
		injectors.stream().map(pos->new int[] {pos.getX(), pos.getY(), pos.getZ()}).
		forEach(arr->injectorsTag.add(new IntArrayTag(arr)));
		nbt.put("Injectors", injectorsTag);
		if(effectRecipe != null) {
			nbt.putString("EffectRecipe", effectRecipe.getId().toString());
		}
		return nbt;
	}

	public int getScaledEnergy(int scale) {
		if(energyStorage.getMaxEnergyStored() <= 0) {
			return 0;
		}
		return Math.min(scale * energyStorage.getEnergyStored() / energyStorage.getMaxEnergyStored(), scale);
	}

	public int getScaledProgress(int scale) {
		if(progress <= 0) {
			return 0;
		}
		return scale * progress / 20000;
	}

	@Override
	public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
		sync(false);
		return new FusionCrafterMenu(windowId, inventory, this);
	}
}