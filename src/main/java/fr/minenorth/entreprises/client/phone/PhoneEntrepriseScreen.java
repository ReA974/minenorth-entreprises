package fr.minenorth.entreprises.client.phone;

import fr.minenorth.entreprises.client.MineNorthStyle;
import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.network.ModNetwork.CompanyView;
import fr.minenorth.entreprises.network.ModNetwork.GradeView;
import fr.minenorth.entreprises.network.ModNetwork.MemberView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application « Entreprise » dessinée dans l'écran du téléphone (mode joueur). Mêmes fonctions que l'écran des entreprises
 * ({@link fr.minenorth.entreprises.client.EntrepriseScreen}) : sans entreprise, invitations et création ; demande en attente ; sinon onglets
 * Infos, Employés, Grades (patron), Compte (droit « compte »), Factures (droit « factures »), et passage d'une entreprise à l'autre.
 * Le serveur revérifie chaque action. Mise en page portrait.
 */
public class PhoneEntrepriseScreen extends PhoneAppBase {
    private static final int T_INFOS = 0, T_MEMBERS = 1, T_GRADES = 2, T_TX = 3, T_INV = 4;
    private static final String[] INV_STATUS = {"En attente", "Payée", "Échec du prélèvement", "Refusée", "Annulée", "Expirée"};
    private static final int INV_AWAITING = 0, INV_FAILED = 2;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private ModNetwork.StatePacket st;
    private String message;
    private boolean messageOk;
    /** Onglet affiché (identifiant stable T_*). */
    private int view = T_INFOS;
    private boolean confirm, invoicing, viewNone;
    /** -1 = liste des grades, -2 = nouveau grade, sinon index du grade modifié. */
    private int selGrade = -1, gradePerms;
    private UUID selPayer;
    private final Field name = new Field("Nom (ex : Garage du Nord)", "", 32), activity = new Field("Activité (Garage, Taxi…)", "", 32),
            player = new Field("Pseudo du joueur", "", 32), gradeName = new Field("Nom du grade", "", 32), salary = new Field("Salaire € / paie", "", 12),
            amount = new Field("Montant €", "", 12), recipient = new Field("Destinataire (nom RP)", "", 64), motif = new Field("Motif (facultatif)", "", 64),
            invAmount = new Field("Montant €", "", 12), invDesc = new Field("Description (64 max.)", "", 64);

    public PhoneEntrepriseScreen(ModNetwork.StatePacket st) {
        super("Entreprise");
        this.st = st;
        this.message = st.message();
        this.messageOk = st.ok();
    }

    public void update(ModNetwork.StatePacket s) {
        if (s.selfId() != st.selfId()) { viewNone = false; view = T_INFOS; resetScroll(); }
        this.st = s;
        this.message = s.message();
        this.messageOk = s.ok();
        this.confirm = false;
        if (s.ok() && !s.message().isEmpty()) {
            name.value = activity.value = player.value = amount.value = recipient.value = motif.value = invAmount.value = invDesc.value = "";
            selPayer = null;
            invoicing = false;
            selGrade = -1;
        }
    }

