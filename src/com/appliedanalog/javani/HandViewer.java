/*
 * HandTracer.java
 *
 * Created on Jun 2, 2011, 9:55:31 AM
 */
package com.appliedanalog.javani;

import com.appliedanalog.javani.generators.TraceClient;
import com.appliedanalog.javani.listeners.HandMovementListener;
import com.appliedanalog.javani.dialogs.HandViewerController;

/**
 *
 * @author jbetker
 */
public class HandViewer extends javax.swing.JFrame implements HandMovementListener {
    HandViewerController controller;
    HandCalculator calc;
    MouseFollower mouse;
    TraceClient client;
    
    /** Creates new form HandTracer */
    public HandViewer(TraceClient cli) {
        initComponents();
        client = cli;
        calc = new HandCalculator();
        mouse = new MouseFollower();
        
        controller = new HandViewerController(this, viewGraph1);
        controller.setLocation(getLocation().x, getLocation().y + getHeight());
        controller.setVisible(true);

        //the order is important here
        cli.addHandListener(controller);
        cli.addHandListener(calc);
        cli.addHandListener(this);
        cli.addHandListener(mouse);

        cli.addDepthListener(calc);
        cli.addDepthListener(viewGraph1);
    }

    //we dont actually do anything with the data, this is used as a callback to let us know
    //that my subcomponents have been updated, specifically so I can port stuff from calc to viewGraph1
    int cur_spline = -1;

    final int DOWN_CLICK_SENSITIVITY = 20;
    final int UP_CLICK_SENSITIVITY = 5;
    int depth_diff_history[] = new int[5];
    int ddh_rptr = 0; boolean clicked = false;
    public void handMoved(double ix, double iy, double iz) {
        if(cur_spline != -1){
            viewGraph1.removeSpline(cur_spline);
        }
        cur_spline = viewGraph1.addSpline();
        for(int x = 0; x < calc.figurePointCount(); x++){
            viewGraph1.addAbsolutePoint(cur_spline, calc.getFigureX(x), calc.getFigureY(x));
        }
        for(int f = 0; f < calc.getFingersDetected(); f++){
            viewGraph1.setXptAbsolute(f, calc.getFingerX(f), calc.getFingerY(f), calc.getHandX(), calc.getHandY(), calc.getOrientation());
        }
        viewGraph1.setNumXpts(calc.getFingersDetected());

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
        viewGraph1.repaint();
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
        controller.setLocation(getLocation().x, getLocation().y + getHeight());
    }//GEN-LAST:event_formComponentMoved

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.appliedanalog.javani.graphs.ClippingBitmapView viewGraph1;
    // End of variables declaration//GEN-END:variables
}
