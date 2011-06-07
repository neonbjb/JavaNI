package listeners;

/**
 * This is a backfeeding interface whereby a data class can utilize an abstract UI
 * class to push out standard-out-like messages.
 * @author jbetker
 */
public interface MessageTransmitter {
    public void println(String msg);
}
