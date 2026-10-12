package fr.minenorth.entreprises.vending;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Objet du distributeur : seul un patron ou un gérant d'une entreprise active peut en poser un. */
public class VendingItem extends BlockItem {
    public VendingItem(Block block, Properties props) { super(block, props); }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp && VendingService.companyFor(sp) == null) {
            sp.displayClientMessage(Component.literal("§cSeul le patron ou un gérant d'une entreprise active peut poser un distributeur."), true);
            return InteractionResult.FAIL;
        }
        return super.place(ctx);
    }
}
