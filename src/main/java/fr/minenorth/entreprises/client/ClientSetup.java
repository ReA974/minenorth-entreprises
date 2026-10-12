package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.MineNorthEntreprises;
import fr.minenorth.entreprises.vending.ModVending;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Enregistrement des écrans et rendus du client (bus du mod, Dist.CLIENT uniquement). */
@Mod.EventBusSubscriber(modid = MineNorthEntreprises.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(ModVending.VENDING_MENU.get(), VendingScreen::new));
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(ModVending.VENDING_BE.get(), VendingRenderer::new);
    }
}
