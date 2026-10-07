package fr.minenorth.entreprises.api;

import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.data.EntrepriseData.Company;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * API pour les autres mods MineNorth. Thread serveur uniquement.
 * Le mod Portes appelle ces méthodes par réflexion : ne pas changer leurs noms ni leurs paramètres.
 */
public final class EntrepriseApi {
    private EntrepriseApi() {}

    private static Company active(MinecraftServer s, int id) {
        Company c = EntrepriseData.get(s).get(id);
        return c == null || c.status != EntrepriseData.ACTIVE ? null : c;
    }

    /** Entreprises actives, au format "id|nom". */
    public static List<String> listCompanies(MinecraftServer s) {
        List<String> out = new ArrayList<>();
        for (Company c : EntrepriseData.get(s).all()) if (c.status == EntrepriseData.ACTIVE) out.add(c.id + "|" + c.name);
        return out;
    }

    /** Nom de l'entreprise, ou "" si elle n'existe plus. */
    public static String companyName(MinecraftServer s, int id) {
        Company c = EntrepriseData.get(s).get(id);
        return c == null ? "" : c.name;
    }

    /** Noms des grades, du plus haut (index 0) au plus bas. */
    public static List<String> gradeNames(MinecraftServer s, int id) {
        List<String> out = new ArrayList<>();
        Company c = EntrepriseData.get(s).get(id);
        if (c != null) for (EntrepriseData.Grade g : c.grades) out.add(g.name);
        return out;
    }

    public static boolean isOwner(ServerPlayer p, int id) {
        Company c = active(p.server, id);
        return c != null && p.getUUID().equals(c.owner);
    }

    /**
     * Vrai si le joueur est le patron, ou un employé dont le grade est au moins minGrade
     * (index 0 = grade le plus haut ; -1 = patron uniquement ; 99 = tous les employés).
     */
    public static boolean canOpen(ServerPlayer p, int id, int minGrade) {
        Company c = active(p.server, id);
        if (c == null) return false;
        if (p.getUUID().equals(c.owner)) return true;
        EntrepriseData.Member m = c.members.get(p.getUUID());
        if (m == null || c.grades.isEmpty()) return false;
        return Math.max(0, Math.min(c.grades.size() - 1, m.grade)) <= minGrade;
    }

    /** Nom de l'entreprise active du joueur (patron ou employé), ou "" s'il n'en a pas. */
    public static String companyName(ServerPlayer p) {
        Company c = EntrepriseData.get(p.server).companyOf(p.getUUID());
        return c == null || c.status != EntrepriseData.ACTIVE ? "" : c.name;
    }
}
