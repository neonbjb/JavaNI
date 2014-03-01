package com.appliedanalog.javani.processors;

import java.awt.Point;
import com.appliedanalog.javani.listeners.DepthMapListener;
import com.appliedanalog.javani.listeners.HandMovementListener;

/**
 *
 * @author jbetker
 */
public class HandCalculator implements DepthMapListener, HandMovementListener {
    HandMeasurement measurement = null;

    int frame_count;
    double depth_sensitivity;
    double hand_orientation = 90; //in degrees, 0deg is straight up
    double[] hand_orientations = new double[5]; //for smoothing
    int rptr_ho = 0; //rotating pointer for that smoothing value.

    int[] cur_dd;
    int dd_width, dd_height;
    int cur_hx;
    int cur_hy;

    int[] fingers_x = new int[5];
    int[] fingers_y = new int[5];
    int[] finger_indices = new int[5];
    int finger_count = 0;
    
    public HandCalculator(){
        depth_sensitivity = 80.;
        frame_count = 0;
    }

    public void attachMeasurement(HandMeasurement m){
        measurement = m;
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
        if(cur_dd == null) return; //havent gotten a depth map yet for some reason..
        cur_hx = (int)x;
        cur_hy = (int)y;
        placeCoordsOnHand();
        if(measurement != null){
            double hand_height = measurement.getKnuckleHeight();
            double hh = measurement.getKnuckleHeight(depthAt(cur_hx, cur_hy));
            System.out.println("Knuckle height: " + hand_height + " adjusted: " + hh);
            Point p = moveDownDistanceOnHand(cur_hx, cur_hy, (int)hh);
            cur_hx = p.x;
            cur_hy = p.y;
        }else{
            Point p = moveDownDistanceOnHand(cur_hx, cur_hy, 50);
            cur_hx = p.x;
            cur_hy = p.y;
        }
        recalcHandShape();
        recalcHandArea();
    }
    
    public int depthAt(int x, int y){
        if(x < 0) x = 0;
        if(y < 0) y = 0;
        if(x >= dd_width) x = dd_width-1;
        if(y >= dd_height) y = dd_height-1;
        return cur_dd[x + y * dd_width];
    }

    public boolean isOnHand(int x, int y){
        return Math.abs(depthAt(x, y) - depthAt(cur_hx, cur_hy)) < depth_sensitivity;
    }

    /**
     * Does correction on the precomputed hand center to at least get the damn thing ON the hand
     */
    int last_hand_depth = -1;
    private void placeCoordsOnHand(){
        if(last_hand_depth != -1 &&
           Math.abs(depthAt(cur_hx, cur_hy) - last_hand_depth) > depth_sensitivity){
            //move in circles with incremented radius' of 5 and degrees of 10, starting at 5 and stopping at 200, seeing if we can find a point on the hand.
            for(int r = 5; r < 200; r+=5){
                for(int a = 0; a < 360; a+=10){
                    int tx = (int)((double)cur_hx + (double)r * Math.cos(a * 2 * Math.PI / 360));
                    int ty = (int)((double)cur_hy + (double)r * Math.sin(a * 2 * Math.PI / 360));
                    if(Math.abs(depthAt(cur_hx, cur_hy) - last_hand_depth) < depth_sensitivity){
                        cur_hx = tx;
                        cur_hy = ty;
                        last_hand_depth = depthAt(cur_hx, cur_hy);
                        return;
                    }
                }
            }
        }
        last_hand_depth = depthAt(cur_hx, cur_hy);
    }
    
