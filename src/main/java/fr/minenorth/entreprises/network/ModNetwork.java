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
            DEPOSIT = 18, WITHDRAW = 19, GET_BUSINESS_CARD = 20;

    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthEntreprises.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
    }

    public static void send(ServerPlayer p, StatePacket s) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s); }

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

    /**
     * balance et txs ne sont remplis que si bankAccess (décidé par le serveur) ; txs jamais en mode admin.
     */
    public record CompanyView(int id, String name, String activity, UUID owner, String ownerName, int status,
                              List<GradeView> grades, List<MemberView> members, boolean dissolveRequested,
                              long balance, boolean bankAccess, List<TxView> txs) {
        static void encode(FriendlyByteBuf b, CompanyView c) {
            b.writeVarInt(c.id); b.writeUtf(c.name); b.writeUtf(c.activity); b.writeUUID(c.owner); b.writeUtf(c.ownerName); b.writeVarInt(c.status);
            b.writeCollection(c.grades, GradeView::encode); b.writeCollection(c.members, MemberView::encode); b.writeBoolean(c.dissolveRequested);
            b.writeLong(c.balance); b.writeBoolean(c.bankAccess); b.writeCollection(c.txs, TxView::encode);
        }
        static CompanyView decode(FriendlyByteBuf b) {
            return new CompanyView(b.readVarInt(), b.readUtf(), b.readUtf(), b.readUUID(), b.readUtf(), b.readVarInt(),
                    b.readList(GradeView::decode), b.readList(MemberView::decode), b.readBoolean(),
                    b.readLong(), b.readBoolean(), b.readList(TxView::decode));
        }
    }

    private static String nz(String v) { return v == null ? "" : v; }
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
