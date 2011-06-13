package com.appliedanalog.javani.processors;

import java.awt.Point;

/**
 * This class centers around the measure() method, which takes in a depth map under
 * the presumption that the user is holding his hand in a calibration pose (upright, fingers spread, normal to the camera),
 * and calculates and stores data about that hand.
 *
 * This class has a number of methods that currently work under the presumption that the hand being measured is the right hand.
 * @author jbetker
 */
public class HandMeasurement {
    /* hand measurements */
    int wrist_width; //measures the hand at the wrist, which is algorithmically defined as the horizontal line below the pit that defines the top of the base of the thumb where the width or the aforementioned line stops decreasing
    int knuckle_width;  //measures the width of the knuckles, which is defined as the line spanning across the hand directly above which are the three pits corresponding to the four fingers
    int knuckle_height; //measures the distances between the center of the wrist defined above and the center of the knuckle defined above
    int thumb_webbing_height; //measures the vertical distance between the pit that defines the top of the base of the thumb and the wrist referenced above
    int middle_finger_length; //measures the vertical distance between the tip of the middle finger (two pits from the right on the right hand) and the knuckle height defined above
    int index_finger_length; //measures the vertical distance between the tip of the index finger (three pits from the right on the right hand) and the knuckle height defined above
    int thumb_length; //measures the distance between the pit defining the top of the base of the thumb and the uppermost point defined by the thumb.
    int distance_to_pinky; //measures the distance between the bridge of the hand and the midpoint between the pit demarkating the pinky and the ring finger
    int distance_to_ring; //measures the distance between the bridge of the hand and the midpoint between the pit demarkating the ring and middle finger and the pit talked about above.
    int distance_to_middle; //measures the distance between the bridge of the hand and the midpoint between the pit demarkating the middle and index finger and the pit talked about above.
    int distance_to_index; //measures the distance between the bridge of the hand and the midpoint between the pit demarkating the middle and the index finger and the closest null approaching the thumb horizonally

    int depth_on_measurement; //perhaps the most important variable, defines the depth of the center of the hand when this measurement took place, which can later be refrenced for scaling purposes.
    Point center_of_hand; //measured center of hand, obviously irrelevant in future states

    HandCalculator calc; //used to trace the outline of the hand.

    int depthmap_width, depthmap_height;

    //debugging variables
    Point thumb, index, middle, ring, pinky;
    Point pthumb, pindex, pmiddle, pring;
    Point athumb, aindex, amiddle, aring, apinky;
    Point knuckle_attach_left, knuckle_attach_right;
    Point wrist_center;

    public HandMeasurement(){
        wrist_width = -1;
        knuckle_width = -1;
        knuckle_height = -1;
        thumb_webbing_height = -1;
        middle_finger_length = -1;
        index_finger_length = -1;
        thumb_length = -1;
        depth_on_measurement = -1;
        calc = new HandCalculator();
    }

    public boolean handMeasured(){
        return depth_on_measurement != -1;
    }

