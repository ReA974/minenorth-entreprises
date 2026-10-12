package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.vending.VendingMachineBE;
import fr.minenorth.entreprises.vending.VendingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Écran du distributeur (charte MineNorth). Gérant : stock comme un coffre + prix par emplacement (clic sur le prix,
 * saisie, « Valider »). Client : on choisit un article puis « Acheter » (paiement par carte, vérifié côté serveur).
 */
public class VendingScreen extends AbstractContainerScreen<VendingMenu> {
    private static final int W = 256;
    private int selected = -1;
    private EditBox priceBox;
    private String status = "";
    private boolean statusOk;
    private MineNorthButton buyButton;

    public VendingScreen(VendingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = menu.manager ? 252 : 188;
    }

    /** Résultat renvoyé par le serveur (achat, prix) : affiché dans l'écran ouvert. */
    public static void result(boolean ok, String message) {
        if (Minecraft.getInstance().screen instanceof VendingScreen s) { s.status = message; s.statusOk = ok; }
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + VendingMenu.GRID_Y + 3 * VendingMenu.CELL_H + 6;
        if (menu.manager) {
            priceBox = new EditBox(font, leftPos + 92, y, 70, 14, Component.literal("Prix"));
            priceBox.setMaxLength(9);
            priceBox.setFilter(s -> s.matches("[0-9]{0,6}([.,][0-9]{0,2})?"));
            addRenderableWidget(priceBox);
            addRenderableWidget(new MineNorthButton(leftPos + 168, y, 68, 14, Component.literal("Valider"), MineNorthStyle.GREEN, this::applyPrice));
        } else {
            buyButton = new MineNorthButton(leftPos + 156, y, 80, 18, Component.literal("Acheter"), MineNorthStyle.GREEN, this::buy);
            addRenderableWidget(buyButton);
        }
    }

    private boolean sellable(int slot) {
        return slot >= 0 && !menu.getSlot(slot).getItem().isEmpty() && menu.price(slot) > 0;
    }

