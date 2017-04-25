package fk.prof.storage.buffer;

import fk.prof.storage.Callback;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * InputStream wrapper for ByteBuffer.
 * @author gaurav.ashok
 */
public class ByteBufferInputStream extends InputStream {

    private Callback closeHandler;
    private ByteBuffer buf;
    private boolean closed = false;

    public ByteBufferInputStream(ByteBuffer buf) {
        this.buf = buf;
        this.closeHandler = null;
    }

    public ByteBufferInputStream(ByteBuffer buf, Callback closeHandler) {
        this.buf = buf;
        this.closeHandler = closeHandler;
    }

    @Override
    public int read() throws IOException {
        if(buf.remaining() > 0) {
            return buf.get();
        }
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if(buf.remaining() > 0) {
            int bytesToRead = Math.min(buf.remaining(), len);
            buf.get(b, off, bytesToRead);
            return bytesToRead;
        }
        return -1;
    }

    @Override
    public int available() throws IOException {
        int remaining = buf.remaining();
        // if there are no bytes remaining, its eof, so return -1.
        return remaining > 0 ? remaining : -1;
    }

    @Override
    public void close() throws IOException {
        if(!closed) {
            closed = true;
            if(closeHandler != null) {
                closeHandler.call();
            }
        }
    }
}
