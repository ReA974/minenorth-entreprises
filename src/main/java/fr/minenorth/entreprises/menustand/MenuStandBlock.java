package fr.minenorth.entreprises.menustand;

import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.item.ModItems;
import fr.minenorth.entreprises.item.RestaurantMenuItem;
import fr.minenorth.entreprises.workshop.WorkshopService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Distributeur de menus : une pile de cartes de restaurant. Clic droit = une copie de la carte configurée (sans stock). Le patron ou un gérant
 * de l'entreprise définit la carte en tenant sa carte signée en main puis Ctrl + clic droit sur la pile (ou accroupi + clic droit) ;
 * seule son entreprise (ou un OP) peut ensuite la changer. Le serveur contrôle tout.
 */
public class MenuStandBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

    public MenuStandBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5f).sound(SoundType.WOOL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MenuStandBE(pos, state); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof MenuStandBE be)) return InteractionResult.PASS;
        // Accroupi avec une carte en main : même effet que Ctrl + clic droit (pour les claviers où Ctrl est pris).
        if (sp.isShiftKeyDown() && sp.getMainHandItem().getItem() instanceof RestaurantMenuItem) {
            configure(sp, be);
            return InteractionResult.SUCCESS;
        }
        give(sp, be);
        return InteractionResult.SUCCESS;
    }

    private static void say(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }

    /** Donne une copie de la carte (sans stock) ; pas de seconde copie identique si le joueur l'a déjà sur lui. */
    private static void give(ServerPlayer sp, MenuStandBE be) {
        if (be.menu == null) {
            say(sp, "§7Ce distributeur est vide : le gérant doit y définir sa carte (carte en main + Ctrl + clic droit).");
            return;
        }
        ItemStack card = new ItemStack(ModItems.RESTAURANT_MENU.get());
        card.getOrCreateTag().put("Menu", be.menu.copy());
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.getItem() instanceof RestaurantMenuItem && ItemStack.isSameItemSameTags(s, card)) {
                say(sp, "§7Vous avez déjà cette carte sur vous.");
                return;
            }
        }
        sp.getInventory().placeItemBackInInventory(card);
        say(sp, "§aVous prenez une carte : " + RestaurantMenuItem.read(card).title());
    }

    /** Définit la carte à donner : celle tenue en main par un gérant de l'entreprise (ou un OP). Appelé par le clic droit accroupi et par le paquet Ctrl + clic droit. */
    public static void configure(ServerPlayer sp, MenuStandBE be) {
        ItemStack held = sp.getMainHandItem();
        if (!(held.getItem() instanceof RestaurantMenuItem)) {
            say(sp, "§7Tenez votre carte de restaurant en main, puis Ctrl + clic droit sur le distributeur.");
            return;
        }
        RestaurantMenuItem.Data data = RestaurantMenuItem.read(held);
        if (data.pages().isEmpty()) { say(sp, "§cCette carte est vide : rédigez-la d'abord (accroupi + clic droit avec la carte)."); return; }
        boolean op = sp.hasPermissions(2);
        EntrepriseData.Company c = WorkshopService.companyFor(sp);
        if (c == null && !op) { say(sp, "§cSeuls le patron et les gérants d'une entreprise peuvent configurer ce distributeur."); return; }
        if (!op && be.menu != null && be.company >= 0 && c != null && be.company != c.id) {
            say(sp, "§cCe distributeur appartient à une autre entreprise.");
            return;
        }
        if (!op && c != null && data.company() >= 0 && data.company() != c.id) {
            say(sp, "§cCette carte appartient à une autre entreprise.");
            return;
        }
        int company = data.company() >= 0 ? data.company() : c != null ? c.id : -1;
        ItemStack copy = held.copy();
        RestaurantMenuItem.write(copy, new RestaurantMenuItem.Data(data.title(), data.sub(), data.theme(), company, data.pages()));
        CompoundTag menu = copy.getTag() == null ? null : copy.getTag().getCompound("Menu");
        if (menu == null) return;
        be.menu = menu.copy();
        be.company = company;
        be.setChanged();
        say(sp, "§aDistributeur configuré : il donnera la carte « " + (data.title().isBlank() ? "Carte" : data.title()) + " ».");
    }
}
