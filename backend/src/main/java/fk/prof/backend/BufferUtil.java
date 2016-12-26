package fk.prof.backend;

import io.vertx.core.buffer.Buffer;

public class BufferUtil {
  public static Buffer resetRunningBuffer(Buffer buffer, int startPos) {
    return buffer.getBuffer(startPos, buffer.length());
  }
}
