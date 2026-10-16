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

    /** Variantes du distributeur (clé : boissons, snacks, cafe, mixte, toutenun). */
    public static final java.util.Map<String, RegistryObject<Item>> VENDING_VARIANTS = new java.util.LinkedHashMap<>();
    static {
        for (String k : fr.minenorth.entreprises.vending.ModVending.KINDS)
            VENDING_VARIANTS.put(k, ITEMS.register("distributeur_" + k, () -> new fr.minenorth.entreprises.vending.VendingItem(
                    fr.minenorth.entreprises.vending.ModVending.VARIANTS.get(k).get(), new Item.Properties())));
    }

    public static final RegistryObject<Item> PAINT_BOOTH = ITEMS.register("cabine_peinture",
            () -> new fr.minenorth.entreprises.workshop.WorkshopItem(fr.minenorth.entreprises.workshop.ModWorkshop.PAINT.get(), new Item.Properties()));

    public static final RegistryObject<Item> RESTAURANT_MENU = ITEMS.register("carte_restaurant", RestaurantMenuItem::new);

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthEntreprises.MOD_ID);
    public static final net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator TAB_ITEMS = (params, out) -> { out.accept(TABLET.get()); out.accept(VENDING.get()); VENDING_VARIANTS.values().forEach(v -> out.accept(v.get())); out.accept(PAINT_BOOTH.get()); out.accept(RESTAURANT_MENU.get()); };

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
