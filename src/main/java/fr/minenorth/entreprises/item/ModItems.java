package fr.minenorth.entreprises.item;

import fr.minenorth.entreprises.MineNorthEntreprises;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthEntreprises.MOD_ID);
    public static final RegistryObject<Item> TABLET = ITEMS.register("tablette_entreprise", TabletItem::new);

    public static final RegistryObject<Item> VENDING = ITEMS.register("distributeur",
            () -> new fr.minenorth.entreprises.vending.VendingItem(fr.minenorth.entreprises.vending.ModVending.VENDING.get(), new Item.Properties()));

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthEntreprises.MOD_ID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("entreprises", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.minenorthentreprises"))
            .icon(() -> new net.minecraft.world.item.ItemStack(TABLET.get()))
            .displayItems((params, out) -> { out.accept(TABLET.get()); out.accept(VENDING.get()); })
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
