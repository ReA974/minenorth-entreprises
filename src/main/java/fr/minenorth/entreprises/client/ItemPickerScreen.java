package fr.minenorth.entreprises.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Sélecteur d'objet pour l'image d'un plat : grille avec recherche (nom ou identifiant), nourriture en premier. */
public class ItemPickerScreen extends Screen {
    private static final int COLS = 9, ROWS = 6, CELL = 20, W = COLS * CELL + 28, H = 230;
    private static List<ItemStack> all;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<ItemStack> shown = new ArrayList<>();
    private EditBox search;
    private int left, top, scroll;

    public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.literal("Choisir un objet"));
        this.parent = parent;
        this.onPick = onPick;
    }

    private static List<ItemStack> all() {
        if (all == null) {
            List<ItemStack> l = new ArrayList<>();
            for (var item : ForgeRegistries.ITEMS) {
                ItemStack st = new ItemStack(item);
                if (!st.isEmpty()) l.add(st);
            }
            l.sort(Comparator.<ItemStack, Boolean>comparing(s -> !s.getItem().isEdible())
                    .thenComparing(s -> String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()))));
            all = l;
        }
        return all;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        String old = search == null ? "" : search.getValue();
        search = new EditBox(font, left + 14, top + 52, W - 28, 14, Component.literal("Recherche"));
        search.setMaxLength(40);
        search.setValue(old);
        search.setResponder(s -> { scroll = 0; filter(); });
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(new MineNorthButton(left + 14, top + H - 24, 100, 16, Component.literal("Retour"), MineNorthStyle.PINK, this::onClose));
        filter();
    }

    private void filter() {
        shown.clear();
        String q = search.getValue().trim().toLowerCase();
        for (ItemStack st : all()) {
            if (q.isEmpty() || st.getHoverName().getString().toLowerCase().contains(q)
                    || String.valueOf(ForgeRegistries.ITEMS.getKey(st.getItem())).contains(q)) shown.add(st);
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() { return Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS); }

    private int gx() { return left + 14; }
    private int gy() { return top + 72; }

    private ItemStack at(double mx, double my) {
        int c = (int) ((mx - gx()) / CELL), r = (int) ((my - gy()) / CELL);
        if (mx < gx() || my < gy() || c < 0 || c >= COLS || r < 0 || r >= ROWS) return ItemStack.EMPTY;
        int i = (scroll + r) * COLS + c;
        return i < shown.size() ? shown.get(i) : ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ItemStack st = at(mx, my);
        if (!st.isEmpty() && button == 0) {
            var key = ForgeRegistries.ITEMS.getKey(st.getItem());
            if (key != null) onPick.accept(key.toString());
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(scroll + (delta < 0 ? 1 : -1), maxScroll()));
        return true;
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "Objet", "Cliquez sur l'image du plat");
        g.drawString(font, "Recherche (nom ou identifiant)", left + 14, top + 42, MineNorthStyle.BLUE, false);
        g.fill(gx() - 1, gy() - 1, gx() + COLS * CELL + 1, gy() + ROWS * CELL + 1, MineNorthStyle.LIST);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int i = (scroll + r) * COLS + c;
                if (i >= shown.size()) break;
                int x = gx() + c * CELL, y = gy() + r * CELL;
                if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) g.fill(x, y, x + CELL, y + CELL, MineNorthStyle.HOVER);
                g.renderItem(shown.get(i), x + 2, y + 2);
            }
        String info = shown.size() + " objet(s)" + (maxScroll() > 0 ? " · molette pour défiler" : "");
        g.drawString(font, info, left + 124, top + H - 20, MineNorthStyle.MUTED, false);
        super.render(g, mx, my, pt);
        ItemStack hov = at(mx, my);
        if (!hov.isEmpty()) g.renderTooltip(font, hov, mx, my);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
