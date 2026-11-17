import java.awt.*; import java.awt.font.*; import java.awt.geom.*; import java.awt.image.*;
import javax.imageio.*; import java.io.*; import java.util.*;

public class Render {
  // The app palette (SPEC.md §5).
  static final Color BLACK=Color.BLACK, ACCENT=new Color(0x00E5FF), MUTED=new Color(0xB8B8B8), WHITE=Color.WHITE;
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
    g.setColor(ACCENT); g.setStroke(new BasicStroke((float)(3*s),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
    g.draw(new Line2D.Double(ox+34*s,oy+54*s,ox+74*s,oy+54*s));
    g.draw(new Line2D.Double(ox+34*s,oy+43*s,ox+34*s,oy+65*s));
    g.draw(new Line2D.Double(ox+74*s,oy+43*s,ox+74*s,oy+65*s));
    g.setColor(WHITE);
    for(double cx: new double[]{34,74}) g.fill(new Ellipse2D.Double(ox+(cx-6)*s,oy+48*s,12*s,12*s));
  }

  static void icon(File out) throws Exception {
    // The launcher shows the middle 72 of the 108-unit viewport; Play masks the square itself.
    BufferedImage im=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB); Graphics2D g=g(im);
    g.setColor(BLACK); g.fillRect(0,0,512,512);
    double s=512/72.0; dimensionLine(g,s,-18*s,-18*s);
    ImageIO.write(im,"png",out);
  }

  static Font font(String f,float size,float tracking) throws Exception {
    Font base=Font.createFont(Font.TRUETYPE_FONT,new File(FONTS+f));
    Map<TextAttribute,Object> m=new HashMap<>(); m.put(TextAttribute.SIZE,size); m.put(TextAttribute.TRACKING,tracking);
    return base.deriveFont(m);
  }

  /**
   * The feature graphic: the app mark (the icon's dimension line) large on black, the
   * wordmark beside it and the tagline beneath; nothing else (SPEC.md §0a).
   */
  static void feature(File out) throws Exception {
    int W=1024,H=500; BufferedImage im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB); Graphics2D g=g(im);
    g.setColor(BLACK); g.fillRect(0,0,W,H);
    FontRenderContext frc=g.getFontRenderContext();
    Font word=font("jetbrains_mono_bold.ttf",92f,0.08f), tag=font("jetbrains_mono_bold.ttf",22f,0.2f);
    Rectangle2D wb=new TextLayout("SHOWDIST",word,frc).getBounds();
    String t="AR TAPE MEASURE  ·  BUBBLE LEVEL"; Rectangle2D tb=new TextLayout(t,tag,frc).getBounds();
    // Mark: viewport x 28-80 (52 units) at scale s; then a gap, then the wordmark; the pair centred.
    double s=4.2, markW=52*s, gap=56, total=markW+gap+wb.getWidth(), x0=(W-total)/2, cy=214;
    dimensionLine(g,s,x0-28*s,cy-54*s);
    g.setFont(word); g.setColor(WHITE);
    g.drawString("SHOWDIST",(float)(x0+markW+gap-wb.getX()),(float)(cy-wb.getY()-wb.getHeight()/2));
    g.setFont(tag); g.setColor(MUTED);
    g.drawString(t,(float)((W-tb.getWidth())/2-tb.getX()),400f);
    ImageIO.write(im,"png",out);
  }

  /**
   * Paints over the status-bar strip (top 150 px) and the gesture handle (bottom 60 px) with
   * the colour just inside them, for screens (like a bottom sheet) that bring the bars back.
   * Only where the row just inside is one flat colour, so a full-screen camera image is left alone.
   */
  static void hideSystemBars(BufferedImage im){
    int w=im.getWidth(), h=im.getHeight();
    if(flatRow(im,160)){ int top=im.getRGB(w/2,160); for(int y=0;y<150;y++) for(int x=0;x<w;x++) im.setRGB(x,y,top); }
    if(flatRow(im,h-62)){ int bottom=im.getRGB(w/2,h-62); for(int y=h-60;y<h;y++) for(int x=0;x<w;x++) im.setRGB(x,y,bottom); }
  }

  static boolean flatRow(BufferedImage im,int y){
    int c=im.getRGB(0,y); for(int x=1;x<im.getWidth();x++) if(im.getRGB(x,y)!=c) return false; return true;
  }

  /** How many rows from the top (or bottom) are one flat colour. */
  static int flatRows(BufferedImage im, boolean fromTop){
    int h=im.getHeight(), n=0;
    for(int i=0;i<h;i++){ if(!flatRow(im,fromTop?i:h-1-i)) return n; n++; }
    return n;
  }

  /**
   * Fits a portrait screenshot into 2:1. First [cropTop] px are cut (for a camera shot, where
   * nothing is flat), then flat bands off the top and bottom, as much as needed, so it keeps
   * full scale; whatever's still too tall is scaled down, with each edge column extended
   * outward to fill the width.
   */
  static final int MARGIN=48;

  static void frame(File in, File out, int cropTop) throws Exception {
    BufferedImage src=ImageIO.read(in); int w=src.getWidth(), h=src.getHeight();
    if(h<=w){ ImageIO.write(src,"png",out); return; }
    hideSystemBars(src);
    src=src.getSubimage(0,cropTop,w,h-cropTop); h-=cropTop;
    // Each flat band keeps a margin, so nothing ends up flush against the edge.
    int trim=Math.max(0,h-2*w), top=Math.max(0,flatRows(src,true)-MARGIN), bottom=Math.max(0,flatRows(src,false)-MARGIN);
    int cutBottom=Math.min(bottom,trim/2), cutTop=Math.min(top,trim-cutBottom); cutBottom=Math.min(bottom,trim-cutTop);
    src=src.getSubimage(0,cutTop,w,h-cutTop-cutBottom); h=src.getHeight();
    if(h<=2*w){ ImageIO.write(src,"png",out); return; }
    int OW=w, OH=2*w, sh=OH, sw=(int)Math.round(w*(OH/(double)h));
    BufferedImage im=new BufferedImage(OW,OH,BufferedImage.TYPE_INT_RGB); Graphics2D g=g(im);
    int ox=(OW-sw)/2; g.drawImage(src,ox,0,sw,sh,null);
    for(int y=0;y<OH;y++){ int l=im.getRGB(ox+1,y), r=im.getRGB(ox+sw-2,y);
      for(int x=0;x<=ox;x++) im.setRGB(x,y,l); for(int x=ox+sw-1;x<OW;x++) im.setRGB(x,y,r); }
    ImageIO.write(im,"png",out);
  }

  /** Args: out-dir, then pairs of in.png and out.png; "out.png:N" first crops N px off the top. */
  public static void main(String[] a) throws Exception {
    File dir=new File(a[0]); dir.mkdirs();
    icon(new File(dir,"icon-512.png")); feature(new File(dir,"feature-graphic-1024x500.png"));
    for(int i=1;i<a.length;i+=2){
      String[] o=a[i+1].split(":");
      frame(new File(a[i]),new File(dir,o[0]),o.length>1?Integer.parseInt(o[1]):0);
    }
  }
}
