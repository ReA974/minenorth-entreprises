package fr.minenorth.entreprises;

import fr.minenorth.entreprises.config.EntrepriseConfig;
import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.item.ModItems;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MineNorthEntreprises.MOD_ID)
public class MineNorthEntreprises {
    public static final String MOD_ID = "minenorthentreprises";

    public MineNorthEntreprises() {
        EntrepriseConfig.load();
        ModNetwork.register();
        ModItems.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
