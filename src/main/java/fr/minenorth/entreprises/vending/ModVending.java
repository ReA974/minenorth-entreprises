package fr.minenorth.entreprises.vending;

import fr.minenorth.entreprises.MineNorthEntreprises;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Enregistrements du distributeur automatique (bloc, entité de bloc, menu). L'objet est dans {@code ModItems}. */
public final class ModVending {
    private ModVending() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthEntreprises.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineNorthEntreprises.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MineNorthEntreprises.MOD_ID);

    /** Variantes visuelles (même fonctionnement, autre façade et autres bruits) : boissons, snacks, café, mixte, tout-en-un. */
    public static final String[] KINDS = {"boissons", "snacks", "cafe", "mixte", "toutenun"};

    public static final RegistryObject<Block> VENDING = BLOCKS.register("distributeur", () -> new VendingMachineBlock("classique"));
    public static final java.util.Map<String, RegistryObject<Block>> VARIANTS = new java.util.LinkedHashMap<>();
    static {
        for (String k : KINDS) VARIANTS.put(k, BLOCKS.register("distributeur_" + k, () -> new VendingMachineBlock(k)));
    }
    public static final RegistryObject<BlockEntityType<VendingMachineBE>> VENDING_BE = BLOCK_ENTITIES.register("distributeur",
            () -> {
                java.util.List<Block> all = new java.util.ArrayList<>();
                all.add(VENDING.get());
                for (RegistryObject<Block> v : VARIANTS.values()) all.add(v.get());
                return BlockEntityType.Builder.of(VendingMachineBE::new, all.toArray(new Block[0])).build(null);
            });
    public static final RegistryObject<MenuType<VendingMenu>> VENDING_MENU = MENUS.register("distributeur",
            () -> IForgeMenuType.create(VendingMenu::new));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
