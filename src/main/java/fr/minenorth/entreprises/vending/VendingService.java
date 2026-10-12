package fr.minenorth.entreprises.vending;

import fr.minenorth.api.BankService;
import fr.minenorth.api.BankTx;
import fr.minenorth.api.MineNorth;
import fr.minenorth.api.PayResult;
import fr.minenorth.entreprises.CompanyAccounts;
import fr.minenorth.entreprises.MineNorthEntreprises;
import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.data.EntrepriseData.Company;
import fr.minenorth.entreprises.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Règles serveur du distributeur : droits, achat par carte (le paiement va sur le compte de l'entreprise), prix. */
@Mod.EventBusSubscriber(modid = MineNorthEntreprises.MOD_ID)
public final class VendingService {
    private VendingService() {}

    /** Entreprise active dont le joueur est patron ou gérant (celle qui peut poser un distributeur), sinon null. */
    public static Company companyFor(ServerPlayer p) {
        Company c = EntrepriseData.get(p.server).companyOf(p.getUUID());
        return c != null && c.status == EntrepriseData.ACTIVE && CompanyAccounts.canBank(c, p.getUUID()) ? c : null;
    }

    /** Entreprise propriétaire du distributeur, uniquement si elle existe et est active. */
    public static Company company(MinecraftServer s, VendingMachineBE be) {
        Company c = be.companyId < 0 ? null : EntrepriseData.get(s).get(be.companyId);
        return c != null && c.status == EntrepriseData.ACTIVE ? c : null;
    }

    /** Gérer le stock et les prix : patron ou gérant de l'entreprise propriétaire, ou OP. */
    public static boolean canManage(ServerPlayer p, VendingMachineBE be) {
        if (p.hasPermissions(2)) return true;
        Company c = be.companyId < 0 ? null : EntrepriseData.get(p.server).get(be.companyId);
        return c != null && CompanyAccounts.canBank(c, p.getUUID());
    }

    private static void reply(ServerPlayer p, boolean ok, String msg) {
        ModNetwork.send(p, new ModNetwork.VendingResultPacket(ok, msg));
    }

    /** Un client achète un article du distributeur dont il a le menu ouvert. */
    public static void buy(ServerPlayer p, int slot) {
        if (!(p.containerMenu instanceof VendingMenu m) || m.manager) return;
        VendingMachineBE be = m.machine(p.level());
        if (be == null || slot < 0 || slot >= VendingMachineBE.SLOTS) return;
        Company c = company(p.server, be);
        if (c == null) { reply(p, false, "Distributeur hors service."); return; }
        ItemStack st = be.inv.getItem(slot);
        int price = be.price(slot);
        if (st.isEmpty()) { reply(p, false, "Emplacement vide."); return; }
        if (price <= 0) { reply(p, false, "Cet article n'est pas à vendre."); return; }
        BankService bank = MineNorth.bank();
        PayResult r = bank.check(p, price);
        if (!r.ok()) { reply(p, false, r.message()); return; }
        String name = st.getHoverName().getString();
        r = bank.transfer(p.server, p.getUUID(), c.accountId, price, BankTx.INCOME, "Distributeur : " + name, MineNorth.displayName(p));
        if (!r.ok()) { reply(p, false, r.message()); return; }
        ItemStack one = be.inv.removeItem(slot, 1);
        if (!p.getInventory().add(one)) p.drop(one, false);
        reply(p, true, name + " acheté.");
    }

    /** Le gérant fixe le prix (en centimes, 0 = pas à vendre) d'un emplacement du distributeur dont il a le menu ouvert. */
    public static void setPrice(ServerPlayer p, int slot, int cents) {
        if (!(p.containerMenu instanceof VendingMenu m) || !m.manager) return;
        VendingMachineBE be = m.machine(p.level());
        if (be == null || !canManage(p, be) || slot < 0 || slot >= VendingMachineBE.SLOTS) return;
        if (cents < 0 || cents > VendingMachineBE.MAX_PRICE) { reply(p, false, "Prix invalide."); return; }
        be.setPrice(slot, cents);
        reply(p, true, cents == 0 ? "Article retiré de la vente." : "Prix enregistré.");
    }

    /** Casser un distributeur : réservé à l'entreprise propriétaire (gérants) et aux OP. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        BlockState st = e.getState();
        if (!(st.getBlock() instanceof VendingMachineBlock) || !(e.getPlayer() instanceof ServerPlayer sp)) return;
        BlockPos pos = st.getValue(VendingMachineBlock.HALF) == DoubleBlockHalf.UPPER ? e.getPos().below() : e.getPos();
        if (sp.level().getBlockEntity(pos) instanceof VendingMachineBE be && !canManage(sp, be)) {
            e.setCanceled(true);
            sp.displayClientMessage(Component.literal("§cCe distributeur appartient à une entreprise."), true);
        }
    }

    /**
     * Accroupi avec un objet en main, Minecraft ignore le bloc : on force l'ouverture pour un gérant (qui doit pouvoir
     * stocker avec des objets en main) sans poser ni utiliser l'objet.
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) return;
        if (!(sp.level().getBlockState(e.getPos()).getBlock() instanceof VendingMachineBlock)) return;
        BlockPos pos = sp.level().getBlockState(e.getPos()).getValue(VendingMachineBlock.HALF) == DoubleBlockHalf.UPPER ? e.getPos().below() : e.getPos();
        if (sp.level().getBlockEntity(pos) instanceof VendingMachineBE be && canManage(sp, be)) {
            e.setUseBlock(Event.Result.ALLOW);
            e.setUseItem(Event.Result.DENY);
        }
    }
}