    public void recalcHandOrientation(){

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
     * Starting at a point on the hand, move down in any direction, maintaining
     * a certainty that you are still within the hands bounds a specified distance.
     * @param sx
     * @param xy
     * @param distance
     */
    public Point moveDownDistanceOnHand(int sx, int sy, int distance){
        int cx = sx;
        int cy = sy;
        int depth = depthAt(sx, sy);
        while(distance(sx, sy, cx, cy) < distance){
            //always prefer going down if possible. never go up.
            if(Math.abs(depthAt(cx, cy + 1) - depth) > depth_sensitivity){
                //re-center the point on the hand
                cx = center(cx, cy);
                if(Math.abs(depthAt(cx, cy + 1) - depth) > depth_sensitivity){
                    System.out.println("moveDownDistanceOnHand: got trapped somehow! bailing..");
                    return new Point(cx, cy);
                }
            }else{
                cy++;
            }
        }
        return new Point(cx, cy);
    }

    /**
     * Calculates the distance of a given point in the depth map from the center
     * of the hand. DOES INCLUDE DEPTH IN THE CALCULATION (obviously somewhat inaccurate
     * due to the non-units of depth)
     * @param x
     * @param y
     * @return
     */
    public double distance(int x1, int y1, int x2, int y2){
        return Math.sqrt(Math.pow(x1 - x2, 2.) + Math.pow(y1 - y2, 2.)
                          + Math.pow(depthAt(x1, y1) - depthAt(x2, y2), 2.));
    }

    public double distanceFromHome(int x, int y){
        return distance(x, y, _homex, _homey);
    }

    public Point midpoint(int x1, int y1, int x2, int y2){
        Point p = new Point();
        p.x = (x1 + x2) / 2;
        p.y = (y1 + y2) / 2;
        return p;
    }

    //finds the left and right walls at the current elevation and returns the center of the hand here.
    //p MUST be on the hand
    public int center(int sx, int sy){
        int lx = sx, rx = sx;
        int s_depth = depthAt(sx, sy);
        while(lx >= 0 && (Math.abs(depthAt(lx, sy) - s_depth) < depth_sensitivity)) lx--;
        while(rx < dd_width && (Math.abs(depthAt(rx, sy) - s_depth) < depth_sensitivity)) rx++;
        return lx + rx / 2;
    }

    /**
     * Iterates using the provided step size until isOnHand(location) returns false.
     * Returns the previous point (the last point ON THE HAND of the progression)
     * @param sx Starting x location.
     * @param sy Starting y location.
     * @param ix x increment per step.
     * @param iy y increment per step.
     * @return
     */
    public Point iterateTillVoid(int sx, int sy, int ix, int iy){
        while(isOnHand(sx, sy)){
            sx += ix;
            sy += iy;
        }
        return new Point(sx - ix, sy - iy);
    }

    int cx, cy;
    Point[] pathing = new Point[10000]; //should never be larger than this
    int num_points = 0;
    boolean got_hand_shape = false;
    void recalcHandShape(){
        setHome(cur_hx, cur_hy);
        num_points = 0;
        finger_count = 0;
        int dir = 1; //always start in this direction
        int x = _homex; int y = _homey;
        //the next variables are for finger detection
        final int SLOPE_SMOOTHING = 20; //determines the number of distance differences that are compiled together to make the 'slope' variable.
        final double MIN_SLOPE = -.5; //this is how low the slope has to go before it's registered as a finger (conveniently, half of the array must register as negative for this to happen)
        final double RESET_SLOPE = 0.; //this is how high the slope must go to reset a finger search
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
                double nd = distanceFromHome(x, y);
                dist_diff[rp_dd] = nd - last_distance; rp_dd = (rp_dd + 1) % SLOPE_SMOOTHING;
                double nslope = slope(dist_diff);
                if(nslope > RESET_SLOPE || (highest_distance != -1. && nslope > MIN_SLOPE)){ //then we can record highest distances
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
                        finger_indices[finger_count] = num_points-1;
                        finger_count++;
                    }
                    highest_distance = -1.; //this flags that we've found a finger, won't need to search till the slope goes positive again.
                }
                last_distance = nd;
            }else{
                //out of points, just give up.
                //debug("We ran out of recording points..");
                got_hand_shape = false;
                return;
            }
        }while(!amIHome(x, y));
        got_hand_shape = true;
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

    public double getSmoothedOrientation(){
        double avg = hand_orientations[0];
        for(int x = 1; x < hand_orientations.length; x++){
            avg += hand_orientations[x];
        }
        return avg / hand_orientations.length;
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

    public Point getFinger(int f){
        return new Point(fingers_x[f], fingers_y[f]);
    }

    public int getFingerIndex(int f){
        return finger_indices[f];
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

    public Point getFigure(int splid){
        if(splid >= num_points) return null;
        return pathing[splid];
    }
    
    public int figurePointCount(){
        return num_points;
    }

    public boolean isFigureValid(){
        return got_hand_shape;
    }

    public int getMaxFigurePoints(){
        return pathing.length;
    }

    public void newCalibration(double tx, double ty, double bx, double by, double z) { } //dont care

    public void debug(String msg){
        System.out.println("[" + frame_count + "] " + msg);
    }
}
