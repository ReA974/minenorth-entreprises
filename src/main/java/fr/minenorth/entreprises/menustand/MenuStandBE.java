package fr.minenorth.entreprises.menustand;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Carte donnée par le distributeur (le NBT « Menu » de la carte de restaurant) et entreprise qui l'a définie. Pas de stock. */
public class MenuStandBE extends BlockEntity {
    @Nullable public CompoundTag menu;
    public int company = -1;

    public MenuStandBE(BlockPos pos, BlockState state) { super(ModMenuStand.BE.get(), pos, state); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (menu != null) tag.put("menu", menu.copy());
        tag.putInt("company", company);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        menu = tag.contains("menu", 10) ? tag.getCompound("menu").copy() : null;
        company = tag.contains("company") ? tag.getInt("company") : -1;
    }
}
