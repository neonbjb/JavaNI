package com.appliedanalog.javani;

import java.awt.Point;
import com.appliedanalog.javani.listeners.DepthMapListener;
import com.appliedanalog.javani.listeners.HandMovementListener;

/**
 *
 * @author jbetker
 */
public class HandCalculator implements DepthMapListener, HandMovementListener {
    int frame_count;

    double depth_sensitivity;

    double hand_orientation; //in degrees, 0deg is straight up
    double[] hand_orientations = new double[5]; //for smoothing
    int rptr_ho = 0; //rotating pointer for that smoothing value.

    int[] cur_dd;
    int dd_width, dd_height;
    int cur_hx;
    int cur_hy;

    int[] fingers_x = new int[5];
    int[] fingers_y = new int[5];
    int finger_count = 0;
    
    public HandCalculator(){
        depth_sensitivity = 80.;
        frame_count = 0;
    }
    
    /**
     * Load the depth image stored in <dd> into the calculator, resetting all of the
     * calculated values. <hx, hy> is the calculated x and y coordinates for hand detection.
     */
    public void newDepthMap(int[] dd, int w, int h){
        cur_dd = dd;
        dd_width = w;
        dd_height = h;
        frame_count++;
    }

    //by their nature, handmoved() messages follow depthAt() messages, so this is when we should do the recalcs
    public void handMoved(double x, double y, double z) {
        cur_hx = (int)x;
        cur_hy = (int)y;
        recalcTrueHandCenter();
        recalcHandOrientation();
        recalcHandShape();
        recalcHandArea();
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
        cur_hy += 70; //just a simple adjustment moves the center down a tad for better calcuations.
    }
    
    /**
     * Starting at the hand origin, this function continuously finds the mid-points of
     * lines spanning horizontally across the hand. The assumption is that the hand is (in general)
     * bilaterally symmetric (obviously the thumb causes this not to be true, hence the following limitation)
     * This will only be valid on hand orientations between around -30deg to +30deg from a right angle. Future
     * implementations will detect fingers extending horizontally and use vertical spanning lines
     * to get the proper orientation in sideways situations.
     */
    final int NUMBER_OF_LINES = 10; //number of spanning lines to find the midpoint of to calculate the orientation. more lines is more accurate until you start reaching non-symmetric parts of the hand (fingers, joined hand/thumb)
    final int SPAN_INCREMENT = 2; //amount to increment the vertical counter for each spanning line
    final int ANGLE_BOUND = 30; //defines the bounding fan for valid angle values (it would just be invalid above or below this...)
    final int MAX_WIDTH_DIFF = 30;
    public void recalcHandOrientation(){
        int[] midpoints = new int[NUMBER_OF_LINES];
        double home_depth = depthAt(cur_hx, cur_hy);
        int running_width = -1;
        for(int x = 0, y = 0; x < NUMBER_OF_LINES; x++, y++){
            int yv = cur_hy - SPAN_INCREMENT * y; //find current height
            if(Math.abs(depthAt(cur_hx, yv) - home_depth) > depth_sensitivity){
                //we're done, we've surpassed the hand..
                return;
            }
            int r, l; //these will hold the left and right pointers
            for(l = 0; l < cur_hx && Math.abs(depthAt(cur_hx - l, yv) - home_depth) <= depth_sensitivity; l++); //badass loop that does calculations :)
            for(r = 0; (r + cur_hx) < dd_width && Math.abs(depthAt(cur_hx + r, yv) - home_depth) <= depth_sensitivity; r++); //and another one!
            if(running_width == -1) running_width = r - l;
            else if(Math.abs(running_width - (r - l)) > MAX_WIDTH_DIFF){ //the width is varying too drastically..
                x = 0; //restart the calculation
                continue;
            }
            l = cur_hx - l; r = cur_hx + r; //convert these into real points on the depth map
            midpoints[x] = (r + l) / 2;
            //debug("Midpoint (" + r + ", " + l + ")--" + x + " deviation from hand center: " + (cur_hx - midpoints[x]));
        }

        //do linear squares regression to get a line slope
        double sx = 0., sy = 0., sxs = 0., sxy = 0.;
        boolean vert = true;
        for(int x = 0; x < NUMBER_OF_LINES; x++){
            if(vert && x > 0){
                if(midpoints[x] != midpoints[x-1]) vert = false; //if any of the x's are different, then its not vertical!
            }
            sx += midpoints[x];
            sxs += (midpoints[x] * midpoints[x]);
            sy += cur_hy - SPAN_INCREMENT * x;
            sxy += midpoints[x] * (cur_hy - SPAN_INCREMENT * x);
        }
        if(vert || midpoints[0] == midpoints[NUMBER_OF_LINES-1]){
            hand_orientation = 90;
            debug("Orientation is vertical (90deg)");
        }else{
            sx /= (double)NUMBER_OF_LINES; sy /= (double)NUMBER_OF_LINES;
            sxs /= (double)NUMBER_OF_LINES; sxy /= (double)NUMBER_OF_LINES;
            double slope = - (sxy - sx * sy) / (sxs - sx * sx); //negate it because the way we are doing this calculates a backwards angle, we want it wrt the positive x axis
            double norient = Math.atan(slope);
            norient = norient * 180 / Math.PI; //tan puts out radians.
            if(norient < 0) norient += 180;
            if(norient > 90 - ANGLE_BOUND && norient < 90 + ANGLE_BOUND){
                hand_orientation = norient;
            }
        }
    }
    
