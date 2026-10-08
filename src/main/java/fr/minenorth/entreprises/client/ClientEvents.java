package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.MineNorthEntreprises;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Événements du client uniquement (chargé seulement sur Dist.CLIENT). */
@Mod.EventBusSubscriber(modid = MineNorthEntreprises.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Déconnexion : vide la file de factures et ferme un écran de facture resté ouvert. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientNetworkHandler.reset();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.screen instanceof InvoiceScreen) mc.setScreen(null); });
    }
}
