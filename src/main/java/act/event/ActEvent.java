package act.event;

import org.osgl.$;

import java.util.EventObject;

/**
 * An {@code ActEvent} is a generic version of {@link EventObject}
 * @param <T> the generic type (the sub class type) of the event source
 */
public class ActEvent<T> extends EventObject {

    private final long ts;

    protected static final Object SOURCE_PLACEHODER = new Object();

    /**
     * This constructor allows sub class to construct a Self source event, e.g.
     * the source can be the event instance itself.
     *
     * **Note** if sub class needs to use this constructor the {@link #getSource()} ()}
     * method must be overwritten
     */
    protected ActEvent() {
        super(SOURCE_PLACEHODER);
        ts = $.ms();
    }

    /**
     * Construct an `ActEvent` with source instance
     * @param source The object on which the Event initially occurred.
     *               or any payload the developer want to attach to the event
     */
    public ActEvent(T source) {
        super(source);
        ts = $.ms();
    }

    /**
     * Unlike the {@link Object#getClass()} method, which always return
     * the java class of the current instance. This {@code eventType()}
     * method allow a certain implementation of the class terminate the
     * return value of the method. For example, suppose you have a event
     * class {@code MyEvent} and you might have some anonymous class
     * of {@code MyEvent}. If you implement the {@code eventType()} of
     * {@code MyEvent} class as follows:
     * <pre>
     *     public class&lt;MyEvent&gt; eventType() {
     *         return MyEvent.class;
     *     }
     * </pre>
     * Then all the anonymous sub class will return the {@code MyEvent.class}
     * instead of their own class.
     * <p>This allows the ActFramework properly handle the event class registration</p>
     * @return the type of the event
     */
    public Class<? extends ActEvent<T>> eventType() {
        return $.cast(getClass());
    }

    public final T source() {
        return $.cast(getSource());
    }

    /**
     * Return the timestamp of the event
     * @return the timestamp
     * @see System#currentTimeMillis()
     */
    public final long timestamp() {
        return ts;
    }
}
