package act.app;

import org.joda.time.DateTime;

import java.util.Map;

/**
 * A daemon encapsulate a long running logic that can be
 *
 * * {@link #start() started}
 * * {@link #stop() stopped}
 * * {@link #restart() restart}
 * * Check {@link #state() state}
 */
public interface Daemon extends Runnable {

    enum State {
        STOPPING, STOPPED, STARTING, STARTED, ERROR, FATAL
    }

    /**
     * Start this daemon
     */
    void start();

    /**
     * Stop this daemon
     */
    void stop();

    /**
     * {@link #stop() stop} this daemon first and then {@link #start() start}
     */
    void restart();

    /**
     * Returns the {@link State state} of this daemon
     * @return the state
     */
    State state();

    /**
     * Returns the timestamp when last state transfer happening
     * @return the timestamp
     */
    DateTime timestamp();

    /**
     * Returns ID of the daemon logic
     * @return ID
     */
    String id();

    /**
     * Returns last error
     * @return the last error encountered
     */
    Exception lastError();

    /**
     * Returns the timestamp when last error happening
     * @return the timestamp
     */
    DateTime errorTimestamp();

    /**
     * Set an attribute to the daemon
     * @param key the attribute key
     * @param value attribute value
     */
    void setAttribute(String key, Object value);

    /**
     * Remove an attribute from a daemon
     * @param key the attribute key
     */
    void removeAttribute(String key);

    /**
     * Return the attribute set to the daemon
     * @param key the attribute key
     * @param <T> the gneric type of the attribute value
     * @return the attribute value
     */
    <T> T getAttribute(String key);

    /**
     * Returns all attributes set on this daemon
     * @return the attributes set on this daemon
     */
    Map<String, Object> getAttributes();
}
