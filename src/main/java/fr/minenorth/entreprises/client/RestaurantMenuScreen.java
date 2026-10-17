package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.item.RestaurantMenuItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Lecture de la carte : une feuille de papier, image du plat à gauche, nom + prix, description dessous. */
public class RestaurantMenuScreen extends Screen {
    private static final int W = 250, H = 250, ROW_H = 36, HEAD_H = 70;

    private final RestaurantMenuItem.Data data;
    private final int[] theme;
    private int left, top, page;
    private MineNorthButton prev, next;

    public RestaurantMenuScreen(RestaurantMenuItem.Data data) {
        super(Component.literal(data.title().isBlank() ? "Carte" : data.title()));
        this.data = data;
        this.theme = RestaurantMenuClient.THEMES[data.theme()];
    }

    private int pages() { return Math.max(1, data.pages().size()); }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
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

        String title = data.title().isBlank() ? "Notre carte" : data.title();
        float s = 1.6f;
        int maxW = W - 24;
        while (s > 0.8f && font.width(MineNorthStyle.bold(title)) * s > maxW) s -= 0.1f;
        title = RestaurantMenuClient.fit(font, title, (int) (maxW / s), true);
        int tw = (int) (font.width(MineNorthStyle.bold(title)) * s);
        MineNorthStyle.scaled(g, MineNorthStyle.bold(title), left + (W - tw) / 2, top + 12, s, accent);
        if (!data.sub().isBlank()) {
            String sub = RestaurantMenuClient.fit(font, data.sub(), W - 24, false);
            g.drawString(font, sub, left + (W - font.width(sub)) / 2, top + 30, ink, false);
        }
        g.fill(left + 30, top + 43, left + W - 30, top + 44, accent);

        if (data.pages().isEmpty()) {
            String empty = "La carte est vide.";
            g.drawString(font, empty, left + (W - font.width(empty)) / 2, top + 110, ink, false);
        } else {
            RestaurantMenuItem.Page pg = data.pages().get(page);
            // Sous-titre de la page (section) : texte d'accent entouré de filets.
            if (!pg.sub().isBlank()) {
                String sec = RestaurantMenuClient.fit(font, pg.sub(), W - 80, true);
                int sw = font.width(MineNorthStyle.bold(sec));
                int sx = left + (W - sw) / 2;
                g.drawString(font, MineNorthStyle.bold(sec), sx, top + 50, accent, false);
                g.fill(left + 14, top + 54, sx - 6, top + 55, border);
                g.fill(sx + sw + 6, top + 54, left + W - 14, top + 55, border);
            }
            List<RestaurantMenuItem.Dish> dishes = pg.dishes();
            for (int i = 0; i < dishes.size(); i++) {
                RestaurantMenuItem.Dish d = dishes.get(i);
                int y = top + HEAD_H + i * ROW_H;
                // Image du plat (à gauche), sur une pastille.
                g.fill(left + 12, y, left + 44, y + 32, border);
                g.fill(left + 13, y + 1, left + 43, y + 31, paper);
                ItemStack st = RestaurantMenuClient.stack(d.image());
                if (!st.isEmpty()) MineNorthStyle.item(g, st, left + 14, y + 2, 1.75f);
                int tx = left + 52, tr = left + W - 12;
                String price = RestaurantMenuClient.fit(font, RestaurantMenuClient.price(d.price()), 90, true);
                int pw = price.isEmpty() ? 0 : font.width(MineNorthStyle.bold(price)) + 8;
                g.drawString(font, MineNorthStyle.bold(RestaurantMenuClient.fit(font, d.name(), tr - tx - pw, true)), tx, y + 1, ink, false);
                if (!price.isEmpty()) g.drawString(font, MineNorthStyle.bold(price), tr - font.width(MineNorthStyle.bold(price)), y + 1, accent, false);
                g.fill(tx, y + 12, tr, y + 13, border);
                List<String> lines = RestaurantMenuClient.wrap(font, d.desc(), tr - tx, 2);
                for (int l = 0; l < lines.size(); l++) g.drawString(font, lines.get(l), tx, y + 16 + l * 10, ink, false);
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
