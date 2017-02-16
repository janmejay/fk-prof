package fk.prof.aggregation.serialize;

import java.io.IOException;

/**
 * @author gaurav.ashok
 */
public class SerializationException extends IOException {
    public SerializationException(String message) {
        super(message);
    }
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
