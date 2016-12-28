package fk.prof.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream implementation which is backed by a {@link AsyncStorage}.
 * Every {@code partSize} chunk of the bytes will be written to the storage.
 * The target path of the chunk will be decided by the {@link FileNamingStrategy}.
 *
 * @see StorageBackedInputStream
 * @author gaurav.ashok
 */
public class StorageBackedOutputStream extends OutputStream {

    private AsyncStorage storage;
    private FileNamingStrategy fileNameStrategy;
    private int partSize;
    private int part;

    private byte[] readyToWriteBuf;
    private byte[] buf;
    private int pos;

    /**
     * @param storage AsyncStorage object.
     * @param fileNameStrategy decides the fileName from the part no.
     * @param partSize Minimum size in bytes.
     */
    public StorageBackedOutputStream(AsyncStorage storage, FileNamingStrategy fileNameStrategy, int partSize) {
        this.storage = storage;
        this.fileNameStrategy = fileNameStrategy;
        this.partSize = partSize;
        this.part = 1;

        this.buf = new byte[partSize];
    }

    @Override
    public void write(int b) throws IOException {
        if(pos == partSize) {
            storeAndSwapBuffer();
        }
        buf[pos++] = (byte)b;
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        int bytesToWrite = len;
        do {
            int remSpaceInBuf = Math.min(bytesToWrite, partSize - pos);
            System.arraycopy(b, off + (len - bytesToWrite), buf, pos, remSpaceInBuf);
            pos += remSpaceInBuf;
            bytesToWrite -= remSpaceInBuf;

            if(pos == partSize) {
                storeAndSwapBuffer();
            }
        } while (bytesToWrite > partSize);

        if(bytesToWrite > 0) {
            System.arraycopy(b, off + (len - bytesToWrite), buf, pos, bytesToWrite);
            pos += bytesToWrite;
        }
    }

    private void storeAndSwapBuffer() throws IOException {
        if(readyToWriteBuf != null) {
            storage.store(fileNameStrategy.getFileName(part), new ByteArrayInputStream(readyToWriteBuf));
            ++part;
        }
        readyToWriteBuf = buf;
        buf = new byte[partSize];

        pos = 0;
    }

    @Override
    public void flush() {
        // flush not supported
    }

    @Override
    public void close() throws IOException {
        if(readyToWriteBuf == null && pos == 0) {
            return;
        }

        if(pos > 0) {
            byte[] tempBuf = new byte[partSize + pos];
            System.arraycopy(readyToWriteBuf, 0, tempBuf, 0, partSize);
            System.arraycopy(buf, 0, tempBuf, partSize, pos);
            readyToWriteBuf = tempBuf;
        }

        storage.store(fileNameStrategy.getFileName(part), new ByteArrayInputStream(readyToWriteBuf));
    }
}
