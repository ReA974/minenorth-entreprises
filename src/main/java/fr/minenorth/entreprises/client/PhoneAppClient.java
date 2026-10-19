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

    /** Clic sur l'icône : ferme le téléphone et demande l'ouverture au serveur (qui revérifie le métier). */
    public static void openFromPhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.setScreen(null);
        ModNetwork.CHANNEL.sendToServer(new PhoneNet.Open());
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) { visible = false; }
}
