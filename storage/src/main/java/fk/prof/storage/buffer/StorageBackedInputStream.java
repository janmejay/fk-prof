package fk.prof.storage.buffer;

import fk.prof.storage.AsyncStorage;
import fk.prof.storage.FileNamingStrategy;
import fk.prof.storage.ObjectNotFoundException;
import fk.prof.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream implementation which is backed by a {@link AsyncStorage} to fetch content
 * from a location. This impl has a notion of file parts i.e. the complete file is broken
 * into multiple parts. When it finishes reading from part 'k' it requests for the next part 'k+1'.
 * Any exception thrown by the {@link AsyncStorage} is thrown back to the user as IOException.
 *
 * @see StorageBackedOutputStream
 * @author gaurav.ashok
 */
public class StorageBackedInputStream extends InputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageBackedInputStream.class);

    private AsyncStorage storage;
    private FileNamingStrategy fileNameStrategy;

    private InputStream buf;
    private int part;

    private boolean eof;

    public StorageBackedInputStream(AsyncStorage storage, FileNamingStrategy fileNameStrategy) {
        this.storage = storage;
        this.fileNameStrategy = fileNameStrategy;

        this.part = 0;
        this.eof = false;
    }

    @Override
    public int read() throws IOException {
        if(!eof && buf == null) {
            fetchAndSwapBuffer();
        }

        if(eof) {
            return -1;
        }

        return buf.read();
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int c = read();
        if (c == -1) {
            return -1;
        }
        b[off] = (byte)c;

        int bytesToRead = len - 1;
        while(bytesToRead > 0 && !eof) {
            int bytesRead = buf.read(b, off + (len - bytesToRead), bytesToRead);

            if(bytesRead == -1) {
                fetchAndSwapBuffer();
                continue;
            }

            bytesToRead -= bytesRead;
        }

        return len - bytesToRead;
    }

    private void fetchAndSwapBuffer() throws IOException {
        String nextFileName = fileNameStrategy.getFileName(part + 1);
        try {
            if(buf != null) {
                buf.close();
            }
            buf = storage.fetch(nextFileName);
            part++;
        }
        catch (ObjectNotFoundException e) {
            // mark eof
            eof = true;
            final String msg = "File: " + nextFileName + " could not be found";
            LOGGER.error(msg, e);
            throw new FileNotFoundException(msg + ". Cause: " + e.getMessage());
        }
        catch (StorageException e) {
            LOGGER.error("Unexpected error while fetching file: {}", nextFileName, e);
            throw new IOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        if(buf == null) {
            return 0;
        }
        if(eof) {
            return -1;
        }

        /* Because the file is cut into multiple parts, it is not possible to know whether we have reached eof or not
         * unless we fetch next file. We will not fetch next file here because available is non blocking.
         */
        int available = buf.available();
        return available == -1 ? 0 : available;
    }

    @Override
    public void close() throws IOException {
        if(buf != null) {
            buf.close();
        }
    }
}