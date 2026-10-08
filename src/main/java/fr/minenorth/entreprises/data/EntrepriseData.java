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

    public static final class Grade {
        public String name; public long salary; public boolean manage;
        public Grade(String name, long salary, boolean manage) { this.name = name; this.salary = salary; this.manage = manage; }
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
        public boolean isIn(UUID player) { return player != null && (player.equals(owner) || members.containsKey(player)); }
    }

    private final Map<Integer, Company> companies = new LinkedHashMap<>();
    /** Invitations en attente : invité -> (id entreprise -> nom de celui qui invite). Non sauvegardé. */
    private final Map<UUID, Map<Integer, String>> invites = new LinkedHashMap<>();
    private int nextId = 1;

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
            if (t.hasUUID("accountId")) c.accountId = t.getUUID("accountId");
            else { c.accountId = UUID.randomUUID(); d.setDirty(); }   // migration : le compte est ouvert au démarrage du serveur
            ListTag gl = t.getList("grades", Tag.TAG_COMPOUND);
            for (int j = 0; j < gl.size(); j++) {
                CompoundTag g = gl.getCompound(j);
                c.grades.add(new Grade(g.getString("name"), g.getLong("salary"), g.getBoolean("manage")));
            }
            ListTag ml = t.getList("members", Tag.TAG_COMPOUND);
            for (int j = 0; j < ml.size(); j++) {
                CompoundTag m = ml.getCompound(j);
                UUID id = m.getUUID("id");
                c.members.put(id, new Member(id, m.getString("name"), m.getInt("grade")));
            }
            if (c.owner != null) { d.companies.put(c.id, c); d.nextId = Math.max(d.nextId, c.id + 1); }
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
            t.putUUID("accountId", c.accountId);
            ListTag gl = new ListTag();
            for (Grade g : c.grades) {
                CompoundTag gt = new CompoundTag();
                gt.putString("name", g.name); gt.putLong("salary", g.salary); gt.putBoolean("manage", g.manage);
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
        return tag;
    }

    public Collection<Company> all() { return companies.values(); }
    public Company get(int id) { return companies.get(id); }

    /** Entreprise dont le joueur est patron ou employé (une seule à la fois). */
    public Company companyOf(UUID player) {
        for (Company c : companies.values()) if (c.isIn(player)) return c;
        return null;
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
        c.grades.add(new Grade("Co-gérant", 0, true));
        c.grades.add(new Grade("Employé", 0, false));
        c.grades.add(new Grade("Stagiaire", 0, false));
        companies.put(c.id, c);
        invites.remove(owner);
        setDirty();
        return c;
    }

    public void remove(int id) {
        companies.remove(id);
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
