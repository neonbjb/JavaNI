/*
 * MeasurementViewer.java
 *
 * Created on Jun 13, 2011, 1:40:53 PM
 */

package com.appliedanalog.javani.dialogs;

import com.appliedanalog.javani.graphs.ClippingBitmapView;
import com.appliedanalog.javani.processors.HandMeasurement;
import java.awt.Color;
import java.awt.Point;

/**
 *
 * @author jbetker
 */
public class MeasurementViewer extends javax.swing.JDialog {
    HandMeasurement measurement = null;
    int[] depth_map;
    int dm_width, dm_height;

    /** Creates new form MeasurementViewer */
    public MeasurementViewer(java.awt.Frame parent, int[] depthmap, int dmw, int dmh) {
        super(parent, false);
        initComponents();
        depth_map = depthmap;
        dm_width = dmw;
        dm_height = dmh;

        view.newDepthMap(depthmap, dmw, dmh);
        view.repaint();
    }

    public void attachMeasurement(HandMeasurement m){
        measurement = m;
        view.enableDepthClipping(m.getMeasurementDepth(), 50);
        view.centerClippingWindowOn(m.getMeasurementCenterOfHand().x, m.getMeasurementCenterOfHand().y);
        //draw measurement information.
        view.addPoint(m.getMeasurementCenterOfHand(), Color.CYAN, true);
        view.addPoint(m.getWristCenter(), Color.YELLOW, true);
        Point wristleft = (Point)m.getWristCenter().clone(); Point wristright = (Point)m.getWristCenter().clone();
        wristleft.move(wristleft.x - m.getWristWidth() / 2, wristleft.y);
        wristright.move(wristright.x + m.getWristWidth() / 2, wristright.y);
        view.addLine(wristleft, wristright);
        view.addLine(m.getMeasurementCenterOfHand(), m.getWristCenter(), Color.YELLOW);
        view.addPoint(m.getLeftKnuckleAttachment(), Color.YELLOW, true);
        view.addPoint(m.getRightKnuckleAttachment(), Color.YELLOW, true);
        view.addLine(m.getLeftKnuckleAttachment(), m.getRightKnuckleAttachment(), Color.YELLOW);
        view.addPoint(m.getThumb(), Color.WHITE, true);
        view.addPoint(m.getIndexFinger(), Color.WHITE, true);
        view.addPoint(m.getMiddleFinger(), Color.WHITE, true);
        view.addPoint(m.getRingFinger(), Color.WHITE, true);
        view.addPoint(m.getPinky(), Color.WHITE, true);
        view.addPoint(m.getThumbAttachment(), Color.WHITE, true);
        view.addPoint(m.getIndexAttachment(), Color.WHITE, true);
        view.addPoint(m.getMiddleAttachment(), Color.WHITE, true);
        view.addPoint(m.getRingAttachment(), Color.WHITE, true);
        view.addPoint(m.getPinkyAttachment(), Color.WHITE, true);
        view.addLine(m.getThumbAttachment(), m.getThumb(), Color.WHITE);
        view.addLine(m.getIndexAttachment(), m.getIndexFinger(), Color.WHITE);
        view.addLine(m.getMiddleAttachment(), m.getMiddleFinger(), Color.WHITE);
        view.addLine(m.getRingAttachment(), m.getRingFinger(), Color.WHITE);
        view.addLine(m.getPinkyAttachment(), m.getPinky(), Color.WHITE);
        view.addLine(m.getMeasurementCenterOfHand(), m.getThumbAttachment(), Color.YELLOW);
        view.addLine(m.getMeasurementCenterOfHand(), m.getIndexAttachment(), Color.YELLOW);
        view.addLine(m.getMeasurementCenterOfHand(), m.getMiddleAttachment(), Color.YELLOW);
        view.addLine(m.getMeasurementCenterOfHand(), m.getRingAttachment(), Color.YELLOW);
        view.addLine(m.getMeasurementCenterOfHand(), m.getPinkyAttachment(), Color.YELLOW);
        view.repaint();
    }

    public ClippingBitmapView getView(){
        return view;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        view = new com.appliedanalog.javani.graphs.ClippingBitmapView();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(view, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(view, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.appliedanalog.javani.graphs.ClippingBitmapView view;
    // End of variables declaration//GEN-END:variables

}
