package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.item.RestaurantMenuItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture de la carte : une feuille de papier, image du plat à gauche, nom + prix, description dessous. Les titres et les noms de plats
 * passent à la ligne au lieu d'être coupés par « … » ; si une page ne tient pas sur la feuille, la suite passe automatiquement sur une page de plus.
 */
public class RestaurantMenuScreen extends Screen {
    private static final int W = 250, H = 270, PAD = 12, MIN_ROW = 36;

    /** Une page affichée : les plats d'une page de la carte qui tiennent sur la feuille (la suite = une autre vue). */
    private record View(RestaurantMenuItem.Page page, List<RestaurantMenuItem.Dish> dishes, boolean cont) {}

    private final RestaurantMenuItem.Data data;
    private final int[] theme;
    private final List<View> views = new ArrayList<>();
    private int left, top, page;
    private MineNorthButton prev, next;

    public RestaurantMenuScreen(RestaurantMenuItem.Data data) {
        super(Component.literal(data.title().isBlank() ? "Carte" : data.title()));
        this.data = data;
        this.theme = RestaurantMenuClient.THEMES[data.theme()];
    }

    private int pages() { return Math.max(1, views.size()); }

    // ------------------------------------------------------------------ mise en page

    private String titleText() { return data.title().isBlank() ? "Notre carte" : data.title(); }

    private float titleScale() {
        float s = 1.5f;
        int maxW = W - 2 * PAD;
        // on réduit un peu tant que le titre tiendrait mal sur 2 lignes
        while (s > 1.0f && RestaurantMenuClient.lines(font, titleText(), (int) (maxW / s), true, 3).size() > 2) s -= 0.1f;
        return s;
    }

    private List<String> titleLines() { return RestaurantMenuClient.lines(font, titleText(), (int) ((W - 2 * PAD) / titleScale()), true, 3); }

    private List<String> subLines() { return data.sub().isBlank() ? List.of() : RestaurantMenuClient.lines(font, data.sub(), W - 2 * PAD, false, 2); }

    private List<String> sectionLines(View v) {
        String t = v.page().sub().isBlank() ? "" : v.page().sub() + (v.cont() ? " (suite)" : "");
        if (t.isEmpty() && v.cont()) t = "(suite)";
        return t.isEmpty() ? List.of() : RestaurantMenuClient.lines(font, t, W - 2 * PAD - 40, true, 2);
    }

    private int headerHeight(View v) {
        int h = 10 + (int) (titleLines().size() * 9 * titleScale() + 4);
        h += subLines().size() * 10 + 6;      // sous-titre général
        h += 6;                               // filet
        int sec = sectionLines(v).size();
        h += sec > 0 ? sec * 10 + 6 : 2;
        return h;
    }

    private int nameWidth(RestaurantMenuItem.Dish d) {
        String price = RestaurantMenuClient.price(d.price());
        int pw = price.isEmpty() ? 0 : font.width(MineNorthStyle.bold(price)) + 8;
        return W - PAD - 52 - PAD - pw;
    }

    private int rowHeight(RestaurantMenuItem.Dish d) {
        int nl = Math.max(1, RestaurantMenuClient.lines(font, d.name(), nameWidth(d), true, 3).size());
        int dl = RestaurantMenuClient.lines(font, d.desc(), W - PAD - 52 - PAD, false, 4).size();
        return Math.max(MIN_ROW, 3 + nl * 10 + 4 + dl * 10 + 4);
    }

