package traceclient;

import java.awt.Point;
import listeners.DepthMapListener;
import listeners.HandMovementListener;

/**
 *
 * @author jbetker
 */
public class HandCalculator implements DepthMapListener, HandMovementListener {
    double depth_sensitivity;
    double hand_orientation; //in degrees, 0deg is straight up
    
    int[] cur_dd;
    int dd_width, dd_height;
    int cur_hx;
    int cur_hy;
    
    public HandCalculator(){
        depth_sensitivity = 80.;
        hand_orientation = 0.;
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
        //does nothing for now.
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
        } System.out.println("CRITICAL: fell through switch in dirIncX.");
        return 1;
    }
    int dirIncY(int dir){
        switch(dir){
            case 0: return -1;
            case 1: return 0;
            case 2: return 1;
            case 3: return 0;
        } System.out.println("CRITICAL: fell through switch in dirIncY.");
        return 1;
    }
    int _homex, _homey, _homedepth;
    void setHome(int x, int y){
        _homex = x; _homey = y + 50;
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

    int cx, cy;

    Point[] pathing = new Point[10000]; //should never be larger than this
    int num_points = 0;
    double furthest_point_len;
    int fp_x, fp_y; //furthest point;
    double fp_depth;
    void recalcHandShape(){
        setHome(cur_hx, cur_hy);
        num_points = 0;
        furthest_point_len = 0; fp_x = 0; fp_y = 0;
        int dir = 1; //always start in this direction
        int x = _homex; int y = _homey;
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
                //calculate the physical distance from the 'hand'
                double nd = Math.sqrt(Math.pow(x - _homex, 2.) + Math.pow(y - _homey, 2.)
                          + Math.pow(depthAt(x, y) - _homedepth, 2.));
                if(nd > furthest_point_len){ //store it if it is highest
                    furthest_point_len = nd;
                    fp_x = x;
                    fp_y = y;
                    fp_depth = depthAt(x, y);
                }
            }else{
                //out of points, just give up.
                //System.out.println("We ran out of recording points..");
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
    
    public double getOrientation(){
        return hand_orientation;
    }
    
    public void setOrientation(double deg){
        hand_orientation = deg;
    }    
    
    public double getHandArea(){
        return hand_area;
    }

    public double getBasalDepth(){
        return _homedepth;
    }

    public double getMaxExtensionDepth(){
        return fp_depth;
    }

    public double getDepthDifference(){
        return _homedepth - fp_depth;
    }

    public int getMaxExtensionX(){
        return fp_x;
    }

    public int getMaxExtensionY(){
        return fp_y;
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
}
