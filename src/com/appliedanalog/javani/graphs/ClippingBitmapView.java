package com.appliedanalog.javani.graphs;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import com.appliedanalog.javani.listeners.DepthMapListener;
import java.awt.Point;
import java.util.concurrent.Semaphore;

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
    int clipping_window = 1;

    Color[] line_colors = new Color[1000];
    Point[][] lines = new Point[1000][2];
    int num_lines = 0;

    boolean[] big_point = new boolean[50000];
    Color[] point_colors = new Color[50000];
    Point[] points = new Point[50000];
    int num_points = 0;
    
    String[] strings = new String[100];
    Point[] strlocs = new Point[100];
    Color[] strcolors = new Color[100];
    int num_strings = 0;

    BufferedImage bufimg; //for drawing

    public ClippingBitmapView(){
        this(0, 0, 320, 240);
    }
    
    public ClippingBitmapView(int clipx, int clipy, int resx, int resy){
        this.resx = resx;
        this.resy = resy;
        this.clipx = clipx;
        this.clipy = clipy;
        img_data = new int[resx * resy];
        bufimg = new BufferedImage(resx, resy, BufferedImage.TYPE_3BYTE_BGR);
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
        bufimg = new BufferedImage(rx, ry, BufferedImage.TYPE_3BYTE_BGR);
        img_data = new int[rx * ry];
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
            if(!do_clipping){
                img_data[id_ptr] = dd[x];
            }else if(Math.abs(dd[x] - clipping_depth) <= clipping_window //if there is clipping, is the specified pixel in the region?
                    ){
                img_data[id_ptr] = (dd[x] - clipping_depth + clipping_window) * (256 * 3 / (clipping_window * 2));
                if(img_data[id_ptr] >= 256 && img_data[id_ptr] < 512){
                    img_data[id_ptr] = 0xff | ((img_data[id_ptr] - 256) << 16);
                }else if(img_data[id_ptr] >= 512){
                    img_data[id_ptr] = ((768 - img_data[id_ptr]) << 16);
                }
            }else{
                img_data[id_ptr] = 0xffffffff;
            }
            id_ptr++;
        }
    }

    //this allows a synchronous painting operation
    Semaphore imgbuf_sem = new Semaphore(1);
    boolean prepainted = false;
    public void syncpaint(){
        try{
            imgbuf_sem.acquire();
            prepainted = true;
            Graphics g = bufimg.createGraphics();

            //we should be stashing the buffered image if possible
            for(int x = 0; x < img_data.length; x++){
                bufimg.setRGB(x % resx, x / resx, img_data[x]);
            }
            g.drawImage(bufimg, 0, 0, null);

            for(int x = 0; x < num_lines; x++){
                g.setColor(line_colors[x]);
                g.drawLine(lines[x][0].x-clipx, lines[x][0].y-clipy, lines[x][1].x-clipx, lines[x][1].y-clipy);
            }

            //strings are unique because they are not relative
            for(int x = 0; x < num_strings; x++){
                g.setColor(strcolors[x]);
                g.drawString(strings[x], strlocs[x].x, strlocs[x].y);
            }

            for(int x = 0; x < num_points; x++){
                int rectsz = (big_point[x] ? 5 : 1);
                int px = (big_point[x] ? points[x].x - 2 : points[x].x) - clipx;
                int py = (big_point[x] ? points[x].y - 2 : points[x].y) - clipy;
                g.setColor(point_colors[x]);
                g.fillRect(px, py, rectsz, rectsz);
            }
        }catch(Exception e){
        }finally{
            imgbuf_sem.release();
        }
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
        if(!prepainted){
            syncpaint();
            prepainted = false;
        }
        try{
            imgbuf_sem.acquire();
            g2.drawImage(bufimg, 0, 0, this);
        }catch(Exception e){
        }finally{
            imgbuf_sem.release();
        }
    }

    public int addString(String s, Point p1){
        return addString(s, p1, Color.BLACK);
    }

    public int addString(String s, Point p1, Color c){
        if(num_strings >= strcolors.length){
            System.out.println("ClippingBitmapView: Ran out of lines!");
            return -1;
        }
        strlocs[num_strings] = p1;
        strcolors[num_strings] = c;
        strings[num_strings] = s;
        num_strings++;
        return num_strings-1;
    }

    public void setString(int ind, String s, Point p1, Color c){
        strlocs[ind] = p1;
        strcolors[ind] = c;
        strings[ind] = s;
    }

    public void removeStrings(int ln){
        Point tp; Color tc; String s;
        for(int x = ln; x < num_strings - 1; x++){
            tp = strlocs[x];
            strlocs[x] = strlocs[x+1];
            strlocs[x+1] = tp;
            tc = strcolors[x];
            strcolors[x] = strcolors[x+1];
            strcolors[x+1] = tc;
            s = strings[x];
            strings[x] = strings[x+1];
            strings[x+1] = s;
        }
        num_strings--;
    }

    public int addLine(Point p1, Point p2){
        return addLine(p1, p2, Color.BLACK);
    }

    public int addLine(Point p1, Point p2, Color c){
        if(num_lines >= line_colors.length){
            System.out.println("ClippingBitmapView: Ran out of lines!");
            return -1;
        }
        lines[num_lines][0] = p1;
        lines[num_lines][1] = p2;
        line_colors[num_lines] = c;
        num_lines++;
        return num_lines-1;
    }

    public void setLine(int ind, Point p1, Point p2, Color c){
        lines[ind][0] = p1;
        lines[ind][1] = p2;
        line_colors[ind] = c;
    }

    public int initLines(int qty){
        for(int x = num_lines; x < qty + num_lines; x++){
            lines[x][0] = new Point(0, 0);
            lines[x][1] = new Point(0, 0);
            line_colors[x] = Color.BLACK;
        }
        num_lines += qty;
        return num_lines-qty;
    }

    public void clearLines(){
        num_lines = 0;
    }

    public void removeLine(int ln){
        Point[] tp; Color tc;
        for(int x = ln; x < num_lines - 1; x++){
            tp = lines[x];
            lines[x] = lines[x+1];
            lines[x+1] = tp;
            tc = line_colors[x];
            line_colors[x] = line_colors[x+1];
            line_colors[x+1] = tc;
        }
        num_lines--;
    }


    public int addPoint(Point p1){
        return addPoint(p1, Color.BLACK, false);
    }

    public int addPoint(Point p1, Color c, boolean big){
        if(num_points >= point_colors.length){
            System.out.println("ClippingBitmapView: Ran out of lines!");
            return -1;
        }
        points[num_points] = p1;
        point_colors[num_points] = c;
        big_point[num_points] = big;
        num_points++;
        return num_points-1;
    }

    public void setPoint(int ind, Point p1, Color c, boolean big){
        points[ind] = p1;
        point_colors[ind] = c;
        big_point[ind] = big;
    }

    public int initPoints(int qty){
        for(int x = num_points; x < qty + num_points; x++){
            points[x] = new Point(0, 0);
            point_colors[x] = Color.BLACK;
            big_point[x] = false;
        }
        num_points += qty;
        return num_points-qty;
    }

    public void clearPoints(int ps, int pe){
        int i = 0;
        for(int x = ps; x < pe; x++){
            points[x] = new Point(0, 0);
            point_colors[x] = Color.BLACK;
            big_point[x] = false;
        }
    }

    public void clearPoints(){
        num_points = 0;
    }

    public void removePoint(int ln){
        Point tp; Color tc; boolean tb;
        for(int x = ln; x < num_points - 1; x++){
            tp = points[x];
            points[x] = points[x+1];
            points[x+1] = tp;
            tc = point_colors[x];
            point_colors[x] = point_colors[x+1];
            point_colors[x+1] = tc;
            tb = big_point[x];
            big_point[x] = big_point[x+1];
            big_point[x+1] = tb;
        }
        num_points--;
    }
}
