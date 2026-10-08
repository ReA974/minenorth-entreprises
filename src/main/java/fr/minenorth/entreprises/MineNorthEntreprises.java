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
        // Facture créée : l'écran de signature s'ouvre chez le client ; le message dans le chat est conservé en rappel.
        InvoiceService.Notifier chat = InvoiceService.notifier;
        InvoiceService.notifier = (payer, inv, companyName) -> {
            ModNetwork.send(payer, new ModNetwork.InvoicePacket(inv.id, companyName, inv.description, inv.cents, inv.issuerName));
            chat.open(payer, inv, companyName);
        };
    }
}
