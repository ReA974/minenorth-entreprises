package fr.minenorth.entreprises.workshop;

import fr.minenorth.entreprises.CompanyAccounts;
import fr.minenorth.entreprises.MineNorthEntreprises;
import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.data.EntrepriseData.Company;
import fr.minenorth.entreprises.workshop.WorkshopPackets.Row;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Règles serveur de la cabine de peinture. Le service est rendu par le PERSONNEL de l'entreprise (patron, gérants, employés)
 * sur le véhicule d'un client ; le client est facturé par le système de factures de l'entreprise : aucun paiement ici.
 */
@Mod.EventBusSubscriber(modid = MineNorthEntreprises.MOD_ID)
public final class WorkshopService {
    private WorkshopService() {}

    /** Rayon de recherche du véhicule autour du poste, et distance maximale du joueur au poste (blocs). */
    private static final double VEHICLE_RADIUS = 6.0, USE_DISTANCE = 8.0;

    /** Entreprise active dont le joueur est patron ou gérant (celle qui peut poser le poste), sinon null. */
    public static Company companyFor(ServerPlayer p) {
        Company c = EntrepriseData.get(p.server).companyOf(p.getUUID());
        return c != null && c.status == EntrepriseData.ACTIVE && CompanyAccounts.canStock(c, p.getUUID()) ? c : null;
    }

    private static Company company(MinecraftServer s, WorkshopBE be) {
        Company c = be.companyId < 0 ? null : EntrepriseData.get(s).get(be.companyId);
        return c != null && c.status == EntrepriseData.ACTIVE ? c : null;
    }

    /** Personnel de l'entreprise propriétaire (patron, gérants, employés) ; les OP aussi. */
    private static boolean isStaff(ServerPlayer p, Company c) {
        return p.hasPermissions(2) || c.isIn(p.getUUID());
    }

    private static boolean canBreak(ServerPlayer p, WorkshopBE be) {
        if (p.hasPermissions(2)) return true;
        Company c = be.companyId < 0 ? null : EntrepriseData.get(p.server).get(be.companyId);
        return c == null || CompanyAccounts.canStock(c, p.getUUID());
    }

    private static Entity vehicleNear(ServerPlayer p, WorkshopBE be) {
        return MtsAccess.nearest((ServerLevel) p.level(), Vec3.atCenterOf(be.getBlockPos()), VEHICLE_RADIUS);
    }

    // ------------------------------------------------------------------ écran

    public static void open(ServerPlayer p, WorkshopBE be) {
        Company c = company(p.server, be);
        if (c == null) { p.displayClientMessage(Component.literal("§cPoste hors service (entreprise inactive)."), true); return; }
        if (!isStaff(p, c)) {
            p.displayClientMessage(Component.literal("§cRéservé au personnel de " + c.name + "."), true);
            return;
        }
        send(p, be, "", true);
    }

    private static void send(ServerPlayer p, WorkshopBE be, String message, boolean ok) {
        Company c = company(p.server, be);
        List<Row> rows = new ArrayList<>();
        String vehicle = "";
        if (c == null) { message = "Poste hors service (entreprise inactive)."; ok = false; }
        else {
            Entity v = vehicleNear(p, be);
            if (v != null) {
                vehicle = MtsAccess.name(v);
                String cur = MtsAccess.currentSubName(v);
                for (MtsAccess.Variant var : MtsAccess.variants(v)) rows.add(new Row(var.name(), var.subName().equals(cur)));
            }
        }
        WorkshopPackets.send(p, new WorkshopPackets.State(be.getBlockPos(), c == null ? "" : c.name, vehicle, message, ok, rows));
    }

    // ------------------------------------------------------------------ actions

    public static void handle(ServerPlayer p, WorkshopPackets.Action a) {
        if (!p.level().isLoaded(a.pos()) || p.blockPosition().distSqr(a.pos()) > USE_DISTANCE * USE_DISTANCE) return;
        if (!(p.level().getBlockEntity(a.pos()) instanceof WorkshopBE be)) return;
        Company c = company(p.server, be);
        if (c == null || !isStaff(p, c)) return;
        if (a.action() != WorkshopPackets.ACT_APPLY) { send(p, be, "", true); return; }

        if (p.getVehicle() != null) { send(p, be, "Descendez du véhicule d'abord.", false); return; }
        Entity v = vehicleNear(p, be);
        if (v == null) { send(p, be, "Aucun véhicule à proximité du poste.", false); return; }
        List<MtsAccess.Variant> variants = MtsAccess.variants(v);
        if (a.index() < 0 || a.index() >= variants.size()) { send(p, be, "Peinture inconnue.", false); return; }
        MtsAccess.Variant target = variants.get(a.index());
        if (target.subName().equals(MtsAccess.currentSubName(v))) { send(p, be, "Le véhicule a déjà cette peinture.", false); return; }
        if (!MtsAccess.repaint((ServerLevel) p.level(), v, target.subName())) {
            send(p, be, "La peinture a échoué : rien n'a été modifié.", false);
            return;
        }
        send(p, be, "Peinture « " + target.name() + " » appliquée. Pensez à facturer le client.", true);
    }

    // ------------------------------------------------------------------ casse

    /** Casser le poste : réservé aux patron / gérants de l'entreprise propriétaire et aux OP. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getState().getBlock() instanceof WorkshopBlock) || !(e.getPlayer() instanceof ServerPlayer sp)) return;
        if (sp.level().getBlockEntity(e.getPos()) instanceof WorkshopBE be && !canBreak(sp, be)) {
            e.setCanceled(true);
            sp.displayClientMessage(Component.literal("§cCe poste appartient à une entreprise."), true);
        }
    }
}
