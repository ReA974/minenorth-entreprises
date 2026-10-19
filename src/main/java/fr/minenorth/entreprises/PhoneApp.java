package fr.minenorth.entreprises;

import fr.minenorth.entreprises.network.PhoneNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application « Entreprise » du téléphone (jar MRPhone patché) : état d'affichage de l'icône et ouverture de l'écran de la tablette.
 * L'icône n'est qu'un confort d'affichage ; l'ouverture passe par {@link #open} qui revérifie le métier, comme la tablette.
 */
@Mod.EventBusSubscriber
public final class PhoneApp {
    private PhoneApp() {}

    /** Dernier état envoyé à chaque joueur connecté. */
    private static final Map<UUID, Boolean> SENT = new HashMap<>();
    /** Dernière ouverture demandée (tick du serveur) : évite le martelage du paquet. */
    private static final Map<UUID, Integer> LAST_OPEN = new HashMap<>();

    static boolean has(ServerPlayer p) {
        try {
            return memberOfActive(p);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean memberOfActive(ServerPlayer p) {
        fr.minenorth.entreprises.data.EntrepriseData.Company c = fr.minenorth.entreprises.data.EntrepriseData.get(p.server).companyOf(p.getUUID());
        return c != null && c.status == fr.minenorth.entreprises.data.EntrepriseData.ACTIVE;
    }

    /** Appelé par le paquet « ouvrir » : revérifie le métier puis ouvre l'écran exactement comme la tablette. */
    public static void open(ServerPlayer p) {
        int now = p.server.getTickCount();
        Integer last = LAST_OPEN.get(p.getUUID());
        if (last != null && now - last < 10) return;
        LAST_OPEN.put(p.getUUID(), now);
        if (!has(p)) {
            p.displayClientMessage(Component.literal("§cL'application Entreprise est réservée aux membres d'une entreprise active."), true);
            return;
        }
        EntrepriseService.openFor(p);
    }

    private static void update(ServerPlayer p) {
        boolean now = has(p);
        Boolean before = SENT.put(p.getUUID(), now);
        if (before == null || before != now) PhoneNet.send(fr.minenorth.entreprises.network.ModNetwork.CHANNEL, p, now);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        SENT.remove(p.getUUID());
        update(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        SENT.remove(e.getEntity().getUUID());
        LAST_OPEN.remove(e.getEntity().getUUID());
    }

    /** Chaque seconde : un changement de métier (grade donné ou retiré, entreprise rejointe ou quittée) met l'icône à jour. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) update(p);
    }
}