    /**
     * The following functions do a follow wall right algorithm to calculate the boundaries
     * of the hand-space
     */
    int turnRight(int dir){
        return ((dir + 1) % 4);
    }
    int turnLeft(int dir){
        dir = dir - 1;
        if(dir < 0) dir = 3;
        return dir;
    }
    int dirIncX(int dir){
        switch(dir){
            case 0: return 0;
            case 1: return 1;
            case 2: return 0;
            case 3: return -1;
        } debug("CRITICAL: fell through switch in dirIncX.");
        return 1;
    }
    int dirIncY(int dir){
        switch(dir){
            case 0: return -1;
            case 1: return 0;
            case 2: return 1;
            case 3: return 0;
        } debug("CRITICAL: fell through switch in dirIncY.");
        return 1;
    }
    int _homex, _homey, _homedepth;
    void setHome(int x, int y){
        _homex = x; _homey = y;
        _homedepth = depthAt(x, y);

    }
    boolean amIHome(int x, int y){
        return (x == _homex) && (y == _homey);
    }
    boolean isWall(int x, int y, int dir){
        int ltx = dirIncX(dir) + x;
        int lty = dirIncY(dir) + y;
        //check and see if there is a virtual wall defined by the placement of <home>
        if(lty > _homey){
            return true;
        }
        //check to see if there is a conventional 'wall' (ie a depth wall)
        if(Math.abs(depthAt(ltx, lty) - _homedepth) > depth_sensitivity){
            return true;
        }
        return false;
    }

    private double slope(double[] distances){
        double avg = 0;
        for(int x = 0; x < distances.length; x++){
            avg += distances[x];
        }
        return avg / distances.length;
    }

    /**
     * Calculates the distance of a given point in the depth map from the center
     * of the hand. DOES INCLUDE DEPTH IN THE CALCULATION (obviously somewhat inaccurate
     * due to the non-units of depth)
     * @param x
     * @param y
     * @return
     */
    private double distance(int x, int y){
        return Math.sqrt(Math.pow(x - _homex, 2.) + Math.pow(y - _homey, 2.)
                          + Math.pow(depthAt(x, y) - _homedepth, 2.));
    }