    private void buy() {
        if (!menu.manager && sellable(selected)) {
            status = "";
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.VendingBuyPacket(selected));
        }
    }

    private void applyPrice() {
        if (selected < 0) { status = "Choisissez d'abord un emplacement (clic sur son prix)."; statusOk = false; return; }
        String txt = priceBox.getValue().trim().replace(',', '.');
        int cents = 0;
        if (!txt.isEmpty() && !txt.equals(".")) {
            BigDecimal v = new BigDecimal(txt).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);
            if (v.compareTo(BigDecimal.valueOf(VendingMachineBE.MAX_PRICE)) > 0) { status = "Prix maximum : 100 000 €."; statusOk = false; return; }
            cents = v.intValue();
        }
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.VendingPricePacket(selected, cents));
    }

    private static String plain(int cents) {
        return cents == 0 ? "" : (cents / 100) + (cents % 100 == 0 ? "" : "." + String.format("%02d", cents % 100));
    }

    /** Cellule sous la souris (index d'emplacement) et position verticale dans la cellule, ou -1. */
    private int cellAt(double mx, double my) {
        double lx = mx - leftPos - VendingMenu.GRID_X, ly = my - topPos - VendingMenu.GRID_Y;
        if (lx < 0 || ly < 0 || lx >= VendingMenu.COLS * VendingMenu.CELL_W || ly >= 3 * VendingMenu.CELL_H) return -1;
        return (int) (ly / VendingMenu.CELL_H) * VendingMenu.COLS + (int) (lx / VendingMenu.CELL_W);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cell = cellAt(mx, my);
        if (cell >= 0 && button == 0) {
            double ly = (my - topPos - VendingMenu.GRID_Y) % VendingMenu.CELL_H;
            if (menu.manager) {
                if (ly >= 19) {   // zone du prix : sélection ; le reste du clic va au coffre
                    selected = cell;
                    priceBox.setValue(plain(menu.price(cell)));
                    setFocused(priceBox);
                    priceBox.setFocused(true);
                    return true;
                }
            } else {
                selected = menu.getSlot(cell).getItem().isEmpty() ? -1 : cell;
                status = "";
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key != 256 && priceBox != null && priceBox.isFocused()) {
            if (key == 257 || key == 335) applyPrice();
            else priceBox.keyPressed(key, scan, mods);
            return true;   // la touche d'inventaire ne ferme pas l'écran pendant la saisie
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        MineNorthStyle.panel(g, leftPos, topPos, imageWidth, imageHeight, "Distributeur", menu.companyName);
        for (int i = 0; i < VendingMenu.SLOTS; i++) {
            int x = leftPos + VendingMenu.GRID_X + (i % VendingMenu.COLS) * VendingMenu.CELL_W,
                y = topPos + VendingMenu.GRID_Y + (i / VendingMenu.COLS) * VendingMenu.CELL_H;
            boolean sel = i == selected;
            g.fill(x, y, x + 34, y + 26, sel ? MineNorthStyle.HOVER : MineNorthStyle.LIST);
            if (sel) g.fill(x, y + 25, x + 34, y + 26, MineNorthStyle.CYAN);
            int price = menu.price(i);
            ItemStack st = menu.getSlot(i).getItem();
            String label; int color;
            if (menu.manager) { label = price > 0 ? MineNorthStyle.euros(price) : "—"; color = price > 0 ? MineNorthStyle.WARN : MineNorthStyle.MUTED; }
            else if (st.isEmpty()) { label = ""; color = MineNorthStyle.MUTED; }
            else if (price > 0) { label = MineNorthStyle.euros(price); color = MineNorthStyle.WARN; }
            else { label = "Indispo"; color = MineNorthStyle.MUTED; }
            if (!label.isEmpty()) {
                int w = Math.round(font.width(label) * 0.8f);
                MineNorthStyle.scaled(g, Component.literal(label), x + (34 - w) / 2, y + 19, 0.8f, color);
            }
        }
        int y = topPos + VendingMenu.GRID_Y + 3 * VendingMenu.CELL_H + 6;
        if (menu.manager) {
            g.drawString(font, selected < 0 ? "Prix (€) :" : "Emplacement " + (selected + 1) + " :", leftPos + 20, y + 3, MineNorthStyle.BLUE, false);
            g.drawString(font, "Vos objets", leftPos + 20, topPos + VendingMenu.PLAYER_Y + 24, MineNorthStyle.MUTED, false);
            for (int r = 0; r < 4; r++)
                for (int c = 0; c < 9; c++) {
                    int sx = leftPos + 47 + c * 18 - 1, sy = topPos + VendingMenu.PLAYER_Y + r * 18 + (r == 3 ? 4 : 0) - 1;
                    g.fill(sx, sy, sx + 18, sy + 18, MineNorthStyle.LIST);
                }
            g.drawString(font, "Clic sur un prix pour le modifier (0 = pas à vendre).", leftPos + 20, topPos + VendingMenu.GRID_Y - 8, MineNorthStyle.MUTED, false);
        } else {
            if (selected >= 0) {
                ItemStack st = menu.getSlot(selected).getItem();
                String name = font.plainSubstrByWidth(st.getHoverName().getString(), 120);
                g.drawString(font, name, leftPos + 20, y + 1, MineNorthStyle.WHITE, false);
                g.drawString(font, sellable(selected) ? MineNorthStyle.euros(menu.price(selected)) : "Non disponible", leftPos + 20, y + 10,
                        sellable(selected) ? MineNorthStyle.WARN : MineNorthStyle.ALERT, false);
            } else g.drawString(font, "Choisissez un article.", leftPos + 20, y + 5, MineNorthStyle.MUTED, false);
            g.drawString(font, "Paiement par carte bancaire (carte en main ou dans l'inventaire).", leftPos + 20, topPos + imageHeight - 14,
                    MineNorthStyle.MUTED, false);
        }
        if (!status.isEmpty())
            g.drawString(font, font.plainSubstrByWidth(status, imageWidth - 40), leftPos + 20, y + (menu.manager ? 20 : 26),
                    statusOk ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) { }   // titre dessiné par renderBg

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        if (buyButton != null) buyButton.enabled(sellable(selected));
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
