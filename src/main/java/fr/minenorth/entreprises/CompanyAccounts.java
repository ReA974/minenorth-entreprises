package fr.minenorth.entreprises;

import fr.minenorth.api.BankService;
import fr.minenorth.api.BankTx;
import fr.minenorth.api.MineNorth;
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
    public static void close(MinecraftServer s, Company c, boolean toTreasury) {
        BankService bank = MineNorth.bank();
        long balance = bank.balance(s, c.accountId);
        if (balance > 0) {
            boolean done = false;
            if (!toTreasury && bank.hasAccount(s, c.owner)) {
                done = bank.transfer(s, c.accountId, c.owner, balance, BankTx.WITHDRAW, "Dissolution", "Système").ok();
            }
            if (!done) {
                bank.payFromAccount(s, c.accountId, balance, toTreasury ? "entreprises:wipe" : "entreprises:dissolution", null);
            }
        }
        bank.closeAccount(s, c.accountId);
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
