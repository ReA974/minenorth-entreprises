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
    public static final int MAX_PAGES = 8, PER_PAGE = 4, MAX_TITLE = 32, MAX_SUB = 48, MAX_NAME = 32, MAX_DESC = 120, MAX_PRICE = 16, MAX_IMG = 64;
    public static final int THEMES = 4;
    private static final String TAG = "Menu";

    public RestaurantMenuItem() { super(new Item.Properties().stacksTo(1)); }

    public record Dish(String name, String desc, String price, String image) {}
    /** Une page de la carte : son sous-titre (« Entrées », « Desserts »…) et jusqu'à {@link #PER_PAGE} plats. */
    public record Page(String sub, List<Dish> dishes) {}
    /** company = entreprise qui a rédigé la carte (-1 : aucune) ; sub = sous-titre général sous le titre. */
    public record Data(String title, String sub, int theme, int company, List<Page> pages) {
        public static Data empty() { return new Data("", "", 0, -1, new ArrayList<>()); }
    }

    private static Dish readDish(CompoundTag d) {
        return new Dish(d.getString("n"), d.getString("d"), d.getString("p"), d.getString("i"));
    }

    private static CompoundTag writeDish(Dish x) {
        CompoundTag c = new CompoundTag();
        c.putString("n", x.name()); c.putString("d", x.desc()); c.putString("p", x.price()); c.putString("i", x.image());
        return c;
    }

    // ------------------------------------------------------------------ NBT
    public static Data read(ItemStack st) {
        CompoundTag root = st.getTag();
        if (root == null || !root.contains(TAG, Tag.TAG_COMPOUND)) return Data.empty();
        CompoundTag t = root.getCompound(TAG);
        List<Page> pages = new ArrayList<>();
        if (t.contains("pages", Tag.TAG_LIST)) {
            ListTag pl = t.getList("pages", Tag.TAG_COMPOUND);
            for (int i = 0; i < pl.size() && i < MAX_PAGES; i++) {
                CompoundTag pc = pl.getCompound(i);
                List<Dish> dishes = new ArrayList<>();
                ListTag dl = pc.getList("dishes", Tag.TAG_COMPOUND);
                for (int j = 0; j < dl.size() && j < PER_PAGE; j++) dishes.add(readDish(dl.getCompound(j)));
                pages.add(new Page(pc.getString("sub"), dishes));
            }
        } else {
            // Ancienne carte (plats à la suite) : découpée en pages de PER_PAGE plats, sans sous-titre.
            ListTag list = t.getList("dishes", Tag.TAG_COMPOUND);
            List<Dish> cur = new ArrayList<>();
            for (int i = 0; i < list.size() && i < MAX_PAGES * PER_PAGE; i++) {
                cur.add(readDish(list.getCompound(i)));
                if (cur.size() == PER_PAGE) { pages.add(new Page("", cur)); cur = new ArrayList<>(); }
            }
            if (!cur.isEmpty()) pages.add(new Page("", cur));
        }
        return new Data(t.getString("title"), t.getString("sub"), Math.floorMod(t.getInt("theme"), THEMES),
                t.contains("company") ? t.getInt("company") : -1, pages);
    }

    public static void write(ItemStack st, Data d) {
        CompoundTag t = new CompoundTag();
        t.putString("title", d.title()); t.putString("sub", d.sub()); t.putInt("theme", d.theme()); t.putInt("company", d.company());
        ListTag pl = new ListTag();
        for (Page pg : d.pages()) {
            CompoundTag pc = new CompoundTag();
            pc.putString("sub", pg.sub());
            ListTag dl = new ListTag();
            for (Dish x : pg.dishes()) dl.add(writeDish(x));
            pc.put("dishes", dl);
            pl.add(pc);
        }
        t.put("pages", pl);
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
        List<Page> pages = new ArrayList<>();
        for (Page pg : in.pages()) {
            if (pages.size() >= MAX_PAGES) break;
            List<Dish> dishes = new ArrayList<>();
            for (Dish d : pg.dishes()) {
                if (dishes.size() >= PER_PAGE) break;
                Dish clean = new Dish(clean(d.name(), MAX_NAME), clean(d.desc(), MAX_DESC), clean(d.price(), MAX_PRICE), cleanImage(d.image()));
                if (!clean.name().isEmpty() || !clean.desc().isEmpty() || !clean.price().isEmpty() || !clean.image().isEmpty()) dishes.add(clean);
            }
            String sub = clean(pg.sub(), MAX_SUB);
            if (!dishes.isEmpty() || !sub.isEmpty()) pages.add(new Page(sub, dishes));
        }
        write(st, new Data(clean(in.title(), MAX_TITLE), clean(in.sub(), MAX_SUB), Math.floorMod(in.theme(), THEMES), company, pages));
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
