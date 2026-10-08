package fr.minenorth.entreprises;

import fr.minenorth.api.BankService;
import fr.minenorth.api.MineNorth;
import fr.minenorth.api.PayResult;
import com.mojang.authlib.GameProfile;
import fr.minenorth.entreprises.config.EntrepriseConfig;
import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.data.EntrepriseData.Company;
import fr.minenorth.entreprises.data.EntrepriseData.Grade;
import fr.minenorth.entreprises.data.EntrepriseData.Member;
import fr.minenorth.entreprises.item.ModItems;
import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.world.item.ItemStack;
import fr.minenorth.entreprises.network.ModNetwork.ActionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Toute la logique serveur des entreprises : ouverture des menus, actions, paie. */
@Mod.EventBusSubscriber
public final class EntrepriseService {
    private EntrepriseService() {}

    /** Joueurs dont le menu a été ouvert par le PNJ (/entreprise <joueur>). Sans cela, aucune action n'est acceptée. */
    private static final Set<UUID> OPEN = new HashSet<>();
    private static long lastPay = 0;

    private record R(String msg, boolean ok) {
        static R ok(String m) { return new R(m, true); }
        static R err(String m) { return new R(m, false); }
    }

    // ------------------------------------------------------------------ ouverture
    public static void openFor(ServerPlayer p) { OPEN.add(p.getUUID()); send(p, false, "", true); }
    public static void openAdmin(ServerPlayer p) { send(p, true, "", true); }

    /** Clic droit avec la tablette : réservée au PDG d'une entreprise active. */
    public static void openTablet(ServerPlayer p) {
        Company c = EntrepriseData.get(p.server).companyOf(p.getUUID());
        if (c == null || !p.getUUID().equals(c.owner)) {
            p.displayClientMessage(Component.literal("§cSeul le PDG d'une entreprise peut utiliser cette tablette."), true);
            return;
        }
        if (c.status != EntrepriseData.ACTIVE) {
            p.displayClientMessage(Component.literal("§eVotre entreprise n'est pas encore validée."), true);
            return;
        }
        openFor(p);
    }

    private static boolean hasTablet(ServerPlayer p) {
        return p.getInventory().contains(new ItemStack(ModItems.TABLET.get()));
    }

    private static void giveTablet(MinecraftServer s, UUID owner) {
        ServerPlayer p = s.getPlayerList().getPlayer(owner);
        if (p == null || hasTablet(p)) return;
        ItemStack tablet = new ItemStack(ModItems.TABLET.get());
        if (!p.getInventory().add(tablet)) p.drop(tablet, false);
        p.sendSystemMessage(Component.literal("§bVous avez reçu votre tablette d'entreprise : clic droit pour gérer votre entreprise."));
    }

    private static ModNetwork.CompanyView view(MinecraftServer s, Company c) {
        List<ModNetwork.GradeView> grades = new ArrayList<>();
        for (Grade g : c.grades) grades.add(new ModNetwork.GradeView(g.name, g.salary, g.manage));
        List<ModNetwork.MemberView> members = new ArrayList<>();
        for (Member m : c.members.values()) {
            ServerPlayer on = s.getPlayerList().getPlayer(m.id);
            if (on != null) m.name = on.getGameProfile().getName();   // pseudo gardé comme clé
            members.add(new ModNetwork.MemberView(m.id, MineNorth.displayName(s, m.id), Math.max(0, Math.min(c.grades.size() - 1, m.grade)), on != null));
        }
        return new ModNetwork.CompanyView(c.id, c.name, c.activity, c.owner, MineNorth.displayName(s, c.owner), c.status, grades, members, c.dissolveRequested);
    }