    public void measure(int[] depth_map, int dm_width, int dm_height, int handpoint_x, int handpoint_y, int handpoint_z){
        depthmap_width = dm_width;
        depthmap_height = dm_height;
        calc.newDepthMap(depth_map, dm_width, dm_height);
        calc.handMoved(handpoint_x, handpoint_y, handpoint_z);
        if(!calc.isFigureValid()){
            System.out.println("Hand calculator could not create a valid hand figure! Try mucking with the maximum point count for the drawer.");
            return;
        }
        if(calc.getFingersDetected() != 5){
            System.out.println("Did not find all of the fingers! Retry the pose!");
            return;
        }

        //first find the wrist
        int tb_ind = findPitAboveThumbBase();
        if(tb_ind == -1){
            System.out.println("Hand calculator couldn't get a valid thumb base, the pose must be incorrect..");
            return;
        }
        Point thumbbase = calc.getFigure(tb_ind); athumb = thumbbase;
        pthumb = thumbbase; athumb = thumbbase;
        int tb_y = thumbbase.y;
        wrist_width = measureWristWidth(thumbbase);
        thumb_webbing_height = wrist_y - tb_y;
        wrist_center = new Point(wrist_x_center, wrist_y);
        //here would be a good time to find thumb length

        //now lets go along the bridge of the hand and calculate the finger values
        int[] finger_mapping = new int[calc.getFingersDetected()]; //mapping that sorts the fingers in calc from least to greatest (thumb is index 0, pinky is 4)
        for(int x = 0; x < calc.getFingersDetected(); x++){ //basic selection sort; its a mapping sort and it's only ever going to be 5 indices so not worth bothering with more complicated MANUEVERS :)!
            int lv; //last value
            if(x == 0) lv = -1;
            else lv = calc.getFingerX(finger_mapping[x-1]);
            int lov = Integer.MAX_VALUE; //lowest value
            int lovi = -1; //lowest value index
            for(int y = 0; y < calc.getFingersDetected(); y++){
                if(calc.getFingerX(y) > lv && calc.getFingerX(y) < lov){
                    lov = calc.getFingerX(y);
                    lovi = y;
                }
            }
            finger_mapping[x] = lovi;
        }
        thumb = calc.getFinger(calc.getFingerIndex(finger_mapping[0]));
        index = calc.getFinger(calc.getFingerIndex(finger_mapping[1]));
        middle = calc.getFinger(calc.getFingerIndex(finger_mapping[2]));
        ring = calc.getFinger(calc.getFingerIndex(finger_mapping[3]));
        pinky = calc.getFinger(calc.getFingerIndex(finger_mapping[4]));
        int ring_pit = findNextLeftPitStartingAtIndex(calc.getFingerIndex(finger_mapping[4]));
        if(ring_pit == -1){
            System.out.println("Hand calculator could not find a pit for the ring finger, the pose must be incorrect.."); return;
        }
        pring = calc.getFigure(ring_pit);
        int middle_pit = findNextLeftPitStartingAtIndex(ring_pit+1);
        if(middle_pit == -1){
            System.out.println("Hand calculator could not find a pit for the middle finger, the pose must be incorrect.."); return;
        }
        pmiddle = calc.getFigure(middle_pit);
        int index_pit = findNextLeftPitStartingAtIndex(middle_pit+1);
        if(index_pit == -1){
            System.out.println("Hand calculator could not find a pit for the index finger, the pose must be incorrect.."); return;
        }
        pindex = calc.getFigure(index_pit);
        int thumb_bridge_x = calc.iterateTillVoid(calc.getFigureX(index_pit), calc.getFigureY(index_pit), -1, 0).x;
        int pinky_bridge_x = calc.iterateTillVoid(calc.getFigureX(ring_pit), calc.getFigureY(ring_pit), 1, 0).x;

        knuckle_attach_left = new Point(thumb_bridge_x, calc.getFigureY(index_pit));
        knuckle_attach_right = new Point(pinky_bridge_x, calc.getFigureY(ring_pit));
        knuckle_width = (int)calc.distance(thumb_bridge_x, calc.getFigureY(index_pit),
                                           pinky_bridge_x, calc.getFigureY(ring_pit));
        Point mp = calc.midpoint(thumb_bridge_x, calc.getFigureY(index_pit),
                                    pinky_bridge_x, calc.getFigureY(ring_pit));
        knuckle_height = (int)calc.distance(wrist_x_center, wrist_y, mp.x, mp.y);
        center_of_hand = calc.midpoint(wrist_x_center, wrist_y, mp.x, mp.y);
        mp = calc.midpoint(pinky_bridge_x, calc.getFigureY(ring_pit),
                                    calc.getFigureX(ring_pit), calc.getFigureY(ring_pit));
        apinky = mp;
        distance_to_pinky = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        mp = calc.midpoint(calc.getFigureX(ring_pit), calc.getFigureY(ring_pit),
                                    calc.getFigureX(middle_pit), calc.getFigureY(middle_pit));
        aring = mp;
        distance_to_ring = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        mp = calc.midpoint(calc.getFigureX(middle_pit), calc.getFigureY(middle_pit),
                                    calc.getFigureX(index_pit), calc.getFigureY(index_pit));
        amiddle = mp;
        distance_to_middle = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        middle_finger_length = (int)calc.distance(mp.x, mp.y, calc.getFingerX(finger_mapping[2]), calc.getFingerY(finger_mapping[2]));
        mp = calc.midpoint(calc.getFigureX(index_pit), calc.getFigureY(index_pit),
                                    thumb_bridge_x, calc.getFigureY(index_pit));
        aindex = mp;
        distance_to_index = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        index_finger_length = (int)calc.distance(mp.x, mp.y, calc.getFingerX(finger_mapping[1]), calc.getFingerY(finger_mapping[1]));

        depth_on_measurement = calc.depthAt(center_of_hand.x, center_of_hand.y);
    }

