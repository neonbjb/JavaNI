package com.appliedanalog.javani.processors;

import com.appliedanalog.javani.dialogs.MeasurementViewer;
import com.appliedanalog.javani.graphs.ClippingBitmapView;
import java.awt.Color;
import java.awt.Point;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * This class is responsible for mapping a given width/height measurement in the xy-plane
 * to it's corresponding width/height measurement given a specified difference in depth.
 *
 * It is a runnable class that does basic calibration and stores it in a local file. Ideally,
 * this calibration must be run only once per sensor and is good from that point forward. The
 * class provides factory-like methods for creating instances of itself.
 *
 * This class is necessary since not all devices might have uniform scaling factors between
 * their xy and z planes (ie while the distance between 1 pixel on the xy corresponds to 1 inch,
 * a 1 unit change in the depth map may not denote that same 1 inch)
 * @author jbetker
 */
public class PerspectiveScaler implements Serializable {
    double scale_factor; //the only variable, very important..

    private PerspectiveScaler(){ }

    static PerspectiveScaler psinst = null;
    public static PerspectiveScaler getScaler(){
        if(psinst == null){
            loadScaler(".scaler");
        }
        return psinst;
    }

    public static void loadScaler(String file){
        try{
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            psinst = (PerspectiveScaler)ois.readObject();
            ois.close();
        }catch(Exception e){
            psinst = new PerspectiveScaler();
        }
    }

