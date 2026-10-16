package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.item.RestaurantMenuItem;
import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Éditeur de la carte (charte MineNorth) : titre, sous-titre, thème de papier, puis les plats un par un
 * (nom, prix, description, image = identifiant d'objet, ou « Objet en main »). Le serveur revérifie le droit d'édition.
 */
public class RestaurantMenuEditScreen extends Screen {
    private static final int W = 300, H = 270;

    private final boolean mainHand;
    private final List<String[]> dishes = new ArrayList<>();   // {nom, description, prix, image}
    private int theme, current;
    private final String initTitle, initSub;
    private int left, top;
    private EditBox title, sub, name, price, desc, image;
    private MineNorthButton themeBtn, prevBtn, nextBtn, addBtn, delBtn;
    private String status = "";

    public RestaurantMenuEditScreen(boolean mainHand, RestaurantMenuItem.Data d) {
        super(Component.literal("Carte de restaurant"));
        this.mainHand = mainHand;
        this.initTitle = d.title();
        this.initSub = d.sub();
        this.theme = d.theme();
        for (var x : d.dishes()) dishes.add(new String[]{x.name(), x.desc(), x.price(), x.image()});
        if (dishes.isEmpty()) dishes.add(new String[]{"", "", "", ""});
    }

    private EditBox box(int x, int y, int w, int max, String value) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.empty());
        b.setMaxLength(max);
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        if (name != null) commitDish();   // redimensionnement de la fenêtre : on garde la saisie en cours
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        int x = left + 14, full = W - 28;
        title = box(x, top + 58, 190, RestaurantMenuItem.MAX_TITLE, title == null ? initTitle : title.getValue());
        sub = box(x, top + 92, full, RestaurantMenuItem.MAX_SUB, sub == null ? initSub : sub.getValue());
        themeBtn = new MineNorthButton(x + 198, top + 58, full - 198, 14, Component.empty(), MineNorthStyle.DARK, () -> { theme = (theme + 1) % RestaurantMenuItem.THEMES; themeLabel(); });
        addRenderableWidget(themeBtn);
        themeLabel();

        prevBtn = new MineNorthButton(x + 90, top + 118, 22, 16, Component.literal("<"), MineNorthStyle.DARK, () -> go(current - 1));
        nextBtn = new MineNorthButton(x + 116, top + 118, 22, 16, Component.literal(">"), MineNorthStyle.DARK, () -> go(current + 1));
        addBtn = new MineNorthButton(x + full - 128, top + 118, 62, 16, Component.literal("+ Plat"), MineNorthStyle.GREEN, this::addDish);
        delBtn = new MineNorthButton(x + full - 62, top + 118, 62, 16, Component.literal("Retirer"), MineNorthStyle.PINK, this::removeDish);
        addRenderableWidget(prevBtn); addRenderableWidget(nextBtn); addRenderableWidget(addBtn); addRenderableWidget(delBtn);

        name = box(x, top + 152, 184, RestaurantMenuItem.MAX_NAME, "");
        price = box(x + 192, top + 152, full - 192, RestaurantMenuItem.MAX_PRICE, "");
        desc = box(x, top + 186, full, RestaurantMenuItem.MAX_DESC, "");
        image = box(x, top + 220, 120, RestaurantMenuItem.MAX_IMG, "");
        addRenderableWidget(new MineNorthButton(x + 126, top + 220, 76, 14, Component.literal("Choisir…"), MineNorthStyle.GREEN, this::pick));
        addRenderableWidget(new MineNorthButton(x + 206, top + 220, full - 206, 14, Component.literal("En main"), MineNorthStyle.CYAN, this::fromHand));

        addRenderableWidget(new MineNorthButton(x, top + H - 26, 130, 18, Component.literal("Enregistrer"), MineNorthStyle.GREEN, this::save));
        addRenderableWidget(new MineNorthButton(x + full - 100, top + H - 26, 100, 18, Component.literal("Annuler"), MineNorthStyle.PINK, this::onClose));
        loadDish();
    }

    private void themeLabel() { themeBtn.setMessage(Component.literal(RestaurantMenuClient.THEME_NAMES[theme])); }

    // ------------------------------------------------------------------ plats
    private void commitDish() {
        String[] d = dishes.get(current);
        d[0] = name.getValue(); d[1] = desc.getValue(); d[2] = price.getValue(); d[3] = image.getValue().trim();
    }

    private void loadDish() {
        String[] d = dishes.get(current);
        name.setValue(d[0]); desc.setValue(d[1]); price.setValue(d[2]); image.setValue(d[3]);
        prevBtn.active = current > 0;
        nextBtn.active = current < dishes.size() - 1;
        addBtn.active = dishes.size() < RestaurantMenuItem.MAX_DISHES;
        delBtn.active = dishes.size() > 1;
    }

    private void go(int i) {
        if (i < 0 || i >= dishes.size()) return;
        commitDish();
        current = i;
        loadDish();
    }

    private void addDish() {
        if (dishes.size() >= RestaurantMenuItem.MAX_DISHES) return;
        commitDish();
        dishes.add(current + 1, new String[]{"", "", "", ""});
        current++;
        loadDish();
    }

    private void removeDish() {
        if (dishes.size() <= 1) return;
        dishes.remove(current);
        current = Math.min(current, dishes.size() - 1);
        loadDish();
    }

    /** Prend l'identifiant de l'objet tenu dans l'autre main (celle qui ne tient pas la carte). */
    private void fromHand() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        ItemStack st = p.getItemInHand(mainHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (st.isEmpty() || st.getItem() instanceof RestaurantMenuItem) {
            status = "Tenez le plat (ou un objet) dans l'autre main.";
            return;
        }
        var key = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (key != null) { image.setValue(key.toString()); status = ""; }
    }

    /** Ouvre la liste des objets (recherche) ; au retour, l'image du plat en cours est remplie. */
    private void pick() {
        commitDish();
        Minecraft.getInstance().setScreen(new ItemPickerScreen(this, id -> image.setValue(id)));
    }

    private void save() {
        commitDish();
        List<RestaurantMenuItem.Dish> out = new ArrayList<>();
        for (String[] d : dishes) out.add(new RestaurantMenuItem.Dish(d[0], d[1], d[2], d[3]));
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.MenuSavePacket(mainHand, title.getValue(), sub.getValue(), theme, out));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "Carte", "Édition (accroupi + clic droit)");
        int x = left + 14;
        g.drawString(font, "Titre de la carte", x, top + 48, MineNorthStyle.BLUE, false);
        g.drawString(font, "Papier", x + 198, top + 48, MineNorthStyle.BLUE, false);
        g.drawString(font, "Sous-titre (facultatif)", x, top + 82, MineNorthStyle.BLUE, false);
        g.fill(left + 10, top + 112, left + W - 10, top + 113, MineNorthStyle.HOVER);
        g.drawString(font, "Plat " + (current + 1) + " / " + dishes.size(), x, top + 122, MineNorthStyle.WARN, false);
        g.drawString(font, "Nom du plat", x, top + 142, MineNorthStyle.BLUE, false);
        g.drawString(font, "Prix (€ ajouté)", x + 192, top + 142, MineNorthStyle.BLUE, false);
        g.drawString(font, "Description", x, top + 176, MineNorthStyle.BLUE, false);
        g.drawString(font, "Image (identifiant d'objet)", x, top + 210, MineNorthStyle.BLUE, false);
        ItemStack st = RestaurantMenuClient.stack(image.getValue());
        if (!st.isEmpty()) MineNorthStyle.item(g, st, left + W - 34, top + 202, 1f);
        if (!status.isEmpty()) g.drawString(font, status, x, top + 238, MineNorthStyle.ALERT, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
