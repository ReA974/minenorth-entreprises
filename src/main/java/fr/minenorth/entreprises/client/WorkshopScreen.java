package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.workshop.WorkshopPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Écran de la cabine de peinture (charte MineNorth), utilisé par le personnel de l'entreprise : on choisit la peinture du véhicule
 * garé près du poste puis « Appliquer ». Le client est facturé séparément (onglet FACTURES). Le serveur revérifie tout.
 */
public class WorkshopScreen extends Screen {
    private static final int W = 300, ROW_H = 20, VISIBLE = 7, HEIGHT = 46 + 22 + VISIBLE * ROW_H + 8 + 20 + 22;

    private WorkshopPackets.State state;
    private int left, top, rowsTop;
    private int selected = -1, scroll;
    private MineNorthButton applyButton;

    private WorkshopScreen(WorkshopPackets.State state) {
        super(Component.literal("Cabine de peinture"));
        this.state = state;
    }

    /** Ouvre l'écran, ou met à jour celui qui est déjà ouvert sur ce poste (on garde la sélection). */
    public static void show(WorkshopPackets.State s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WorkshopScreen w && w.state.pos().equals(s.pos())) {
            w.state = s;
            if (w.selected >= s.rows().size()) w.selected = -1;
            return;
        }
        mc.setScreen(new WorkshopScreen(s));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - HEIGHT) / 2);
        rowsTop = top + 46 + 22;
        int y = rowsTop + VISIBLE * ROW_H + 8;
        applyButton = new MineNorthButton(left + 14, y, 150, 18, Component.literal("Appliquer cette peinture"), MineNorthStyle.GREEN, this::apply);
        addRenderableWidget(applyButton);
        addRenderableWidget(new MineNorthButton(left + W - 14 - 100, y, 100, 18, Component.literal("Actualiser"), MineNorthStyle.DARK,
                () -> send(WorkshopPackets.ACT_REFRESH, 0)));
    }

    private void send(int action, int index) {
        ModNetwork.CHANNEL.sendToServer(new WorkshopPackets.Action(state.pos(), action, index));
    }

    private void apply() {
        if (selected >= 0 && selected < state.rows().size() && !state.rows().get(selected).current()) send(WorkshopPackets.ACT_APPLY, selected);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, state.rows().size() - VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= left + 10 && mx < left + W - 10 && my >= rowsTop && my < rowsTop + VISIBLE * ROW_H) {
            int i = scroll + (int) ((my - rowsTop) / ROW_H);
            if (i < state.rows().size()) { selected = i; return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, HEIGHT, "Cabine de peinture", state.company());
        int x = left + 14, y = top + 48;
        if (state.vehicle().isEmpty()) {
            g.drawString(font, "Aucun véhicule à proximité : garez-le près du poste.", x, y, MineNorthStyle.WARN, false);
        } else {
            g.drawString(font, "Véhicule :", x, y, MineNorthStyle.BLUE, false);
            g.drawString(font, font.plainSubstrByWidth(state.vehicle(), W - 100), x + font.width("Véhicule :") + 4, y, MineNorthStyle.WHITE, false);
        }
        List<WorkshopPackets.Row> rows = state.rows();
        applyButton.enabled(selected >= 0 && selected < rows.size() && !rows.get(selected).current());
        for (int r = 0; r < VISIBLE; r++) {
            int i = scroll + r;
            if (i >= rows.size()) break;
            WorkshopPackets.Row row = rows.get(i);
            int ry = rowsTop + r * ROW_H;
            boolean hov = mx >= left + 10 && mx < left + W - 10 && my >= ry && my < ry + ROW_H;
            MineNorthStyle.card(g, left + 10, ry + 1, W - 20, ROW_H - 2, i == selected || hov, row.current() ? MineNorthStyle.GREEN : MineNorthStyle.CYAN);
            g.drawString(font, font.plainSubstrByWidth(row.label(), W - 120), left + 18, ry + 6, MineNorthStyle.WHITE, false);
            if (row.current()) g.drawString(font, "actuelle", left + W - 18 - font.width("actuelle"), ry + 6, MineNorthStyle.OK, false);
        }
        if (rows.size() > VISIBLE)
            g.drawString(font, "molette : défiler", left + W - 14 - font.width("molette : défiler"), rowsTop - 10, MineNorthStyle.MUTED, false);
        if (!state.message().isEmpty())
            g.drawString(font, font.plainSubstrByWidth(state.message(), W - 28), x, top + HEIGHT - 14, state.ok() ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        else
            g.drawString(font, "Facturez le client depuis la tablette (FACTURES).", x, top + HEIGHT - 14, MineNorthStyle.MUTED, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
