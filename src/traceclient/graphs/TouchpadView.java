package traceclient.graphs;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import listeners.HandMovementListener;

/**
 *
 * @author James
 */
public class TouchpadView extends Canvas implements HandMovementListener{

    //raw coords
    double rx, ry;
    //mapped coords
    double mx, my;

    double tl_x = 178.37, tl_y = 92.51;
    double bl_x = 449.53, bl_y = 271;

    public void setCoords(double nx, double ny){
        rx = nx;
        ry = ny;

        double tb_width = bl_x - tl_x;
        double tb_height = bl_y - tl_y;
        if(nx < tl_x){
            mx = 0;
        }else if(nx > bl_x){
            mx = this.getWidth();
        }else{
            mx = getWidth() * ((nx - tl_x) / tb_width);
        }
        if(ny < tl_y){
            my = 0;
        }else if(ny > bl_y){
            my = this.getHeight();
        }else{
            my = getHeight() * ((ny - tl_y) / tb_height);
        }
    }

    public void handMoved(double x, double y, double z) {
        setCoords(x, y);
        repaint();
    }

    public void newCalibration(double ntx, double nty, double nbx, double nby, double z){
        tl_x = ntx; tl_y = nty;
        bl_x = nbx; bl_y = nby;
        setCoords(rx, ry);
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
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.drawRect((int)mx, (int)my, 3, 3);
        
        g2.drawImage(i, 0, 0, this);
    }
}
