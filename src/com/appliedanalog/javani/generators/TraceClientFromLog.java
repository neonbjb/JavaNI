package com.appliedanalog.javani.generators;

import com.appliedanalog.javani.listeners.DepthMapListener;
import com.appliedanalog.javani.listeners.HandMovementListener;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

/**
 * Redirects IO to a TraceClient variant from a log file (generated from within
 * TraceClient)
 * @author James
 */
public class TraceClientFromLog extends TraceClient{
    DataInputStream log_is;

    static final InputStream fake_is = null;
    public TraceClientFromLog(String lg){
        super(fake_is);
        try{
            log_is = new DataInputStream(new FileInputStream(lg));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void start(){
        if(running) return; //we only need one of these bad boys.
        (new Thread(this)).start();
    }

    @Override
    public void run(){
        running = true;
        try{
            Thread.sleep(500); //dont ask me why.............. :(
            int counter = 0;
            while(running){
                long wait_time = log_is.readLong();
                if(counter != 0) Thread.sleep(wait_time);
                int cmd = log_is.readShort();
                switch(cmd){
                case 0: //hand moved message
                    double x = log_is.readInt();
                    double y = log_is.readInt();
                    double z = log_is.readInt();
                    if(counter == 0) break; //throw this one out because we're missing a depth map..
                    Iterator<HandMovementListener> iter = handlisteners.iterator();
                    while(iter.hasNext()){
                        iter.next().handMoved(x, y, z);
                    }
                    break;
                case 1: //new depth map message
                    int len = log_is.readInt();
                    int w = log_is.readShort();
                    if(depth_buffer == null || depth_buffer.length != len){
                        depth_buffer = new int[len];
                        depth_buffer_raw = new byte[len * 2];
                    }
                    int read = log_is.read(depth_buffer_raw);
                    while(read < len * 2){
                        read += log_is.read(depth_buffer_raw, read, len * 2 - read);
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
                counter++;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