    public static void saveScaler(){
        try{
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(".scaler"));
            oos.writeObject(psinst);
            oos.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    static int[] _curdm;
    static int _cdmw, _cdmh; //current depth map width/height
    static int depthAt(int x, int y){
        if(x < 0) x = 0;
        if(y < 0) y = 0;
        if(x >= _cdmw) x = _cdmw-1;
        if(y >= _cdmh) y = _cdmh-1;
        return _curdm[x + y * _cdmw];
    }
    static int max_field = 2000;
    static boolean isInPlane(int x1, int y1, int x2, int y2){
        if(depthAt(x1, y1) == 0 || depthAt(x2, y2) == 0) return false;
        if(Math.abs(depthAt(x1, y1) - depthAt(x2, y2)) < 35){
            if(depthAt(x1, y1) < max_field){
                return true;
            }
        }
        return false;
    }
    static int width(int x, int y){
        //search left then right
        int min_x = x;
        int max_x = x;
        while(isInPlane(x, y, min_x, y)) min_x--;
        while(isInPlane(x, y, max_x, y)) max_x++;
        if(dbg != null) dbg.addLine(new Point(min_x, y), new Point(max_x, y), Color.CYAN);
        return max_x - min_x;
    }

    static final int MIN_HORIZ = 120;
    static final int MIN_VERT = 70;
    static final int WIDTH_RANGE = 20;

    //first do a vertical scan; we are looking for anything with 20+ scanning lines of
    //consistent "in plane" values that are near one another.
    //
    //the search will work as follows: run across until you find MIN_HORIZ in plane, skip to
    //center, run down to see if you find MIN_VERT in plane (if you dont, continue alg), then verify
    //widths by going back to the top center, and running down calculating the widths. If all widths
    //are within WIDTH_RANGE of one another, it will find a center point and
    //finally use that center point to find the width and height of the card in the dm_far. From there,
    //get the ratios of width to depth and you have your answer
    static ClippingBitmapView dbg;
    public static int stage1_x, stage1_y, stage1_z;
    public static int stage1_width;
    public static boolean calibrateStage1(int[] dm_close, int dm_w, int dm_h, MeasurementViewer mv){
        ClippingBitmapView view = mv.getView();
        dbg = view;
        //int cbv_pt = 0;

        max_field = 1000;
        _curdm = dm_close;
        _cdmh = dm_h;
        _cdmw = dm_w;
        int[] widths = new int[MIN_VERT];
        int current_obs_width = 0;
        int cow_x = 0, cow_y = 0; //location of the currently observed width
        int y = 0, x = 0;
        while(y < (dm_h - MIN_VERT)){
            x = 0;
            while(x < (dm_w - MIN_HORIZ)){
                view.repaint();
                if(isInPlane(x, y, cow_x, cow_y)){
                    current_obs_width++;
                    if(current_obs_width >= MIN_HORIZ){
                        //search down the vertical axis of the midpoint of this observed width
                        int tx = (x + cow_x) / 2; int ty = y;
                        boolean vert_broken = false;
                        while(!vert_broken && (ty - y) < MIN_VERT){
                            if(!isInPlane(x, y, tx, ty)) vert_broken = true;
                            view.addPoint(new Point(tx, ty));
                            view.repaint();
                            ty++;
                        }
                        if(!vert_broken){
                            //ok so theres a horizontal line the applicable length in plane AND a vert one, lets see if all the widths match
                            double avg_width = 0.;
                            for(int i = 0; i < MIN_VERT; i++){
                                view.addPoint(new Point(tx, y+i), Color.GREEN, true);
                                view.repaint();
                                widths[i] = width(tx, y + i);
                                avg_width += widths[i];
                            }
                            //check that they are reasonably similar.
                            avg_width /= MIN_VERT;
                            for(int i = 0; !vert_broken && i < MIN_VERT; i++){
                                if(Math.abs(avg_width - widths[i]) > WIDTH_RANGE) vert_broken = true;
                            }
                            if(!vert_broken){
                                stage1_x = tx;
                                stage1_y = y + MIN_VERT / 2;
                                stage1_z = depthAt(stage1_x, stage1_y);
                                stage1_width = (int)avg_width;
                                System.out.println("Average observed width: " + avg_width);
                                //now calculate the average width along a portion of the card starting at the center
                                view.addPoint(new Point(stage1_x, stage1_y), Color.ORANGE, true);
                                return true;
                            }
                        }
                        //we've fallen out, reset everything.
                        //view.clearPoints((cbv_pt-500)<0?0:(cbv_pt-500), cbv_pt);
                        current_obs_width = 0;
                        cow_x = x;
                        cow_y = y;
                    }
                }else{
                    //view.clearPoints((cbv_pt-500)<0?0:(cbv_pt-500), cbv_pt);
                    current_obs_width = 0;
                    cow_x = x;
                    cow_y = y;
                }
                x++;
            }
            y++;
        }
        return false;
    }

    //so we've got an x, y, z centration on the card, now lets look at the zoomed depth map.
    //this MUST be preceded by a call to calibrateStage1.
    public static double calibrateStage2(int[] dm_far){
        if(dbg != null){
            dbg.clearPoints();
            dbg.clearLines();
        }
        max_field = 3000;
        _curdm = dm_far;
        int nd = depthAt(stage1_x, stage1_y);
        final int MIN_WIDTHS = 20;
        double avg_width = 0.;
        //first find the top.
        int yt = stage1_y - MIN_WIDTHS / 2;
        for(int x = 0; x < MIN_WIDTHS; x++){
            if(!isInPlane(stage1_x, stage1_y, stage1_x, yt + x)){
                System.out.println("You brought the calibration box out too far!");
                return -1.;
            }
            avg_width += width(stage1_x, yt + x);
        }
        avg_width /= (double)MIN_WIDTHS;
        double scle = stage1_width / avg_width;
        getScaler().scale_factor = (stage1_width - avg_width) / (double)(nd - stage1_z);
        System.out.println("A scale of " + scle + " at " + (nd - stage1_z) + " makes for a scale factor of " + getScaler().scale_factor);
        return getScaler().scale_factor;
    }

    public static void forceScaleFactor(double sf){
        getScaler().scale_factor = sf;
        saveScaler();
    }

    /**
     * This function is the point of this entire class. It takes an xy measurement and
     * scales it based on a change in depth.
     * @param measurement
     * @param depth_change
     * @return
     */
    public static double scale(double measurement, double depth_change){
        return getScaler().scale_factor * depth_change + measurement;
    }
}
