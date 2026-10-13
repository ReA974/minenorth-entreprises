package fr.minenorth.entreprises.workshop;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Cabine de peinture rattachée à une entreprise (seule donnée : l'entreprise propriétaire). */
public class WorkshopBE extends BlockEntity {
    /** Entreprise propriétaire, -1 si aucune (poste inerte). */
    public int companyId = -1;

    public WorkshopBE(BlockPos pos, BlockState state) {
        super(ModWorkshop.WORKSHOP_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("company", companyId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        companyId = tag.contains("company") ? tag.getInt("company") : -1;
    }
}