    int cx, cy;
    Point[] pathing = new Point[10000]; //should never be larger than this
    int num_points = 0;
    void recalcHandShape(){
        setHome(cur_hx, cur_hy);
        num_points = 0;
        finger_count = 0;
        int dir = 1; //always start in this direction
        int x = _homex; int y = _homey;
        //the next variables are for finger detection
        final int SLOPE_SMOOTHING = 20; //determines the number of distance differences that are compiled together to make the 'slope' variable.
        final double MIN_SLOPE = -.5; //this is how low the slope has to go before it's registered as a finger (conveniently, half of the array must register as negative for this to happen)
        double last_distance = 0; //calculated distance of immediate last point.
        double[] dist_diff = new double[SLOPE_SMOOTHING]; //used with the private 'slope' function to calculate the trend going on at any one moment
        int rp_dd = 0; //rotating insertion pointer for the distance difference vector
        int highest_x = _homex, highest_y = _homey; //these values are set to the maximal distance as long as the slope is positive, but are recorded and reset to zero as soon as it goes sufficiently negative.
        double highest_distance = -1.;
        do{
            //start by attempting to move forward
            if(!isWall(x, y, dir)){
                x += dirIncX(dir);
                y += dirIncY(dir);
            }else{ //ok can't go forward, lets turn left and see if we can go that way
                dir = turnLeft(dir);
                if(isWall(x, y, dir)){
                    //well we got ourselves in a rut, lets turn left one more time for an about face
                    dir = turnLeft(dir);
                }
                x += dirIncX(dir);
                y += dirIncY(dir);
            }
            //make sure there is still a wall on our right
            if(!isWall(x, y, turnRight(dir))){
                //well turn right and go 'around the corner'
                dir = turnRight(dir);
                x += dirIncX(dir);
                y += dirIncY(dir);
            } //otherwise we're fine.
            if(num_points < pathing.length){
                pathing[num_points] = new Point(x, y);
                num_points++;
                cx = x; cy = y;
                //calculate the physical distance from the 'hand' (finger calculation)
                double nd = distance(x, y);
                dist_diff[rp_dd] = nd - last_distance; rp_dd = (rp_dd + 1) % SLOPE_SMOOTHING;
                double nslope = slope(dist_diff);
                if(nslope > MIN_SLOPE){ //then we can record highest distances
                    if(highest_distance < nd){
                        highest_distance = nd;
                        highest_x = x;
                        highest_y = y;
                    }  
                }else if(highest_distance != -1.){ //then it hasnt been reset yet, record this as a finger
                    if(finger_count >= fingers_x.length){
                        debug("Ran out of recording slots for fingers..");
                    }else{
                        fingers_x[finger_count] = highest_x;
                        fingers_y[finger_count] = highest_y;
                        finger_count++;
                    }
                    highest_distance = -1.; //this flags that we've found a finger, won't need to search till the slope goes positive again.
                }
                last_distance = nd;
            }else{
                //out of points, just give up.
                //debug("We ran out of recording points..");
                return;
            }
        }while(!amIHome(x, y));
    }
    
    /**
     * calculates the area of the polygon created by the splines calculated in recalcHandSplines(),
     * representing the area of the upper hand.
     */
    double hand_area;
    private void recalcHandArea(){
        //not calculated for now..
    }
    
    public double getDepthSensitivity(){
        return depth_sensitivity;
    }
    
    public void setDepthSensitivity(double nds){
        depth_sensitivity = nds;
    }

    /**
     * Returns orientation in degrees.
     * @return
     */
    public double getOrientation(){
        return hand_orientation;
    }
    
    public void setOrientation(double deg){
        hand_orientation = deg;
    }

    public int getHandX(){
        return cur_hx;
    }

    public int getHandY(){
        return cur_hy;
    }
    
    public double getHandArea(){
        return hand_area;
    }

    public double getBasalDepth(){
        return _homedepth;
    }

    public int getFingerX(int f){
        return fingers_x[f];
    }

    public int getFingerY(int f){
        return fingers_y[f];
    }

    public int getFingersDetected(){
        return finger_count;
    }

    public double getFingerLength(int f){
        return Math.sqrt(Math.pow(fingers_x[f] - _homex, 2.) + Math.pow(fingers_y[f] - _homey, 2.)
                          + Math.pow(depthAt(fingers_x[f], fingers_y[f]) - _homedepth, 2.));
    }

    public int getMaxFinger(){
        int max = 0;
        double maxlen = getFingerLength(0);
        for(int x = 0; x < finger_count; x++){
            double nl = getFingerLength(x);
            if(nl > maxlen){
                maxlen = nl;
                max = x;
            }
        }
        return max;
    }

    public double getMaxFingerDepthDifference(){
        int mf = getMaxFinger();
        return getFingerLength(mf) - _homedepth;
    }
    
    public int getFigureX(int splid){
        if(splid >= num_points) return 0;
        return pathing[splid].x;
    }
    
    public int getFigureY(int splid){
        if(splid >= num_points) return 0;
        return pathing[splid].y;
    }
    
    public int figurePointCount(){
        return num_points;
    }

    public void newCalibration(double tx, double ty, double bx, double by, double z) { } //dont care

    public void debug(String msg){
        System.out.println("[" + frame_count + "] " + msg);
    }
}
