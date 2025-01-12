package thelm.packageddraconic.tile;

import com.brandon3055.draconicevolution.api.fusioncrafting.ICraftingInjector;
import com.brandon3055.draconicevolution.api.fusioncrafting.IFusionCraftingInventory;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thelm.packagedauto.tile.TileBase;
import thelm.packageddraconic.block.BlockMarkedInjector;
import thelm.packageddraconic.energy.EnergyStorageMarkedInjector;
import thelm.packageddraconic.integration.appeng.networking.HostHelperTileMarkedInjector;
import thelm.packageddraconic.inventory.InventoryMarkedInjector;

@Optional.InterfaceList({
	@Optional.Interface(iface="appeng.api.networking.IGridHost", modid="appliedenergistics2"),
	@Optional.Interface(iface="appeng.api.networking.security.IActionHost", modid="appliedenergistics2"),
})
public class TileMarkedInjector extends TileBase implements ITickable, ICraftingInjector, IGridHost, IActionHost {

	public static int[] chargeRate = {300, 220, 140, 60};
	public static int[] craftRate = {2, 3, 5, 7};

	public boolean firstTick = true;
	public EnergyStorageMarkedInjector energyStorage = new EnergyStorageMarkedInjector(this);
	public BlockPos crafterPos = null;
	public int tier = -1;

	public TileMarkedInjector() {
		setInventory(new InventoryMarkedInjector(this));
		if(Loader.isModLoaded("appliedenergistics2")) {
			hostHelper = new HostHelperTileMarkedInjector(this);
		}
	}

	@Override
	protected String getLocalizedName() {
		return getBlockType().getLocalizedName();
	}

	@Override
	public void update() {
		if(firstTick) {
			firstTick = false;
			if(!world.isRemote && hostHelper != null) {
				hostHelper.isActive();
			}
		}
	}

	public void ejectItem() {
		if(hostHelper != null && hostHelper.isActive()) {
			hostHelper.ejectItem();
		}
		ItemStack stack = inventory.getStackInSlot(0);
		inventory.setInventorySlotContents(0, ItemStack.EMPTY);
		if(!stack.isEmpty()) {
			EnumFacing facing = getDirection();
			double dx = world.rand.nextFloat()/2+0.25+facing.getXOffset()*0.5;
			double dy = world.rand.nextFloat()/2+0.25+facing.getYOffset()*0.5;
			double dz = world.rand.nextFloat()/2+0.25+facing.getZOffset()*0.5;
			EntityItem entityitem = new EntityItem(world, pos.getX()+dx, pos.getY()+dy, pos.getZ()+dz, stack);
			entityitem.setDefaultPickupDelay();
			world.spawnEntity(entityitem);
		}
	}

	@Override
	public int getPedestalTier() {
		if(tier == -1) {
			Block block = getBlockType();
			if(block instanceof BlockMarkedInjector) {
				tier = ((BlockMarkedInjector)block).tier;
			}
		}
		return tier;
	}

	public int getChargeRate() {
		return chargeRate[MathHelper.clamp(getPedestalTier(), 0, chargeRate.length-1)];
	}

	public int getCraftRate() {
		return craftRate[MathHelper.clamp(getPedestalTier(), 0, craftRate.length-1)];
	}

	@Override
	public ItemStack getStackInPedestal() {
		return inventory.getStackInSlot(0);
	}

	@Override
	public void setStackInPedestal(ItemStack stack) {
		inventory.setInventorySlotContents(0, stack);
	}

	@Override
	public boolean setCraftingInventory(IFusionCraftingInventory craftingInventory) {
		if(craftingInventory == null) {
			crafterPos = null;
			return false;
		}
		if(validateCraftingInventory() && !world.isRemote) {
			return false;
		}
		crafterPos = ((TileEntity)craftingInventory).getPos();
		return true;
	}

	public IFusionCraftingInventory getCraftingInventory() {
		validateCraftingInventory();
		if(crafterPos != null) {
			TileEntity tile = world.getTileEntity(crafterPos);
			if(tile instanceof IFusionCraftingInventory) {
				return (IFusionCraftingInventory)tile;
			}
		}
		return null;
	}

	@Override
	public EnumFacing getDirection() {
		IBlockState state = world.getBlockState(pos);
		if(state.getBlock() instanceof BlockMarkedInjector) {
			return state.getValue(BlockDirectional.FACING);
		}
		return EnumFacing.UP;
	}

	@Override
	public long getInjectorCharge() {
		return energyStorage.getExtendedEnergyStored();
	}

	public boolean validateCraftingInventory() {
		if(!getStackInPedestal().isEmpty() && crafterPos != null) {
			TileEntity tile = world.getTileEntity(crafterPos);
			if(tile instanceof IFusionCraftingInventory && !tile.isInvalid() && ((IFusionCraftingInventory)tile).craftingInProgress()) {
				return true;
			}
		}
		crafterPos = null;
		return false;
	}

	@Override
	public void onCraft() {
		if(crafterPos != null) {
			energyStorage.setEnergyStored(0);
			crafterPos = null;
		}
	}

	@Override
	public int getComparatorSignal() {
		return inventory.getStackInSlot(0).isEmpty() ? 0 : 15;
	}

	public HostHelperTileMarkedInjector hostHelper;

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
		energyStorage.readFromNBT(nbt);
		crafterPos = null;
		if(nbt.hasKey("CrafterPos")) {
			int[] posArray = nbt.getIntArray("CrafterPos");
			crafterPos = new BlockPos(posArray[0], posArray[1], posArray[2]);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		energyStorage.writeToNBT(nbt);
		if(crafterPos != null) {
			nbt.setIntArray("CrafterPos", new int[] {crafterPos.getX(), crafterPos.getY(), crafterPos.getZ()});
		}
		if(hostHelper != null) {
			hostHelper.writeToNBT(nbt);
		}
		return nbt;
	}

	@Override
	public void readSyncNBT(NBTTagCompound nbt) {
		super.readSyncNBT(nbt);
		inventory.readFromNBT(nbt);
	}

	@Override
	public NBTTagCompound writeSyncNBT(NBTTagCompound nbt) {
		super.writeSyncNBT(nbt);
		inventory.writeToNBT(nbt);
		return nbt;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing from) {
		return capability == CapabilityEnergy.ENERGY && getDirection() != from || super.hasCapability(capability, from);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		return capability == CapabilityEnergy.ENERGY && getDirection() != facing ? (T)energyStorage : super.getCapability(capability, facing);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public GuiContainer getClientGuiElement(EntityPlayer player, Object... args) {
		return null;
	}

	@Override
	public Container getServerGuiElement(EntityPlayer player, Object... args) {
		return null;
	}
}
