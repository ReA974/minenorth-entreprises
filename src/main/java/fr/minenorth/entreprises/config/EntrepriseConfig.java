package fr.minenorth.entreprises.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuration : config/minenorth_entreprises.json
 * Rechargée avec /entreprisereload.
 */
public final class EntrepriseConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static EntrepriseConfig current = new EntrepriseConfig();

    public String _aide = "activites_bloquees / noms_bloques : mots interdits (sans tenir compte des majuscules). "
            + "Ex : [\"casino\", \"armes\"]. Les OP ne sont pas concernés. Rechargez avec /entreprisereload.";
    /** Frais payés par carte bancaire à la création (0 = gratuit). */
    public double frais_creation_euros = 5000;
    /** true : une entreprise créée par un joueur reste « en attente » jusqu'à validation par un OP. */
    public boolean validation_op = true;
    public List<String> activites_bloquees = new ArrayList<>();
    public List<String> noms_bloques = new ArrayList<>();
    /** Intervalle entre deux paies, en minutes. */
    public int salaire_intervalle_minutes = 60;
    /** Salaire maximum par paie pour un grade. */
    public double salaire_max_euros = 2000;
    public int max_employes = 20;
    public int max_grades = 6;

    public static EntrepriseConfig get() { return current; }

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("minenorth_entreprises.json"); }

    public static boolean load() {
        // Config côté serveur uniquement : le client ne crée ni ne lit aucun fichier.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER) return true;
        Path f = file();
        boolean ok = true;
        try {
            if (Files.exists(f)) {
                EntrepriseConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), EntrepriseConfig.class);
                if (c != null) current = c;
            }
        } catch (Exception e) {
            ok = false;
        }
        EntrepriseConfig c = current;
        if (c.activites_bloquees == null) c.activites_bloquees = new ArrayList<>();
        if (c.noms_bloques == null) c.noms_bloques = new ArrayList<>();
        c.salaire_intervalle_minutes = Math.max(1, c.salaire_intervalle_minutes);
        c.max_employes = Math.max(1, c.max_employes);
        c.max_grades = Math.max(1, Math.min(6, c.max_grades));
        // On ne réécrit pas un fichier illisible : l'admin peut ainsi corriger sa faute de frappe.
        if (ok) {
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return ok;
    }

    public long feeCents() { return Math.max(0, Math.round(frais_creation_euros * 100.0)); }
    public long maxSalaryCents() { return Math.max(0, Math.round(salaire_max_euros * 100.0)); }

    private static boolean blocked(List<String> words, String value) {
        String v = value.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (w != null && !w.isBlank() && v.contains(w.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
    public boolean activityBlocked(String activity) { return blocked(activites_bloquees, activity); }
    public boolean nameBlocked(String name) { return blocked(noms_bloques, name); }
}
