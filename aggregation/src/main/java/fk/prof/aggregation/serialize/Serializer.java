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

    public static void writeInt32(int value, OutputStream os) throws IOException {
        byte[] bytes = {(byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value};
        os.write(bytes);
    }

    /**
     * Writes a protobuf message to the provided {@link OutputStream}.
     * Message will be serialized in the format [size: 4bytes][message: size bytes][checksum: 4bytes].
     * @param message
     * @param os
     * @throws IOException
     */
    public static void writeMessage(Message message, OutputStream os) throws IOException {
        byte[] bytes = message.toByteArray();
        int size = bytes.length;

        Adler32 adler32 = new Adler32();
        adler32.update(size >> 24);
        adler32.update(size >> 16);
        adler32.update(size >> 8);
        adler32.update(size);

        adler32.update(bytes);

        writeInt32(size, os);
        os.write(bytes);
        writeInt32((int)adler32.getValue(), os);
    }
}
