package fr.minenorth.entreprises.workshop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Objet d'un poste d'atelier : seul un patron ou un gérant d'une entreprise active peut le poser. */
public class WorkshopItem extends BlockItem {
    public WorkshopItem(Block block, Properties props) { super(block, props); }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp && WorkshopService.companyFor(sp) == null) {
            sp.displayClientMessage(WorkshopBlock.refusal(), true);
            return InteractionResult.FAIL;
        }
        return super.place(ctx);
    }
}
