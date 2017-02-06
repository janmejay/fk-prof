package fk.prof.aggregation.serialize;

import com.google.protobuf.Message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Adler32;

/**
 * Base class for serializers.
 * Provides helper methods to serialize uint32 and protobuf message.
 * @author gaurav.ashok
 */
public abstract class Serializer<T> {

    public abstract void serialize(T object, OutputStream os) throws IOException;

    public static void writeFixedWidthInt32(int value, OutputStream os) throws IOException {
        byte[] bytes = {(byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value};
        os.write(bytes);
    }
}
