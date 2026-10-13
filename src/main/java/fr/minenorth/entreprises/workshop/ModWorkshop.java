package fr.minenorth.entreprises.workshop;

import fr.minenorth.entreprises.MineNorthEntreprises;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Enregistrements de la cabine de peinture (l'objet est dans {@code ModItems}). */
public final class ModWorkshop {
    private ModWorkshop() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthEntreprises.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineNorthEntreprises.MOD_ID);

    public static final RegistryObject<Block> PAINT = BLOCKS.register("cabine_peinture", WorkshopBlock::new);
    public static final RegistryObject<BlockEntityType<WorkshopBE>> WORKSHOP_BE = BLOCK_ENTITIES.register("cabine_peinture",
            () -> BlockEntityType.Builder.of(WorkshopBE::new, PAINT.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
