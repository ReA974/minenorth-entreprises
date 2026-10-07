package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.client.Minecraft;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    /** Met à jour l'écran déjà ouvert (on garde l'onglet et la page), sinon en ouvre un nouveau. */
    public static void state(ModNetwork.StatePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EntrepriseScreen s && s.isAdmin() == p.admin()) s.update(p);
        else mc.setScreen(new EntrepriseScreen(p));
    }
}
