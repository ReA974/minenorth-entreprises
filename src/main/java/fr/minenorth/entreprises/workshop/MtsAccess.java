package fr.minenorth.entreprises.workshop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Seul point de contact de l'atelier avec Immersive Vehicles (MTS), par réflexion : aucune classe MTS n'est liée à la
 * compilation. Un véhicule est une entité {@code mts:builder_existing} dont le NBT porte tout l'état ; le champ
 * « entity » de cette entité est le véhicule MTS lui-même (classes lues dans Immersive Vehicles 24.0.0).
 * Tout est protégé : si MTS change, l'atelier répond « véhicule illisible » au lieu de planter.
 */
public final class MtsAccess {
    public static final String VEHICLE_ENTITY = "mts:builder_existing";
    /** Mêmes clés que le mod Garage : retrait voulu (l'assurance ne doit pas jouer) et photo d'origine. */
    public static final String HANDLED = "mnrp_handled", ORIGIN = "mnrp_origin";

    public record Variant(String subName, String name) {}

    private MtsAccess() {}

    // ------------------------------------------------------------------ véhicule

    public static boolean isVehicle(Entity e) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && k.toString().equals(VEHICLE_ENTITY);
    }

    /** Véhicule le plus proche de {@code pos} dans le rayon donné, ou null. */
    public static Entity nearest(ServerLevel level, Vec3 pos, double radius) {
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, new AABB(pos, pos).inflate(radius), MtsAccess::isVehicle)) {
            double d = e.position().distanceToSqr(pos);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static Object field(Object o, String name) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Object inner(Entity e) { return field(e, "entity"); }

    // ------------------------------------------------------------------ peinture

    /** Nom du modèle (sous-définition MTS), ou l'identifiant système si illisible. */
    public static String name(Entity e) {
        try {
            Object sub = field(inner(e), "subDefinition");
            if (field(sub, "name") instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}
        CompoundTag t = new CompoundTag();
        e.saveWithoutId(t);
        String sys = t.getString("systemName");
        return sys.isEmpty() ? "Véhicule" : sys;
    }

    public static String currentSubName(Entity e) {
        CompoundTag t = new CompoundTag();
        e.saveWithoutId(t);
        return t.getString("subName");
    }

    /** Peintures disponibles : sous-définitions du modèle (champ « definitions » de la définition MTS). */
    public static List<Variant> variants(Entity e) {
        List<Variant> out = new ArrayList<>();
        try {
            if (field(field(inner(e), "definition"), "definitions") instanceof Iterable<?> defs) {
                for (Object d : defs) {
                    if (!(field(d, "subName") instanceof String sub)) continue;
                    String n = field(d, "name") instanceof String s && !s.isBlank() ? s : (sub.isEmpty() ? "Standard" : sub.replace("_", " ").trim());
                    out.add(new Variant(sub, n));
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /**
     * Repeint : le véhicule est recréé à l'identique (NBT complet : pièces, carburant, dégâts, propriétaire, assurance) avec la
     * nouvelle sous-définition. La nouvelle entité est créée AVANT de retirer l'ancienne : en cas d'échec rien ne bouge.
     * Le retrait est marqué « voulu » pour que l'assurance du mod Garage ne joue pas.
     */
    public static boolean repaint(ServerLevel level, Entity old, String subName) {
        CompoundTag snap = new CompoundTag();
        old.saveWithoutId(snap);
        snap.remove("UUID");
        snap.remove("Passengers");
        CompoundTag fd = snap.contains("ForgeData", 10) ? snap.getCompound("ForgeData") : new CompoundTag();
        CompoundTag origin = fd.contains(ORIGIN, 10) ? fd.getCompound(ORIGIN).copy() : null;
        fd.remove(ORIGIN);
        fd.remove(HANDLED);
        snap.put("ForgeData", fd);
        snap.putString("subName", subName);
        Vec3 p = old.position();
        Entity fresh = create(level, snap, p.x, p.y, p.z);
        if (fresh == null) return false;
        if (origin != null) {   // la photo d'origine (assurance) suit la nouvelle peinture
            origin.putString("subName", subName);
            fresh.getPersistentData().put(ORIGIN, origin);
        }
        old.getPersistentData().putBoolean(HANDLED, true);
        old.discard();
        level.addFreshEntityWithPassengers(fresh);
        return true;
    }

    private static Entity create(ServerLevel level, CompoundTag saved, double x, double y, double z) {
        CompoundTag tag = saved.copy();
        tag.putString("id", VEHICLE_ENTITY);
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x)); pos.add(DoubleTag.valueOf(y)); pos.add(DoubleTag.valueOf(z));
        tag.put("Pos", pos);
        ListTag motion = new ListTag();
        motion.add(DoubleTag.valueOf(0)); motion.add(DoubleTag.valueOf(0)); motion.add(DoubleTag.valueOf(0));
        tag.put("Motion", motion);
        tag.putDouble("positionx", x);
        tag.putDouble("positiony", y);
        tag.putDouble("positionz", z);
        for (String k : new String[]{"motionx", "motiony", "motionz"}) if (tag.contains(k)) tag.putDouble(k, 0);
        return EntityType.loadEntityRecursive(tag, level, en -> en);
    }
}