    /*
     * Iterates horizontally finding the edges of the current horizontal scanning line
     */
    int gw_centerx; //adjusted every time this function is called, represents the centered x coordinate in the last call.
    private int getWidth(int x, int y){
        int eh, el;
        for(eh = x; x < depthmap_width && calc.isOnHand(eh, y); eh++); eh--;
        for(el = x; x >= 0 && calc.isOnHand(el, y); el--); el++;
        gw_centerx = (eh + el) / 2;
        return eh - el;
    }

    private double average(int[] inc){
        double accu = 0.;
        for(int x = 0; x < inc.length; x++){
            accu += inc[x];
        }
        return accu / inc.length;
    }

    int wrist_y; //defines the height at which the wrist is measured in this measurement
    int wrist_x_center; //defines the center of the wrist as measured in this snapshot
    private int measureWristWidth(Point thumbbase){
        int prev_width;
        int[] prev_width_diff = new int[5];
        int pw_rptr = 0; //rotating pointer
        //prefill the widths
        prev_width = getWidth(thumbbase.x, thumbbase.y);
        for(int x = 1; x <= prev_width_diff.length; x++){
            int w = getWidth(gw_centerx, thumbbase.y - x);
            prev_width_diff[x-1] = w - prev_width;
            prev_width = w;
        }

        //iterate until the average derivative of widths goes from negative to zero or more
        int ycoord = thumbbase.y - 5;
        int w = 0;
        while(average(prev_width_diff) < 0.){
            w = getWidth(gw_centerx, ycoord);
            prev_width_diff[pw_rptr] = w - prev_width;
            prev_width = w;
            ycoord--;
            pw_rptr = (pw_rptr + 1) % prev_width_diff.length;
        }
        wrist_y = ycoord;
        wrist_x_center = gw_centerx;
        return w;
    }

    //these functions all return indices within the point outlined created by calc

    //this function REQUIRES that the depth map of the hand has a distance of at least
    //20 points on the y axis between the tip of the thumb and the pit of the thumb. It
    //also requires that the depth map cannot have 20 units of error.
    private int findPitAboveThumbBase(){
        int cind = calc.figurePointCount() - 1;
        int cind_el = calc.getFigureY(0);
        
        //start by following the outline left until it hits an edge and goes up.
        while(calc.getFigureY(cind) == cind_el){
            cind--;
        }

        //from here, the figure y value will increase to a maximum point (the thumb, 
        //then it will decrease to a minimum point (the thumb pit), we want to find the latter
        //we will follow the outline up until it reaches a point from which it reaches 20 units below
        //the maximum recorded height, at which point we will start recording for the lowest point found and wait until
        //we've re-reached the maximum elevation.

        //the irony here is that the "max" is actually the smallest y, and the "min" is actually the largest y (due to the coordinate system)
        int max_found, min_found = -1; //indices in the figure
        int max_found_y, min_found_y;
        max_found = cind; max_found_y = calc.getFigureY(cind);
        //phase 1, search until we've come 20 units below our maximum
        while(cind > 0 && calc.getFigureY(cind) - max_found_y < 20){
            int fy = calc.getFigureY(cind);
            if(fy < max_found_y){
                max_found = cind; max_found_y = fy;
            }
            cind--;
        }
        if(cind == 0) return -1;
        min_found_y = max_found_y;

        //phase 2, search until we've come back up to the maximum
        while(cind > 0 && calc.getFigureY(cind) > max_found){
            int fy = calc.getFigureY(cind);
            if(fy > min_found_y){
                min_found = cind; min_found_y = fy;
            }
            cind--;
        }
        if(cind == 0) return -1;

        return min_found;
    }

