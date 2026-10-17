package fr.minenorth.entreprises.menustand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : Ctrl + clic droit sur un distributeur de menus (le serveur revérifie portée, bloc, carte en main et droits). */
public record SetMenuPacket(BlockPos pos) {
    public static void encode(SetMenuPacket p, FriendlyByteBuf b) { b.writeBlockPos(p.pos); }

    public static SetMenuPacket decode(FriendlyByteBuf b) { return new SetMenuPacket(b.readBlockPos()); }

    public static void handle(SetMenuPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sp == null || !sp.level().isLoaded(p.pos) || sp.distanceToSqr(p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5) > 36) return;
            if (sp.level().getBlockEntity(p.pos) instanceof MenuStandBE be) MenuStandBlock.configure(sp, be);
        });
        ctx.get().setPacketHandled(true);
    }
}
