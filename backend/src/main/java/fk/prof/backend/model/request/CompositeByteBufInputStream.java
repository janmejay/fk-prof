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

  // TODO: The mark and reset semantics for this stream are different from convention.
  // Appropriately, markSupported returns false and there are separate methods for mark and reset with overloaded functionality
  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * Besides marking the current position, this method discards all the bytes read until this point.
   * Calling reset does not restore the discarded bytes
   * Call discardReadBytesAndMark() when you are sure you have read all previous bytes correctly and can be freed up
   */
  public void discardReadBytesAndMark() {
    //CompositeByteBuf::discardReadBytes method changes readerIndex position so calling this before storing readerIndex position
    buffer.discardReadBytes();
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
  // All bytes read since discardReadBytesAndMark() was called on inputstream
  // or from the start if discardReadBytesAndMark() was never called prior to calling this method,
  // till the current read position are copied in a new byte array
  public byte[] getBytesReadSinceDiscardAndMark() {
    int readBytes = buffer.readerIndex() - readerIndexAtMark;
    byte[] bytesReadSinceMark = new byte[readBytes];
    buffer.getBytes(readerIndexAtMark, bytesReadSinceMark, 0, readBytes);
    return bytesReadSinceMark;
  }

}