    /**
     * The index passed into this function MUST be located either 20 units above the
     * pit you are searching for (the tip of a finger works perfect), or in a pit adjacent to
     * the pit you are looking for, such that in iterating left from the given location,
     * you will move up 20 units above the pit you are searching for before falling back
     * into it. Finally, there must be a steep raise of 20 units on the other side of the pit
     * for it to be properly registered
     * @param is
     * @return
     */
    private int findNextLeftPitStartingAtIndex(int is){
        int cind = is;

        //NOTE: the "max" is actually the smallest y, and the "min" is actually the largest y (due to the coordinate system)
        int max_found, min_found = -1; //indices in the figure
        int max_found_y, min_found_y;
        max_found = cind; max_found_y = calc.getFigureY(cind);
        //phase 1, search until we've come 20 units below our maximum
        while(cind < calc.figurePointCount() && calc.getFigureY(cind) - max_found_y < 20){
            int fy = calc.getFigureY(cind);
            if(fy < max_found_y){
                max_found = cind; max_found_y = fy;
            }
            cind++;
        }
        if(cind == calc.figurePointCount()) return -1;
        min_found_y = max_found_y;

        //phase 2, search until we've come back up to the maximum
        while(cind < calc.figurePointCount() && min_found_y - calc.getFigureY(cind) < 20){
            int fy = calc.getFigureY(cind);
            if(fy > min_found_y){
                min_found = cind; min_found_y = fy;
            }
            cind++;
        }
        if(cind == calc.figurePointCount()) return -1;

        return min_found;
    }

    public int getWristWidth(){
        return wrist_width;
    }

    public int getWristWidth(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(wrist_width, dd);
    }

    public int getKnuckleWidth(){
        return knuckle_width;
    }

    public int getKnuckleWidth(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(knuckle_width, dd);
    }

    public int getKnuckleHeight(){
        return knuckle_height;
    }

    public int getKnuckleHeight(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(knuckle_height, dd);
    }

    public int getThumbWebbingHeight(){
        return thumb_webbing_height;
    }

    public int getThumbWebbingHeight(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(thumb_webbing_height, dd);
    }

    public int getMiddleFingerLength(){
        return middle_finger_length;
    }

    public int getMiddleFingerLength(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(middle_finger_length, dd);
    }

    public int getIndexFingerLength(){
        return index_finger_length;
    }

    public int getIndexFingerLength(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(index_finger_length, dd);
    }

    public int getThumbLength(){
        return thumb_length;
    }

    public int getThumbLength(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(thumb_length, dd);
    }

    public int getDistanceToPinky(){
        return distance_to_pinky;
    }

    public int getDistanceToPinky(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(distance_to_pinky, dd);
    }

    public int getDistanceToRing(){
        return distance_to_ring;
    }

    public int getDistanceToRing(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(distance_to_ring, dd);
    }

    public int getDistanceToMiddle(){
        return distance_to_middle;
    }

    public int getDistanceToMiddle(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(distance_to_middle, dd);
    }

    public int getDistanceToIndex(){
        return distance_to_index;
    }

    public int getDistanceToIndex(int cohd){
        int dd = depth_on_measurement - cohd;
        return (int)PerspectiveScaler.getScaler().scale(distance_to_index, dd);
    }

    //The fields below are frame-specific: they describe the depth map as it was when
    //the measurement was taken, but do not necessarily have any pertinence to the
    //geometry of the hand itself. (they are mostly for debugging)
    public int getMeasurementDepth(){
        return depth_on_measurement;
    }
    public Point getMeasurementCenterOfHand(){
        return center_of_hand;
    }
    public Point getThumb(){
        return thumb;
    }
    public Point getIndexFinger(){
        return index;
    }
    public Point getMiddleFinger(){
        return middle;
    }
    public Point getRingFinger(){
        return ring;
    }
    public Point getPinky(){
        return pinky;
    }
    public Point getThumbPit(){
        return pthumb;
    }
    public Point getIndexPit(){
        return pindex;
    }
    public Point getMiddlePit(){
        return pmiddle;
    }
    public Point getRingPit(){
        return pring;
    }
    public Point getLeftKnuckleAttachment(){
        return knuckle_attach_left;
    }
    public Point getRightKnuckleAttachment(){
        return knuckle_attach_right;
    }
    public Point getWristCenter(){
        return wrist_center;
    }
    public Point getThumbAttachment(){
        return athumb;
    }
    public Point getIndexAttachment(){
        return aindex;
    }
    public Point getMiddleAttachment(){
        return amiddle;
    }
    public Point getRingAttachment(){
        return aring;
    }
    public Point getPinkyAttachment(){
        return apinky;
    }
}
