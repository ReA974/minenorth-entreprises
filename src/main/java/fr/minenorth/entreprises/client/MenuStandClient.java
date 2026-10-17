package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.MineNorthEntreprises;
import fr.minenorth.entreprises.menustand.MenuStandBlock;
import fr.minenorth.entreprises.menustand.SetMenuPacket;
import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ctrl + clic droit sur un distributeur de menus : au lieu du clic normal, on demande au serveur de configurer la carte à donner. */
@Mod.EventBusSubscriber(modid = MineNorthEntreprises.MOD_ID, value = Dist.CLIENT)
public final class MenuStandClient {
    private MenuStandClient() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getLevel().getBlockState(e.getPos()).getBlock() instanceof MenuStandBlock) || !Screen.hasControlDown()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        ModNetwork.CHANNEL.sendToServer(new SetMenuPacket(e.getPos()));
    }
}
