package fr.minenorth.entreprises;

import com.mojang.logging.LogUtils;
import fr.minenorth.api.BankService;
import fr.minenorth.api.BankTx;
import fr.minenorth.api.MineNorth;
import fr.minenorth.api.PayResult;
import fr.minenorth.entreprises.config.EntrepriseConfig;
import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.data.EntrepriseData.Company;
import fr.minenorth.entreprises.data.EntrepriseData.Invoice;
import fr.minenorth.entreprises.data.EntrepriseData.Invoice.Status;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Factures d'une entreprise à un client : création, signature, refus, annulation et prélèvement différé.
 * Tout s'exécute sur le thread serveur. Une facture n'est prélevée que par {@link #collect}, qui vérifie lui-même
 * le statut attendu et l'entreprise (existante, ACTIVE) ; {@code PAID} est définitif.
 */
public final class InvoiceService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final long MAX_CENTS = 1_000_000_000L;
    public static final int MAX_DESCRIPTION = 64;
    public static final int MAX_AWAITING_PER_PAYER = 3;
    private static final String NOT_FOUND = "Facture introuvable.";

    private InvoiceService() {}

    /** Prévient le client qu'une facture l'attend (la tâche 9 remplace l'implémentation par l'ouverture de l'écran). */
    @FunctionalInterface
    public interface Notifier { void open(ServerPlayer payer, Invoice inv, String companyName); }

    public static volatile Notifier notifier = (payer, inv, companyName) -> payer.sendSystemMessage(Component.literal(
            "§bFacture de " + companyName + " : " + inv.description + " — " + EntrepriseService.money(inv.cents) + " (signature requise)"));

    /** Factures en cours de prélèvement : protège contre toute réentrance pendant l'appel à la banque. */
    private static final Set<Integer> COLLECTING = new HashSet<>();

    // ------------------------------------------------------------------ création
    /** Crée une facture de {@code c} pour {@code payer}. Retourne un message d'erreur, ou null. */
    public static String create(ServerPlayer issuer, Company c, UUID payer, long cents, String description) {
        MinecraftServer s = issuer.server;
        EntrepriseData d = EntrepriseData.get(s);
        if (c == null || d.get(c.id) != c) return "Entreprise introuvable.";
        if (!CompanyAccounts.canBank(c, issuer.getUUID())) return "Vous n'avez pas accès aux factures.";
        if (c.status != EntrepriseData.ACTIVE) return "L'entreprise n'est pas encore validée.";
        if (cents <= 0 || cents > MAX_CENTS) return "Montant invalide.";
        String desc = EntrepriseService.clean(description);
        if (desc.isEmpty() || desc.length() > MAX_DESCRIPTION) return "La description doit faire entre 1 et " + MAX_DESCRIPTION + " caractères.";
        if (payer == null) return "Ce client doit être connecté.";
        if (payer.equals(issuer.getUUID())) return "Vous ne pouvez pas vous facturer vous-même.";
        ServerPlayer client = s.getPlayerList().getPlayer(payer);
        if (client == null) return "Ce client doit être connecté.";
        int max = EntrepriseConfig.get().facture_distance_blocs;
        if (client.level().dimension() != issuer.level().dimension()
                || client.distanceToSqr(issuer) > (double) max * max) return "Ce client doit être à moins de " + max + " blocs.";
        int awaiting = 0;
        for (Invoice inv : d.invoicesOfPayer(payer)) if (inv.status == Status.AWAITING_SIGNATURE) awaiting++;
        if (awaiting >= MAX_AWAITING_PER_PAYER) return "Ce client a déjà trop de factures en attente.";

        Invoice inv = d.newInvoice(c.id, issuer.getUUID(), MineNorth.displayName(issuer), payer, MineNorth.displayName(client),
                cents, desc, System.currentTimeMillis());
        try {
            notifier.open(client, inv, c.name);
        } catch (RuntimeException e) {
            LOGGER.warn("Facture {} : notification du client impossible.", inv.id, e);
        }
        return null;
    }

    // ------------------------------------------------------------------ actions du client
    /** Le client signe : prélèvement immédiat, ou prélèvement différé si les fonds manquent. Message d'erreur, ou null. */
    public static String sign(ServerPlayer payer, int invoiceId) {
        MinecraftServer s = payer.server;
        EntrepriseData d = EntrepriseData.get(s);
        Invoice inv = d.invoice(invoiceId);
        if (inv == null || !inv.payer.equals(payer.getUUID())) return NOT_FOUND;   // id deviné : on ne révèle rien
        if (inv.status != Status.AWAITING_SIGNATURE || COLLECTING.contains(inv.id)) return "Cette facture n'est plus à signer.";
        if (System.currentTimeMillis() - inv.created >= signatureDelayMs()) {
            d.setInvoiceStatus(inv, Status.EXPIRED);
            EntrepriseService.tell(s, inv.issuer, "§eLa facture de " + EntrepriseService.money(inv.cents) + " adressée à "
                    + inv.payerName + " a expiré sans signature.");
            return "Cette facture n'est plus à signer.";
        }
        Company c = d.get(inv.companyId);
        String companyName = c == null ? "" : c.name;
        PayResult r = collect(s, d, inv, Status.AWAITING_SIGNATURE);
        if (r == null) return "Cette facture n'est plus à signer.";
        switch (inv.status) {
            case PAID: paidMessages(s, inv, companyName); return null;
            case FAILED: {
                payer.sendSystemMessage(Component.literal("§eFonds insuffisants : la facture de " + EntrepriseService.money(inv.cents)
                        + " sera prélevée automatiquement dès que vous aurez l'argent."));
                EntrepriseService.tell(s, inv.issuer, "§e" + inv.payerName + " a signé la facture mais le paiement a échoué "
                        + "(fonds insuffisants). Prélèvement automatique en attente.");
                return null;
            }
            case CANCELED: return "Entreprise indisponible.";
            default:   // reste AWAITING_SIGNATURE
                if (r == PayResult.NO_ACCOUNT) return "Le compte de l'entreprise est introuvable.";   // le client, lui, a un compte
                return r.ok() ? "Paiement impossible." : r.message();
        }
    }

    /** Le client refuse une facture à signer. Message d'erreur, ou null. */
    public static String refuse(ServerPlayer payer, int invoiceId) {
        MinecraftServer s = payer.server;
        EntrepriseData d = EntrepriseData.get(s);
        Invoice inv = d.invoice(invoiceId);
        if (inv == null || !inv.payer.equals(payer.getUUID())) return NOT_FOUND;
        if (inv.status != Status.AWAITING_SIGNATURE || COLLECTING.contains(inv.id)) return "Cette facture n'est plus à signer.";
        d.setInvoiceStatus(inv, Status.REFUSED);
        EntrepriseService.tell(s, inv.issuer, "§e" + inv.payerName + " a refusé la facture de "
                + EntrepriseService.money(inv.cents) + " (" + inv.description + ").");
        return null;
    }

    /** Annulation par un gérant de l'entreprise émettrice ou un OP. Message d'erreur, ou null. */
    public static String cancel(ServerPlayer actor, int invoiceId) {
        MinecraftServer s = actor.server;
        EntrepriseData d = EntrepriseData.get(s);
        Invoice inv = d.invoice(invoiceId);
        if (inv == null) return NOT_FOUND;
        Company c = d.get(inv.companyId);
        if (!CompanyAccounts.canBank(c, actor.getUUID()) && !actor.hasPermissions(2)) return NOT_FOUND;
        if (!inv.status.open() || COLLECTING.contains(inv.id)) return "Cette facture ne peut plus être annulée.";
        d.setInvoiceStatus(inv, Status.CANCELED);
        EntrepriseService.tell(s, inv.payer, "§eLa facture de " + EntrepriseService.money(inv.cents)
                + (c == null ? "" : " de « " + c.name + " »") + " (" + inv.description + ") a été annulée.");
        return null;
    }

    // ------------------------------------------------------------------ prélèvement
    /**
     * Seule tentative de prélèvement (signature, relance, connexion). Ne fait rien (null) si la facture n'a pas le statut
     * {@code expected} ou si un prélèvement est déjà en cours. Entreprise disparue ou non ACTIVE : facture annulée
     * (CANCELED), aucun mouvement. Sinon un seul {@code transfer} du montant entier ; le statut change dès le retour de la
     * banque, avant tout message : OK → PAID ; fonds insuffisants ou pas de compte du client → FAILED ; autre échec →
     * statut inchangé. {@code lastAttempt} est mis à jour à chaque appel à la banque.
     */
    private static PayResult collect(MinecraftServer s, EntrepriseData d, Invoice inv, Status expected) {
        if (inv.status != expected || !expected.open() || COLLECTING.contains(inv.id)) return null;
        Company c = d.get(inv.companyId);
        if (c == null || c.status != EntrepriseData.ACTIVE) {
            d.setInvoiceStatus(inv, Status.CANCELED);
            return PayResult.UNAVAILABLE;
        }
        if (inv.cents <= 0 || inv.cents > MAX_CENTS) {   // jamais créé ainsi ; garde-fou contre une sauvegarde altérée
            d.setInvoiceStatus(inv, Status.CANCELED);
            return PayResult.INVALID_AMOUNT;
        }
        BankService bank = MineNorth.bank();
        PayResult r;
        COLLECTING.add(inv.id);
        try {
            r = bank.transfer(s, inv.payer, c.accountId, inv.cents, BankTx.INCOME, "Facture : " + inv.description,
                    MineNorth.displayName(s, inv.payer));
        } catch (RuntimeException e) {
            LOGGER.warn("Facture {} : erreur de la banque pendant le prélèvement.", inv.id, e);
            r = PayResult.UNAVAILABLE;
        } finally {
            COLLECTING.remove(inv.id);
        }
        if (r == null) r = PayResult.UNAVAILABLE;
        d.setInvoiceAttempt(inv, System.currentTimeMillis());
        if (r.ok()) {
            d.setInvoiceStatus(inv, Status.PAID);
        } else if (r == PayResult.INSUFFICIENT_FUNDS || (r == PayResult.NO_ACCOUNT && !hasAccountSafe(bank, s, inv.payer))) {
            if (inv.status != Status.FAILED) d.setInvoiceStatus(inv, Status.FAILED);
        }
        return r;
    }

    private static boolean hasAccountSafe(BankService bank, MinecraftServer s, UUID id) {
        try { return bank.hasAccount(s, id); } catch (RuntimeException e) { return false; }
    }

    /** Relance d'une facture FAILED (tick et connexion) : messages seulement en cas de succès. */
    private static void retry(MinecraftServer s, EntrepriseData d, Invoice inv) {
        Company c = d.get(inv.companyId);
        String companyName = c == null ? "" : c.name;
        PayResult r = collect(s, d, inv, Status.FAILED);
        if (r == null) return;
        if (inv.status == Status.PAID) paidMessages(s, inv, companyName);
        else if (inv.status == Status.CANCELED) closedMessages(s, inv, "l'entreprise n'est plus active");
    }

    private static void paidMessages(MinecraftServer s, Invoice inv, String companyName) {
        String amount = EntrepriseService.money(inv.cents);
        EntrepriseService.tell(s, inv.payer, "§aFacture payée : " + amount + " à " + companyName + ".");
        EntrepriseService.tell(s, inv.issuer, "§a" + inv.payerName + " a payé la facture de " + amount + " (" + inv.description + ").");
    }

    private static void closedMessages(MinecraftServer s, Invoice inv, String why) {
        String text = "§eFacture de " + EntrepriseService.money(inv.cents) + " (" + inv.description + ") annulée : " + why + ".";
        EntrepriseService.tell(s, inv.payer, text);
        EntrepriseService.tell(s, inv.issuer, text);
    }

    private static long signatureDelayMs() { return EntrepriseConfig.get().facture_signature_secondes * 1000L; }

    // ------------------------------------------------------------------ tâches de fond
    /** Appelé une fois par seconde : expirations, relances, annulations. */
    public static void tick(MinecraftServer s) {
        EntrepriseData d = EntrepriseData.get(s);
        EntrepriseConfig cfg = EntrepriseConfig.get();
        long now = System.currentTimeMillis();
        long retryMs = cfg.facture_relance_minutes * 60_000L;
        long expireMs = cfg.facture_expiration_jours * 86_400_000L;
        for (Invoice inv : d.openInvoices()) {
            try {
                if (!inv.status.open() || COLLECTING.contains(inv.id)) continue;
                Company c = d.get(inv.companyId);
                if (c == null || c.status != EntrepriseData.ACTIVE) {
                    d.setInvoiceStatus(inv, Status.CANCELED);
                    closedMessages(s, inv, "l'entreprise n'est plus active");
                } else if (inv.status == Status.AWAITING_SIGNATURE) {
                    if (now - inv.created >= signatureDelayMs()) {
                        d.setInvoiceStatus(inv, Status.EXPIRED);
                        EntrepriseService.tell(s, inv.issuer, "§eLa facture de " + EntrepriseService.money(inv.cents)
                                + " adressée à " + inv.payerName + " a expiré sans signature.");
                    }
                } else if (expireMs > 0 && now - inv.created >= expireMs) {
                    d.setInvoiceStatus(inv, Status.CANCELED);
                    EntrepriseService.tell(s, inv.issuer, "§eLa facture de " + EntrepriseService.money(inv.cents) + " de "
                            + inv.payerName + " est annulée : impayée depuis " + cfg.facture_expiration_jours + " jour(s).");
                } else if (now - inv.lastAttempt >= retryMs) {
                    retry(s, d, inv);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Facture {} : erreur pendant le traitement périodique.", inv.id, e);
            }
        }
    }

    /** Connexion : une tentative immédiate pour chaque facture signée mais impayée du joueur. */
    public static void onLogin(ServerPlayer p) {
        EntrepriseData d = EntrepriseData.get(p.server);
        for (Invoice inv : d.invoicesOfPayer(p.getUUID())) {
            if (inv.status == Status.FAILED) retry(p.server, d, inv);
        }
    }

    /** Déconnexion : les factures encore à signer du joueur expirent. */
    public static void onLogout(ServerPlayer p) {
        EntrepriseData d = EntrepriseData.get(p.server);
        for (Invoice inv : d.invoicesOfPayer(p.getUUID())) {
            if (inv.status != Status.AWAITING_SIGNATURE || COLLECTING.contains(inv.id)) continue;
            d.setInvoiceStatus(inv, Status.EXPIRED);
            EntrepriseService.tell(p.server, inv.issuer, "§e" + inv.payerName + " s'est déconnecté : la facture de "
                    + EntrepriseService.money(inv.cents) + " a expiré.");
        }
    }

    /** Joueur effacé : ses factures ouvertes sont annulées. Retourne true si au moins une l'a été. */
    public static boolean onWipe(MinecraftServer s, UUID payer) {
        EntrepriseData d = EntrepriseData.get(s);
        boolean any = false;
        for (Invoice inv : d.invoicesOfPayer(payer)) {
            if (!inv.status.open()) continue;
            d.setInvoiceStatus(inv, Status.CANCELED);
            EntrepriseService.tell(s, inv.issuer, "§eFacture de " + EntrepriseService.money(inv.cents) + " (" + inv.description
                    + ") annulée : le client a été supprimé.");
            any = true;
        }
        return any;
    }

    /** Entreprise dissoute, refusée ou effacée : ses factures ouvertes sont annulées (à appeler avant la fermeture du compte). */
    public static void onCompanyClosed(MinecraftServer s, int companyId) {
        EntrepriseData d = EntrepriseData.get(s);
        for (Invoice inv : d.invoicesOf(companyId)) {
            if (!inv.status.open()) continue;
            d.setInvoiceStatus(inv, Status.CANCELED);
            EntrepriseService.tell(s, inv.payer, "§eFacture de " + EntrepriseService.money(inv.cents) + " (" + inv.description
                    + ") annulée : l'entreprise a fermé.");
        }
    }
}
