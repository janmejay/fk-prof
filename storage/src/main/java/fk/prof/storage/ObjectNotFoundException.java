package fk.prof.storage;

/**
 * Exception to signify that the object was not found when trying to fetch from {@link AsyncStorage}.
 * @author gaurav.ashok
 */
public class ObjectNotFoundException extends StorageException {

    public ObjectNotFoundException(String message) {
        super(message);
    }

    public ObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