    private void buildViews() {
        views.clear();
        int footer = 30;
        for (RestaurantMenuItem.Page pg : data.pages()) {
            List<RestaurantMenuItem.Dish> cur = new ArrayList<>();
            boolean cont = false;
            int used = 0;
            for (RestaurantMenuItem.Dish d : pg.dishes()) {
                int rh = rowHeight(d);
                int avail = H - footer - headerHeight(new View(pg, cur, cont));
                if (!cur.isEmpty() && used + rh > avail) {
                    views.add(new View(pg, cur, cont));
                    cur = new ArrayList<>();
                    cont = true;
                    used = 0;
                }
                cur.add(d);
                used += rh;
            }
            views.add(new View(pg, cur, cont));
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        buildViews();
        prev = new MineNorthButton(left + 14, top + H - 26, 40, 16, Component.literal("<"), theme[3], () -> { page--; refresh(); });
        next = new MineNorthButton(left + W - 54, top + H - 26, 40, 16, Component.literal(">"), theme[3], () -> { page++; refresh(); });
        addRenderableWidget(prev);
        addRenderableWidget(next);
        refresh();
    }

    private void refresh() {
        page = Math.max(0, Math.min(page, pages() - 1));
        prev.visible = prev.active = page > 0;
        next.visible = next.active = page < pages() - 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int paper = theme[0], border = theme[1], ink = theme[2], accent = theme[3];
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, border);
        g.fill(left, top, left + W, top + H, paper);
        g.fill(left + 5, top + 5, left + W - 5, top + 6, border);
        g.fill(left + 5, top + H - 6, left + W - 5, top + H - 5, border);

        int y = top + 12;
        float s = titleScale();
        for (String line : titleLines()) {
            int tw = (int) (font.width(MineNorthStyle.bold(line)) * s);
            MineNorthStyle.scaled(g, MineNorthStyle.bold(line), left + (W - tw) / 2, y, s, accent);
            y += (int) (9 * s);
        }
        y += 4;
        for (String line : subLines()) {
            g.drawString(font, line, left + (W - font.width(line)) / 2, y, ink, false);
            y += 10;
        }
        y += 4;
        g.fill(left + 30, y, left + W - 30, y + 1, accent);
        y += 5;

        if (views.isEmpty()) {
            String empty = "La carte est vide.";
            g.drawString(font, empty, left + (W - font.width(empty)) / 2, top + 110, ink, false);
        } else {
            View v = views.get(page);
            // Sous-titre de la page (section) : texte d'accent, passe à la ligne, entouré de filets.
            List<String> sec = sectionLines(v);
            if (!sec.isEmpty()) {
                int widest = 0;
                for (String l : sec) widest = Math.max(widest, font.width(MineNorthStyle.bold(l)));
                int mid = y + (sec.size() * 10) / 2 - 1;
                int sx = left + (W - widest) / 2;
                g.fill(left + 14, mid, sx - 6, mid + 1, border);
                g.fill(sx + widest + 6, mid, left + W - 14, mid + 1, border);
                for (String l : sec) {
                    g.drawString(font, MineNorthStyle.bold(l), left + (W - font.width(MineNorthStyle.bold(l))) / 2, y, accent, false);
                    y += 10;
                }
                y += 6;
            } else {
                y += 2;
            }
            for (RestaurantMenuItem.Dish d : v.dishes()) {
                int rh = rowHeight(d);
                // Image du plat (à gauche), sur une pastille.
                g.fill(left + 12, y, left + 44, y + 32, border);
                g.fill(left + 13, y + 1, left + 43, y + 31, paper);
                ItemStack st = RestaurantMenuClient.stack(d.image());
                if (!st.isEmpty()) MineNorthStyle.item(g, st, left + 14, y + 2, 1.75f);
                int tx = left + 52, tr = left + W - 12;
                String price = RestaurantMenuClient.price(d.price());
                List<String> nameLines = RestaurantMenuClient.lines(font, d.name(), nameWidth(d), true, 3);
                int ly = y + 1;
                for (String l : nameLines) {
                    g.drawString(font, MineNorthStyle.bold(l), tx, ly, ink, false);
                    ly += 10;
                }
                if (!price.isEmpty()) g.drawString(font, MineNorthStyle.bold(price), tr - font.width(MineNorthStyle.bold(price)), y + 1, accent, false);
                g.fill(tx, ly + 1, tr, ly + 2, border);
                ly += 5;
                for (String l : RestaurantMenuClient.lines(font, d.desc(), tr - tx, false, 4)) {
                    g.drawString(font, l, tx, ly, ink, false);
                    ly += 10;
                }
                y += rh;
            }
        }
        if (pages() > 1) {
            String p = (page + 1) + " / " + pages();
            g.drawString(font, p, left + (W - font.width(p)) / 2, top + H - 22, ink, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        page += delta < 0 ? 1 : -1;
        refresh();
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
