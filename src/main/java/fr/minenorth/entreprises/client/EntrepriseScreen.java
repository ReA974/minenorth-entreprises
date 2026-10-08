package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.network.ModNetwork.CompanyView;
import fr.minenorth.entreprises.network.ModNetwork.GradeView;
import fr.minenorth.entreprises.network.ModNetwork.MemberView;
import fr.minenorth.entreprises.network.ModNetwork.TxView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Interface des entreprises (charte MineNorth / Permis).
 * Mode joueur : création, invitation, gestion de son entreprise.
 * Mode OP : liste, création, validation, modification, dissolution, employés et grades.
 */
public class EntrepriseScreen extends Screen {
    private static final int W = 400, H = 244, ROWS = 6, ADMIN_ROWS = 7, TX_ROWS = 4;
    /** Identifiants stables des onglets (indépendants de leur position à l'écran). */
    private static final int T_INFOS = 0, T_MEMBERS = 1, T_GRADES = 2, T_TX = 3;
    private static final DateTimeFormatter TX_DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private ModNetwork.StatePacket st;
    private int left, top;
    private int tab, page;
    /** Mode OP : entreprise sélectionnée (-1 = liste). */
    private int sel = -1;
    /** Onglet grades : -1 = aucun, -2 = nouveau grade, sinon index du grade en cours de modification. */
    private int selGrade = -1;
    private boolean creating, confirm, gradeManage;
    private String message = "";
    private boolean messageOk = true;

    private EditBox bName, bActivity, bOwner, bPlayer, bGradeName, bSalary, bAmount, bRecipient, bMotif;
    private String kName = "", kActivity = "", kOwner = "", kPlayer = "", kGradeName = "", kSalary = "", kAmount = "",
            kRecipient = "", kMotif = "";

    private record Label(String text, int x, int y, int color) {}
    private record Card(int x, int y, int w, int h, int accent) {}
    private final List<Label> labels = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();

    public EntrepriseScreen(ModNetwork.StatePacket st) {
        super(Component.literal("Entreprises"));
        this.st = st;
        this.message = st.message();
        this.messageOk = st.ok();
    }

    public boolean isAdmin() { return st.admin(); }

    /** Nouvel état envoyé par le serveur après une action. */
    public void update(ModNetwork.StatePacket s) {
        keep();
        this.st = s;
        this.message = s.message();
        this.messageOk = s.ok();
        this.confirm = false;
        if (s.ok() && !s.message().isEmpty()) {
            if (creating) { creating = false; kName = kActivity = kOwner = ""; }
            kPlayer = "";
            kAmount = "";
            kRecipient = kMotif = "";
            selGrade = -1;
        }
        rebuild();
    }

