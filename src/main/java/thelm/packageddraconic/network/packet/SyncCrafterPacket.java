package thelm.packageddraconic.network.packet;

import java.util.function.Supplier;

import com.brandon3055.draconicevolution.api.crafting.IFusionStateMachine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.PacketDistributor.TargetPoint;
import thelm.packageddraconic.network.PacketHandler;
import thelm.packageddraconic.tile.FusionCrafterTile;

public class SyncCrafterPacket {

	private BlockPos pos;
	private IFusionStateMachine.FusionState fusionState;
	private short progress;
	private float animProgress;
	private short animLength;

	public SyncCrafterPacket(FusionCrafterTile tile) {
		pos = tile.getBlockPos();
		fusionState = tile.fusionState;
		progress = tile.progress;
		animProgress = tile.animProgress;
		animLength = tile.animLength;
	}

	private SyncCrafterPacket(BlockPos pos, byte fusionState, short progress, float animProgress, short animLength) {
		this.pos = pos;
		this.fusionState = IFusionStateMachine.FusionState.values()[fusionState];
		this.progress = progress;
		this.animProgress = animProgress;
		this.animLength = animLength;
	}

	public void encode(PacketBuffer buf) {
		buf.writeBlockPos(pos);
		buf.writeByte(fusionState.ordinal());
		buf.writeShort(progress);
		buf.writeFloat(animProgress);
		buf.writeShort(animLength);
	}

	public static SyncCrafterPacket decode(PacketBuffer buf) {
		return new SyncCrafterPacket(buf.readBlockPos(), buf.readByte(), buf.readShort(), buf.readFloat(), buf.readShort());
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(()->{
			ClientWorld world = Minecraft.getInstance().level;
			if(world.isLoaded(pos)) {
				TileEntity te = world.getBlockEntity(pos);
				if(te instanceof FusionCrafterTile) {
					FusionCrafterTile tile = (FusionCrafterTile)te;
					tile.fusionState = fusionState;
					tile.progress = progress;
					tile.animProgress = animProgress;
					tile.animLength = animLength;
				}
			}
		});
		ctx.get().setPacketHandled(true);
	}

	public static void sync(FusionCrafterTile tile) {
		double x = tile.getBlockPos().getX()+0.5;
		double y = tile.getBlockPos().getY()+0.5;
		double z = tile.getBlockPos().getZ()+0.5;
		PacketHandler.INSTANCE.send(PacketDistributor.NEAR.with(()->new TargetPoint(x, y, z, 32, tile.getLevel().dimension())), new SyncCrafterPacket(tile));
	}
}