    private void send(int action, int companyId, String a, String b, String c, int n) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(false, action, companyId, cut(a), cut(b), cut(c), n));
    }

    private static String cut(String v) { return v == null ? "" : v.length() > 64 ? v.substring(0, 64) : v; }

    private static String euros(long cents) { return MineNorthStyle.euros(cents); }

    private static UUID me() {
        var p = Minecraft.getInstance().player;
        return p == null ? new UUID(0, 0) : p.getUUID();
    }

    private CompanyView company() {
        if (viewNone) return null;
        for (CompanyView c : st.companies()) if (c.id() == st.selfId()) return c;
        return null;
    }

    private static GradeView gradeOf(CompanyView c, UUID player) {
        for (MemberView m : c.members()) if (m.id().equals(player) && !c.grades().isEmpty())
            return c.grades().get(Math.max(0, Math.min(c.grades().size() - 1, m.grade())));
        return null;
    }

    // ------------------------------------------------------------------ droits et onglets

    private boolean owner(CompanyView c) { return c.owner().equals(me()); }

    private boolean can(CompanyView c, int perm) {
        GradeView g = gradeOf(c, me());
        return owner(c) || (g != null && g.has(perm));
    }

    private List<Integer> tabIds() {
        List<Integer> ids = new ArrayList<>();
        CompanyView c = company();
        if (c == null || c.status() == 0) return ids;
        ids.add(T_INFOS);
        ids.add(T_MEMBERS);
        if (owner(c)) ids.add(T_GRADES);
        if (c.bankAccess()) ids.add(T_TX);
        if (can(c, EntrepriseData.P_INVOICES)) ids.add(T_INV);
        return ids;
    }

    @Override protected String title() { return "Entreprise"; }

    @Override
    protected String[] tabs() {
        List<Integer> ids = tabIds();
        String[] out = new String[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = switch (ids.get(i)) {
            case T_MEMBERS -> "Employés";
            case T_GRADES -> "Grades";
            case T_TX -> "Compte";
            case T_INV -> "Factures";
            default -> "Infos";
        };
        return out;
    }

    @Override
    protected void tabChanged() {
        List<Integer> ids = tabIds();
        if (tab < 0 || tab >= ids.size()) return;
        view = ids.get(tab);
        message = "";
        confirm = invoicing = false;
        selGrade = -1;
        if (view == T_INV || view == T_TX) send(ModNetwork.VIEW_TX, st.selfId(), "", "", "", 0);   // rafraîchit historique, factures, joueurs proches
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        List<Integer> ids = tabIds();
        if (!ids.contains(view)) view = T_INFOS;
        tab = Math.max(0, ids.indexOf(view));
        super.render(g, mx, my, pt);
    }

    @Override
    protected String badge() {
        CompanyView c = company();
        if (c == null) return "Sans entreprise";
        if (c.status() == 0) return "En attente";
        return c.open() ? "Ouverte" : "Fermée";
    }

    @Override
    protected int badgeColor() {
        CompanyView c = company();
        return c == null ? DARK : c.status() == 0 ? PINK : c.open() ? GREEN : RED;
    }

    @Override protected String message() { return message; }

    @Override protected boolean messageError() { return !messageOk; }

    @Override
    protected float content(GuiGraphics g, int mx, int my, float y) {
        y = selector(g, mx, my, y);
        CompanyView c = company();
        if (c == null) return none(g, mx, my, y);
        if (c.status() == 0) return pending(g, mx, my, y, c);
        return switch (view) {
            case T_MEMBERS -> members(g, mx, my, y, c);
            case T_GRADES -> grades(g, mx, my, y, c);
            case T_TX -> account(g, mx, my, y, c);
            case T_INV -> invoices(g, mx, my, y, c);
            default -> infos(g, mx, my, y, c);
        };
    }

    // ------------------------------------------------------------------ outils de dessin

    private float section(GuiGraphics g, String title, float y) {
        text(g, title, L, y, 9, R - L, CYAN);
        return y + 16;
    }

    private void card(GuiGraphics g, float t, float b, int accent) {
        box(g, L, t, R, b, 12, CARD);
        box(g, L + 3, t + 6, L + 6, b - 6, 1.5f, accent);
    }

    private float kv(GuiGraphics g, String key, String value, float y, int color) {
        text(g, key, L + 12, y, 8, R - L - 24, MUTED);
        return wrap(g, value, L + 12, y + 11, 9.5f, R - L - 24, color) + 4;
    }

    /** Plusieurs entreprises (ou invitations / création possibles) : bouton qui passe de l'une à l'autre. */
    private float selector(GuiGraphics g, int mx, int my, float y) {
        List<ModNetwork.MineView> mine = st.mine();
        boolean extra = st.canJoinMore() || !st.invites().isEmpty();
        if (mine.isEmpty() || (mine.size() < 2 && !extra)) return y;
        int n = mine.size() + (extra ? 1 : 0);
        int cur = mine.size();
        if (!viewNone) for (int i = 0; i < mine.size(); i++) if (mine.get(i).id() == st.selfId()) cur = i;
        String label = cur >= mine.size() ? "Invitations / nouvelle entreprise" : mine.get(cur).name();
        int next = (cur + 1) % n;
        button(g, mx, my, L, y, R, y + 22, (cur + 1) + "/" + n + " · " + label + "  (changer)", 9, DARK, true, () -> {
            message = "";
            resetScroll();
            if (next >= mine.size()) viewNone = true;
            else { viewNone = false; view = T_INFOS; send(ModNetwork.SELECT, mine.get(next).id(), "", "", "", 0); }
        });
        return y + 30;
    }

    // ------------------------------------------------------------------ sans entreprise / en attente

    private float none(GuiGraphics g, int mx, int my, float y) {
        for (ModNetwork.InviteView iv : st.invites()) {
            card(g, y, y + 62, CYAN);
            wrap(g, "Invitation : " + iv.company() + " (par " + iv.by() + ")", L + 12, y + 7, 9.5f, R - L - 24, WHITE);
            float mid = (L + R) / 2;
            button(g, mx, my, L + 12, y + 32, mid - 3, y + 54, "Accepter", 9, GREEN, true, () -> send(ModNetwork.ACCEPT, iv.companyId(), "", "", "", 0));
            button(g, mx, my, mid + 3, y + 32, R - 8, y + 54, "Refuser", 9, PINK, true, () -> send(ModNetwork.REFUSE_INVITE, iv.companyId(), "", "", "", 0));
            y += 67;
        }
        y = section(g, "CRÉER UNE ENTREPRISE", y + 4);
        field(g, mx, my, name, L, y, R);
        field(g, mx, my, activity, L, y + 28, R);
        y += 58;
        y = wrap(g, "Frais de création : " + (st.fee() > 0 ? euros(st.fee()) + " (carte bancaire)" : "gratuit"), L, y, 9, R - L, TEXT);
        y = wrap(g, st.validation() ? "La création doit ensuite être validée par un administrateur." : "L'entreprise est active immédiatement.",
                L, y + 2, 8.5f, R - L, MUTED) + 6;
        button(g, mx, my, L, y, R, y + 26, st.canJoinMore() ? "Créer l'entreprise" : "Limite d'entreprises atteinte", 10, GREEN, st.canJoinMore(),
                () -> send(ModNetwork.CREATE, 0, name.value, activity.value, "", 0));
        return y + 34;
    }

    private float pending(GuiGraphics g, int mx, int my, float y, CompanyView c) {
        card(g, y, y + 92, WARN);
        text(g, "Demande en attente de validation", L + 12, y + 7, 10, R - L - 24, WARN);
        float yy = kv(g, "Nom", c.name(), y + 22, TEXT);
        kv(g, "Activité", c.activity(), yy, TEXT);
        y += 98;
        y = wrap(g, "Un administrateur doit valider votre entreprise.", L, y, 8.5f, R - L, MUTED) + 6;
        button(g, mx, my, L, y, R, y + 26, confirm ? "Confirmer l'annulation (remboursée)" : "Annuler la demande", 9.5f, PINK, true, () -> {
            if (confirm) send(ModNetwork.DISSOLVE, c.id(), "", "", "", 0); else confirm = true;
        });
        return y + 34;
    }

    // ------------------------------------------------------------------ infos

    private float infos(GuiGraphics g, int mx, int my, float y, CompanyView c) {
        boolean owner = owner(c);
        GradeView mine = gradeOf(c, me());
        long payroll = 0;
        for (MemberView m : c.members()) if (!c.grades().isEmpty()) payroll += c.grades().get(Math.max(0, Math.min(c.grades().size() - 1, m.grade()))).salary();
        text(g, c.name(), L, y, 13, R - L, WHITE);
        y += 22;
        float top = y;
        y += 8;
        y = kv(g, "Activité", c.activity(), y, TEXT);
        y = kv(g, "Patron", c.ownerName(), y, TEXT);
        y = kv(g, "Statut", (c.dissolveRequested() ? "Active · dissolution demandée" : "Active") + " · " + (c.open() ? "ouverte" : "fermée"), y, c.open() ? OK : WARN);
        y = kv(g, "Employés", c.members().size() + " / " + st.maxEmployees(), y, TEXT);
        y = kv(g, owner ? "Votre rôle" : "Votre grade", owner ? "Patron" : mine == null ? "—" : mine.name() + " · salaire " + euros(mine.salary()), y, TEXT);
        y = kv(g, "Masse salariale", euros(payroll) + " par paie (toutes les " + st.payMinutes() + " min)", y, TEXT);
        if (c.bankAccess()) y = kv(g, "Solde du compte", euros(c.balance()), y, c.balance() > 0 ? OK : ALERT);
        box(g, L, top, L + 3, y, 1.5f, CYAN);
        y = wrap(g, "Salaires versés aux employés connectés, prélevés sur le compte de l'entreprise.", L, y + 4, 8.5f, R - L, MUTED) + 8;
        if (can(c, EntrepriseData.P_OPEN)) {
            button(g, mx, my, L, y, R, y + 26, c.open() ? "Fermer l'entreprise" : "Ouvrir l'entreprise", 10, c.open() ? PINK : GREEN, true,
                    () -> send(ModNetwork.SET_OPEN, c.id(), "", "", "", c.open() ? 0 : 1));
            y += 32;
        }
        if (owner) {
            // Le PDG ne peut ni renommer ni dissoudre lui-même : il demande la dissolution à un administrateur.
            button(g, mx, my, L, y, R, y + 26, "Obtenir la tablette", 10, CYAN, true, () -> send(ModNetwork.GET_TABLET, c.id(), "", "", "", 0));
            y += 32;
            if (c.dissolveRequested()) button(g, mx, my, L, y, R, y + 26, "Annuler la dissolution", 10, DARK, true,
                    () -> send(ModNetwork.REQUEST_DISSOLVE, c.id(), "0", "", "", 0));
            else button(g, mx, my, L, y, R, y + 26, confirm ? "Confirmer la demande" : "Demander la dissolution", 10, PINK, true, () -> {
                if (confirm) send(ModNetwork.REQUEST_DISSOLVE, c.id(), "1", "", "", 0); else confirm = true;
            });
        } else {
            button(g, mx, my, L, y, R, y + 26, confirm ? "Confirmer la démission" : "Démissionner", 10, PINK, true, () -> {
                if (confirm) send(ModNetwork.LEAVE, c.id(), "", "", "", 0); else confirm = true;
            });
        }
        return y + 34;
    }

    // ------------------------------------------------------------------ employés

    private float members(GuiGraphics g, int mx, int my, float y, CompanyView c) {
        boolean owner = owner(c), manage = can(c, EntrepriseData.P_RECRUIT);
        UUID me = me();
        int last = c.grades().size() - 1;
        if (c.members().isEmpty()) y = wrap(g, "Aucun employé pour le moment.", L, y, 9.5f, R - L, MUTED) + 4;
        for (MemberView m : c.members()) {
            GradeView gr = c.grades().isEmpty() ? null : c.grades().get(Math.max(0, Math.min(last, m.grade())));
            card(g, y, y + 36, m.online() ? OK : DARK);
            text(g, m.name(), L + 12, y + 6, 10, R - L - 110, m.online() ? WHITE : MUTED);
            text(g, gr == null ? "—" : gr.name() + " · " + euros(gr.salary()), L + 12, y + 20, 8.5f, R - L - 110, TEXT);
            String id = m.id().toString();
            if (owner) {
                // « + » = monter en grade (index plus petit), « - » = descendre.
                button(g, mx, my, R - 86, y + 7, R - 62, y + 29, "+", 10, DARK, m.grade() > 0, () -> send(ModNetwork.SET_GRADE, c.id(), id, "", "", m.grade() - 1));
                button(g, mx, my, R - 58, y + 7, R - 34, y + 29, "-", 10, DARK, m.grade() < last, () -> send(ModNetwork.SET_GRADE, c.id(), id, "", "", m.grade() + 1));
            }
            if (owner || (manage && !m.id().equals(me) && (gr == null || !gr.manage())))
                button(g, mx, my, R - 30, y + 7, R - 6, y + 29, "X", 9, PINK, true, () -> send(ModNetwork.FIRE, c.id(), id, "", "", 0));
            y += 41;
        }
        if (!manage) return y;
        y = section(g, "EMBAUCHER", y + 4);
        field(g, mx, my, player, L, y, R);
        button(g, mx, my, L, y + 28, R, y + 52, "Embaucher", 9.5f, GREEN, !player.value.isBlank(), () -> send(ModNetwork.INVITE, c.id(), player.value, "", "", 0));
        return y + 60;
    }

    // ------------------------------------------------------------------ grades (patron)

    private static String permsSummary(int perms) {
        int n = Integer.bitCount(perms & EntrepriseData.P_ALL);
        return n == 0 ? "aucun droit" : n == EntrepriseData.P_ALL_COUNT ? "tous les droits" : n + " droit" + (n > 1 ? "s" : "");
    }

    private float grades(GuiGraphics g, int mx, int my, float y, CompanyView c) {
        List<GradeView> grades = c.grades();
        if (selGrade == -1) {
            for (int i = 0; i < grades.size(); i++) {
                GradeView gr = grades.get(i);
                final int idx = i;
                card(g, y, y + 36, gr.manage() ? OK : DARK);
                text(g, (i + 1) + ". " + gr.name(), L + 12, y + 6, 10, R - L - 100, WHITE);
                text(g, euros(gr.salary()) + " / paie · " + permsSummary(gr.perms()), L + 12, y + 20, 8.5f, R - L - 100, TEXT);
                button(g, mx, my, R - 80, y + 7, R - 6, y + 29, "Modifier", 9, DARK, true, () -> {
                    selGrade = idx; gradePerms = gr.perms(); gradeName.value = gr.name();
                    salary.value = euros(gr.salary()).replace(" €", "");
                    resetScroll();
                });
                y += 41;
            }
            y = wrap(g, "Vous choisissez les droits de chaque grade (Modifier).", L, y + 2, 8.5f, R - L, MUTED) + 6;
            button(g, mx, my, L, y, R, y + 26, "Nouveau grade", 10, DARK, grades.size() < st.maxGrades(), () -> {
                selGrade = -2; gradePerms = 0; gradeName.value = ""; salary.value = ""; resetScroll();
            });
            return y + 34;
        }
        boolean isNew = selGrade < 0 || selGrade >= grades.size();
        y = section(g, isNew ? "NOUVEAU GRADE" : "MODIFIER LE GRADE", y);
        field(g, mx, my, gradeName, L, y, R);
        field(g, mx, my, salary, L, y + 28, R);
        y = section(g, "DROITS (toucher pour accorder ou retirer)", y + 60);
        for (int i = 0; i < EntrepriseData.P_LABELS.length; i++) {
            final int bit = 1 << i;
            boolean on = (gradePerms & bit) != 0;
            button(g, mx, my, L, y, R, y + 24, (on ? "OUI · " : "NON · ") + EntrepriseData.P_LABELS[i], 9, on ? GREEN : DARK, true, () -> gradePerms ^= bit);
            y += 29;
        }
        y = wrap(g, "Le patron a toujours tous les droits.", L, y + 2, 8.5f, R - L, MUTED) + 6;
        final int sel = selGrade;
        button(g, mx, my, L, y, R, y + 26, "Enregistrer", 10, GREEN, true,
                () -> send(ModNetwork.GRADE_SAVE, c.id(), gradeName.value, salary.value, String.valueOf(gradePerms), isNew ? -1 : sel));
        y += 32;
        float mid = (L + R) / 2;
        if (!isNew) button(g, mx, my, L, y, mid - 3, y + 24, "Supprimer", 9.5f, PINK, true, () -> send(ModNetwork.GRADE_DELETE, c.id(), "", "", "", sel));
        button(g, mx, my, isNew ? L : mid + 3, y, R, y + 24, "Annuler", 9.5f, DARK, true, () -> { selGrade = -1; resetScroll(); });
        return y + 32;
    }

    // ------------------------------------------------------------------ compte

    private float account(GuiGraphics g, int mx, int my, float y, CompanyView c) {
        box(g, L, y, R, y + 56, 14, CARD);
        text(g, "Solde du compte", L + 12, y + 8, 9, 200, MUTED);
        text(g, euros(c.balance()), L + 12, y + 23, 18, R - L - 24, c.balance() > 0 ? OK : ALERT);
        y += 64;
        button(g, mx, my, L, y, R, y + 24, "Obtenir ma carte entreprise", 9.5f, CYAN, true, () -> send(ModNetwork.GET_BUSINESS_CARD, c.id(), "", "", "", 0));
        y += 32;
        y = section(g, "DÉPÔT ET VIREMENTS", y);
        field(g, mx, my, amount, L, y, R);
        y += 28;
        float mid = (L + R) / 2;
        button(g, mx, my, L, y, mid - 3, y + 24, "Déposer", 9.5f, GREEN, true, () -> send(ModNetwork.DEPOSIT, c.id(), amount.value, "", "", 0));
        button(g, mx, my, mid + 3, y, R, y + 24, "Vers mon compte", 9.5f, DARK, true, () -> send(ModNetwork.WITHDRAW, c.id(), amount.value, "", "", 0));
        y += 30;
        // Virement vers un joueur (nom RP) : même montant ; le motif est facultatif.
        field(g, mx, my, recipient, L, y, R);
        field(g, mx, my, motif, L, y + 28, R);
        button(g, mx, my, L, y + 56, R, y + 80, "Virer à un joueur", 9.5f, GREEN, !recipient.value.isBlank(),
                () -> send(ModNetwork.TRANSFER_PLAYER, c.id(), amount.value, recipient.value, motif.value, 0));
        y = section(g, "HISTORIQUE", y + 90);
        if (c.txs().isEmpty()) return wrap(g, "Aucune transaction pour le moment.", L, y, 9.5f, R - L, MUTED);
        for (ModNetwork.TxView t : c.txs()) {
            boolean plus = t.cents() >= 0;
            int color = plus ? OK : ALERT;
            card(g, y, y + 36, color);
            text(g, t.label(), L + 12, y + 6, 9.5f, R - L - 110, WHITE);
            right(g, (plus ? "+" : "") + euros(t.cents()), R - 10, y + 6, 10, color);
            text(g, DATE.format(Instant.ofEpochMilli(t.time())) + " · " + t.actor(), L + 12, y + 20, 8, R - L - 110, MUTED);
            right(g, "solde " + euros(t.balanceAfter()), R - 10, y + 21, 8, TEXT);
            y += 40;
        }
        return y;
    }

    // ------------------------------------------------------------------ factures

    private float invoices(GuiGraphics g, int mx, int my, float y, CompanyView c) {
        button(g, mx, my, L, y, R, y + 24, invoicing ? "Retour à la liste" : "Nouvelle facture", 9.5f, invoicing ? DARK : GREEN, true, () -> {
            if (!invoicing) send(ModNetwork.VIEW_TX, c.id(), "", "", "", 0);   // rafraîchit les joueurs proches
            invoicing = !invoicing;
            resetScroll();
        });
        y += 32;
        if (invoicing) return invoiceForm(g, mx, my, y, c);
        text(g, ModNetwork.INVOICES_SENT + " dernières factures", L, y, 8.5f, R - L, MUTED);
        y += 14;
        if (c.invoices().isEmpty()) return wrap(g, "Aucune facture pour le moment.", L, y, 9.5f, R - L, MUTED);
        for (ModNetwork.InvoiceView v : c.invoices()) {
            int si = v.status() >= 0 && v.status() < INV_STATUS.length ? v.status() : 4;
            int color = si == 1 ? OK : si == INV_AWAITING ? WARN : si == INV_FAILED || si == 3 ? ALERT : MUTED;
            boolean cancel = si == INV_AWAITING || si == INV_FAILED;
            float h = cancel ? 72 : 50;
            card(g, y, y + h, color);
            text(g, v.payerName(), L + 12, y + 6, 10, R - L - 100, WHITE);
            right(g, euros(v.cents()), R - 10, y + 6, 10, WHITE);
            text(g, v.description(), L + 12, y + 20, 8.5f, R - L - 24, TEXT);
            text(g, DATE.format(Instant.ofEpochMilli(v.created())) + " · " + INV_STATUS[si], L + 12, y + 33, 8.5f, R - L - 24, color);
            if (cancel) {
                final int id = v.id();
                button(g, mx, my, L + 12, y + 46, R - 8, y + 66, "Annuler la facture", 9, PINK, true, () -> send(ModNetwork.CANCEL_INVOICE, c.id(), "", "", "", id));
            }
            y += h + 5;
        }
        return y;
    }

    private float invoiceForm(GuiGraphics g, int mx, int my, float y, CompanyView c) {
        List<ModNetwork.PlayerView> near = c.nearby();
        boolean found = false;
        for (ModNetwork.PlayerView pv : near) if (pv.id().equals(selPayer)) found = true;
        if (!found) selPayer = null;   // joueur parti entre deux rafraîchissements
        y = section(g, "CLIENT (JOUEUR À PROXIMITÉ)", y);
        if (near.isEmpty()) y = wrap(g, "Aucun joueur à proximité.", L, y, 9.5f, R - L, MUTED) + 4;
        float w = (R - L - 5) / 2;
        for (int i = 0; i < near.size(); i++) {
            ModNetwork.PlayerView pv = near.get(i);
            float l = L + (i % 2) * (w + 5);
            button(g, mx, my, l, y, l + w, y + 22, pv.name(), 9, pv.id().equals(selPayer) ? CYAN : DARK, true, () -> selPayer = pv.id());
            if (i % 2 == 1 || i == near.size() - 1) y += 27;
        }
        y = section(g, "FACTURE", y + 4);
        field(g, mx, my, invAmount, L, y, R);
        field(g, mx, my, invDesc, L, y + 28, R);
        button(g, mx, my, L, y + 56, R, y + 82, "Créer la facture", 10, GREEN, selPayer != null,
                () -> send(ModNetwork.CREATE_INVOICE, c.id(), selPayer == null ? "" : selPayer.toString(), invAmount.value, invDesc.value, 0));
        return wrap(g, "Le client doit être à proximité et signer la facture sur son écran.", L, y + 90, 8.5f, R - L, MUTED);
    }

    // ------------------------------------------------------------------ cycle de vie

    /** Retour à l'accueil du téléphone (ou autre écran) : le serveur ferme la session du guichet. */
    @Override
    public void removed() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(false, ModNetwork.CLOSE, 0, "", "", "", 0));
        super.removed();
    }
}