    // ------------------------------------------------------------------ outils
    private void send(int action, int companyId, String a, String b, String c, int n) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(st.admin(), action, companyId,
                cut(a), cut(b), cut(c), n));
    }
    private static String cut(String v) { return v == null ? "" : v.length() > 64 ? v.substring(0, 64) : v; }

    private void keep() {
        if (bName != null) kName = bName.getValue();
        if (bActivity != null) kActivity = bActivity.getValue();
        if (bOwner != null) kOwner = bOwner.getValue();
        if (bPlayer != null) kPlayer = bPlayer.getValue();
        if (bGradeName != null) kGradeName = bGradeName.getValue();
        if (bSalary != null) kSalary = bSalary.getValue();
        if (bAmount != null) kAmount = bAmount.getValue();
        if (bRecipient != null) kRecipient = bRecipient.getValue();
        if (bMotif != null) kMotif = bMotif.getValue();
    }
    private void rebuild() { clearWidgets(); init(); }
    /** Change de vue en conservant ce qui a été saisi. */
    private void go(Runnable change) { keep(); message = ""; change.run(); rebuild(); }

    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }
    private EditBox box(int x, int y, int w, String hint, String value) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.literal(hint));
        b.setMaxLength(32);
        b.setHint(Component.literal(hint));
        b.setValue(value == null ? "" : value);
        addRenderableWidget(b);
        return b;
    }
    private void label(String text, int x, int y, int color) { labels.add(new Label(text, x, y, color)); }
    private void label(String text, int x, int y, int color, int maxWidth) { label(font.plainSubstrByWidth(text, maxWidth), x, y, color); }
    private void kv(String key, String value, int x, int y) {
        label(key, x, y, MineNorthStyle.BLUE);
        label(value, x + font.width(key) + 4, y, MineNorthStyle.TEXT, W - 36 - font.width(key));
    }
    private void card(int x, int y, int w, int h, int accent) { cards.add(new Card(x, y, w, h, accent)); }

    private CompanyView company(int id) {
        for (CompanyView c : st.companies()) if (c.id() == id) return c;
        return null;
    }
    private static UUID me() {
        var p = Minecraft.getInstance().player;
        return p == null ? new UUID(0, 0) : p.getUUID();
    }
    private static String gradeName(CompanyView c, int index) {
        return c.grades().isEmpty() ? "—" : c.grades().get(Math.max(0, Math.min(c.grades().size() - 1, index))).name();
    }
    private static GradeView gradeOf(CompanyView c, UUID player) {
        for (MemberView m : c.members()) if (m.id().equals(player) && !c.grades().isEmpty())
            return c.grades().get(Math.max(0, Math.min(c.grades().size() - 1, m.grade())));
        return null;
    }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        labels.clear();
        cards.clear();
        bName = bActivity = bOwner = bPlayer = bGradeName = bSalary = bAmount = bRecipient = bMotif = null;

        boolean sub = st.admin() && (sel >= 0 || creating);
        btn(left + W - 100, top + 10, 86, 16, sub ? "Retour" : "Fermer", MineNorthButton.GHOST, this::back);

        if (st.admin()) {
            if (creating) buildAdminCreate();
            else if (company(sel) != null) buildCompany(company(sel), true);
            else { sel = -1; buildAdminList(); }
        } else {
            CompanyView c = company(st.selfId());
            if (c == null) buildNone();
            else if (c.status() == 0) buildPending(c);
            else buildCompany(c, false);
        }
    }

    private void back() {
        if (st.admin() && (sel >= 0 || creating)) {
            go(() -> { sel = -1; creating = false; tab = 0; page = 0; selGrade = -1; confirm = false; });
        } else onClose();
    }

    /** Joueur sans entreprise : invitation éventuelle + formulaire de création. */
    private void buildNone() {
        int x = left + 14, w = W - 28, y = top + 50;
        if (!st.invites().isEmpty()) {
            ModNetwork.InviteView iv = st.invites().get(0);
            card(x, y, w, 24, MineNorthStyle.CYAN);
            label("Invitation : " + iv.company() + " (par " + iv.by() + ")", x + 8, y + 8, MineNorthStyle.TEXT, w - 170);
            btn(x + w - 152, y + 3, 72, 18, "ACCEPTER", MineNorthStyle.GREEN, () -> send(ModNetwork.ACCEPT, iv.companyId(), "", "", "", 0));
            btn(x + w - 76, y + 3, 72, 18, "REFUSER", MineNorthStyle.PINK, () -> send(ModNetwork.REFUSE_INVITE, iv.companyId(), "", "", "", 0));
            y += 30;
        }
        label("NOM DE L'ENTREPRISE", x, y, MineNorthStyle.BLUE);
        bName = box(x, y + 11, w, "Ex : Garage du Nord", kName);
        label("ACTIVITÉ", x, y + 36, MineNorthStyle.BLUE);
        bActivity = box(x, y + 47, w, "Ex : Garage, Restaurant, Taxi...", kActivity);
        label("Frais de création : " + (st.fee() > 0 ? MineNorthStyle.euros(st.fee()) + " (paiement par carte bancaire)" : "gratuit"),
                x, y + 74, MineNorthStyle.TEXT);
        label(st.validation() ? "La création doit ensuite être validée par un administrateur."
                : "L'entreprise est active immédiatement.", x, y + 86, MineNorthStyle.MUTED);
        btn(x, y + 102, w, 22, "CRÉER L'ENTREPRISE", MineNorthStyle.GREEN,
                () -> send(ModNetwork.CREATE, 0, bName.getValue(), bActivity.getValue(), "", 0));
    }

    private void buildPending(CompanyView c) {
        int x = left + 14, w = W - 28, y = top + 54;
        card(x, y, w, 66, MineNorthStyle.WARN);
        label("Demande en attente de validation", x + 10, y + 8, MineNorthStyle.WARN);
        kv("Nom :", c.name(), x + 10, y + 24);
        kv("Activité :", c.activity(), x + 10, y + 37);
        label("Un administrateur doit valider votre entreprise.", x + 10, y + 51, MineNorthStyle.MUTED);
        btn(x, y + 78, w, 22, confirm ? "CONFIRMER L'ANNULATION (REMBOURSÉE)" : "ANNULER LA DEMANDE", MineNorthStyle.PINK, () -> {
            if (confirm) send(ModNetwork.DISSOLVE, c.id(), "", "", "", 0); else go(() -> confirm = true);
        });
    }

    /**
     * Vue d'une entreprise : onglets INFOS / EMPLOYÉS / GRADES / TRANSACTIONS. admin = droits complets (OP).
     * L'onglet TRANSACTIONS n'apparaît que si le serveur a accordé {@code bankAccess} (jamais en mode admin).
     */
    private void buildCompany(CompanyView c, boolean admin) {
        UUID me = me();
        boolean owner = admin || c.owner().equals(me);
        GradeView mine = gradeOf(c, me);
        boolean manage = owner || (mine != null && mine.manage());
        boolean bank = !admin && c.bankAccess();
        if ((tab == T_GRADES && !owner) || (tab == T_TX && !bank)) { tab = T_INFOS; page = 0; }

        int x = left + 14, w = W - 28;
        List<String> names = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        names.add("INFOS"); ids.add(T_INFOS);
        names.add("EMPLOYÉS"); ids.add(T_MEMBERS);
        if (owner) { names.add("GRADES"); ids.add(T_GRADES); }
        if (bank) { names.add("TRANSACTIONS"); ids.add(T_TX); }
        for (int i = 0; i < names.size(); i++) {
            final int t = ids.get(i);
            btn(x + i * 94, top + 46, 90, 18, names.get(i), tab == t ? MineNorthStyle.CYAN : MineNorthStyle.DARK,
                    () -> go(() -> { tab = t; page = 0; selGrade = -1; confirm = false; }));
        }
        int y0 = top + 72;
        if (tab == T_INFOS) buildInfos(c, admin, owner, mine, x, y0, w);
        else if (tab == T_MEMBERS) buildMembers(c, admin, owner, manage, x, y0, w);
        else if (tab == T_GRADES) buildGrades(c, x, y0, w);
        else buildTransactions(c, x, y0, w);
    }

    /** Onglet TRANSACTIONS : solde, dépôt / virement / carte, historique paginé (le serveur valide tout). */
    private void buildTransactions(CompanyView c, int x, int y0, int w) {
        label("SOLDE DU COMPTE :", x, y0 + 3, MineNorthStyle.BLUE);
        label(MineNorthStyle.euros(c.balance()), x + font.width("SOLDE DU COMPTE :") + 6, y0 + 3,
                c.balance() > 0 ? MineNorthStyle.OK : MineNorthStyle.ALERT);
        btn(x + w - 170, y0 - 2, 170, 16, "Obtenir ma carte entreprise", MineNorthStyle.CYAN,
                () -> send(ModNetwork.GET_BUSINESS_CARD, c.id(), "", "", "", 0));

        int yr = y0 + 18;
        bAmount = box(x, yr, 100, "Montant €", kAmount);
        btn(x + 106, yr - 1, 80, 20, "Déposer", MineNorthStyle.GREEN,
                () -> send(ModNetwork.DEPOSIT, c.id(), bAmount.getValue(), "", "", 0));
        btn(x + 192, yr - 1, w - 192, 20, "Virer vers mon compte", MineNorthStyle.DARK,
                () -> send(ModNetwork.WITHDRAW, c.id(), bAmount.getValue(), "", "", 0));

        // Virement vers un joueur (nom RP) : réutilise le montant ci-dessus ; le motif est facultatif.
        int yp = yr + 24;
        bRecipient = box(x, yp, 126, "Destinataire (nom RP)", kRecipient);
        bRecipient.setMaxLength(64);
        bMotif = box(x + 132, yp, 124, "Motif (facultatif)", kMotif);
        bMotif.setMaxLength(64);
        btn(x + 262, yp - 1, w - 262, 20, "Virer à un joueur", MineNorthStyle.GREEN,
                () -> send(ModNetwork.TRANSFER_PLAYER, c.id(), bAmount.getValue(), bRecipient.getValue(), bMotif.getValue(), 0));

        // Colonnes : date | libellé | montant (aligné à droite) | solde après (aligné à droite) | auteur.
        int cDate = x + 6, cLabel = x + 66, rAmount = x + 234, rAfter = x + 302, cActor = x + 308;
        int yh = y0 + 68;
        label("DATE", cDate, yh, MineNorthStyle.BLUE);
        label("LIBELLÉ", cLabel, yh, MineNorthStyle.BLUE);
        right("MONTANT", rAmount, yh, MineNorthStyle.BLUE);
        right("SOLDE", rAfter, yh, MineNorthStyle.BLUE);
        label("PAR", cActor, yh, MineNorthStyle.BLUE);

        List<TxView> list = c.txs();
        int pages = Math.max(1, (list.size() + TX_ROWS - 1) / TX_ROWS);
        page = Math.max(0, Math.min(pages - 1, page));
        if (list.isEmpty()) label("Aucune transaction pour le moment.", x, yh + 16, MineNorthStyle.MUTED);
        for (int i = 0; i < TX_ROWS; i++) {
            int idx = page * TX_ROWS + i;
            if (idx >= list.size()) break;
            TxView t = list.get(idx);
            int y = yh + 11 + i * 14;
            boolean plus = t.cents() >= 0;
            int color = plus ? MineNorthStyle.OK : MineNorthStyle.ALERT;
            card(x, y, w, 13, color);
            label(TX_DATE.format(Instant.ofEpochMilli(t.time())), cDate, y + 3, MineNorthStyle.MUTED, cLabel - cDate - 4);
            label(t.label(), cLabel, y + 3, MineNorthStyle.WHITE, rAmount - 64 - cLabel);
            right((plus ? "+" : "") + MineNorthStyle.euros(t.cents()), rAmount, y + 3, color);
            right(MineNorthStyle.euros(t.balanceAfter()), rAfter, y + 3, MineNorthStyle.TEXT);
            label(t.actor(), cActor, y + 3, MineNorthStyle.MUTED, x + w - 4 - cActor);
        }
        pager(pages, x + w, top + H - 36);
    }

    /** Libellé aligné à droite sur {@code rightX}. */
    private void right(String text, int rightX, int y, int color) { label(text, rightX - font.width(text), y, color); }

    private void buildInfos(CompanyView c, boolean admin, boolean owner, GradeView mine, int x, int y0, int w) {
        int half = (w - 8) / 2;
        long payroll = 0;
        for (MemberView m : c.members()) if (!c.grades().isEmpty()) payroll += c.grades().get(Math.max(0, Math.min(c.grades().size() - 1, m.grade()))).salary();
        String status = c.status() == 0 ? "En attente de validation" : c.dissolveRequested() ? "Active • dissolution demandée" : "Active";

        if (admin) {
            label("NOM", x, y0, MineNorthStyle.BLUE);
            bName = box(x, y0 + 11, half, "Nom de l'entreprise", c.name());
            label("ACTIVITÉ", x + half + 8, y0, MineNorthStyle.BLUE);
            bActivity = box(x + half + 8, y0 + 11, half, "Activité", c.activity());
            label("PATRON (PSEUDO MINECRAFT)", x, y0 + 36, MineNorthStyle.BLUE);
            bOwner = box(x, y0 + 47, half, "Pseudo du patron", c.ownerName());
            label("STATUT", x + half + 8, y0 + 36, MineNorthStyle.BLUE);
            label(status, x + half + 8, y0 + 52, c.status() == 0 || c.dissolveRequested() ? MineNorthStyle.WARN : MineNorthStyle.OK);
            label(c.members().size() + " employé(s) • masse salariale " + MineNorthStyle.euros(payroll) + " par paie", x, y0 + 72, MineNorthStyle.MUTED);
            btn(x, y0 + 86, w, 20, "ENREGISTRER LES MODIFICATIONS", MineNorthStyle.CYAN,
                    () -> send(ModNetwork.EDIT, c.id(), bName.getValue(), bActivity.getValue(), bOwner.getValue(), 0));
            int y = y0 + 110;
            if (c.status() == 0) {
                btn(x, y, half, 20, "VALIDER L'ENTREPRISE", MineNorthStyle.GREEN, () -> send(ModNetwork.VALIDATE, c.id(), "", "", "", 0));
                btn(x + half + 8, y, half, 20, "REFUSER (REMBOURSER)", MineNorthStyle.PINK, () -> send(ModNetwork.REFUSE, c.id(), "", "", "", 0));
                y += 24;
            }
            boolean asked = c.dissolveRequested();
            btn(x, y, asked ? half : w, 20, confirm ? "CONFIRMER LA DISSOLUTION" : asked ? "ACCEPTER LA DISSOLUTION" : "DISSOUDRE L'ENTREPRISE", MineNorthStyle.PINK, () -> {
                if (confirm) send(ModNetwork.DISSOLVE, c.id(), "", "", "", 0); else go(() -> confirm = true);
            });
            if (asked) btn(x + half + 8, y, half, 20, "REFUSER LA DISSOLUTION", MineNorthStyle.DARK, () -> send(ModNetwork.REJECT_DISSOLVE, c.id(), "", "", "", 0));
            return;
        }

        card(x, y0, w, 96, MineNorthStyle.CYAN);
        int lx = x + 10, y = y0 + 8;
        kv("Activité :", c.activity(), lx, y);
        kv("Patron :", c.ownerName(), lx, y + 13);
        kv("Statut :", status, lx, y + 26);
        kv("Employés :", c.members().size() + " / " + st.maxEmployees(), lx, y + 39);
        if (owner) kv("Votre rôle :", "Patron", lx, y + 52);
        else kv("Votre grade :", mine == null ? "—" : mine.name() + " • salaire " + MineNorthStyle.euros(mine.salary()), lx, y + 52);
        kv("Masse salariale :", MineNorthStyle.euros(payroll) + " par paie (toutes les " + st.payMinutes() + " min)", lx, y + 65);
        if (c.bankAccess()) kv("Solde du compte :", MineNorthStyle.euros(c.balance()), lx, y + 78);
        label("Salaires versés aux employés connectés, prélevés sur le compte de l'entreprise.", x, y0 + 102, MineNorthStyle.MUTED, w);
        if (owner) {
            // Le PDG ne peut ni renommer ni dissoudre lui-même : il demande la dissolution à un administrateur.
            btn(x, y0 + 118, half, 20, "OBTENIR LA TABLETTE", MineNorthStyle.CYAN, () -> send(ModNetwork.GET_TABLET, c.id(), "", "", "", 0));
            if (c.dissolveRequested()) {
                btn(x + half + 8, y0 + 118, half, 20, "ANNULER LA DISSOLUTION", MineNorthStyle.DARK, () -> send(ModNetwork.REQUEST_DISSOLVE, c.id(), "0", "", "", 0));
            } else {
                btn(x + half + 8, y0 + 118, half, 20, confirm ? "CONFIRMER LA DEMANDE" : "DEMANDER LA DISSOLUTION", MineNorthStyle.PINK, () -> {
                    if (confirm) send(ModNetwork.REQUEST_DISSOLVE, c.id(), "1", "", "", 0); else go(() -> confirm = true);
                });
            }
        } else {
            btn(x, y0 + 118, w, 20, confirm ? "CONFIRMER LA DÉMISSION" : "DÉMISSIONNER", MineNorthStyle.PINK, () -> {
                if (confirm) send(ModNetwork.LEAVE, c.id(), "", "", "", 0); else go(() -> confirm = true);
            });
        }
    }

    private void buildMembers(CompanyView c, boolean admin, boolean owner, boolean manage, int x, int y0, int w) {
        List<MemberView> list = c.members();
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(pages - 1, page));
        UUID me = me();
        if (list.isEmpty()) label("Aucun employé pour le moment.", x, y0 + 6, MineNorthStyle.MUTED);
        int last = c.grades().size() - 1;
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= list.size()) break;
            MemberView m = list.get(idx);
            int y = y0 + i * 20;
            GradeView g = c.grades().isEmpty() ? null : c.grades().get(Math.max(0, Math.min(last, m.grade())));
            card(x, y, w, 18, m.online() ? MineNorthStyle.OK : MineNorthStyle.DARK);
            label(m.name(), x + 8, y + 5, m.online() ? MineNorthStyle.WHITE : MineNorthStyle.MUTED, 110);
            label(g == null ? "—" : g.name(), x + 124, y + 5, MineNorthStyle.TEXT, 96);
            label(g == null ? "" : MineNorthStyle.euros(g.salary()), x + 224, y + 5, MineNorthStyle.MUTED, 70);
            String id = m.id().toString();
            if (owner) {
                // "+" = monter en grade (index plus petit), "-" = descendre.
                btn(x + w - 62, y + 2, 16, 14, "+", MineNorthStyle.DARK, () -> send(ModNetwork.SET_GRADE, c.id(), id, "", "", m.grade() - 1)).enabled(m.grade() > 0);
                btn(x + w - 44, y + 2, 16, 14, "-", MineNorthStyle.DARK, () -> send(ModNetwork.SET_GRADE, c.id(), id, "", "", m.grade() + 1)).enabled(m.grade() < last);
            }
            if (owner || (manage && !m.id().equals(me) && (g == null || !g.manage()))) {
                btn(x + w - 22, y + 2, 20, 14, "X", MineNorthStyle.PINK, () -> send(ModNetwork.FIRE, c.id(), id, "", "", 0));
            }
        }
        int yb = top + H - 50;
        if (manage) {
            bPlayer = box(x, yb, 140, "Pseudo du joueur", kPlayer);
            btn(x + 146, yb - 1, 96, 20, admin ? "AJOUTER" : "EMBAUCHER", MineNorthStyle.GREEN,
                    () -> send(ModNetwork.INVITE, c.id(), bPlayer.getValue(), "", "", 0));
        }
        pager(pages, x + w, yb);
    }

    private void pager(int pages, int right, int y) {
        if (pages <= 1) return;
        btn(right - 78, y - 1, 20, 20, "<", MineNorthStyle.DARK, () -> go(() -> page--)).enabled(page > 0);
        String p = (page + 1) + " / " + pages;
        label(p, right - 39 - font.width(p) / 2, y + 5, MineNorthStyle.TEXT);
        btn(right - 20, y - 1, 20, 20, ">", MineNorthStyle.DARK, () -> go(() -> page++)).enabled(page < pages - 1);
    }

    private void buildGrades(CompanyView c, int x, int y0, int w) {
        List<GradeView> grades = c.grades();
        for (int i = 0; i < grades.size() && i < 6; i++) {
            GradeView g = grades.get(i);
            int y = y0 + i * 18;
            final int idx = i;
            card(x, y, w, 16, selGrade == i ? MineNorthStyle.CYAN : MineNorthStyle.DARK);
            label((i + 1) + ". " + g.name(), x + 8, y + 4, MineNorthStyle.WHITE, 130);
            label(MineNorthStyle.euros(g.salary()) + " / paie", x + 146, y + 4, MineNorthStyle.TEXT, 90);
            if (g.manage()) label("gestion", x + 244, y + 4, MineNorthStyle.OK);
            btn(x + w - 68, y + 1, 66, 14, "MODIFIER", MineNorthStyle.DARK, () -> go(() -> {
                selGrade = idx; gradeManage = g.manage(); kGradeName = g.name();
                kSalary = MineNorthStyle.euros(g.salary()).replace(" €", "");
            }));
        }
        int yb = top + H - 50;
        if (selGrade == -1) {
            label("« gestion » : ce grade peut embaucher et licencier.", x, yb - 12, MineNorthStyle.MUTED);
            btn(x, yb - 1, w, 20, "NOUVEAU GRADE", MineNorthStyle.DARK,
                    () -> go(() -> { selGrade = -2; gradeManage = false; kGradeName = ""; kSalary = ""; }))
                    .enabled(grades.size() < st.maxGrades());
            return;
        }
        boolean isNew = selGrade < 0 || selGrade >= grades.size();
        label(isNew ? "NOUVEAU GRADE" : "MODIFIER LE GRADE", x, yb - 12, MineNorthStyle.BLUE);
        bGradeName = box(x, yb, 112, "Nom du grade", kGradeName);
        bSalary = box(x + 118, yb, 64, "Salaire €", kSalary);
        btn(x + 188, yb - 1, 80, 20, gradeManage ? "GESTION : OUI" : "GESTION : NON", gradeManage ? MineNorthStyle.CYAN : MineNorthStyle.DARK,
                () -> go(() -> gradeManage = !gradeManage));
        btn(x + 272, yb - 1, 38, 20, "OK", MineNorthStyle.GREEN,
                () -> send(ModNetwork.GRADE_SAVE, c.id(), bGradeName.getValue(), bSalary.getValue(), gradeManage ? "1" : "0", isNew ? -1 : selGrade));
        if (!isNew) {
            final int del = selGrade;
            btn(x + 314, yb - 1, 58, 20, "SUPPR.", MineNorthStyle.PINK, () -> send(ModNetwork.GRADE_DELETE, c.id(), "", "", "", del));
        } else {
            btn(x + 314, yb - 1, 58, 20, "ANNULER", MineNorthStyle.PINK, () -> go(() -> selGrade = -1));
        }
    }

    // ------------------------------------------------------------------ mode OP
    private void buildAdminList() {
        int x = left + 14, w = W - 28, y0 = top + 48;
        List<CompanyView> list = st.companies();
        int pages = Math.max(1, (list.size() + ADMIN_ROWS - 1) / ADMIN_ROWS);
        page = Math.max(0, Math.min(pages - 1, page));
        if (list.isEmpty()) label("Aucune entreprise enregistrée.", x, y0 + 6, MineNorthStyle.MUTED);
        for (int i = 0; i < ADMIN_ROWS; i++) {
            int idx = page * ADMIN_ROWS + i;
            if (idx >= list.size()) break;
            CompanyView c = list.get(idx);
            int y = y0 + i * 20;
            boolean pending = c.status() == 0;
            boolean asked = c.dissolveRequested();
            card(x, y, w, 18, pending ? MineNorthStyle.WARN : asked ? MineNorthStyle.ALERT : MineNorthStyle.OK);
            label(c.name(), x + 8, y + 5, MineNorthStyle.WHITE, 104);
            label(c.activity(), x + 116, y + 5, MineNorthStyle.TEXT, 76);
            label(c.ownerName(), x + 196, y + 5, MineNorthStyle.MUTED, 66);
            label(pending ? "ATTENTE" : asked ? "DISSOL." : c.members().size() + " emp.", x + 266, y + 5,
                    pending ? MineNorthStyle.WARN : asked ? MineNorthStyle.ALERT : MineNorthStyle.MUTED, 48);
            btn(x + w - 52, y + 2, 50, 14, "GÉRER", pending || asked ? MineNorthStyle.GREEN : MineNorthStyle.DARK,
                    () -> go(() -> { sel = c.id(); tab = 0; page = 0; selGrade = -1; confirm = false; }));
        }
        int yb = top + H - 50;
        btn(x, yb - 1, 170, 20, "CRÉER UNE ENTREPRISE", MineNorthStyle.GREEN,
                () -> go(() -> { creating = true; kName = kActivity = kOwner = ""; }));
        pager(pages, x + w, yb);
    }

    private void buildAdminCreate() {
        int x = left + 14, w = W - 28, y = top + 50;
        label("NOM DE L'ENTREPRISE", x, y, MineNorthStyle.BLUE);
        bName = box(x, y + 11, w, "Nom de l'entreprise", kName);
        label("ACTIVITÉ", x, y + 36, MineNorthStyle.BLUE);
        bActivity = box(x, y + 47, w, "Activité", kActivity);
        label("PATRON (PSEUDO MINECRAFT)", x, y + 72, MineNorthStyle.BLUE);
        bOwner = box(x, y + 83, w, "Pseudo du patron", kOwner);
        label("Création sans frais, active immédiatement.", x, y + 108, MineNorthStyle.MUTED);
        btn(x, y + 122, w, 22, "CRÉER L'ENTREPRISE", MineNorthStyle.GREEN,
                () -> send(ModNetwork.CREATE, 0, bName.getValue(), bActivity.getValue(), bOwner.getValue(), 0));
    }

    // ------------------------------------------------------------------ rendu
    private String heading() {
        CompanyView c = st.admin() ? company(sel) : company(st.selfId());
        if (st.admin() && creating) return "NOUVELLE ENTREPRISE";
        if (c != null) return (c.name().length() > 20 ? c.name().substring(0, 19) + "…" : c.name()).toUpperCase(java.util.Locale.ROOT);
        return st.admin() ? "GESTION DES ENTREPRISES" : "CRÉER UNE ENTREPRISE";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, heading(), st.admin() ? "OP • ADMINISTRATION" : "MINENORTH RP • REGISTRE DU COMMERCE");
        for (Card c : cards) MineNorthStyle.card(g, c.x(), c.y(), c.w(), c.h(), false, c.accent());
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, W - 28), left + 14, top + H - 14,
                    messageOk ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        }
        super.render(g, mx, my, pt);
    }

    /** Appelé quand l'écran est retiré (Échap, Fermer, autre écran) : le serveur ferme la session du guichet. */
    @Override
    public void removed() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(st.admin(), ModNetwork.CLOSE, 0, "", "", "", 0));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
