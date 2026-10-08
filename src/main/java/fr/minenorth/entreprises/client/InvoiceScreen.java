package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Facture reçue d'une entreprise (charte MineNorth) : le client signe ou refuse. Fermer l'écran (Échap) ne répond pas :
 * la facture expire alors côté serveur. Le serveur revérifie tout (client de la facture, statut, délai).
 */
public class InvoiceScreen extends Screen {
    private static final int W = 280, H = 170, DESC_LINES = 3;

    private final ModNetwork.InvoicePacket inv;
    private int left, top;
    private boolean answered;

    public InvoiceScreen(ModNetwork.InvoicePacket inv) {
        super(Component.literal("Facture"));
        this.inv = inv;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        int x = left + 14, half = (W - 28 - 8) / 2;
        addRenderableWidget(new MineNorthButton(x, top + H - 30, half, 20, Component.literal("Signer la facture"),
                MineNorthStyle.GREEN, () -> answer(true)));
        addRenderableWidget(new MineNorthButton(x + half + 8, top + H - 30, half, 20, Component.literal("Refuser"),
                MineNorthStyle.PINK, () -> answer(false)));
    }

    /** Une seule réponse par écran (double clic sans effet), puis fermeture. */
    private void answer(boolean sign) {
        if (answered) return;
        answered = true;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.InvoiceActionPacket(inv.invoiceId(), sign));
        ClientNetworkHandler.nextInvoice();
    }

    /** Échap : ferme sans répondre (l'expiration du serveur s'applique) et affiche la facture suivante éventuelle. */
    @Override
    public void onClose() { ClientNetworkHandler.nextInvoice(); }

    /** Découpe le texte en au plus {@code max} lignes de {@code width} pixels ; « … » si le texte est tronqué. */
    private List<String> wrap(String text, int width, int max) {
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

    private void kv(GuiGraphics g, String key, String value, int x, int y) {
        g.drawString(font, key, x, y, MineNorthStyle.BLUE, false);
        int vx = x + font.width(key) + 4;
        g.drawString(font, font.plainSubstrByWidth(value, left + W - 14 - vx), vx, y, MineNorthStyle.WHITE, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "Facture", "Signature requise");
        int x = left + 14, w = W - 28;
        kv(g, "Entreprise :", inv.company(), x, top + 48);
        kv(g, "Émise par :", inv.issuerName(), x, top + 61);
        g.drawString(font, "Description :", x, top + 74, MineNorthStyle.BLUE, false);
        MineNorthStyle.card(g, x, top + 84, w, 34, false, MineNorthStyle.CYAN);
        List<String> lines = wrap(inv.description(), w - 14, DESC_LINES);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 8, top + 87 + i * 10, MineNorthStyle.TEXT, false);
        g.drawString(font, "Montant :", x, top + 125, MineNorthStyle.BLUE, false);
        MineNorthStyle.scaled(g, MineNorthStyle.bold(MineNorthStyle.euros(inv.cents())), x + font.width("Montant :") + 6, top + 123,
                1.3f, MineNorthStyle.WARN);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
