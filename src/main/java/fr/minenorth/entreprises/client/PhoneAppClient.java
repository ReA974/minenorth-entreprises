package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.network.PhoneNet;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Côté client de l'application « Entreprise » du téléphone (appelée par réflexion par le jar MRPhone patché) :
 * {@link #visible()} vient du paquet d'état du serveur, {@link #openFromPhone()} demande l'écran au serveur.
 */
@Mod.EventBusSubscriber(modid = fr.minenorth.entreprises.MineNorthEntreprises.MOD_ID, value = Dist.CLIENT)
public final class PhoneAppClient {
    private static volatile boolean visible;

    private PhoneAppClient() {}

    public static boolean visible() { return visible; }

    public static void set(boolean v) { visible = v; }

    /** Téléphone MikasRevs Phone 2 présent : à tester AVANT de toucher phone.PhoneEntreprise. */
    public static boolean phone() { return net.minecraftforge.fml.ModList.get().isLoaded("newphone"); }

    /**
     * Clic sur l'icône : demande l'ouverture au serveur ; la réponse ouvre l'application DANS le téléphone (phone.PhoneEntrepriseScreen).
     * Sans le téléphone : ancien comportement (écran de la tablette).
     */
    public static void openFromPhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (phone()) { fr.minenorth.entreprises.client.phone.PhoneEntreprise.request(); return; }
        mc.setScreen(null);
        ModNetwork.CHANNEL.sendToServer(new PhoneNet.Open());
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) { visible = false; }
}
