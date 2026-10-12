package fr.minenorth.entreprises.vending;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stock (18 emplacements) et prix d'un distributeur, rattaché à une entreprise. Existe seulement sur la moitié basse. */
public class VendingMachineBE extends BlockEntity {
    public static final int SLOTS = 18;
    /** Prix maximum d'un article : 100 000 € (en centimes). */
    public static final int MAX_PRICE = 10_000_000;

    /** Entreprise propriétaire, -1 si aucune (distributeur inerte). */
    public int companyId = -1;
    private final int[] prices = new int[SLOTS];
    public final SimpleContainer inv = new SimpleContainer(SLOTS);

    public VendingMachineBE(BlockPos pos, BlockState state) {
        super(ModVending.VENDING_BE.get(), pos, state);
        inv.addListener(c -> { setChanged(); sync(); });
    }

    public int price(int slot) { return slot < 0 || slot >= SLOTS ? 0 : prices[slot]; }

    public void setPrice(int slot, int cents) {
        if (slot < 0 || slot >= SLOTS) return;
        prices[slot] = Math.max(0, Math.min(MAX_PRICE, cents));
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("company", companyId);
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() && prices[i] == 0) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("slot", (byte) i);
            t.putInt("price", prices[i]);
            if (!st.isEmpty()) t.put("item", st.save(new CompoundTag()));
            list.add(t);
        }
        tag.put("slots", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        companyId = tag.contains("company") ? tag.getInt("company") : -1;
        inv.clearContent();
        java.util.Arrays.fill(prices, 0);
        ListTag list = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int s = t.getByte("slot");
            if (s < 0 || s >= SLOTS) continue;
            prices[s] = Math.max(0, Math.min(MAX_PRICE, t.getInt("price")));
            if (t.contains("item")) inv.setItem(s, ItemStack.of(t.getCompound("item")));
        }
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
