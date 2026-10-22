package fr.minenorth.entreprises.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Entreprises du serveur, sauvegardées avec le monde (data/minenorth_entreprises.dat). */
public class EntrepriseData extends SavedData {
    private static final String NAME = "minenorth_entreprises";
    public static final int PENDING = 0, ACTIVE = 1;

    /** Droits qu'un patron accorde à chaque grade (masque de bits). Le patron a toujours tous les droits. */
    public static final int P_RECRUIT = 1, P_BANK = 2, P_INVOICES = 4, P_STOCK = 8, P_OPEN = 16, P_ALL = 31, P_ALL_COUNT = 5;
    /** Libellés affichés dans l'interface, dans l'ordre des bits. */
    public static final String[] P_LABELS = {"Embaucher / licencier", "Compte de l'entreprise", "Factures", "Stock et distributeurs", "Ouvrir / fermer"};

    public static final class Grade {
        public String name; public long salary; public int perms;
        public Grade(String name, long salary, int perms) { this.name = name; this.salary = salary; this.perms = perms & P_ALL; }
        public boolean has(int perm) { return (perms & perm) != 0; }
        /** Grade « de gestion » : au moins un droit (ancien réglage « manage »). */
        public boolean manage() { return perms != 0; }
    }
    public static final class Member {
        public final UUID id; public String name; public int grade;
        public Member(UUID id, String name, int grade) { this.id = id; this.name = name; this.grade = grade; }
    }
    public static final class Company {
        public int id; public String name = "", activity = ""; public UUID owner; public String ownerName = "";
        public int status = PENDING; public long feePaid; public long created;
        /** Le PDG a demandé la dissolution : un OP doit l'accepter ou la refuser. */
        public boolean dissolveRequested;
        /** Ouverture voulue par la direction (bouton de la tablette). Affichée sur la carte seulement si quelqu'un de l'entreprise est connecté. */
        public boolean open;
        /** Identifiant du compte EuroBank de l'entreprise (jamais le patron). */
        public UUID accountId;
        /** Du plus haut (index 0) au plus bas. Le patron n'a pas de grade. */
        public final List<Grade> grades = new ArrayList<>();
        public final Map<UUID, Member> members = new LinkedHashMap<>();

        public Grade gradeOf(UUID player) {
            Member m = members.get(player);
            if (m == null || grades.isEmpty()) return null;
            return grades.get(Math.max(0, Math.min(grades.size() - 1, m.grade)));
        }
        /** Vrai si le patron, ou si le grade du membre a ce droit. */
        public boolean has(UUID player, int perm) {
            if (player == null) return false;
            if (player.equals(owner)) return true;
            Grade g = gradeOf(player);
            return g != null && g.has(perm);
        }
        public boolean isIn(UUID player) { return player != null && (player.equals(owner) || members.containsKey(player)); }
    }

    /** Facture d'une entreprise à un client. Seuls {@code status} et {@code lastAttempt} changent après la création. */
    public static final class Invoice {
        public enum Status {
            AWAITING_SIGNATURE, PAID, FAILED, REFUSED, CANCELED, EXPIRED;
            /** Facture encore ouverte : à signer, ou signée et en attente de prélèvement. */
            public boolean open() { return this == AWAITING_SIGNATURE || this == FAILED; }
        }
        public final int id, companyId;
        public final UUID issuer, payer;
        /** Noms RP au moment de la création. */
        public final String issuerName, payerName;
        public final long cents;
        public final String description;
        /** Date de création (epoch ms). */
        public final long created;
        public Status status;
        /** Dernière tentative de prélèvement (epoch ms), 0 si aucune. */
        public long lastAttempt;

        public Invoice(int id, int companyId, UUID issuer, String issuerName, UUID payer, String payerName,
                       long cents, String description, long created, Status status, long lastAttempt) {
            this.id = id; this.companyId = companyId; this.issuer = issuer; this.issuerName = issuerName;
            this.payer = payer; this.payerName = payerName; this.cents = cents; this.description = description;
            this.created = created; this.status = status; this.lastAttempt = lastAttempt;
        }
    }

    /** Factures closes (payées, refusées, annulées, expirées) gardées par entreprise. */
    public static final int CLOSED_INVOICES_KEPT = 100;

    private final Map<Integer, Company> companies = new LinkedHashMap<>();
    /** Factures par id (ordre croissant de création). */
    private final Map<Integer, Invoice> invoices = new LinkedHashMap<>();
    /** Invitations en attente : invité -> (id entreprise -> nom de celui qui invite). Non sauvegardé. */
    private final Map<UUID, Map<Integer, String>> invites = new LinkedHashMap<>();
    /** Entreprise « active » de chaque joueur (celle que ses menus et ses outils utilisent) quand il appartient à plusieurs. */
    private final Map<UUID, Integer> active = new LinkedHashMap<>();
    private int nextId = 1;
    private int nextInvoiceId = 1;

