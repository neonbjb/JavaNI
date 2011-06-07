package traceclient.graphs;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import listeners.DepthMapListener;

/**
 *
 * @author jbetker
 */
public class ClippingBitmapView extends Canvas implements DepthMapListener{
    int[] img_data;
    int resx, resy;
    int clipx, clipy;
    
    //this enables z-axis clipping, removing data points that are gt or lt the clipping window from the specified depth
    boolean do_clipping = false;
    int clipping_depth = 0;
    int clipping_window = 0;

    boolean do_xpt = false;
    int xpt_x, xpt_y;

    boolean display_dbg1 = false;
    String dbg1_text;
    
    ArrayList<Polygon> splines = new ArrayList<Polygon>();
    
    public ClippingBitmapView(){
        this(0, 0, 320, 240);
    }
    
    public ClippingBitmapView(int clipx, int clipy, int resx, int resy){
        this.resx = resx;
        this.resy = resy;
        this.clipx = clipx;
        this.clipy = clipy;
        img_data = new int[resx * resy];
    }
    
    public void enableDepthClipping(int cd, int cw){
        do_clipping = true;
        clipping_depth = cd;
        clipping_window = cw;
    }
    
    public void disableDepthClipping(){
        do_clipping = false;
    }
    
    public void setResolution(int rx, int ry){
        resx = rx;
        resy = ry;
        img_data = new int[rx * ry];
    }

    public void setXpt(int x, int y){
        xpt_x = x;
        xpt_y = y;
        do_xpt = true;
    }

    public void setXptAbsolute(int x, int y){
        xpt_x = x - clipx;
        xpt_y = y - clipy;
        do_xpt = true;
    }
    
    public int addSpline(){
        splines.add(new Polygon());
        return splines.size()-1;
    }
    
    public void addPoint(int splineid, int x, int y){
        splines.get(splineid).addPoint(x, y);
    }

    /**
     * This function adds a spline point from the coordinate space of the whole bitmap,
     * rather than the clipped window
     * @param splineid
     * @param x
     * @param y
     */
    public void addAbsolutePoint(int splineid, int x, int y){
        splines.get(splineid).addPoint(x - clipx, y - clipy);
    }
    
    public Polygon getSpline(int splineid){
        return splines.get(splineid);
    }
    
    public void removeSpline(int splineid){
        splines.remove(splineid);
    }
    
    /**
     * Does NOT move the clipping window in the graph that is currently displayed,
     * will move it the next time new image data is fed in.
     * @param x
     * @param y 
     */
    public void moveClippingWindow(int x, int y){
        clipx = x;
        clipy = y;
    }

    public void centerClippingWindowOn(int x, int y){
        clipx = x - (resx / 2);
        clipy = y - (resy / 2);
        if(clipx < 0) clipx = 0;
        if(clipy < 0) clipy = 0;
    }

    public void debugText(String str){
        display_dbg1 = true;
        dbg1_text = str;
    }

    public void disableDebug(){
        display_dbg1 = false;
    }
    
    /**
     * Applies clipping to the new depth data and repaints.
     * @param dd 
     */
    public void newDepthMap(int[] dd, int width, int h){
        int row, col;
        int id_ptr = 0;
        for(int x = 0; x < dd.length; x++){
            col = x % width;
            row = x / width;
            if(col <= clipx || col > clipx + resx) continue;
            if(row <= clipy || row > clipy + resy) continue;
            if(!do_clipping ||
               Math.abs(dd[x] - clipping_depth) <= clipping_window //if there is clipping, is the specified pixel in the region?
                    ){
                img_data[id_ptr] = dd[x];
            }else{
                img_data[id_ptr] = 0;
            }
            id_ptr++;
        }
        repaint();
    }
    
    /**
     * Implemented from Canvas. Manages double buffering for semi-smooth frame
     * progression.
     * @param g Graphics.
     */
    public void update(Graphics g){
        paint(g);
    }

    /**
     * Implemented from Canvas. 
     * @param g2 Graphics to draw onto.
     */
    public void paint(Graphics g2){
        int w = this.getWidth();
        int h = this.getHeight();
        
        BufferedImage i = new BufferedImage(w, h, ColorSpace.TYPE_RGB);
        Graphics g = i.createGraphics();
        
        //we should be stashing the buffered image if possible
        BufferedImage img = new BufferedImage(resx, resy, BufferedImage.TYPE_3BYTE_BGR);
        for(int x = 0; x < img_data.length; x++){
            img.setRGB(x % resx, x / resx, img_data[x]);
        }
        g.drawImage(img, 0, 0, null);

        //splines
        g.setColor(Color.WHITE);
        Iterator<Polygon> iter = splines.iterator();
        while(iter.hasNext()){
            g.drawPolygon(iter.next());
        }

        //extension point
        if(do_xpt){
            g.drawRect(xpt_x, xpt_y, 2, 2);
        }

        if(display_dbg1){
            g.drawString(dbg1_text, 20, 20);
        }
        
        g2.drawImage(i, 0, 0, this);
    }
}
