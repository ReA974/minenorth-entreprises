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

    // ------------------------------------------------------------------ bruitages (sons Minecraft combinés, à retardement)

    public enum Sfx { OPEN, PAID, ERROR }

    private record Later(int[] ticks, Runnable run) {}
    private static final java.util.List<Later> LATER = new java.util.ArrayList<>();

    private static void later(int ticks, Runnable r) { synchronized (LATER) { LATER.add(new Later(new int[]{ticks}, r)); } }

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        java.util.List<Runnable> due = new java.util.ArrayList<>();
        synchronized (LATER) {
            for (java.util.Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
                Later l = it.next();
                if (--l.ticks[0] <= 0) { due.add(l.run); it.remove(); }
            }
        }
        due.forEach(Runnable::run);
    }

    private static void play(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        level.playSound(null, pos, s, net.minecraft.sounds.SoundSource.BLOCKS, vol, pitch);
    }

    /** Bruit de la machine : ouverture (bip du lecteur), erreur (buzzer grave), achat (bip, moteur, chute, ding ; la machine à café fait couler le café). */
    public static void sound(net.minecraft.world.level.Level level, BlockPos pos, Sfx what, String kind) {
        if (level.isClientSide) return;
        switch (what) {
            case OPEN -> play(level, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), 0.6f, 1.6f);
            case ERROR -> {
                play(level, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
                later(4, () -> play(level, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.5f));
            }
            case PAID -> {
                play(level, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), 0.7f, 1.9f);               // carte acceptée
                later(5, () -> play(level, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), 0.7f, 2.0f));
                if (kind.equals("cafe")) {
                    later(10, () -> play(level, pos, net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, 0.7f, 0.9f));   // mouture
                    later(24, () -> play(level, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 0.35f, 1.7f));         // le café coule
                    later(34, () -> play(level, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 0.35f, 1.6f));      // vapeur
                } else {
                    later(10, () -> play(level, pos, net.minecraft.sounds.SoundEvents.PISTON_EXTEND, 0.35f, 1.8f));        // le moteur tourne
                    later(16, () -> play(level, pos, net.minecraft.sounds.SoundEvents.PISTON_CONTRACT, 0.35f, 1.9f));
                    later(22, () -> play(level, pos, net.minecraft.sounds.SoundEvents.BARREL_CLOSE, 0.7f, 0.8f));          // l'article tombe dans le bac
                }
                later(kind.equals("cafe") ? 40 : 26, () -> play(level, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.3f));   // ding
            }
        }
    }

    private static void error(ServerPlayer p, VendingMachineBE be) {
        if (be.getBlockState().getBlock() instanceof VendingMachineBlock vb) sound(p.level(), be.getBlockPos(), Sfx.ERROR, vb.kind);
    }

    /** Entreprise active dont le joueur est patron ou gérant (celle qui peut poser un distributeur), sinon null. */
    public static Company companyFor(ServerPlayer p) {
        Company c = EntrepriseData.get(p.server).companyOf(p.getUUID());
        return c != null && c.status == EntrepriseData.ACTIVE && CompanyAccounts.canStock(c, p.getUUID()) ? c : null;
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
        return c != null && CompanyAccounts.canStock(c, p.getUUID());
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
        if (c == null) { reply(p, false, "Distributeur hors service."); error(p, be); return; }
        ItemStack st = be.inv.getItem(slot);
        int price = be.price(slot);
        if (st.isEmpty()) { reply(p, false, "Emplacement vide."); error(p, be); return; }
        if (price <= 0) { reply(p, false, "Cet article n'est pas à vendre."); error(p, be); return; }
        BankService bank = MineNorth.bank();
        PayResult r = bank.check(p, price);
        if (!r.ok()) { reply(p, false, r.message()); error(p, be); return; }
        String name = st.getHoverName().getString();
        r = bank.transfer(p.server, p.getUUID(), c.accountId, price, BankTx.INCOME, "Distributeur : " + name, MineNorth.displayName(p));
        if (!r.ok()) { reply(p, false, r.message()); error(p, be); return; }
        ItemStack one = be.inv.removeItem(slot, 1);
        if (!p.getInventory().add(one)) p.drop(one, false);
        reply(p, true, name + " acheté.");
        if (be.getBlockState().getBlock() instanceof VendingMachineBlock vb) sound(p.level(), be.getBlockPos(), Sfx.PAID, vb.kind);
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
