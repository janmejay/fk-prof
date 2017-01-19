package fk.prof.storage;

/**
 * @author gaurav.ashok
 */
public interface FileNamingStrategy {
    String getFileName(int part);
}
