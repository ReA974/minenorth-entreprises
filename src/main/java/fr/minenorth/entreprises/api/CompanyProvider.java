package fr.minenorth.entreprises.api;

import fr.minenorth.api.CompanyService;
import fr.minenorth.entreprises.CompanyAccounts;
import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.data.EntrepriseData.Company;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Fournit les entreprises aux autres mods via MineNorth API (boutiques et concessions réservées à une entreprise). */
public final class CompanyProvider implements CompanyService {
    @Override
    public List<String> activeCompanies(MinecraftServer s) {
        return EntrepriseApi.listCompanies(s);
    }

    @Override
    public String name(MinecraftServer s, int id) {
        return EntrepriseApi.companyName(s, id);
    }

    @Override
    public int idOf(MinecraftServer s, String name) {
        if (name == null) return 0;
        String wanted = name.trim();
        for (Company c : EntrepriseData.get(s).all()) {
            if (c.status == EntrepriseData.ACTIVE && c.name.equalsIgnoreCase(wanted)) return c.id;
        }
        return 0;
    }

    @Override
    public boolean isMember(ServerPlayer p, int id, boolean directionOnly) {
        Company c = EntrepriseData.get(p.server).get(id);
        if (c == null || c.status != EntrepriseData.ACTIVE) return false;
        if (directionOnly) return CompanyAccounts.canBank(c, p.getUUID());   // patron + grades avec droit de gestion
        return p.getUUID().equals(c.owner) || c.members.containsKey(p.getUUID());
    }
}
