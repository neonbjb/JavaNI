/*
 * HandTracer.java
 *
 * Created on Jun 2, 2011, 9:55:31 AM
 */
package com.appliedanalog.javani;

import com.appliedanalog.javani.processors.HandCalculator;
import com.appliedanalog.javani.generators.TraceClient;
import com.appliedanalog.javani.listeners.HandMovementListener;
import java.awt.Color;
import java.awt.Point;

/**
 *
 * @author jbetker
 */
public class HandViewer extends javax.swing.JFrame implements HandMovementListener {
    HandCalculator calc;
    MouseFollower mouse;
    TraceClient client;
    int fpindex; //where to start adding figure points to the viewer
    int fingersindex; //where to start adding finger points to the viewer
    
    /** Creates new form HandTracer */
    public HandViewer(TraceClient cli) {
        initComponents();
        client = cli;
        calc = new HandCalculator();
        mouse = new MouseFollower();

        //the order is important here
        cli.addHandListener(calc);
        cli.addHandListener(this);
        cli.addHandListener(mouse);

        cli.addDepthListener(calc);
        cli.addDepthListener(viewGraph1);

        fpindex = viewGraph1.initPoints(calc.getMaxFigurePoints());
        fingersindex = viewGraph1.initPoints(5);
    }

    //we dont actually do anything with the data, this is used as a callback to let us know
    //that my subcomponents have been updated, specifically so I can port stuff from calc to viewGraph1
    int cur_spline = -1;

    final int DOWN_CLICK_SENSITIVITY = 20;
    final int UP_CLICK_SENSITIVITY = 5;
    int depth_diff_history[] = new int[5];
    int ddh_rptr = 0; boolean clicked = false;
    public void handMoved(double ix, double iy, double iz) {
        viewGraph1.clearPoints(fpindex, fpindex + calc.getMaxFigurePoints()-1);
        viewGraph1.clearPoints(fingersindex, fingersindex + 4);
        for(int x = 0; x < calc.figurePointCount(); x++){
            viewGraph1.setPoint(fpindex + x, calc.getFigure(x), Color.YELLOW, false);
        }
        for(int f = 0; f < calc.getFingersDetected(); f++){
            viewGraph1.setPoint(fingersindex + f, new Point(calc.getFingerX(f), calc.getFingerY(f)), Color.ORANGE, true);
        }

        //get average depth difference
        double avg_distance = depth_diff_history[0];
        for(int x = 1; x < depth_diff_history.length; x++) avg_distance += depth_diff_history[x];
        avg_distance /= depth_diff_history.length;
            for(int x = 1; x < depth_diff_history.length; x++) avg_distance += depth_diff_history[x];
            avg_distance /= depth_diff_history.length;
        if(Math.abs(avg_distance - calc.getMaxFingerDepthDifference()) > 100){
            //System.out.println("Throwing this one out: avg=" + avg_distance + " ano=" + calc.getDepthDifference());
        }else{
            depth_diff_history[ddh_rptr] = (int)calc.getMaxFingerDepthDifference();
            ddh_rptr = (ddh_rptr + 1) % depth_diff_history.length;
            avg_distance = depth_diff_history[0];
        }

        if(!clicked && avg_distance > DOWN_CLICK_SENSITIVITY){
            System.out.println("Mouse down");
            mouse.mousedown();
            (new Thread(){
                public void run(){
                    try{ Thread.sleep(25); }catch(Exception e){}
                    mouse.mouseup();
                }
            }).start();
            clicked = true;
        }else if(clicked && avg_distance < UP_CLICK_SENSITIVITY){
            System.out.println("Mouse up");
            //mouse.mouseup();
            clicked = false;
        }
        viewGraph1.syncpaint();
        viewGraph1.repaint();
        
        //recenter for the next frame.. this frame's already dead jim :)
        viewGraph1.enableDepthClipping((int)iz, 50);
        viewGraph1.centerClippingWindowOn((int)ix, (int)iy);
    }

    public void newCalibration(double tx, double ty, double bx, double by, double z) { }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        viewGraph1 = new com.appliedanalog.javani.graphs.ClippingBitmapView();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentMoved(java.awt.event.ComponentEvent evt) {
                formComponentMoved(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(viewGraph1, javax.swing.GroupLayout.PREFERRED_SIZE, 379, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(viewGraph1, javax.swing.GroupLayout.PREFERRED_SIZE, 278, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formComponentMoved(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentMoved
    }//GEN-LAST:event_formComponentMoved

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.appliedanalog.javani.graphs.ClippingBitmapView viewGraph1;
    // End of variables declaration//GEN-END:variables
}