    public static EntrepriseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EntrepriseData::load, EntrepriseData::new, NAME);
    }

    public static EntrepriseData load(CompoundTag tag) {
        EntrepriseData d = new EntrepriseData();
        d.nextId = Math.max(1, tag.getInt("nextId"));
        ListTag list = tag.getList("companies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Company c = new Company();
            c.id = t.getInt("id"); c.name = t.getString("name"); c.activity = t.getString("activity");
            if (t.hasUUID("owner")) c.owner = t.getUUID("owner");
            c.ownerName = t.getString("ownerName"); c.status = t.getInt("status");
            c.feePaid = t.getLong("feePaid"); c.created = t.getLong("created");
            c.dissolveRequested = t.getBoolean("dissolveRequested");
            c.open = t.getBoolean("open");
            if (t.hasUUID("accountId")) c.accountId = t.getUUID("accountId");
            else { c.accountId = UUID.randomUUID(); d.setDirty(); }   // migration : le compte est ouvert au démarrage du serveur
            ListTag gl = t.getList("grades", Tag.TAG_COMPOUND);
            for (int j = 0; j < gl.size(); j++) {
                CompoundTag g = gl.getCompound(j);
                // migration : l'ancien réglage « manage » donne tous les droits, sinon aucun
                int perms = g.contains("perms") ? g.getInt("perms") : g.getBoolean("manage") ? P_ALL : 0;
                c.grades.add(new Grade(g.getString("name"), g.getLong("salary"), perms));
            }
            ListTag ml = t.getList("members", Tag.TAG_COMPOUND);
            for (int j = 0; j < ml.size(); j++) {
                CompoundTag m = ml.getCompound(j);
                UUID id = m.getUUID("id");
                c.members.put(id, new Member(id, m.getString("name"), m.getInt("grade")));
            }
            if (c.owner != null) { d.companies.put(c.id, c); d.nextId = Math.max(d.nextId, c.id + 1); }
        }
        ListTag al = tag.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) {
            CompoundTag t = al.getCompound(i);
            if (t.hasUUID("p")) d.active.put(t.getUUID("p"), t.getInt("c"));
        }
        // Factures : absentes des anciennes sauvegardes (liste vide).
        d.nextInvoiceId = Math.max(1, tag.getInt("nextInvoiceId"));
        ListTag il = tag.getList("invoices", Tag.TAG_COMPOUND);
        for (int i = 0; i < il.size(); i++) {
            CompoundTag t = il.getCompound(i);
            if (!t.hasUUID("issuer") || !t.hasUUID("payer")) continue;
            Invoice.Status st;
            try { st = Invoice.Status.valueOf(t.getString("status")); }
            catch (IllegalArgumentException e) { st = Invoice.Status.CANCELED; }   // statut inconnu : jamais prélevé
            Invoice inv = new Invoice(t.getInt("id"), t.getInt("companyId"), t.getUUID("issuer"), t.getString("issuerName"),
                    t.getUUID("payer"), t.getString("payerName"), t.getLong("cents"), t.getString("description"),
                    t.getLong("created"), st, t.getLong("lastAttempt"));
            d.invoices.put(inv.id, inv);
            d.nextInvoiceId = Math.max(d.nextInvoiceId, inv.id + 1);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        for (Company c : companies.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id); t.putString("name", c.name); t.putString("activity", c.activity);
            t.putUUID("owner", c.owner); t.putString("ownerName", c.ownerName); t.putInt("status", c.status);
            t.putLong("feePaid", c.feePaid); t.putLong("created", c.created);
            t.putBoolean("dissolveRequested", c.dissolveRequested);
            t.putBoolean("open", c.open);
            t.putUUID("accountId", c.accountId);
            ListTag gl = new ListTag();
            for (Grade g : c.grades) {
                CompoundTag gt = new CompoundTag();
                gt.putString("name", g.name); gt.putLong("salary", g.salary); gt.putInt("perms", g.perms); gt.putBoolean("manage", g.manage());
                gl.add(gt);
            }
            t.put("grades", gl);
            ListTag ml = new ListTag();
            for (Member m : c.members.values()) {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("id", m.id); mt.putString("name", m.name); mt.putInt("grade", m.grade);
                ml.add(mt);
            }
            t.put("members", ml);
            list.add(t);
        }
        tag.put("companies", list);
        ListTag al = new ListTag();
        for (Map.Entry<UUID, Integer> e : active.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("p", e.getKey()); t.putInt("c", e.getValue());
            al.add(t);
        }
        tag.put("active", al);
        tag.putInt("nextInvoiceId", nextInvoiceId);
        ListTag il = new ListTag();
        for (Invoice inv : invoices.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", inv.id); t.putInt("companyId", inv.companyId);
            t.putUUID("issuer", inv.issuer); t.putString("issuerName", inv.issuerName);
            t.putUUID("payer", inv.payer); t.putString("payerName", inv.payerName);
            t.putLong("cents", inv.cents); t.putString("description", inv.description);
            t.putLong("created", inv.created); t.putString("status", inv.status.name());
            t.putLong("lastAttempt", inv.lastAttempt);
            il.add(t);
        }
        tag.put("invoices", il);
        return tag;
    }

    // ------------------------------------------------------------------ factures
    public Invoice newInvoice(int companyId, UUID issuer, String issuerName, UUID payer, String payerName,
                              long cents, String description, long created) {
        Invoice inv = new Invoice(nextInvoiceId++, companyId, issuer, issuerName, payer, payerName, cents, description,
                created, Invoice.Status.AWAITING_SIGNATURE, 0);
        invoices.put(inv.id, inv);
        setDirty();
        return inv;
    }

    public Invoice invoice(int id) { return invoices.get(id); }

    /** Factures d'une entreprise, de la plus récente à la plus ancienne. */
    public List<Invoice> invoicesOf(int companyId) {
        List<Invoice> out = new ArrayList<>();
        for (Invoice inv : invoices.values()) if (inv.companyId == companyId) out.add(inv);
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return out;
    }

    /** Factures adressées à un client, de la plus récente à la plus ancienne. */
    public List<Invoice> invoicesOfPayer(UUID payer) {
        List<Invoice> out = new ArrayList<>();
        for (Invoice inv : invoices.values()) if (inv.payer.equals(payer)) out.add(inv);
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return out;
    }

    /** Factures ouvertes (à signer ou en attente de prélèvement), copie sûre à parcourir. */
    public List<Invoice> openInvoices() {
        List<Invoice> out = new ArrayList<>();
        for (Invoice inv : invoices.values()) if (inv.status.open()) out.add(inv);
        return out;
    }

    /** Change le statut, marque la sauvegarde et, si la facture est close, purge les plus anciennes closes de l'entreprise. */
    public void setInvoiceStatus(Invoice inv, Invoice.Status status) {
        inv.status = status;
        setDirty();
        if (!status.open()) pruneClosed(inv.companyId);
    }

    public void setInvoiceAttempt(Invoice inv, long when) {
        inv.lastAttempt = when;
        setDirty();
    }

    /** Supprime toutes les factures de l'entreprise (ouvertes ou closes) : à appeler après leur annulation. */
    public void removeInvoicesOf(int companyId) {
        if (invoices.values().removeIf(inv -> inv.companyId == companyId)) setDirty();
    }

    private void pruneClosed(int companyId) {
        List<Invoice> closed = new ArrayList<>();
        for (Invoice inv : invoicesOf(companyId)) if (!inv.status.open()) closed.add(inv);   // plus récentes d'abord
        for (int i = CLOSED_INVOICES_KEPT; i < closed.size(); i++) invoices.remove(closed.get(i).id);
    }

    public Collection<Company> all() { return companies.values(); }
    public Company get(int id) { return companies.get(id); }

    /** Entreprises dont le joueur est patron ou employé, dans l'ordre de création. */
    public List<Company> companiesOf(UUID player) {
        List<Company> out = new ArrayList<>();
        for (Company c : companies.values()) if (c.isIn(player)) out.add(c);
        return out;
    }

    /** Entreprise active du joueur (la dernière choisie, sinon la première), ou null s'il n'en a aucune. */
    public Company companyOf(UUID player) {
        Integer id = active.get(player);
        Company c = id == null ? null : companies.get(id);
        if (c != null && c.isIn(player)) return c;
        for (Company o : companies.values()) if (o.isIn(player)) return o;
        return null;
    }

    /** Choisit l'entreprise active du joueur ; faux s'il n'en fait pas partie. */
    public boolean setActive(UUID player, int companyId) {
        Company c = companies.get(companyId);
        if (c == null || !c.isIn(player)) return false;
        if (!Integer.valueOf(companyId).equals(active.put(player, companyId))) setDirty();
        return true;
    }
    public Company byName(String name) {
        for (Company c : companies.values()) if (c.name.equalsIgnoreCase(name.trim())) return c;
        return null;
    }

    public Company create(String name, String activity, UUID owner, String ownerName, int status, long feePaid) {
        Company c = new Company();
        c.id = nextId++; c.name = name; c.activity = activity; c.owner = owner; c.ownerName = ownerName;
        c.accountId = UUID.randomUUID();
        c.status = status; c.feePaid = feePaid; c.created = System.currentTimeMillis();
        c.grades.add(new Grade("Co-gérant", 0, P_ALL));
        c.grades.add(new Grade("Employé", 0, 0));
        c.grades.add(new Grade("Stagiaire", 0, 0));
        companies.put(c.id, c);
        invites.remove(owner);
        setDirty();
        return c;
    }

    public void remove(int id) {
        companies.remove(id);
        active.values().removeIf(v -> v == id);
        for (Map<Integer, String> m : invites.values()) m.remove(id);
        setDirty();
    }

    public void invite(UUID target, int companyId, String by) { invites.computeIfAbsent(target, k -> new LinkedHashMap<>()).put(companyId, by); }
    public Map<Integer, String> invitesOf(UUID target) { return invites.getOrDefault(target, Map.of()); }
    public boolean consumeInvite(UUID target, int companyId) {
        Map<Integer, String> m = invites.get(target);
        return m != null && m.remove(companyId) != null;
    }
    public void clearInvites(UUID target) { invites.remove(target); }
}
