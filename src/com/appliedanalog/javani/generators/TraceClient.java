package com.appliedanalog.javani.generators;

import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import com.appliedanalog.javani.listeners.DepthMapListener;
import com.appliedanalog.javani.listeners.HandMovementListener;
import com.appliedanalog.javani.listeners.MessageTransmitter;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 *
 * @author jbetker
 */
public class TraceClient implements Runnable{    
    ArrayList<HandMovementListener> handlisteners = new ArrayList<HandMovementListener>();
    ArrayList<DepthMapListener> depthlisteners = new ArrayList<DepthMapListener>();
    int[] depth_buffer = null;
    byte[] depth_buffer_raw = null;
    boolean running = false;
    InputStream src;
    int frame_counter = 0;

    //logging
    final boolean DO_LOGGING = true;
    boolean recording_started;
    DataOutputStream log_os;
    
    final int FRAME_SKIP = 1; //skip every (n-1) frames

    public TraceClient(InputStream source){
        src = source;
        if(DO_LOGGING){
            try{
                log_os = new DataOutputStream(new FileOutputStream("trace_log.bin"));
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    final int CALIBRATION_DISTANCE = 100;
    
    public void start(){
        if(running) return; //we only need one of these bad boys.
        (new Thread(this)).start();
    }
    
    public void stop(){
        running = false;
    }
    
    public void addHandListener(HandMovementListener hml){
        handlisteners.add(hml);
    }
    
    public void addDepthListener(DepthMapListener dml){
        depthlisteners.add(dml);
    }
    
    boolean _calibrating = false;
    MessageTransmitter _cmt = null;
    public void calibrate(MessageTransmitter mt){
        _calibrating = true;
        _cmt = mt;
        handlisteners.add(new HandMovementListener(){
            int state = 0;
            double tlx = 0, tly = 0;
            double blx = 1, bly = 1;
            double cal_z;
            
            public void newCalibration(double tx, double ty, double bx, double by, double z){
                _calibrating = false;
                _cmt = null;
            }

            public void handMoved(double x, double y, double z){
                try{
                    switch(state){
                    case 0:
                        _cmt.println("Bring your hand to a resting distance from the camera, you have 5 seconds.");
                        state = 999; //invalid state, it will be brought back when needed.
                        (new Thread(){
                            public void run(){
                                try{
                                    Thread.sleep(5000);
                                    state = 1;
                                }catch(Exception e){} //tee hee!
                            }
                        }).start();
                        break;
                    case 1:
                        cal_z = z;
                        _cmt.println("Calibration distance set. Now move your hand to the top left of the window and then back.");
                        state = 2;
                        break;
                    case 2:
                        if(z - cal_z >= CALIBRATION_DISTANCE){
                            tlx = x;
                            tly = y;
                            state = 3;
                        }
                        break;
                    case 3:
                        _cmt.println("Captured top left. Now move your hand forward to the resting distance from the camera.");
                        state = 4;
                        break;
                    case 4:
                        if(z - cal_z < CALIBRATION_DISTANCE / 2){
                            state = 5;
                        }
                        break;
                    case 5:
                        _cmt.println("OK, now lets calibrate the bottom right of the window. Move your hand there and pull back.");
                        state = 6;
                        break;
                    case 6:
                        if(z - cal_z >= CALIBRATION_DISTANCE){
                            blx = x;
                            bly = y;
                            state = 7;
                            _cmt.println("Calibration set! tl: [" + tlx + ", " + tly + "] br: [" + blx + ", " + bly + "]");
                            System.out.println("Calibration set! tl: [" + tlx + ", " + tly + "] br: [" + blx + ", " + bly + "]");
                            Iterator<HandMovementListener> iter = handlisteners.iterator();
                            while(iter.hasNext()){
                                iter.next().newCalibration(tlx, tly, blx, bly, cal_z);
                            }
                            //should remove this calibrator from the listener list here..
                        }
                        break;
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
    }

    public void run(){
        running = true;
        recording_started = false; //dont record until the first receipt of a hand signal
        long last_time = System.currentTimeMillis();
        try{
            DataInputStream bin = new DataInputStream(src);
            while(running){
                int hm_count = 0, dm_count = 0;
                int cmd = leShort(bin.readShort() & 0xffff);
                if(DO_LOGGING && cmd == 0) recording_started = true; //0 is the code for the hand moved message.
                if(recording_started){
                    log_os.writeLong(System.currentTimeMillis() - last_time);
                    last_time = System.currentTimeMillis();
                    log_os.writeShort(cmd);
                }
                switch(cmd){
                case 0: //hand moved message
                    hm_count++;
                    if(hm_count % FRAME_SKIP != 0) break;
                    double x = leInt(bin.readInt());
                    double y = leInt(bin.readInt());
                    double z = leInt(bin.readInt());
                    if(recording_started){
                        log_os.writeInt((int)x);
                        log_os.writeInt((int)y);
                        log_os.writeInt((int)z);
                    }
                    Iterator<HandMovementListener> iter = handlisteners.iterator();
                    while(iter.hasNext()){
                        iter.next().handMoved(x, y, z);
                    }
                    break;
                case 1: //new depth map message
                    dm_count++;
                    if(dm_count % FRAME_SKIP != 0) break;
                    int len = leInt(bin.readInt());
                    int w = leShort(bin.readShort());
                    if(recording_started){
                        log_os.writeInt(len);
                        log_os.writeShort(w);
                    }
                    if(depth_buffer == null || depth_buffer.length != len){
                        depth_buffer = new int[len];
                        depth_buffer_raw = new byte[len * 2];
                    }
                    int read = bin.read(depth_buffer_raw);
                    while(read < len * 2){
                        read += bin.read(depth_buffer_raw, read, len * 2 - read);
                    }
                    if(recording_started){
                        log_os.write(depth_buffer_raw);
                    }
                    ByteBuffer bufi = ByteBuffer.wrap(depth_buffer_raw);
                    bufi.order(ByteOrder.LITTLE_ENDIAN);
                    for(int i = 0; i < len; i++){
                        depth_buffer[i] = bufi.getShort() & 0xffff;
                    }
                    Iterator<DepthMapListener> iter2 = depthlisteners.iterator();
                    while(iter2.hasNext()){
                        iter2.next().newDepthMap(depth_buffer, w, len / w);
                    }
                    break;
                }
                frame_counter++;
                if(recording_started){
                    log_os.flush();
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    //possibly bugged with negatives
    private int leInt(int i){
        int r = ((i&0xff)<<24)+((i&0xff00)<<8)+((i&0xff0000)>>8)+((i>>>24)&0xff);
        return r;
    }
    
    private int leShort(int i){    
        return (((i>>8)&0xff)+((i << 8)&0xff00));
    }
}
