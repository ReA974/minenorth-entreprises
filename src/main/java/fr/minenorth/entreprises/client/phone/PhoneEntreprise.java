package fr.minenorth.entreprises.client.phone;

import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.network.PhoneNet;
import net.minecraft.client.Minecraft;

/**
 * Seul point d'entrée vers {@link PhoneEntrepriseScreen} (qui hérite d'une classe du téléphone MikasRevs Phone 2). Ne JAMAIS toucher cette classe sans
 * avoir vérifié {@code PhoneAppClient.phone()} : sa vérification par la JVM charge PhoneEntrepriseScreen, donc la classe du téléphone. Côté client uniquement.
 */
public final class PhoneEntreprise {
    /** Ouverture demandée depuis le téléphone : le prochain état (joueur) ouvre l'application dans le téléphone (pendant 5 s). */
    private static long pendingUntil;

    private PhoneEntreprise() {}

    /** Clic sur l'icône : on garde le téléphone ouvert et on demande l'ouverture au serveur. */
    public static void request() {
        pendingUntil = System.currentTimeMillis() + 5000;
        ModNetwork.CHANNEL.sendToServer(new PhoneNet.Open());
    }

    /** true si l'état a été pris par l'application du téléphone (ouverte ou mise à jour). */
    public static boolean handle(ModNetwork.StatePacket state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneEntrepriseScreen ps) {
            ps.update(state);
            return true;
        }
        if (System.currentTimeMillis() < pendingUntil && mc.player != null) {
            pendingUntil = 0;
            PhoneNavigationState.open(mc, mc.screen, new PhoneEntrepriseScreen(state));
            return true;
        }
        return false;
    }
}
