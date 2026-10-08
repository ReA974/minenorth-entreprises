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
        long balance = bank.balance(s, c.accountId);
        if (balance > 0) {
            boolean done = false;
            if (!toTreasury && bank.hasAccount(s, c.owner)) {
                PayResult r = bank.transfer(s, c.accountId, c.owner, balance, BankTx.WITHDRAW, "Dissolution", "Système");
                done = r.ok();
                if (!done) LOGGER.warn("Entreprise « {} » (id {}, compte {}) : virement du solde {} au patron refusé ({}), envoi au trésor.",
                        c.name, c.id, c.accountId, balance, r);
            }
            if (!done) {
                PayResult r = bank.payFromAccount(s, c.accountId, balance, toTreasury ? "entreprises:wipe" : "entreprises:dissolution", null);
                if (!r.ok()) LOGGER.warn("Entreprise « {} » (id {}, compte {}) : envoi du solde {} au trésor refusé ({}).",
                        c.name, c.id, c.accountId, balance, r);
            }
        }
        if (bank.closeAccount(s, c.accountId)) return true;
        LOGGER.warn("Entreprise « {} » (id {}) : fermeture du compte {} impossible, solde résiduel {} centimes.",
                c.name, c.id, c.accountId, bank.balance(s, c.accountId));
        return false;
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
