package fr.minenorth.entreprises.menustand;

import fr.minenorth.entreprises.MineNorthEntreprises;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Distributeur de menus : une pile de cartes de restaurant à poser, qui en donne une copie à chaque clic droit. */
public final class ModMenuStand {
    private ModMenuStand() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthEntreprises.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineNorthEntreprises.MOD_ID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("distributeur_menus", MenuStandBlock::new);
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MenuStandBE>> BE = BLOCK_ENTITIES.register("distributeur_menus",
            () -> BlockEntityType.Builder.of(MenuStandBE::new, BLOCK.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
