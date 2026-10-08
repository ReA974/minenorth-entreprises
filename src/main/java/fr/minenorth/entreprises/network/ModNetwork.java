package fr.minenorth.entreprises.network;

import fr.minenorth.entreprises.EntrepriseService;
import fr.minenorth.entreprises.MineNorthEntreprises;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModNetwork {
    private ModNetwork() {}

    // Actions envoyées par l'interface.
    public static final int CREATE = 1, DISSOLVE = 2, INVITE = 3, FIRE = 4, SET_GRADE = 5, GRADE_SAVE = 6, GRADE_DELETE = 7,
            ACCEPT = 8, REFUSE_INVITE = 9, LEAVE = 10, VALIDATE = 11, REFUSE = 12, EDIT = 13, CLOSE = 14,
            REQUEST_DISSOLVE = 15, REJECT_DISSOLVE = 16, GET_TABLET = 17,
            DEPOSIT = 18, WITHDRAW = 19, GET_BUSINESS_CARD = 20,
            TRANSFER_PLAYER = 21,   // a = montant (€), b = nom RP du destinataire, c = motif (≤ 64)
            VIEW_TX = 22,           // OP : companyId = entreprise ciblée (historique et factures) ; joueur : simple rafraîchissement
            CREATE_INVOICE = 23,    // a = UUID du client, b = montant (€), c = description (≤ 64)
            CANCEL_INVOICE = 24;    // n = id de la facture

    private static final String PROTOCOL = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthEntreprises.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, InvoicePacket.class, InvoicePacket::encode, InvoicePacket::decode, InvoicePacket::handle);
        CHANNEL.registerMessage(id++, InvoiceActionPacket.class, InvoiceActionPacket::encode, InvoiceActionPacket::decode, InvoiceActionPacket::handle);
    }

    public static void send(ServerPlayer p, StatePacket s) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s); }
    public static void send(ServerPlayer p, InvoicePacket s) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s); }

    public record GradeView(String name, long salary, boolean manage) {
        static void encode(FriendlyByteBuf b, GradeView g) { b.writeUtf(g.name); b.writeLong(g.salary); b.writeBoolean(g.manage); }
        static GradeView decode(FriendlyByteBuf b) { return new GradeView(b.readUtf(), b.readLong(), b.readBoolean()); }
    }
    public record MemberView(UUID id, String name, int grade, boolean online) {
        static void encode(FriendlyByteBuf b, MemberView m) { b.writeUUID(m.id); b.writeUtf(m.name); b.writeVarInt(m.grade); b.writeBoolean(m.online); }
        static MemberView decode(FriendlyByteBuf b) { return new MemberView(b.readUUID(), b.readUtf(), b.readVarInt(), b.readBoolean()); }
    }
    /** Une ligne de l'historique du compte entreprise (copie de {@code fr.minenorth.api.BankTx}). */
    public record TxView(long time, String category, long cents, long balanceAfter, String label, String actor) {
        static void encode(FriendlyByteBuf b, TxView t) {
            b.writeLong(t.time); b.writeUtf(nz(t.category)); b.writeLong(t.cents); b.writeLong(t.balanceAfter);
            b.writeUtf(nz(t.label)); b.writeUtf(nz(t.actor));
        }
        static TxView decode(FriendlyByteBuf b) {
            return new TxView(b.readLong(), b.readUtf(), b.readLong(), b.readLong(), b.readUtf(), b.readUtf());
        }
    }
    /** Nombre maximum de transactions envoyées au client (les plus récentes). */
    public static final int TX_SENT = 50;
    /** Nombre maximum de factures envoyées au client (les plus récentes) et de joueurs proches proposés. */
    public static final int INVOICES_SENT = 50, NEARBY_SENT = 20;

    /** Une facture de l'entreprise ; status = ordinal de {@code EntrepriseData.Invoice.Status}. */
    public record InvoiceView(int id, long created, String payerName, String description, long cents, int status) {
        static void encode(FriendlyByteBuf b, InvoiceView v) {
            b.writeVarInt(v.id); b.writeLong(v.created); b.writeUtf(cut(v.payerName, 64), 64); b.writeUtf(cut(v.description, 64), 64);
            b.writeLong(v.cents); b.writeVarInt(v.status);
        }
        static InvoiceView decode(FriendlyByteBuf b) {
            return new InvoiceView(b.readVarInt(), b.readLong(), b.readUtf(64), b.readUtf(64), b.readLong(), b.readVarInt());
        }
    }
    /** Joueur proche pouvant recevoir une facture (nom RP). */
    public record PlayerView(UUID id, String name) {
        static void encode(FriendlyByteBuf b, PlayerView v) { b.writeUUID(v.id); b.writeUtf(cut(v.name, 64), 64); }
        static PlayerView decode(FriendlyByteBuf b) { return new PlayerView(b.readUUID(), b.readUtf(64)); }
    }

    /**
     * Mode joueur : balance et txs ne sont remplis que si bankAccess (décidé par le serveur).
     * Mode admin (OP) : bankAccess = false, balance rempli pour toutes les entreprises, txs seulement pour l'entreprise ciblée (VIEW_TX).
     * invoices suit la même règle que txs ; nearby n'est rempli qu'en mode joueur avec bankAccess.
     */
    public record CompanyView(int id, String name, String activity, UUID owner, String ownerName, int status,
                              List<GradeView> grades, List<MemberView> members, boolean dissolveRequested,
                              long balance, boolean bankAccess, List<TxView> txs,
                              List<InvoiceView> invoices, List<PlayerView> nearby) {
        static void encode(FriendlyByteBuf b, CompanyView c) {
            b.writeVarInt(c.id); b.writeUtf(c.name); b.writeUtf(c.activity); b.writeUUID(c.owner); b.writeUtf(c.ownerName); b.writeVarInt(c.status);
            b.writeCollection(c.grades, GradeView::encode); b.writeCollection(c.members, MemberView::encode); b.writeBoolean(c.dissolveRequested);
            b.writeLong(c.balance); b.writeBoolean(c.bankAccess); b.writeCollection(c.txs, TxView::encode);
            b.writeCollection(c.invoices, InvoiceView::encode); b.writeCollection(c.nearby, PlayerView::encode);
        }
        static CompanyView decode(FriendlyByteBuf b) {
            return new CompanyView(b.readVarInt(), b.readUtf(), b.readUtf(), b.readUUID(), b.readUtf(), b.readVarInt(),
                    b.readList(GradeView::decode), b.readList(MemberView::decode), b.readBoolean(),
                    b.readLong(), b.readBoolean(), b.readList(TxView::decode),
                    b.readList(InvoiceView::decode), b.readList(PlayerView::decode));
        }
    }

    private static String nz(String v) { return v == null ? "" : v; }
    /** Chaîne non nulle tronquée à {@code max} caractères (pour writeUtf avec limite). */
    private static String cut(String v, int max) { v = nz(v); return v.length() > max ? v.substring(0, max) : v; }

    /** Serveur → client : une facture attend la signature du joueur (ouvre l'écran de facture). */
    public record InvoicePacket(int invoiceId, String company, String description, long cents, String issuerName) {
        static void encode(InvoicePacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.invoiceId); b.writeUtf(cut(p.company, 64), 64); b.writeUtf(cut(p.description, 64), 64);
            b.writeLong(p.cents); b.writeUtf(cut(p.issuerName, 64), 64);
        }
        static InvoicePacket decode(FriendlyByteBuf b) {
            return new InvoicePacket(b.readVarInt(), b.readUtf(64), b.readUtf(64), b.readLong(), b.readUtf(64));
        }
        static void handle(InvoicePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.entreprises.client.ClientNetworkHandler.invoice(p)));
            c.get().setPacketHandled(true);
        }
    }

    /**
     * Client → serveur : le joueur signe (sign = true) ou refuse une facture. Pas de garde OPEN (le client n'est pas au
     * guichet) : {@code InvoiceService.sign/refuse} vérifient que l'expéditeur est bien le client de la facture.
     */
    public record InvoiceActionPacket(int invoiceId, boolean sign) {
        static void encode(InvoiceActionPacket p, FriendlyByteBuf b) { b.writeVarInt(p.invoiceId); b.writeBoolean(p.sign); }
        static InvoiceActionPacket decode(FriendlyByteBuf b) { return new InvoiceActionPacket(b.readVarInt(), b.readBoolean()); }
        static void handle(InvoiceActionPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) EntrepriseService.invoiceAction(sp, p.invoiceId, p.sign); });
            c.get().setPacketHandled(true);
        }
    }
    public record InviteView(int companyId, String company, String by) {
        static void encode(FriendlyByteBuf b, InviteView i) { b.writeVarInt(i.companyId); b.writeUtf(i.company); b.writeUtf(i.by); }
        static InviteView decode(FriendlyByteBuf b) { return new InviteView(b.readVarInt(), b.readUtf(), b.readUtf()); }
    }

    /** État complet envoyé au client : ouvre ou met à jour l'interface. selfId = entreprise du joueur (-1 si aucune). */
    public record StatePacket(boolean admin, String message, boolean ok, long fee, boolean validation, int payMinutes,
                              int maxEmployees, int maxGrades, int selfId, List<CompanyView> companies, List<InviteView> invites) {
        static void encode(StatePacket p, FriendlyByteBuf b) {
            b.writeBoolean(p.admin); b.writeUtf(p.message); b.writeBoolean(p.ok); b.writeLong(p.fee); b.writeBoolean(p.validation);
            b.writeVarInt(p.payMinutes); b.writeVarInt(p.maxEmployees); b.writeVarInt(p.maxGrades); b.writeInt(p.selfId);
            b.writeCollection(p.companies, CompanyView::encode); b.writeCollection(p.invites, InviteView::encode);
        }
        static StatePacket decode(FriendlyByteBuf b) {
            return new StatePacket(b.readBoolean(), b.readUtf(), b.readBoolean(), b.readLong(), b.readBoolean(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readInt(),
                    b.readList(CompanyView::decode), b.readList(InviteView::decode));
        }
        static void handle(StatePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.entreprises.client.ClientNetworkHandler.state(p)));
            c.get().setPacketHandled(true);
        }
    }

    public record ActionPacket(boolean admin, int action, int companyId, String a, String b, String c, int n) {
        static void encode(ActionPacket p, FriendlyByteBuf b) {
            b.writeBoolean(p.admin); b.writeVarInt(p.action); b.writeInt(p.companyId);
            b.writeUtf(p.a, 64); b.writeUtf(p.b, 64); b.writeUtf(p.c, 64); b.writeInt(p.n);
        }
        static ActionPacket decode(FriendlyByteBuf b) {
            return new ActionPacket(b.readBoolean(), b.readVarInt(), b.readInt(), b.readUtf(64), b.readUtf(64), b.readUtf(64), b.readInt());
        }
        static void handle(ActionPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) EntrepriseService.handle(sp, p); });
            c.get().setPacketHandled(true);
        }
    }
}
