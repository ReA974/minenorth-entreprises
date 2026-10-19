package fr.minenorth.entreprises.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Application « Entreprise » du téléphone (MikasRevs Phone 2 patché) : le serveur dit au client si l'icône doit s'afficher
 * ({@link State}) et le client demande l'ouverture de l'écran ({@link Open}, revérifiée par le serveur).
 */
public final class PhoneNet {
    private PhoneNet() {}

    /** Enregistre les deux paquets à partir de l'identifiant donné ; renvoie le prochain identifiant libre. */
    public static int register(SimpleChannel ch, int id) {
        ch.registerMessage(id++, State.class, State::encode, State::decode, State::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(id++, Open.class, Open::encode, Open::decode, Open::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    public static void send(SimpleChannel ch, ServerPlayer p, boolean visible) {
        ch.send(PacketDistributor.PLAYER.with(() -> p), new State(visible));
    }

    /** Serveur -> client : l'icône de l'application doit-elle s'afficher ? (information d'affichage seulement : l'ouverture est revérifiée). */
    public record State(boolean visible) {
        static void encode(State p, FriendlyByteBuf b) { b.writeBoolean(p.visible); }
        static State decode(FriendlyByteBuf b) { return new State(b.readBoolean()); }
        static void handle(State p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.entreprises.client.PhoneAppClient.set(p.visible())));
            c.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : ouvrir l'écran de la tablette depuis le téléphone. Le serveur revérifie le métier. */
    public record Open() {
        static void encode(Open p, FriendlyByteBuf b) {}
        static Open decode(FriendlyByteBuf b) { return new Open(); }
        static void handle(Open p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) fr.minenorth.entreprises.PhoneApp.open(sp); });
            c.get().setPacketHandled(true);
        }
    }
}
