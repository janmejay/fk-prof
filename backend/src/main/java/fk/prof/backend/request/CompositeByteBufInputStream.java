package fk.prof.backend.request;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class CompositeByteBufInputStream extends InputStream {
  private final CompositeByteBuf buffer;

  public CompositeByteBufInputStream() {
    buffer = Unpooled.compositeBuffer();
  }

  public CompositeByteBufInputStream(int maxNumComponents) {
    buffer = Unpooled.compositeBuffer(maxNumComponents);
  }

  public void accept(ByteBuf newSource) {
    buffer.addComponent(true, newSource);
  }

  @Override
  public int read() throws IOException {
    if (!buffer.isReadable()) {
      return -1;
    }
    int readByte = buffer.readByte() & 0xff;
    return readByte;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int available = available();
    if (available == 0) {
      return -1;
    }

    len = Math.min(available, len);
    buffer.readBytes(b, off, len);
    return len;
  }

  @Override
  public long skip(long n) throws IOException {
    if (n > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Java byte arrays cannot be longer than Integer.MAX_VALUE, cannot continue with skip operation");
    } else {
      return skipBytes((int) n);
    }
  }

  private int skipBytes(int n) throws IOException {
    int nBytes = Math.min(available(), n);
    buffer.skipBytes(nBytes);
    return nBytes;
  }

  @Override
  public int available() throws IOException {
    return buffer.readableBytes();
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public synchronized void mark(int readlimit) {
    buffer.markReaderIndex();
    buffer.discardReadBytes();
  }

  @Override
  public void reset() throws IOException {
    buffer.resetReaderIndex();
  }

  @Override
  public void close() throws IOException {
    buffer.release();
  }

  public void updateChecksumSinceMarked(Adler32 checksum) {
    int currentReaderIndex = buffer.readerIndex();

    // reset the readerIndex buffer, so we can get marked readerIndex
    buffer.resetReaderIndex();
    int bytesRead = currentReaderIndex - buffer.readerIndex();
    if(bytesRead > 0) {
      ByteBuffer[] nioBuffers = buffer.nioBuffers(buffer.readerIndex(), bytesRead);
      for(int i = 0; i < nioBuffers.length; ++i) {
        checksum.update(nioBuffers[i]);
      }
    }

    // fix the reader index
    buffer.readerIndex(currentReaderIndex);
  }
}
