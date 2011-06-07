package traceclient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 *
 * @author James
 */
public class Tracer extends javax.swing.JFrame implements Runnable{
    double hx, hy, hz;
    ArrayList<HandMovementListener> handlisteners = new ArrayList<HandMovementListener>();
    MouseFollower follow = new MouseFollower();

    /** Creates new form Tracer */
    public Tracer() {
        initComponents();
        handlisteners.add(tracerCanvas1);
        handlisteners.add(follow);
        (new Thread(this)).start();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tracerCanvas1 = new traceclient.TracerCanvas();
        slZ = new javax.swing.JSlider();
        bCalibrate = new javax.swing.JButton();
        lText = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        slZ.setMaximum(1000);
        slZ.setOrientation(javax.swing.JSlider.VERTICAL);

        bCalibrate.setText("Calibrate");
        bCalibrate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bCalibrateActionPerformed(evt);
            }
        });

        lText.setText(" ");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(bCalibrate, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(tracerCanvas1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lText, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 635, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(slZ, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lText)
                        .addGap(7, 7, 7)
                        .addComponent(tracerCanvas1, javax.swing.GroupLayout.PREFERRED_SIZE, 442, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(slZ, javax.swing.GroupLayout.DEFAULT_SIZE, 452, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bCalibrate, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void bCalibrateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bCalibrateActionPerformed
        handlisteners.add(new HandMovementListener(){
            int state = 0;
            double tlx = 0, tly = 0;
            double blx = 1, bly = 1;
            double cal_z;

            public void handMoved(double x, double y, double z){
                try{
                    switch(state){
                    case 0:
                        lText.setText("Bring your hand to a resting distance from the camera, you have 5 seconds.");
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
                        lText.setText("Calibration distance set. Now move your hand to the top left of the window and then back.");
                        state = 2;
                        break;
                    case 2:
                        if(z - cal_z >= 200){
                            tlx = x;
                            tly = y;
                            state = 3;
                        }
                        break;
                    case 3:
                        lText.setText("Captured top left. Now move your hand forward to the resting distance from the camera.");
                        state = 4;
                        break;
                    case 4:
                        if(z - cal_z < 50){
                            state = 5;
                        }
                        break;
                    case 5:
                        lText.setText("OK, now lets calibrate the bottom right of the window. Move your hand there and pull back.");
                        state = 6;
                        break;
                    case 6:
                        if(z - cal_z >= 200){
                            blx = x;
                            bly = y;
                            state = 7;
                            lText.setText("Calibration set! tl: [" + tlx + ", " + tly + "] br: [" + blx + ", " + bly + "]");
                            System.out.println("Calibration set! tl: [" + tlx + ", " + tly + "] br: [" + blx + ", " + bly + "]");
                            tracerCanvas1.setTracerBounds(tlx, tly, blx, bly);
                            follow.calibrate(tlx, tly, blx, bly, cal_z);
                            //should remove this calibrator from the listener list here..
                        }
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
    }//GEN-LAST:event_bCalibrateActionPerformed

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Tracer().setVisible(true);
            }
        });
    }

    public void run(){
        try{
            Socket sock = new Socket("localhost", 18353);
            BufferedReader bin = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String str;
            while((str = bin.readLine()) != null){
                //System.out.println(str);
                StringTokenizer tok = new StringTokenizer(str, ",");
                double x = Double.parseDouble(tok.nextToken());
                double y = Double.parseDouble(tok.nextToken());
                double z = Double.parseDouble(tok.nextToken());
                Iterator<HandMovementListener> iter = handlisteners.iterator();
                while(iter.hasNext()){
                    iter.next().handMoved(x, y, z);
                }
                if(z > slZ.getMaximum()){
                    slZ.setMaximum((int)z+1);
                }
                slZ.setValue((int)z);
                hx = x;
                hy = y;
                hz = z;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bCalibrate;
    private javax.swing.JLabel lText;
    private javax.swing.JSlider slZ;
    private traceclient.TracerCanvas tracerCanvas1;
    // End of variables declaration//GEN-END:variables

}