package fr.minenorth.entreprises.vending;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Menu du distributeur. Mode gérant : le stock se manipule comme un coffre et les prix sont modifiables.
 * Mode client : les emplacements sont en lecture seule (l'achat passe par un paquet, jamais par un clic d'inventaire).
 * Les positions des emplacements sont partagées avec l'écran ({@code VendingScreen}).
 */
public class VendingMenu extends AbstractContainerMenu {
    public static final int SLOTS = VendingMachineBE.SLOTS, COLS = 6;
    /** Géométrie de l'écran : cellule d'un article (largeur x hauteur) et origine de la grille. */
    public static final int CELL_W = 36, CELL_H = 28, GRID_X = 20, GRID_Y = 50;
    public static final int PLAYER_Y = 168;

    public final BlockPos pos;
    public final boolean manager;
    public final String companyName;
    private final Container container;
    private final ContainerData prices;
    private final ContainerLevelAccess access;

    /** Côté client (données reçues du serveur). */
    public VendingMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(SLOTS), new SimpleContainerData(SLOTS * 2), buf.readBlockPos(), buf.readBoolean(),
                buf.readUtf(64), ContainerLevelAccess.NULL);
    }

    /** Côté serveur. */
    public VendingMenu(int id, Inventory inv, VendingMachineBE be, BlockPos pos, boolean manager, String companyName) {
        this(id, inv, be.inv, new ContainerData() {
            @Override public int get(int i) { int p = be.price(i >> 1); return (i & 1) == 0 ? p & 0xFFFF : p >>> 16; }
            @Override public void set(int i, int v) { }
            @Override public int getCount() { return SLOTS * 2; }
        }, pos, manager, companyName, ContainerLevelAccess.create(be.getLevel(), pos));
    }

    private VendingMenu(int id, Inventory inv, Container container, ContainerData prices, BlockPos pos, boolean manager,
                        String companyName, ContainerLevelAccess access) {
        super(ModVending.VENDING_MENU.get(), id);
        this.container = container; this.prices = prices; this.pos = pos; this.manager = manager;
        this.companyName = companyName; this.access = access;
        checkContainerSize(container, SLOTS);
        addDataSlots(prices);
        for (int i = 0; i < SLOTS; i++) {
            int x = GRID_X + (i % COLS) * CELL_W + (CELL_W - 18) / 2 + 1, y = GRID_Y + (i / COLS) * CELL_H + 1;
            addSlot(manager ? new Slot(container, i, x, y) : new Slot(container, i, x, y) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean mayPickup(Player p) { return false; }
            });
        }
        if (manager) {
            for (int r = 0; r < 3; r++)
                for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 47 + c * 18, PLAYER_Y + r * 18));
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 47 + c * 18, PLAYER_Y + 58));
        }
    }

    /** Prix en centimes de l'emplacement (valeur recomposée de deux demi-mots : le réseau ne porte que 16 bits par donnée). */
    public int price(int slot) {
        if (slot < 0 || slot >= SLOTS) return 0;
        return (prices.get(slot * 2) & 0xFFFF) | ((prices.get(slot * 2 + 1) & 0xFFFF) << 16);
    }

    /** L'entité du distributeur, côté serveur ou client ; null si elle a disparu. */
    public VendingMachineBE machine(Level level) {
        return level.getBlockEntity(pos) instanceof VendingMachineBE be ? be : null;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!manager) return;   // client : aucune manipulation d'inventaire
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!manager) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, SLOTS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((l, p) -> l.getBlockState(p).getBlock() instanceof VendingMachineBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
    }
}
