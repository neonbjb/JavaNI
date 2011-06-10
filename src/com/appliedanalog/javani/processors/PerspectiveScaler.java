package com.appliedanalog.javani.processors;

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
public class PerspectiveScaler {
    private PerspectiveScaler(){
        
    }

    static PerspectiveScaler psinst = null;
    public static PerspectiveScaler getScaler(){
        if(psinst == null){
            loadScaler(".scaler");
        }
        return psinst;
    }

    public static void loadScaler(String file){
        
    }

    /**
     * This function is the point of this entire class. It takes an xy measurement and
     * scales it based on a change in depth.
     * @param measurement
     * @param depth_change
     * @return
     */
    public double scale(double measurement, double depth_change){
        return 0.;
    }
}
