package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    /** Factures reçues pendant qu'un écran de facture est déjà ouvert : affichées ensuite, dans l'ordre. */
    private static final Deque<ModNetwork.InvoicePacket> PENDING = new ArrayDeque<>();
    private static final int MAX_PENDING = 8;

    /** Met à jour l'écran déjà ouvert (on garde l'onglet et la page), sinon en ouvre un nouveau. */
    public static void state(ModNetwork.StatePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof InvoiceScreen) return;   // la facture affichée doit rester répondable
        if (mc.screen instanceof EntrepriseScreen s && s.isAdmin() == p.admin()) s.update(p);
        else mc.setScreen(new EntrepriseScreen(p));
    }

    /** Une facture attend la signature du joueur : ouvre l'écran de facture (ou la met en file si une autre est affichée). */
    public static void invoice(ModNetwork.InvoicePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof InvoiceScreen) {
            if (PENDING.size() < MAX_PENDING) PENDING.add(p);
            return;
        }
        // Un conteneur ouvert (coffre, inventaire) est fermé proprement côté serveur avant d'afficher la facture.
        if (mc.screen instanceof AbstractContainerScreen<?> && mc.player != null) mc.player.closeContainer();
        mc.setScreen(new InvoiceScreen(p));
    }

    /** Déconnexion du serveur : les factures en file ne concernent plus cette session. */
    static void reset() { PENDING.clear(); }

    /** Écran de facture terminé (réponse envoyée ou Échap) : facture suivante en attente, sinon retour au jeu. */
    static void nextInvoice() {
        ModNetwork.InvoicePacket next = PENDING.poll();
        Minecraft.getInstance().setScreen(next == null ? null : new InvoiceScreen(next));
    }
}
