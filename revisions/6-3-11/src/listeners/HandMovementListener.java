package listeners;

public interface HandMovementListener {
    public void handMoved(double x, double y, double z);
    public void newCalibration(double tx, double ty, double bx, double by, double z);
}
