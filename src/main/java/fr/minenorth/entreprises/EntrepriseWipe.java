package fr.minenorth.entreprises;

import fr.minenorth.api.PlayerWipeEvent;
import fr.minenorth.entreprises.data.EntrepriseData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Suppression d'un joueur depuis le panneau admin : il quitte toutes les entreprises, ses invitations sont effacées
 * et les entreprises dont il était le patron sont dissoutes.
 */
@Mod.EventBusSubscriber(modid = MineNorthEntreprises.MOD_ID)
public final class EntrepriseWipe {
    private EntrepriseWipe() {}

    @SubscribeEvent
    public static void onWipe(PlayerWipeEvent e) {
        EntrepriseData d = EntrepriseData.get(e.server());
        boolean any = InvoiceService.onWipe(e.server(), e.player());   // factures du client effacé : annulées
        any |= !d.invitesOf(e.player()).isEmpty();
        d.clearInvites(e.player());
        List<Integer> owned = new ArrayList<>();
        for (EntrepriseData.Company c : d.all()) {
            if (e.player().equals(c.owner)) owned.add(c.id);
            else if (c.members.remove(e.player()) != null) { any = true; CompanyAccounts.syncSigners(e.server(), c); }
        }
        for (int id : owned) {
            EntrepriseData.Company c = d.get(id);
            InvoiceService.onCompanyClosed(e.server(), id);   // avant la fermeture du compte
            if (c != null) CompanyAccounts.close(e.server(), c, true);   // solde au trésor : le patron est effacé
            d.remove(id);
        }
        if (any || !owned.isEmpty()) {
            d.setDirty();
            e.cleaned("entreprises");
        }
    }
}
