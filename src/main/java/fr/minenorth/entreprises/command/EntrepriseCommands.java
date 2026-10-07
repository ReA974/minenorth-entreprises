package fr.minenorth.entreprises.command;

import fr.minenorth.entreprises.EntrepriseService;
import fr.minenorth.entreprises.config.EntrepriseConfig;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Toutes les commandes sont réservées aux OP / à la console (niveau 2) :
 * les joueurs ne peuvent ouvrir le menu qu'en passant par le PNJ qui exécute /entreprise <joueur>.
 */
@Mod.EventBusSubscriber
public final class EntrepriseCommands {
    private EntrepriseCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        d.register(Commands.literal("entreprise").requires(s -> s.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                    EntrepriseService.openFor(target);
                    return 1;
                })));
        d.register(Commands.literal("entrepriseadmin").requires(s -> s.hasPermission(2)).executes(c -> {
            EntrepriseService.openAdmin(c.getSource().getPlayerOrException());
            return 1;
        }));
        d.register(Commands.literal("entreprisereload").requires(s -> s.hasPermission(2)).executes(c -> {
            boolean ok = EntrepriseConfig.load();
            c.getSource().sendSystemMessage(Component.literal(ok ? "§aConfiguration des entreprises rechargée."
                    : "§cFichier minenorth_entreprises.json illisible : ancienne configuration conservée."));
            return ok ? 1 : 0;
        }));
    }
}
