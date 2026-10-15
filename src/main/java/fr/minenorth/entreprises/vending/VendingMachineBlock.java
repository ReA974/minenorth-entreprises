package fr.minenorth.entreprises.vending;

import fr.minenorth.entreprises.data.EntrepriseData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** Distributeur automatique : bloc de deux hauteurs ; l'entité (stock, prix, entreprise) est sur la moitié basse. */
public class VendingMachineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    /** Genre de machine (classique, boissons, snacks, cafe, mixte, toutenun) : façade et bruits. */
    public final String kind;

    public VendingMachineBlock(String kind) {
        super(BlockBehaviour.Properties.of().strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HALF); }

    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        if (pos.getY() >= ctx.getLevel().getMaxBuildHeight() - 1 || !ctx.getLevel().getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (!level.isClientSide && placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof VendingMachineBE be) {
            EntrepriseData.Company c = VendingService.companyFor(sp);
            if (c != null) { be.companyId = c.id; be.setChanged(); }
        }
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
        DoubleBlockHalf half = s.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? s : Blocks.AIR.defaultBlockState();
        }
        return s;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState bs = level.getBlockState(below);
            if (bs.is(this) && bs.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(bs));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Le stock tombe quand la moitié basse disparaît. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && state.getValue(HALF) == DoubleBlockHalf.LOWER && level.getBlockEntity(pos) instanceof VendingMachineBE be) {
            Containers.dropContents(level, pos, be.inv);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) pos = pos.below();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof VendingMachineBE be) {
            EntrepriseData.Company c = VendingService.company(sp.server, be);
            if (c == null) {
                sp.displayClientMessage(Component.literal("§cDistributeur hors service."), true);
                return InteractionResult.CONSUME;
            }
            // Clic droit normal : vue client (achat) pour tout le monde. Accroupi, le gérant accède au stock et aux prix.
            VendingService.sound(level, pos, VendingService.Sfx.OPEN, kind);
            boolean manager = VendingService.canManage(sp, be) && sp.isShiftKeyDown();
            final BlockPos fpos = pos;
            final String name = c.name;
            NetworkHooks.openScreen(sp, new MenuProvider() {
                @Override public Component getDisplayName() { return Component.literal("Distributeur"); }
                @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new VendingMenu(id, inv, be, fpos, manager, name);
                }
            }, buf -> { buf.writeBlockPos(fpos); buf.writeBoolean(manager); buf.writeUtf(name, 64); });
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new VendingMachineBE(pos, state) : null;
    }
}
