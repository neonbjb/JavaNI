package traceclient;

import listeners.HandMovementListener;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.InputEvent;

/**
 *
 * @author James
 */
public class MouseFollower implements HandMovementListener{
    double cal_top_x, cal_top_y, cal_bot_x, cal_bot_y;
    double cal_z;

    Robot robot;
    int scr_w, scr_h;

    boolean calibrated = false;

    public MouseFollower(){
        try{
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] gs = ge.getScreenDevices();
            scr_w = 0; scr_h = 0;
            for(int x = 0; x < 1; x++){// gs.length; x++){
                DisplayMode dm = gs[x].getDisplayMode();
                scr_w += dm.getWidth();
                if(dm.getHeight() > scr_h){
                    scr_h = dm.getHeight();
                }
            }
            System.out.println("Calculated system resolution to be: " + scr_w + "x" + scr_h);
            robot = new Robot();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void newCalibration(double tx, double ty, double bx, double by, double z){
        cal_top_x = tx; cal_top_y = ty;
        cal_bot_x = bx; cal_bot_y = by;
        cal_z = z;
        calibrated = true;
    }

    public void handMoved(double nx, double ny, double z) {
        if(robot == null || !calibrated) return;

        double tb_width = cal_bot_x - cal_top_x;
        double tb_height = cal_bot_y - cal_top_y;

        double mx, my;
        if(nx < cal_top_x){
            mx = 0;
        }else if(nx > cal_bot_x){
            mx = scr_w;
        }else{
            mx = scr_w * ((nx - cal_top_x) / tb_width);
        }
        if(ny < cal_top_y){
            my = 0;
        }else if(ny > cal_bot_y){
            my = scr_h;
        }else{
            my = scr_h * ((ny - cal_top_y) / tb_height);
        }

        robot.mouseMove((int)mx, (int)my);
    }

    public void mousedown(){
        if(calibrated){
            robot.mousePress(InputEvent.BUTTON1_MASK);
        }
    }

    public void mouseup(){
        if(calibrated){
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
        }
    }
}