    private static void send(ServerPlayer p, boolean admin, String msg, boolean ok) {
        EntrepriseData d = EntrepriseData.get(p.server);
        EntrepriseConfig cfg = EntrepriseConfig.get();
        List<ModNetwork.CompanyView> list = new ArrayList<>();
        List<ModNetwork.InviteView> invites = new ArrayList<>();
        Company self = d.companyOf(p.getUUID());
        if (admin) {
            for (Company c : d.all()) list.add(view(p.server, c));
        } else if (self != null) {
            list.add(view(p.server, self));
        } else {
            for (Map.Entry<Integer, String> e : d.invitesOf(p.getUUID()).entrySet()) {
                Company c = d.get(e.getKey());
                if (c != null) invites.add(new ModNetwork.InviteView(c.id, c.name, e.getValue()));
            }
        }
        ModNetwork.send(p, new ModNetwork.StatePacket(admin, msg, ok, cfg.feeCents(), cfg.validation_op,
                cfg.salaire_intervalle_minutes, cfg.max_employes, cfg.max_grades, self == null ? -1 : self.id, list, invites));
    }

    // ------------------------------------------------------------------ outils
    private static void tell(MinecraftServer s, UUID id, String text) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null) p.sendSystemMessage(Component.literal(text));
    }

    private static GameProfile resolve(MinecraftServer s, String name, boolean allowOffline) {
        if (name == null || name.isBlank()) return null;
        ServerPlayer on = s.getPlayerList().getPlayerByName(name.trim());
        if (on != null) return on.getGameProfile();
        if (!allowOffline || s.getProfileCache() == null) return null;
        return s.getProfileCache().get(name.trim()).orElse(null);
    }

    private static long euros(String text) {
        try {
            String v = text == null ? "" : text.trim().replace(',', '.').replace("€", "").trim();
            if (v.isEmpty()) return 0;
            double d = Double.parseDouble(v);
            return d < 0 ? -1 : Math.round(d * 100.0);
        } catch (NumberFormatException e) { return -1; }
    }

    private static String money(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (a / 100) + (a % 100 == 0 ? "" : "," + String.format("%02d", a % 100)) + " €";
    }

    /** Texte saisi : sans codes couleur (§) ni caractères de contrôle, espaces normalisés. */
    private static String clean(String v) { return v == null ? "" : v.replaceAll("[\\p{Cntrl}§]", " ").trim().replaceAll("\\s+", " "); }

    /** Vérifie un nom + une activité. Retourne un message d'erreur ou null. */
    private static String validate(EntrepriseData d, String name, String activity, Company except, boolean admin) {
        if (name.length() < 3 || name.length() > 32) return "Le nom doit faire entre 3 et 32 caractères.";
        if (activity.length() < 2 || activity.length() > 32) return "L'activité doit faire entre 2 et 32 caractères.";
        Company same = d.byName(name);
        if (same != null && same != except) return "Une entreprise porte déjà ce nom.";
        if (!admin) {
            EntrepriseConfig cfg = EntrepriseConfig.get();
            if (cfg.nameBlocked(name)) return "Ce nom d'entreprise n'est pas autorisé.";
            if (cfg.activityBlocked(activity)) return "Cette activité n'est pas autorisée sur le serveur.";
        }
        return null;
    }

    private static void refundFee(MinecraftServer s, Company c) {
        if (c.feePaid <= 0) return;
        MineNorth.bank().refund(s, c.owner, c.feePaid, "entreprises:creation");
        c.feePaid = 0;
    }

    private static void dissolve(MinecraftServer s, EntrepriseData d, Company c, String reason) {
        for (Member m : c.members.values()) tell(s, m.id, "§eL'entreprise « " + c.name + " » " + reason + ".");
        CompanyAccounts.close(s, c, false);   // solde rendu au patron (ou au trésor), puis compte fermé
        d.remove(c.id);
    }

    // ------------------------------------------------------------------ actions
    public static void handle(ServerPlayer p, ActionPacket k) {
        if (k.action() == ModNetwork.CLOSE) { OPEN.remove(p.getUUID()); return; }
        boolean admin = k.admin();
        if (admin ? !p.hasPermissions(2) : !OPEN.contains(p.getUUID())) return;
        EntrepriseData d = EntrepriseData.get(p.server);
        Company before = admin ? d.get(k.companyId()) : d.companyOf(p.getUUID());
        R r;
        try {
            r = act(p, k, admin);
        } catch (RuntimeException e) {
            r = R.err("Action impossible.");
        }
        // Embauche, licenciement, grade, droit « manage », patron : les signataires suivent toujours.
        Company after = admin ? d.get(k.companyId()) : d.companyOf(p.getUUID());
        if (before != null && d.get(before.id) == before) CompanyAccounts.syncSigners(p.server, before);
        if (after != null && after != before && d.get(after.id) == after) CompanyAccounts.syncSigners(p.server, after);
        if (r != null) send(p, admin, r.msg(), r.ok());
    }

    private static R act(ServerPlayer p, ActionPacket k, boolean admin) {
        MinecraftServer s = p.server;
        EntrepriseData d = EntrepriseData.get(s);
        EntrepriseConfig cfg = EntrepriseConfig.get();
        UUID me = p.getUUID();

        switch (k.action()) {
            case ModNetwork.CREATE: return admin ? adminCreate(s, d, k) : create(p, d, cfg, k);
            case ModNetwork.ACCEPT: {
                if (admin) return null;
                if (d.companyOf(me) != null) return R.err("Vous faites déjà partie d'une entreprise.");
                Company c = d.get(k.companyId());
                if (c == null || !d.consumeInvite(me, k.companyId())) return R.err("Cette invitation n'est plus valable.");
                if (c.members.size() >= cfg.max_employes) return R.err("Cette entreprise est complète.");
                c.members.put(me, new Member(me, p.getGameProfile().getName(), c.grades.size() - 1));
                d.clearInvites(me); d.setDirty();
                tell(s, c.owner, "§a" + MineNorth.displayName(p) + " a rejoint « " + c.name + " ».");
                return R.ok("Vous avez rejoint « " + c.name + " ».");
            }
            case ModNetwork.REFUSE_INVITE: {
                if (admin) return null;
                d.consumeInvite(me, k.companyId());
                return R.ok("Invitation refusée.");
            }
            default: break;
        }

        // Toutes les actions suivantes visent une entreprise existante.
        Company c = admin ? d.get(k.companyId()) : d.companyOf(me);
        if (c == null) return R.err("Entreprise introuvable.");
        boolean owner = admin || me.equals(c.owner);
        Grade myGrade = c.gradeOf(me);
        boolean manage = owner || (myGrade != null && myGrade.manage);

        switch (k.action()) {
            case ModNetwork.DISSOLVE: {
                if (!owner) return R.err("Seul le patron peut dissoudre l'entreprise.");
                // Une entreprise active ne peut être dissoute que par un OP : le PDG fait une demande.
                if (!admin && c.status == EntrepriseData.ACTIVE) return R.err("Faites une demande de dissolution : un administrateur la traitera.");
                if (c.status == EntrepriseData.PENDING) refundFee(s, c);
                if (admin) tell(s, c.owner, "§eVotre entreprise « " + c.name + " » a été dissoute par un administrateur.");
                dissolve(s, d, c, "a été dissoute");
                return R.ok(c.status == EntrepriseData.PENDING ? "Demande annulée." : "Entreprise dissoute.");
            }
            case ModNetwork.REQUEST_DISSOLVE: {
                if (admin || !me.equals(c.owner)) return R.err("Seul le PDG peut demander la dissolution.");
                if (c.status != EntrepriseData.ACTIVE) return R.err("L'entreprise n'est pas encore validée.");
                boolean ask = "1".equals(k.a());
                c.dissolveRequested = ask; d.setDirty();
                if (!ask) return R.ok("Demande de dissolution annulée.");
                for (ServerPlayer op : s.getPlayerList().getPlayers()) {
                    if (op.hasPermissions(2)) op.sendSystemMessage(Component.literal("§e[Entreprises] " + MineNorth.displayName(p)
                            + " demande la dissolution de « " + c.name + " ». /entrepriseadmin pour la traiter."));
                }
                return R.ok("Demande de dissolution envoyée aux administrateurs.");
            }
            case ModNetwork.REJECT_DISSOLVE: {
                if (!admin) return null;
                if (!c.dissolveRequested) return R.err("Aucune demande de dissolution en cours.");
                c.dissolveRequested = false; d.setDirty();
                tell(s, c.owner, "§eVotre demande de dissolution de « " + c.name + " » a été refusée.");
                return R.ok("Demande de dissolution refusée.");
            }
            case ModNetwork.GET_TABLET: {
                if (admin || !me.equals(c.owner)) return R.err("Seul le PDG peut obtenir la tablette.");
                if (c.status != EntrepriseData.ACTIVE) return R.err("L'entreprise n'est pas encore validée.");
                if (hasTablet(p)) return R.err("Vous avez déjà une tablette d'entreprise.");
                giveTablet(s, me);
                return R.ok("Tablette d'entreprise remise.");
            }
            case ModNetwork.LEAVE: {
                if (admin || me.equals(c.owner)) return R.err("Le patron ne peut pas démissionner : il doit dissoudre l'entreprise.");
                c.members.remove(me); d.setDirty();
                tell(s, c.owner, "§e" + MineNorth.displayName(p) + " a démissionné de « " + c.name + " ».");
                return R.ok("Vous avez quitté « " + c.name + " ».");
            }
            case ModNetwork.INVITE: {
                if (!manage) return R.err("Vous n'avez pas le droit d'embaucher.");
                if (!admin && c.status != EntrepriseData.ACTIVE) return R.err("L'entreprise n'est pas encore validée.");
                if (c.members.size() >= cfg.max_employes) return R.err("Nombre maximum d'employés atteint (" + cfg.max_employes + ").");
                GameProfile target = resolve(s, k.a(), admin);
                if (target == null) return R.err(admin ? "Joueur introuvable." : "Ce joueur doit être connecté.");
                if (d.companyOf(target.getId()) != null) return R.err(target.getName() + " fait déjà partie d'une entreprise.");
                if (admin) {
                    c.members.put(target.getId(), new Member(target.getId(), target.getName(), c.grades.size() - 1));
                    d.clearInvites(target.getId()); d.setDirty();
                    tell(s, target.getId(), "§aVous avez été ajouté à l'entreprise « " + c.name + " ».");
                    return R.ok(target.getName() + " ajouté à l'entreprise.");
                }
                d.invite(target.getId(), c.id, MineNorth.displayName(p));
                tell(s, target.getId(), "§b" + MineNorth.displayName(p) + " vous propose de rejoindre « " + c.name
                        + " ». Rendez-vous au guichet des entreprises pour accepter.");
                return R.ok("Invitation envoyée à " + target.getName() + ".");
            }
            case ModNetwork.FIRE: {
                if (!manage) return R.err("Vous n'avez pas le droit de licencier.");
                Member m = c.members.get(uuid(k.a()));
                if (m == null) return R.err("Employé introuvable.");
                if (!owner && (m.id.equals(me) || c.gradeOf(m.id).manage)) return R.err("Seul le patron peut licencier ce membre.");
                c.members.remove(m.id); d.setDirty();
                tell(s, m.id, "§eVous ne faites plus partie de l'entreprise « " + c.name + " ».");
                return R.ok(MineNorth.displayName(s, m.id) + " ne fait plus partie de l'entreprise.");
            }
            case ModNetwork.SET_GRADE: {
                if (!owner) return R.err("Seul le patron peut changer les grades.");
                Member m = c.members.get(uuid(k.a()));
                if (m == null) return R.err("Employé introuvable.");
                m.grade = Math.max(0, Math.min(c.grades.size() - 1, k.n())); d.setDirty();
                tell(s, m.id, "§bVotre grade dans « " + c.name + " » est maintenant : " + c.grades.get(m.grade).name + ".");
                return R.ok(MineNorth.displayName(s, m.id) + " est maintenant " + c.grades.get(m.grade).name + ".");
            }
            case ModNetwork.GRADE_SAVE: {
                if (!owner) return R.err("Seul le patron peut modifier les grades.");
                String name = clean(k.a());
                if (name.length() < 2 || name.length() > 20) return R.err("Le nom du grade doit faire entre 2 et 20 caractères.");
                long salary = euros(k.b());
                if (salary < 0) return R.err("Salaire invalide.");
                if (!admin && salary > cfg.maxSalaryCents()) return R.err("Salaire maximum : " + money(cfg.maxSalaryCents()) + ".");
                boolean mg = "1".equals(k.c());
                if (k.n() < 0) {
                    if (c.grades.size() >= cfg.max_grades) return R.err("Nombre maximum de grades atteint.");
                    c.grades.add(new Grade(name, salary, mg));
                } else {
                    if (k.n() >= c.grades.size()) return R.err("Grade introuvable.");
                    Grade g = c.grades.get(k.n());
                    g.name = name; g.salary = salary; g.manage = mg;
                }
                d.setDirty();
                return R.ok("Grade « " + name + " » enregistré.");
            }
            case ModNetwork.GRADE_DELETE: {
                if (!owner) return R.err("Seul le patron peut modifier les grades.");
                int idx = k.n();
                if (idx < 0 || idx >= c.grades.size()) return R.err("Grade introuvable.");
                if (c.grades.size() <= 1) return R.err("Il faut garder au moins un grade.");
                c.grades.remove(idx);
                // Les employés du grade supprimé passent au grade le plus bas ; les suivants remontent d'un cran.
                for (Member m : c.members.values()) {
                    if (m.grade == idx) m.grade = c.grades.size() - 1;
                    else if (m.grade > idx) m.grade--;
                }
                d.setDirty();
                return R.ok("Grade supprimé.");
            }
            case ModNetwork.VALIDATE: {
                if (!admin) return null;
                if (c.status == EntrepriseData.ACTIVE) return R.err("Cette entreprise est déjà active.");
                c.status = EntrepriseData.ACTIVE; d.setDirty();
                tell(s, c.owner, "§aVotre entreprise « " + c.name + " » a été validée !");
                giveTablet(s, c.owner);
                return R.ok("Entreprise validée.");
            }
            case ModNetwork.REFUSE: {
                if (!admin) return null;
                if (c.status != EntrepriseData.PENDING) return R.err("Seule une demande en attente peut être refusée.");
                refundFee(s, c);
                tell(s, c.owner, "§cVotre demande d'entreprise « " + c.name + " » a été refusée. Les frais vous ont été remboursés.");
                dissolve(s, d, c, "a été refusée");
                return R.ok("Demande refusée et frais remboursés.");
            }
            case ModNetwork.EDIT: {
                if (!admin) return null;
                String name = clean(k.a()), activity = clean(k.b());
                String err = validate(d, name, activity, c, true);
                if (err != null) return R.err(err);
                GameProfile boss = resolve(s, k.c(), true);
                if (boss == null) return R.err("Patron introuvable.");
                if (!boss.getId().equals(c.owner)) {
                    Company other = d.companyOf(boss.getId());
                    if (other != null && other != c) return R.err(boss.getName() + " fait déjà partie d'une autre entreprise.");
                    c.members.remove(boss.getId());
                    tell(s, c.owner, "§eVous n'êtes plus le patron de « " + c.name + " ».");
                    c.owner = boss.getId();
                    tell(s, c.owner, "§aVous êtes maintenant le patron de « " + name + " ».");
                }
                c.ownerName = boss.getName(); c.name = name; c.activity = activity; d.setDirty();
                CompanyAccounts.rename(s, c);
                return R.ok("Entreprise modifiée.");
            }
            default: return null;
        }
    }

    private static UUID uuid(String v) {
        try { return UUID.fromString(v); } catch (RuntimeException e) { return new UUID(0, 0); }
    }

    private static R create(ServerPlayer p, EntrepriseData d, EntrepriseConfig cfg, ActionPacket k) {
        if (d.companyOf(p.getUUID()) != null) return R.err("Vous faites déjà partie d'une entreprise.");
        String name = clean(k.a()), activity = clean(k.b());
        String err = validate(d, name, activity, null, false);
        if (err != null) return R.err(err);
        if (MineNorth.bank() == BankService.NONE) return R.err("Service bancaire indisponible.");
        long fee = cfg.feeCents();
        if (fee > 0) {
            PayResult pay = MineNorth.bank().charge(p, fee, "entreprises:creation");
            if (pay != PayResult.OK) return R.err("Paiement refusé : " + pay.message());
        }
        boolean pending = cfg.validation_op;
        Company c = d.create(name, activity, p.getUUID(), p.getGameProfile().getName(),
                pending ? EntrepriseData.PENDING : EntrepriseData.ACTIVE, fee);
        if (!CompanyAccounts.open(p.server, c)) {
            refundFee(p.server, c);
            d.remove(c.id);
            return R.err("Impossible d'ouvrir le compte bancaire de l'entreprise.");
        }
        CompanyAccounts.syncSigners(p.server, c);
        if (pending) {
            for (ServerPlayer op : p.server.getPlayerList().getPlayers()) {
                if (op.hasPermissions(2)) op.sendSystemMessage(Component.literal("§e[Entreprises] " + MineNorth.displayName(p)
                        + " demande la création de « " + c.name + " » (" + c.activity + "). /entrepriseadmin pour valider."));
            }
            return R.ok("Demande envoyée : un administrateur doit la valider.");
        }
        giveTablet(p.server, p.getUUID());
        return R.ok("Entreprise « " + c.name + " » créée.");
    }

    private static R adminCreate(MinecraftServer s, EntrepriseData d, ActionPacket k) {
        String name = clean(k.a()), activity = clean(k.b());
        String err = validate(d, name, activity, null, true);
        if (err != null) return R.err(err);
        GameProfile boss = resolve(s, k.c(), true);
        if (boss == null) return R.err("Patron introuvable.");
        if (d.companyOf(boss.getId()) != null) return R.err(boss.getName() + " fait déjà partie d'une entreprise.");
        if (MineNorth.bank() == BankService.NONE) return R.err("Service bancaire indisponible.");
        Company c = d.create(name, activity, boss.getId(), boss.getName(), EntrepriseData.ACTIVE, 0);
        if (!CompanyAccounts.open(s, c)) {
            d.remove(c.id);
            return R.err("Impossible d'ouvrir le compte bancaire de l'entreprise.");
        }
        CompanyAccounts.syncSigners(s, c);
        tell(s, boss.getId(), "§aUn administrateur a créé votre entreprise « " + c.name + " ».");
        giveTablet(s, boss.getId());
        return R.ok("Entreprise « " + c.name + " » créée.");
    }

    // ------------------------------------------------------------------ paie
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        long now = System.currentTimeMillis();
        if (lastPay == 0) { lastPay = now; return; }
        if (now - lastPay < EntrepriseConfig.get().salaire_intervalle_minutes * 60_000L) return;
        lastPay = now;
        payroll(e.getServer());
    }

    /** Verse les salaires aux employés connectés, prélevés sur le compte bancaire du patron. */
    private static void payroll(MinecraftServer s) {
        EntrepriseData d = EntrepriseData.get(s);
        BankService bank = MineNorth.bank();
        for (Company c : d.all()) {
            if (c.status != EntrepriseData.ACTIVE) continue;
            long paid = 0; int unpaid = 0;
            for (Member m : c.members.values()) {
                ServerPlayer emp = s.getPlayerList().getPlayer(m.id);
                Grade g = c.gradeOf(m.id);
                if (emp == null || g == null || g.salary <= 0) continue;
                if (!bank.hasAccount(s, m.id)) {
                    emp.sendSystemMessage(Component.literal("§cSalaire non versé : vous n'avez pas de compte bancaire."));
                    continue;
                }
                if (!bank.hasAccount(s, c.owner) || !bank.transfer(s, c.owner, m.id, g.salary).ok()) {
                    unpaid++;
                    emp.sendSystemMessage(Component.literal("§cSalaire non versé : le compte du patron de « " + c.name + " » est insuffisant."));
                    continue;
                }
                paid += g.salary;
                emp.sendSystemMessage(Component.literal("§aSalaire reçu de « " + c.name + " » : " + money(g.salary) + "."));
            }
            if (paid > 0) tell(s, c.owner, "§e« " + c.name + " » : " + money(paid) + " de salaires prélevés sur votre compte.");
            if (unpaid > 0) tell(s, c.owner, "§c« " + c.name + " » : " + unpaid + " salaire(s) non versé(s), solde insuffisant.");
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        OPEN.remove(e.getEntity().getUUID());
    }
}
