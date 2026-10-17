package fr.minenorth.entreprises.client;

import fr.minenorth.entreprises.MineNorthEntreprises;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Charte graphique MineNorth : identique au mod Permis / ATM EuroBank (fond bleu nuit, boutons plats). */
public final class MineNorthStyle {
    public static final int FRAME = 0xFF0E0E10;
    /** Fond du panneau. */
    public static final int PANEL = 0xFF161048;
    /** Fond des blocs / listes. */
    public static final int LIST = 0xFF0E0A34;
    public static final int HOVER = 0xFF2E2480;
    public static final int CYAN = 0xFF20AAEB;
    public static final int DARK = 0xFF4A3CB4;
    public static final int PINK = 0xFFC83CF0;
    /** Vert des boutons de validation. */
    public static final int GREEN = 0xFF1E9E52;
    /** Couleur des libellés de champs. */
    public static final int BLUE = 0xFF20AAEB;
    public static final int TEXT = 0xFFCFE3FF;
    public static final int MUTED = 0xFF8FA8E0;
    public static final int WARN = 0xFFFFE066;
    public static final int ALERT = 0xFFFF6A9A;
    /** Vert des textes de statut. */
    public static final int OK = 0xFF6CFF9A;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int DISABLED = 0xFF2A2468;
    public static final ResourceLocation LOGO = new ResourceLocation(MineNorthEntreprises.MOD_ID, "textures/gui/logo.png");

    private MineNorthStyle() {}

    public static int lighten(int c) {
        int r=Math.min(255,((c>>16)&255)+35), g=Math.min(255,((c>>8)&255)+35), b=Math.min(255,(c&255)+35);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    /** Rectangle aux coins arrondis (rayon r en pixels GUI). */
    public static void rfill(GuiGraphics g,int x1,int y1,int x2,int y2,int r,int c){
        r=Math.max(0,Math.min(r,Math.min(x2-x1,y2-y1)/2));
        g.fill(x1,y1+r,x2,y2-r,c);
        for(int i=0;i<r;i++){
            double dy=r-i-0.5;
            int inset=r-(int)Math.round(Math.sqrt(r*(double)r-dy*dy));
            g.fill(x1+inset,y1+i,x2-inset,y1+i+1,c);
            g.fill(x1+inset,y2-i-1,x2-inset,y2-i,c);
        }
    }
    /** « PRENDRE MON SERVICE » devient « Prendre mon service » (plus lisible) ; le texte déjà en minuscules reste intact. */
    public static String soft(String s){
        if(s==null||s.isEmpty()||!s.equals(s.toUpperCase(java.util.Locale.ROOT))) return s;
        String l=s.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(l.charAt(0))+l.substring(1);
    }
    public static Component bold(String s){ return Component.literal(s).withStyle(ChatFormatting.BOLD); }

    /** Fond + cadre + logo + titre agrandi, comme les écrans Permis. */
    public static void panel(GuiGraphics g,int left,int top,int w,int h,String title,String subtitle){
        Font font=Minecraft.getInstance().font;
        rfill(g,left-3,top-3,left+w+3,top+h+3,9,FRAME);
        rfill(g,left,top,left+w,top+h,7,PANEL);
        RenderSystem.enableBlend();
        g.blit(LOGO,left+8,top+5,28,28,0,0,256,256,256,256);
        scaled(g,bold(title),left+42,top+9,1.6f,WHITE);
        if(subtitle!=null&&!subtitle.isBlank()) g.drawString(font,subtitle,left+42,top+26,MUTED,false);
        rfill(g,left+10,top+40,left+w-10,top+42,1,HOVER);
    }
    public static void scaled(GuiGraphics g,Component c,int x,int y,float s,int color){
        Font font=Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().scale(s,s,1f);
        g.drawString(font,c,Math.round(x/s),Math.round(y/s),color,false);
        g.pose().popPose();
    }
    /** Bloc sombre avec liseré de couleur à gauche. */
    public static void card(GuiGraphics g,int x,int y,int w,int h,boolean hover,int accent){
        rfill(g,x,y,x+w,y+h,4,accent);
        rfill(g,x+3,y,x+w,y+h,4,hover?HOVER:LIST);
    }
    /** Bouton plat. */
    public static void button(GuiGraphics g,int x,int y,int w,int h,String label,int color,boolean hover,boolean active){
        Font f=Minecraft.getInstance().font;
        int bg=!active?DISABLED:(hover?lighten(color):color);
        rfill(g,x,y,x+w,y+h,h>=16?5:3,bg);
        if(active&&hover) rfill(g,x+1,y+1,x+w-1,y+h-1,h>=16?4:2,bg);
        label=soft(label);
        g.drawString(f,label,x+w/2-f.width(label)/2,y+(h-8)/2,active?WHITE:MUTED,false);
    }
    public static void item(GuiGraphics g,ItemStack st,int x,int y,float scale){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(st,0,0);g.pose().popPose();
    }
    public static String euros(long cents){
        long a=Math.abs(cents); return (cents<0?"-":"")+(a/100)+(a%100==0?"":","+String.format("%02d",a%100))+" €";
    }
}
