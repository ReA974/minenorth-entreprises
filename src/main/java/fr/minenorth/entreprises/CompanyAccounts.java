package fr.minenorth.entreprises;

import fr.minenorth.api.BankService;
import fr.minenorth.api.BankTx;
import com.mojang.logging.LogUtils;
import fr.minenorth.api.MineNorth;
import fr.minenorth.api.PayResult;
import org.slf4j.Logger;
import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.data.EntrepriseData.Company;
import fr.minenorth.entreprises.data.EntrepriseData.Grade;
import fr.minenorth.entreprises.data.EntrepriseData.Member;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Compte EuroBank d'une entreprise : ouverture, renommage, signataires, fermeture. */
@Mod.EventBusSubscriber
public final class CompanyAccounts {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CompanyAccounts() {}

    /** Accès aux fonctions bancaires de l'entreprise : le patron, ou un membre dont le grade a le droit « manage ». */
    public static boolean canBank(Company c, UUID id) {
        if (c == null || id == null) return false;
        if (id.equals(c.owner)) return true;
        Grade g = c.gradeOf(id);
        return g != null && g.manage;
    }

    public static boolean open(MinecraftServer s, Company c) {
        return MineNorth.bank().openBusinessAccount(s, c.accountId, c.name);
    }

    public static void rename(MinecraftServer s, Company c) {
        MineNorth.bank().renameAccount(s, c.accountId, c.name);
    }

    /** Signataires : le patron et tous les membres dont le grade a « manage ». */
    public static void syncSigners(MinecraftServer s, Company c) {
        Set<UUID> signers = new HashSet<>();
        signers.add(c.owner);
        for (Member m : c.members.values()) if (canBank(c, m.id)) signers.add(m.id);
        MineNorth.bank().setSigners(s, c.accountId, signers);
    }

    /**
     * Ferme le compte : le solde restant va au patron (virement « Dissolution »), ou au trésor si {@code toTreasury}
     * ou si le patron ne peut pas le recevoir ; puis le compte est fermé.
     */
    public static boolean close(MinecraftServer s, Company c, boolean toTreasury) {
        BankService bank = MineNorth.bank();
        if (bank == BankService.NONE) return true;   // aucun compte n'a pu exister
        // Le solde sort par paliers : transfer et payFromAccount refusent tout montant au-dessus du plafond.
        long balance = bank.balance(s, c.accountId);
        while (balance > 0) {
            long chunk = Math.min(balance, MAX_TRANSFER);
            boolean done = false;
            if (!toTreasury && bank.hasAccount(s, c.owner)) {
                PayResult r = bank.transfer(s, c.accountId, c.owner, chunk, BankTx.WITHDRAW, "Dissolution", "Système");
                done = r.ok();
                if (!done) LOGGER.warn("Entreprise « {} » (id {}, compte {}) : virement de {} au patron refusé ({}), envoi au trésor.",
                        c.name, c.id, c.accountId, chunk, r);
            }
            if (!done) {
                PayResult r = bank.payFromAccount(s, c.accountId, chunk, toTreasury ? "entreprises:wipe" : "entreprises:dissolution", null);
                done = r.ok();
                if (!done) LOGGER.warn("Entreprise « {} » (id {}, compte {}) : envoi de {} au trésor refusé ({}), solde résiduel {}.",
                        c.name, c.id, c.accountId, chunk, r, bank.balance(s, c.accountId));
            }
            long next = bank.balance(s, c.accountId);
            if (!done || next >= balance) break;   // plus de progrès : on s'arrête, le solde reste sur le compte
            balance = next;
        }
        if (bank.closeAccount(s, c.accountId)) return true;
        LOGGER.warn("Entreprise « {} » (id {}) : fermeture du compte {} impossible, solde résiduel {} centimes.",
                c.name, c.id, c.accountId, bank.balance(s, c.accountId));
        return false;
    }

    private static final long MAX_TRANSFER = 1_000_000_000L;

    private static String check(Company c, ServerPlayer p, long cents) {
        if (!canBank(c, p.getUUID())) return "Vous n'avez pas accès au compte de l'entreprise.";
        if (c.status != EntrepriseData.ACTIVE) return "L'entreprise n'est pas encore validée.";
        if (cents <= 0 || cents > MAX_TRANSFER) return "Montant invalide.";
        return null;
    }

    /** Dépôt du compte du joueur vers le compte de l'entreprise. Retourne un message d'erreur, ou null. */
    public static String deposit(ServerPlayer p, Company c, long cents) {
        String err = check(c, p, cents);
        if (err != null) return err;
        String name = MineNorth.displayName(p);
        PayResult r = MineNorth.bank().transfer(p.server, p.getUUID(), c.accountId, cents, BankTx.DEPOSIT, "Dépôt", name);
        return r.ok() ? null : r.message();
    }

    /** Virement du compte de l'entreprise vers le compte du joueur lui-même (même pour un gérant). */
    public static String withdraw(ServerPlayer p, Company c, long cents) {
        String err = check(c, p, cents);
        if (err != null) return err;
        String name = MineNorth.displayName(p);
        PayResult r = MineNorth.bank().transfer(p.server, c.accountId, p.getUUID(), cents, BankTx.WITHDRAW, "Virement vers " + name, name);
        if (r == PayResult.NO_ACCOUNT && MineNorth.bank().hasAccount(p.server, p.getUUID())) return "Le compte de l'entreprise est introuvable.";
        return r.ok() ? null : r.message();
    }

    public static boolean giveCard(ServerPlayer p, Company c) {
        if (!canBank(c, p.getUUID()) || c.status != EntrepriseData.ACTIVE) return false;
        return MineNorth.bank().giveBusinessCard(p, c.accountId, c.name);
    }

    /** Migration et resynchronisation : chaque société a son compte ouvert et ses signataires à jour. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        if (MineNorth.bank() == BankService.NONE) return;
        MinecraftServer s = e.getServer();
        for (Company c : EntrepriseData.get(s).all()) {
            if (open(s, c)) syncSigners(s, c);
        }
    }
}
