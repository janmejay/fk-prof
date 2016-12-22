package fk.prof.common.store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

/**
 * @author gaurav.ashok
 */
public class StorageBackedInputStream extends InputStream {

    private static final Logger logger = LogManager.getLogger(StorageBackedInputStream.class);

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
            if(bytesToRead > 0) {
                fetchAndSwapBuffer();
            }
        }

        return len - bytesToRead;
    }

    private void fetchAndSwapBuffer() throws IOException {
        String nextFileName = fileNameStrategy.getFileName(part + 1);
        try {
            if(buf != null) {
                buf.close();
            }
            buf = storage.fetch(nextFileName).get();
            part++;
        } catch (FileNotFoundException | ExecutionException | InterruptedException e) {
            logger.warn("File: {} could not be fetched", nextFileName, e);
            buf = null;

            // mark eof
            eof = true;
        }
    }

    @Override
    public int available() throws IOException {
        if(buf == null) {
            return -1;
        }
        return buf.available();
    }

    @Override
    public void close() throws IOException {
        if(buf != null) {
            buf.close();
        }
    }
}
