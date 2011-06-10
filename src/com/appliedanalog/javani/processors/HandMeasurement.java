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

    public void measure(int[] depth_map, int dm_width, int dm_height){
        calc.newDepthMap(depth_map, dm_width, dm_height);
        if(calc.getFingersDetected() != 5){
            System.out.println("Did not find all of the fingers! Retry the pose!");
            return;
        }

        //first find the wrist
        int thumbbase = findPitAboveThumbBase();
        int tb_y = calc.getFigureY(thumbbase);
        wrist_width = measureWristWidth(tb_y + 1);
        thumb_webbing_height = wrist_y - tb_y;
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
        int ring_pit = findNextLeftPitStartingAtIndex(calc.getFingerIndex(finger_mapping[4]));
        int middle_pit = findNextLeftPitStartingAtIndex(ring_pit+1);
        int index_pit = findNextLeftPitStartingAtIndex(middle_pit+1);
        int thumb_bridge_x = calc.iterateTillVoid(calc.getFigureX(index_pit), calc.getFigureY(index_pit), -1, 0).x;
        int pinky_bridge_x = calc.iterateTillVoid(calc.getFigureX(ring_pit), calc.getFigureY(ring_pit), 1, 0).x;

        knuckle_width = (int)calc.distance(thumb_bridge_x, calc.getFigureY(index_pit),
                                           pinky_bridge_x, calc.getFigureY(ring_pit));
        Point mp = calc.midpoint(thumb_bridge_x, calc.getFigureY(index_pit),
                                    pinky_bridge_x, calc.getFigureY(ring_pit));
        knuckle_height = (int)calc.distance(wrist_x_center, wrist_y, mp.x, mp.y);
        center_of_hand = calc.midpoint(wrist_x_center, wrist_y, mp.x, mp.y);
        mp = calc.midpoint(pinky_bridge_x, calc.getFigureY(ring_pit),
                                    calc.getFigureX(ring_pit), calc.getFigureY(ring_pit));
        distance_to_pinky = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        mp = calc.midpoint(calc.getFigureX(ring_pit), calc.getFigureY(ring_pit),
                                    calc.getFigureX(middle_pit), calc.getFigureY(middle_pit));
        distance_to_ring = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        mp = calc.midpoint(calc.getFigureX(middle_pit), calc.getFigureY(middle_pit),
                                    calc.getFigureX(index_pit), calc.getFigureY(index_pit));
        distance_to_middle = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        middle_finger_length = (int)calc.distance(mp.x, mp.y, calc.getFingerX(finger_mapping[2]), calc.getFingerY(finger_mapping[2]));
        mp = calc.midpoint(calc.getFigureX(index_pit), calc.getFigureY(index_pit),
                                    thumb_bridge_x, calc.getFigureY(index_pit));
        distance_to_index = (int)calc.distance(pinky_bridge_x, calc.getFigureY(ring_pit), mp.x, mp.y);
        index_finger_length = (int)calc.distance(mp.x, mp.y, calc.getFingerX(finger_mapping[1]), calc.getFingerY(finger_mapping[1]));

        depth_on_measurement = calc.depthAt(center_of_hand.x, center_of_hand.y);
    }

    int wrist_y; //defines the height at which the wrist is measured in this measurement
    int wrist_x_center; //defines the center of the wrist as measured in this snapshot
    private int measureWristWidth(int y_beg){
        return -1;
    }

    //these functions all return indices within the point outlined created by calc
    private int findPitAboveThumbBase(){
        return -1;
    }

    private int findNextLeftPitStartingAtIndex(int is){
        return -1;
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
}
