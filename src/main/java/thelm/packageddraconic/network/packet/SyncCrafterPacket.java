package thelm.packageddraconic.network.packet;

import java.util.function.Supplier;

import com.brandon3055.draconicevolution.api.crafting.IFusionStateMachine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import thelm.packageddraconic.block.entity.FusionCrafterBlockEntity;
import thelm.packageddraconic.network.PacketHandler;

public record SyncCrafterPacket(BlockPos pos, IFusionStateMachine.FusionState fusionState, short progress, float animProgress, short animLength) {

	public SyncCrafterPacket(FusionCrafterBlockEntity crafter) {
		this(crafter.getBlockPos(), crafter.fusionState, crafter.progress, crafter.animProgress, crafter.animLength);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		buf.writeByte(fusionState.ordinal());
		buf.writeShort(progress);
		buf.writeFloat(animProgress);
		buf.writeShort(animLength);
	}

	public static SyncCrafterPacket decode(FriendlyByteBuf buf) {
		return new SyncCrafterPacket(buf.readBlockPos(), IFusionStateMachine.FusionState.values()[buf.readByte()], buf.readShort(), buf.readFloat(), buf.readShort());
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(()->{
			ClientLevel level = Minecraft.getInstance().level;
			if(level.isLoaded(pos)) {
				BlockEntity be = level.getBlockEntity(pos);
				if(be instanceof FusionCrafterBlockEntity crafter) {
					crafter.fusionState = fusionState;
					crafter.progress = progress;
					crafter.animProgress = animProgress;
					crafter.animLength = animLength;
				}
			}
		});
		ctx.get().setPacketHandled(true);
	}

	public static void sync(FusionCrafterBlockEntity crafter) {
		double x = crafter.getBlockPos().getX()+0.5;
		double y = crafter.getBlockPos().getY()+0.5;
		double z = crafter.getBlockPos().getZ()+0.5;
		PacketHandler.INSTANCE.send(PacketDistributor.NEAR.with(()->new TargetPoint(x, y, z, 32, crafter.getLevel().dimension())), new SyncCrafterPacket(crafter));
	}
}
