package thelm.packageddraconic;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import thelm.packageddraconic.client.event.ClientEventHandler;
import thelm.packageddraconic.event.CommonEventHandler;

@Mod(PackagedDraconic.MOD_ID)
public class PackagedDraconic {

	public static final String MOD_ID = "packageddraconic";

	public PackagedDraconic() {
		CommonEventHandler.getInstance().onConstruct();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ()->()->{
			ClientEventHandler.getInstance().onConstruct();
		});
	}
}
