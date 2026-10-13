package fr.minenorth.entreprises.workshop;

import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Paquets de la cabine de peinture, enregistrés sur le canal du mod. */
public final class WorkshopPackets {
    private WorkshopPackets() {}

    public static final int ACT_APPLY = 1, ACT_REFRESH = 2;
    private static final int MAX_ROWS = 64;

    public static void register(int firstId) {
        ModNetwork.CHANNEL.registerMessage(firstId, State.class, State::encode, State::decode, State::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ModNetwork.CHANNEL.registerMessage(firstId + 1, Action.class, Action::encode, Action::decode, Action::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(ServerPlayer p, State s) { ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s); }

    private static String cut(String v, int max) { v = v == null ? "" : v; return v.length() > max ? v.substring(0, max) : v; }

    /** Une peinture proposée ; {@code current} = celle que porte déjà le véhicule. */
    public record Row(String label, boolean current) {
        static void encode(FriendlyByteBuf b, Row r) { b.writeUtf(cut(r.label, 64), 64); b.writeBoolean(r.current); }
        static Row decode(FriendlyByteBuf b) { return new Row(b.readUtf(64), b.readBoolean()); }
    }

    /** Serveur → client : état de l'écran (l'ouvre ou le met à jour). */
    public record State(BlockPos pos, String company, String vehicle, String message, boolean ok, List<Row> rows) {
        static void encode(State s, FriendlyByteBuf b) {
            b.writeBlockPos(s.pos); b.writeUtf(cut(s.company, 64), 64); b.writeUtf(cut(s.vehicle, 64), 64);
            b.writeUtf(cut(s.message, 120), 120); b.writeBoolean(s.ok); b.writeCollection(s.rows, Row::encode);
        }
        static State decode(FriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            String company = b.readUtf(64), vehicle = b.readUtf(64), message = b.readUtf(120);
            boolean ok = b.readBoolean();
            int n = b.readVarInt();
            if (n < 0 || n > MAX_ROWS) throw new io.netty.handler.codec.DecoderException("Liste trop longue : " + n);
            List<Row> rows = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) rows.add(Row.decode(b));
            return new State(pos, company, vehicle, message, ok, rows);
        }
        static void handle(State s, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.entreprises.client.WorkshopScreen.show(s)));
            c.get().setPacketHandled(true);
        }
    }

    /** Client → serveur : appliquer la peinture {@code index} (ACT_APPLY) ou rafraîchir. Tout est revérifié. */
    public record Action(BlockPos pos, int action, int index) {
        static void encode(Action a, FriendlyByteBuf b) { b.writeBlockPos(a.pos); b.writeVarInt(a.action); b.writeVarInt(a.index); }
        static Action decode(FriendlyByteBuf b) { return new Action(b.readBlockPos(), b.readVarInt(), b.readVarInt()); }
        static void handle(Action a, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) WorkshopService.handle(sp, a); });
            c.get().setPacketHandled(true);
        }
    }
}
