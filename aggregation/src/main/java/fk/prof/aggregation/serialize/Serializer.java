package fk.prof.aggregation.serialize;

import com.google.protobuf.Message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Adler32;

/**
 * Base class for serializers.
 * Provided helper methods to serialize Integer and protobuf message.
 * Protobuf message will be serialized in the format [size][message][checksum].
 * @author gaurav.ashok
 */
public abstract class Serializer<T> {

    public abstract void serialize(T object, OutputStream os) throws IOException;

    public static void writeInt32(int value, OutputStream os) throws IOException {
        byte[] bytes = {(byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value};
        os.write(bytes);
    }

    /**
     * Write protobuf message with its size followed by a checksum.
     * format: [size: 4 bytes][message: size bytes][checksum: 4 bytes]
     * @param message
     * @param os
     * @throws IOException
     */
    public static void writeFenced(Message message, OutputStream os) throws IOException {
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
