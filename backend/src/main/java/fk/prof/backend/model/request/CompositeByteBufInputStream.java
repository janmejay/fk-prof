package fk.prof.backend.model.request;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.InputStream;

public class CompositeByteBufInputStream extends InputStream {
  private final CompositeByteBuf buffer;
  private int readerIndexAtMark = 0;


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
      return skipBytes(Integer.MAX_VALUE);
    } else {
      return skipBytes((int) n);
    }
  }

  public int skipBytes(int n) throws IOException {
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
  public void mark(int readlimit) {
    buffer.markReaderIndex();
    readerIndexAtMark = buffer.readerIndex();
  }

  @Override
  public void reset() throws IOException {
    buffer.resetReaderIndex();
  }

  @Override
  public void close() throws IOException {
    buffer.release();
  }

  // NOTE: Use this method with caution.
  // This copies all bytes read since mark() was called on inputstream
  // or start if mark() was never called prior to calling this method till the current read position in a new byte array
  public byte[] getBytesReadSinceMark() {
    int readBytes = buffer.readerIndex() - readerIndexAtMark;
    byte[] bytesReadSinceMark = new byte[readBytes];
    buffer.getBytes(readerIndexAtMark, bytesReadSinceMark, 0, readBytes);
    return bytesReadSinceMark;
  }

}
