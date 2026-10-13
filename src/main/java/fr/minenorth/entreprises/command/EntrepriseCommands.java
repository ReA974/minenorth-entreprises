package fr.minenorth.entreprises.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.minenorth.api.MineNorth;
import fr.minenorth.entreprises.EntrepriseService;
import fr.minenorth.entreprises.data.EntrepriseData;
import net.minecraft.commands.SharedSuggestionProvider;
import java.util.UUID;
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
        // Relie le capital de la banque (prêts) au compte de l'entreprise « banque ».
        d.register(Commands.literal("entreprisebanque").requires(s -> s.hasPermission(2))
                .executes(c -> {
                    var s = c.getSource().getServer();
                    UUID acc = MineNorth.bank().bankCapitalAccount(s);
                    String name = acc == null ? null : EntrepriseData.get(s).all().stream()
                            .filter(co -> acc.equals(co.accountId)).map(co -> co.name).findFirst().orElse("compte " + acc);
                    c.getSource().sendSystemMessage(Component.literal(name == null
                            ? "§eLe capital de la banque n'est relié à aucune entreprise. §7/entreprisebanque <nom> pour relier."
                            : "§aCapital de la banque relié à l'entreprise : §f" + name));
                    return 1;
                })
                .then(Commands.literal("aucune").executes(c -> {
                    MineNorth.bank().linkBankCapital(c.getSource().getServer(), null);
                    c.getSource().sendSystemMessage(Component.literal("§eCapital de la banque délié (il repart de zéro)."));
                    return 1;
                }))
                .then(Commands.argument("nom", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(EntrepriseData.get(c.getSource().getServer()).all().stream()
                                .filter(co -> co.status == EntrepriseData.ACTIVE).map(co -> co.name), b))
                        .executes(c -> {
                            var s = c.getSource().getServer();
                            String wanted = StringArgumentType.getString(c, "nom").trim();
                            var co = EntrepriseData.get(s).all().stream().filter(x -> x.name.equalsIgnoreCase(wanted)).findFirst().orElse(null);
                            if (co == null || co.status != EntrepriseData.ACTIVE) {
                                c.getSource().sendFailure(Component.literal("Entreprise introuvable ou non active : " + wanted));
                                return 0;
                            }
                            if (!MineNorth.bank().linkBankCapital(s, co.accountId)) {
                                c.getSource().sendFailure(Component.literal("Impossible : l'entreprise n'a pas de compte bancaire."));
                                return 0;
                            }
                            c.getSource().sendSystemMessage(Component.literal("§aLe capital de la banque (prêts) est maintenant le compte de l'entreprise §f"
                                    + co.name + "§a. Le capital interne y a été versé."));
                            return 1;
                        })));
        d.register(Commands.literal("entreprisereload").requires(s -> s.hasPermission(2)).executes(c -> {
            boolean ok = EntrepriseConfig.load();
            c.getSource().sendSystemMessage(Component.literal(ok ? "§aConfiguration des entreprises rechargée."
                    : "§cFichier minenorth_entreprises.json illisible : ancienne configuration conservée."));
            return ok ? 1 : 0;
        }));
    }
}
