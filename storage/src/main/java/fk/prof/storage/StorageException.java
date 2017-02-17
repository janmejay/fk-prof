package fk.prof.storage;

/**
 * Base exception to represent any exception occurred while storing/fetching using {@link AsyncStorage}.
 * @author gaurav.ashok
 */
public class StorageException extends RuntimeException {

    private boolean retriable;

    public StorageException(String message) {
        this(message, false);
    }

    public StorageException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public StorageException(String message, boolean isRetryable) {
        super(message);
        retriable = false;
    }

    public StorageException(String message, Throwable cause, boolean isRetryable) {
        super(message, cause);
        retriable = false;
    }

    /**
     * Hint to whether the same request can be made again.
     */
    public boolean isRetriable() {
        return retriable;
    }
}
