import java.awt.*; import java.awt.font.*; import java.awt.geom.*; import java.awt.image.*;
import javax.imageio.*; import java.io.*; import java.util.*;

public class Render {
  static final Color INK=new Color(0x0D313D), CITRINE=new Color(0xA29553), PEACH=new Color(0xFB5628), GREY=new Color(0xC0B6A5);
  static final String FONTS="/home/carlos/development/showdist-android/app/src/main/res/font/";

  static Graphics2D g(BufferedImage im){ Graphics2D g=im.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY); return g; }

  /** The adaptive icon's dimension line (ic_launcher_foreground.xml), viewport units mapped by s + offset. */
  static void dimensionLine(Graphics2D g, double s, double ox, double oy){
    g.setColor(CITRINE); g.setStroke(new BasicStroke((float)(3.5*s),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
    g.draw(new Line2D.Double(ox+34*s,oy+54*s,ox+74*s,oy+54*s));
    g.draw(new Line2D.Double(ox+34*s,oy+43*s,ox+34*s,oy+65*s));
    g.draw(new Line2D.Double(ox+74*s,oy+43*s,ox+74*s,oy+65*s));
    g.setColor(PEACH);
    for(double cx: new double[]{34,74}) g.fill(new Ellipse2D.Double(ox+(cx-6)*s,oy+48*s,12*s,12*s));
  }

  static void icon(File out) throws Exception {
    // The launcher shows the middle 72 of the 108-unit viewport; Play masks the square itself.
    BufferedImage im=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB); Graphics2D g=g(im);
    g.setColor(INK); g.fillRect(0,0,512,512);
    double s=512/72.0; dimensionLine(g,s,-18*s,-18*s);
    ImageIO.write(im,"png",out);
  }

  static Font font(String f,float size,float tracking) throws Exception {
    Font base=Font.createFont(Font.TRUETYPE_FONT,new File(FONTS+f));
    Map<TextAttribute,Object> m=new HashMap<>(); m.put(TextAttribute.SIZE,size); m.put(TextAttribute.TRACKING,tracking);
    return base.deriveFont(m);
  }

  static void feature(File out) throws Exception {
    int W=1024,H=500; BufferedImage im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB); Graphics2D g=g(im);
    g.setColor(INK); g.fillRect(0,0,W,H);
    // Ruler band along the bottom, as on the main menu (SPEC.md §6.4), scaled 1 dp = 1.6 px.
    double dp=1.6, base=H-16*dp; g.setColor(CITRINE);
    g.setStroke(new BasicStroke((float)(2.6*dp),BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER)); g.draw(new Line2D.Double(0,base,W,base));
    int i=0; for(double x=12; x<=W+8; x+=24*dp, i++){ boolean major=i%5==0;
      g.setStroke(new BasicStroke((float)((major?4:2.6)*dp),BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
      g.draw(new Line2D.Double(x,base,x,base-(major?60:28)*dp)); }
    // Wordmark, its dimension line at the same width, then the tagline.
    Font word=font("jetbrains_mono_bold.ttf",104f,0.09f); g.setFont(word);
    FontRenderContext frc=g.getFontRenderContext();
    Rectangle2D wb=new TextLayout("SHOWDIST",word,frc).getBounds();
    double wx=(W-wb.getWidth())/2-wb.getX(), wy=172;
    g.setColor(CITRINE); g.drawString("SHOWDIST",(float)wx,(float)wy);
    double left=wx+wb.getX(), right=left+wb.getWidth(), cy=wy+44, r=9, st=4.5;
    g.setStroke(new BasicStroke((float)st,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
    g.draw(new Line2D.Double(left+r,cy,right-r,cy));
    for(double x: new double[]{left+r,right-r}){ g.setColor(CITRINE); g.draw(new Line2D.Double(x,cy-17,x,cy+17));
      g.setColor(PEACH); g.fill(new Ellipse2D.Double(x-r,cy-r,2*r,2*r)); }
    Font tag=font("jetbrains_mono_bold.ttf",24f,0.25f); g.setFont(tag); g.setColor(GREY);
    String t="AR TAPE MEASURE  ·  BUBBLE LEVEL"; Rectangle2D tb=new TextLayout(t,tag,frc).getBounds();
    g.drawString(t,(float)((W-tb.getWidth())/2-tb.getX()),(float)(cy+76));
    ImageIO.write(im,"png",out);
  }

  /** Fits a screenshot into 2:1 by scaling and extending each edge row (or column) outward. */
  static void frame(File in, File out) throws Exception {
    BufferedImage src=ImageIO.read(in); int w=src.getWidth(), h=src.getHeight();
    boolean portrait=h>w; int longS=Math.max(w,h), shortS=Math.min(w,h);
    if(longS<=2*shortS){ ImageIO.write(src,"png",out); return; }
    int newLong=2*shortS; double k=newLong/(double)longS; int newShort=(int)Math.round(shortS*k);
    int OW=portrait?shortS:newLong, OH=portrait?newLong:shortS;
    int sw=portrait?newShort:newLong, sh=portrait?newLong:newShort;
    BufferedImage im=new BufferedImage(OW,OH,BufferedImage.TYPE_INT_RGB); Graphics2D g=g(im);
    int ox=(OW-sw)/2, oy=(OH-sh)/2; g.drawImage(src,ox,oy,sw,sh,null);
    if(portrait){ for(int y=0;y<OH;y++){ int l=im.getRGB(ox+1,y), r=im.getRGB(ox+sw-2,y);
        for(int x=0;x<=ox;x++) im.setRGB(x,y,l); for(int x=ox+sw-1;x<OW;x++) im.setRGB(x,y,r); } }
    else { for(int x=0;x<OW;x++){ int t=im.getRGB(x,oy+1), b=im.getRGB(x,oy+sh-2);
        for(int y=0;y<=oy;y++) im.setRGB(x,y,t); for(int y=oy+sh-1;y<OH;y++) im.setRGB(x,y,b); } }
    ImageIO.write(im,"png",out);
  }

  public static void main(String[] a) throws Exception {
    File dir=new File(a[0]); dir.mkdirs();
    icon(new File(dir,"icon-512.png")); feature(new File(dir,"feature-graphic-1024x500.png"));
    for(int i=1;i<a.length;i+=2) frame(new File(a[i]),new File(dir,a[i+1]));
  }
}
