package fr.minenorth.entreprises.item;

import fr.minenorth.entreprises.data.EntrepriseData;
import fr.minenorth.entreprises.network.ModNetwork;
import fr.minenorth.entreprises.workshop.WorkshopService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Carte de restaurant (menu papier). Le contenu vit dans le NBT de l'objet : on peut donc la donner, la jeter, la copier.
 * Clic droit = lecture. Accroupi + clic droit = édition, réservée au patron / aux gérants d'une entreprise active
 * (et aux OP) ; une carte déjà signée par une entreprise n'est modifiable que par cette entreprise. Le serveur revérifie
 * tout à l'enregistrement et assainit les textes.
 */
public class RestaurantMenuItem extends Item {
    public static final int MAX_DISHES = 12, MAX_TITLE = 32, MAX_SUB = 48, MAX_NAME = 32, MAX_DESC = 120, MAX_PRICE = 16, MAX_IMG = 64;
    public static final int THEMES = 4;
    private static final String TAG = "Menu";

    public RestaurantMenuItem() { super(new Item.Properties().stacksTo(1)); }

    public record Dish(String name, String desc, String price, String image) {}
    /** company = entreprise qui a rédigé la carte (-1 : aucune). */
    public record Data(String title, String sub, int theme, int company, List<Dish> dishes) {
        public static Data empty() { return new Data("", "", 0, -1, new ArrayList<>()); }
    }

    // ------------------------------------------------------------------ NBT
    public static Data read(ItemStack st) {
        CompoundTag root = st.getTag();
        if (root == null || !root.contains(TAG, Tag.TAG_COMPOUND)) return Data.empty();
        CompoundTag t = root.getCompound(TAG);
        List<Dish> dishes = new ArrayList<>();
        ListTag list = t.getList("dishes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < MAX_DISHES; i++) {
            CompoundTag d = list.getCompound(i);
            dishes.add(new Dish(d.getString("n"), d.getString("d"), d.getString("p"), d.getString("i")));
        }
        return new Data(t.getString("title"), t.getString("sub"), Math.floorMod(t.getInt("theme"), THEMES),
                t.contains("company") ? t.getInt("company") : -1, dishes);
    }

    public static void write(ItemStack st, Data d) {
        CompoundTag t = new CompoundTag();
        t.putString("title", d.title()); t.putString("sub", d.sub()); t.putInt("theme", d.theme()); t.putInt("company", d.company());
        ListTag list = new ListTag();
        for (Dish x : d.dishes()) {
            CompoundTag c = new CompoundTag();
            c.putString("n", x.name()); c.putString("d", x.desc()); c.putString("p", x.price()); c.putString("i", x.image());
            list.add(c);
        }
        t.put("dishes", list);
        st.getOrCreateTag().put(TAG, t);
    }

    @Override
    public Component getName(ItemStack st) {
        String title = read(st).title();
        return title.isBlank() ? super.getName(st) : Component.literal(title);
    }

    // ------------------------------------------------------------------ usage
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            boolean edit = sp.isShiftKeyDown();
            if (edit && !canEdit(sp, read(st).company())) {
                sp.displayClientMessage(Component.literal("Seuls le patron et les gérants d'une entreprise peuvent modifier cette carte."), true);
                edit = false;
            }
            ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    new ModNetwork.MenuOpenPacket(hand == InteractionHand.MAIN_HAND, edit));
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    private static boolean canEdit(ServerPlayer sp, int stampedCompany) {
        if (sp.hasPermissions(2)) return true;
        EntrepriseData.Company c = WorkshopService.companyFor(sp);
        return c != null && (stampedCompany < 0 || stampedCompany == c.id);
    }

    // ------------------------------------------------------------------ enregistrement (serveur)
    public static void save(ServerPlayer sp, boolean mainHand, Data in) {
        ItemStack st = sp.getItemInHand(mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(st.getItem() instanceof RestaurantMenuItem)) return;
        Data old = read(st);
        if (!canEdit(sp, old.company())) {
            sp.displayClientMessage(Component.literal("Modification refusée : cette carte appartient à une autre entreprise."), true);
            return;
        }
        EntrepriseData.Company c = WorkshopService.companyFor(sp);
        int company = old.company() >= 0 ? old.company() : (c != null ? c.id : -1);
        List<Dish> dishes = new ArrayList<>();
        for (Dish d : in.dishes()) {
            if (dishes.size() >= MAX_DISHES) break;
            Dish clean = new Dish(clean(d.name(), MAX_NAME), clean(d.desc(), MAX_DESC), clean(d.price(), MAX_PRICE), cleanImage(d.image()));
            if (!clean.name().isEmpty() || !clean.desc().isEmpty() || !clean.price().isEmpty() || !clean.image().isEmpty()) dishes.add(clean);
        }
        write(st, new Data(clean(in.title(), MAX_TITLE), clean(in.sub(), MAX_SUB), Math.floorMod(in.theme(), THEMES), company, dishes));
        sp.displayClientMessage(Component.literal("Carte enregistrée."), true);
    }

    /** Retire les caractères de contrôle (dont les codes § de mise en forme) et borne la longueur. */
    static String clean(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) if (ch >= ' ' && ch != '§' && ch != 127) b.append(ch);
        String out = b.toString().trim();
        return out.length() > max ? out.substring(0, max) : out;
    }

    /** Identifiant d'objet valide et existant, sinon vide. */
    static String cleanImage(String s) {
        String id = clean(s, MAX_IMG);
        ResourceLocation rl = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return rl != null && ForgeRegistries.ITEMS.containsKey(rl) ? rl.toString() : "";
    }
}
