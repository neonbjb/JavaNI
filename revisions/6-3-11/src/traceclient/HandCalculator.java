package traceclient;

import listeners.DepthMapListener;
import listeners.HandMovementListener;

/**
 *
 * @author jbetker
 */
public class HandCalculator implements DepthMapListener, HandMovementListener {
    double depth_sensitivity;
    double hand_orientation; //in degrees, 0deg is straight up
    int spline_segmentation; //number of segments in the spline, divide 180 by this value to get the degree between each segment
    double fanout_angle = 240.;
    
    int[] cur_dd;
    int dd_width, dd_height;
    int cur_hx;
    int cur_hy;
    int base_depth;
    
    public HandCalculator(){
        depth_sensitivity = 50.;
        hand_orientation = 0.;
        spline_segmentation = 30;
        splines = new int[spline_segmentation];
    }
    
    /**
     * Load the depth image stored in <dd> into the calculator, resetting all of the
     * calculated values. <hx, hy> is the calculated x and y coordinates for hand detection.
     */
    public void newDepthMap(int[] dd, int w, int h){
        cur_dd = dd;
        dd_width = w;
        dd_height = h;
    }

    //by their nature, handmoved() messages follow depthAt() messages, so this is when we should do the recalcs
    public void handMoved(double x, double y, double z) {
        cur_hx = (int)x;
        cur_hy = (int)y;
        recalcTrueHandCenter();
        recalcHandSplines();
        recalcHandArea();
        recalcMaxExtension();
        recalcExtensionDepth();
    }
    
    private int depthAt(int x, int y){
        if(x < 0) x = 0;
        if(y < 0) y = 0;
        if(x >= dd_width) x = dd_width-1;
        if(y >= dd_height) y = dd_height-1;
        return cur_dd[x + y * dd_width];
    }
    
    /**
     * Does correction on the precomputed hand center to get a better estimate
     * for where it truly is.
     */
    private void recalcTrueHandCenter(){
        //does nothing for now.
    }
    
    /**
     * Starts at the current hand center on the depth map, and repeatedly moves
     * out at an angle of (hand_orientation+<a>) where <a> is a value from [-90,90]
     * interspersed exactly <spline_stementation> times. When the depth has surpassed
     * the sensitivity value +/-, the function records the distance from the center
     * of the hand and stores it in a spline array.
     */
    int[] splines;
    private void recalcHandSplines(){
        base_depth = depthAt(cur_hx, cur_hy);
        for(int i = 0; i < splines.length; i++){
            double angle = fanout_angle * i / splines.length;
            angle -= fanout_angle / 2;
            //find the proper x, y increments for exploring this spline
            double x = cur_hx, y = cur_hy;
            double ix = Math.sin(angle * Math.PI / 180);
            double iy = -Math.cos(angle * Math.PI / 180);
            int counter = 0;
            while(Math.abs(base_depth - depthAt((int)x, (int)y)) < depth_sensitivity){
                x += ix;
                y += iy;
                if(x < 0 || y < 0 || x >= dd_width || y >= dd_height) break;
                counter++;
            }
            splines[i] = counter-1;
        }
    }
    
    /**
     * calculates the area of the polygon created by the splines calculated in recalcHandSplines(),
     * representing the area of the upper hand.
     */
    double hand_area;
    private void recalcHandArea(){
        //not calculated for now..
    }
    
    /**
     * Sets me_length as the greatest of the splines from recalcHandSplines() and sets
     * me_angle to be the corresponding angle.
     */
    int me_length;
    double me_angle;
    private void recalcMaxExtension(){
        me_length = splines[0];
        me_angle = -90.;
        for(int x = 0; x < splines.length; x++){
            if(splines[x] > me_length){
                me_angle = 180. * x / splines.length - 90;
                me_length = splines[x];
            }
        }
    }
    
    /**
     * requires the max extension length and depth to be
     * calculated. This goes along the max extension angle and allows each step to
     * be reduced in depth by a value of <sensitivity> (as opposed to never being reduced
     * by more than that in total). Jumps of more then <sensitivity> end the depth calculation
     * and the maximum depth reached is stored.
     */
    double me_depth;
    private void recalcExtensionDepth(){
        //I'd like to make this more specific and follow the finger down, but its unnecessary right now, we can just return the depth of the max extension
        double td = depthAt(this.getMaxExtensionX(), this.getMaxExtensionY());
        if(td != 0) me_depth = td;
    }
    
    public double getDepthSensitivity(){
        return depth_sensitivity;
    }
    
    public void setDepthSensitivity(double nds){
        depth_sensitivity = nds;
    }
    
    public double getOrientation(){
        return hand_orientation;
    }
    
    public void setOrientation(double deg){
        hand_orientation = deg;
    }    
    
    public int getSplineSegmentation(){
        return spline_segmentation;
    }
    
    public void setSplineSegmentation(int segs){
        spline_segmentation = segs;
        splines = new int[segs];
    }
    
    public double getHandArea(){
        return hand_area;
    }
    
    public double getMaxExtensionLength(){
        return me_length;
    }
    
    public double getMaxExtensionAngle(){
        return me_angle;
    }

    public double getBasalDepth(){
        return base_depth;
    }

    public double getMaxExtensionDepth(){
        return me_depth;
    }

    public double getDepthDifference(){
        return base_depth - me_depth;
    }

    public int getMaxExtensionX(){
        return (int)(me_length * Math.sin(me_angle * Math.PI / 180)) + cur_hx;
    }

    public int getMaxExtensionY(){
        return (int)(me_length * - Math.cos(me_angle * Math.PI / 180)) + cur_hy;
    }

    public double getHandX(){
        return cur_hx;
    }
    
    public double getHandY(){
        return cur_hy;
    }    
    
    /**
     * Note this returns the spline in their "polar"-like coordinate system,
     * you should use getSplineX(int) getSplineY(int) and numSplines() to retrieve
     * the splines in (x,y) coordinates.
     * @return 
     */
    public int[] getSplines(){
        return splines;
    }
    
    public int getSplineX(int splid){
        double angle = fanout_angle * splid / splines.length - fanout_angle / 2;
        return (int)((double)splines[splid] * Math.sin(angle * Math.PI / 180)) + cur_hx;
    }
    
    public int getSplineY(int splid){
        double angle = fanout_angle * splid / splines.length - fanout_angle / 2;
        return (int)((double)splines[splid] * -Math.cos(angle * Math.PI / 180)) + cur_hy;
    }
    
    public int numSplines(){
        return splines.length;
    }

    public void newCalibration(double tx, double ty, double bx, double by, double z) { } //dont care
}
