import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class BytephaseUiAssets {
    static Path root;
    static Graphics2D graphics;
    static void line(double... coords) {
        Path2D path = new Path2D.Double(); path.moveTo(coords[0], coords[1]);
        for (int i = 2; i < coords.length; i += 2) path.lineTo(coords[i], coords[i+1]);
        graphics.draw(path);
    }
    static void circle(double x, double y, double r) { graphics.draw(new Ellipse2D.Double(x-r,y-r,r*2,r*2)); }
    static void icon(String name) throws Exception {
        BufferedImage image = new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.scale(2,2); graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(name) {
            case "combat" -> { circle(12,12,7); circle(12,12,2); line(12,2,12,6);line(12,18,12,22);line(2,12,6,12);line(18,12,22,12); }
            case "visuals" -> { line(12,3,14,9,21,12,14,14,12,21,10,14,3,12,10,9,12,3); }
            case "player" -> { circle(12,7,3.5); graphics.draw(new Arc2D.Double(4,13,16,15,0,180,Arc2D.OPEN)); }
            case "movement" -> { circle(14,4,1.8); line(8,21,11,14,8,11,11,7,15,10,19,11);line(11,14,15,16,16,21); }
            case "world" -> { graphics.draw(new RoundRectangle2D.Double(4,4,16,16,3,3));line(4,9,20,9);line(9,4,9,20);line(4,15,20,15); }
            case "explore" -> { circle(12,12,9);line(16,7,13,14,7,17,10,10,16,7); }
            case "other" -> { circle(5,12,1.3);circle(12,12,1.3);circle(19,12,1.3); }
            case "configs" -> { line(3,7,3,20,21,20,21,7,12,7,9,4,3,4,3,7); }
            case "friends" -> { circle(9,7,3); graphics.draw(new Arc2D.Double(2,13,14,14,0,180,Arc2D.OPEN));graphics.draw(new Arc2D.Double(13,4,6,6,-80,180,Arc2D.OPEN));line(17,14,20,16,22,20); }
            case "settings" -> { circle(12,12,3);circle(12,12,7);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(12+Math.cos(a)*7,12+Math.sin(a)*7,12+Math.cos(a)*10,12+Math.sin(a)*10);} }
            case "themes" -> { circle(12,12,9);circle(8,8,0.8);circle(14,7,0.8);circle(17,12,0.8);circle(7,14,0.8);line(13,21,12,17,16,16,20,16); }
        }
        graphics.dispose();ImageIO.write(image,"png",root.resolve("ui/"+name+".png").toFile());
        Files.writeString(root.resolve("ui/"+name+".png.mcmeta"), "{\"texture\":{\"blur\":true,\"clamp\":true}}\n");
    }
    public static void main(String[] args) throws Exception {
        root=Path.of(args[0]);Files.createDirectories(root.resolve("ui"));
        for(String name:new String[]{"combat","visuals","player","movement","world","explore","other","configs","friends","themes","settings"})icon(name);
        BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(256.0/120,256.0/120);g.translate(-40,-50);
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var document=factory.newDocumentBuilder().parse(root.resolve("clickgui-logo.svg").toFile());
        NodeList rects=document.getElementsByTagName("rect");
        for(int i=0;i<rects.getLength();i++){
            Element rect=(Element)rects.item(i);Element parent=(Element)rect.getParentNode();
            double x=Double.parseDouble(rect.getAttribute("x")),y=Double.parseDouble(rect.getAttribute("y"));
            double w=Double.parseDouble(rect.getAttribute("width")),h=Double.parseDouble(rect.getAttribute("height")),r=Double.parseDouble(rect.getAttribute("rx"));
            String color=rect.getAttribute("fill");if(color.isEmpty())color=parent.getAttribute("fill");
            if(color.equals("#fff"))color="#ffffff";
            float alpha=parent.hasAttribute("opacity")?Float.parseFloat(parent.getAttribute("opacity")):1;
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));g.setColor(Color.decode(color));
            g.fill(new RoundRectangle2D.Double(x,y,w,h,r*2,r*2));
        }
        g.dispose();ImageIO.write(image,"png",root.resolve("clickgui-logo.png").toFile());
        Files.writeString(root.resolve("clickgui-logo.png.mcmeta"), "{\"texture\":{\"blur\":true,\"clamp\":true}}\n");
        System.out.println("Added the supplied logo and eleven native sidebar icons.");
    }
}
