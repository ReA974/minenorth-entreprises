package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.item.RestaurantMenuItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Point d'entrée client de la carte de restaurant : thèmes, ouverture des écrans, utilitaires partagés. */
public final class RestaurantMenuClient {
    private RestaurantMenuClient() {}

    /** Thèmes de papier : {fond, bordure, encre, accent}. */
    public static final int[][] THEMES = {
            {0xFFF4E8CC, 0xFFB89B63, 0xFF3A2E1E, 0xFF9C2F2F},   // crème
            {0xFF2A2A2E, 0xFF111114, 0xFFEDEDED, 0xFFE0B040},   // bistrot
            {0xFFFBE4EA, 0xFFD58AA0, 0xFF4A2432, 0xFFC03060},   // rose
            {0xFFE3EEF9, 0xFF6C93C4, 0xFF1E2E48, 0xFF2060B0}    // bleu
    };
    public static final String[] THEME_NAMES = {"Crème", "Bistrot", "Rose", "Bleu"};

    public static void open(boolean mainHand, boolean edit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack st = mc.player.getItemInHand(mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(st.getItem() instanceof RestaurantMenuItem)) return;
        RestaurantMenuItem.Data d = RestaurantMenuItem.read(st);
        mc.setScreen(edit ? new RestaurantMenuEditScreen(mainHand, d) : new RestaurantMenuScreen(d));
    }

    /** Objet de l'identifiant donné, ou ItemStack.EMPTY. */
    public static ItemStack stack(String id) {
        ResourceLocation rl = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id.trim());
        var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null || !ForgeRegistries.ITEMS.containsKey(rl) ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Texte raccourci (avec « … ») pour tenir dans {@code width} pixels, en gras ou non (le gras est plus large). */
    public static String fit(Font font, String text, int width, boolean bold) {
        if (text == null) return "";
        java.util.function.ToIntFunction<String> w = s -> bold ? font.width(MineNorthStyle.bold(s)) : font.width(s);
        if (w.applyAsInt(text) <= width) return text;
        String s = text;
        while (!s.isEmpty() && w.applyAsInt(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Prix affiché : « € » ajouté s'il n'a pas été saisi. */
    public static String price(String p) {
        if (p == null || p.isBlank()) return "";
        String t = p.trim();
        return t.contains("€") || t.toLowerCase().contains("eur") ? t : t + " €";
    }

    /** Découpe le texte en au plus {@code max} lignes de {@code width} pixels ; « … » si le texte est tronqué. */
    public static List<String> wrap(Font font, String text, int width, int max) {
        List<String> out = new ArrayList<>();
        String rest = text == null ? "" : text.trim();
        while (!rest.isEmpty() && out.size() < max) {
            String line = font.plainSubstrByWidth(rest, width);
            if (line.isEmpty()) break;
            if (line.length() < rest.length()) {
                int sp = line.lastIndexOf(' ');
                if (sp > 0) line = line.substring(0, sp);
            }
            out.add(line);
            rest = rest.substring(line.length()).trim();
        }
        if (!rest.isEmpty() && !out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, font.plainSubstrByWidth(out.get(last), width - font.width("…")) + "…");
        }
        return out;
    }
}
