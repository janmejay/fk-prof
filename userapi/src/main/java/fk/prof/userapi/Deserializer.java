package fk.prof.userapi;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Adler32;

/**
 * @author gaurav.ashok
 */
public abstract class Deserializer<T> {
    abstract public T deserialize(InputStream in);

    public static int readInt32(InputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    /**
     * Read a series of bytes that was serialized in the format of [size][message][checksum].
     * @param in
     * @return
     * @throws IOException
     */
    public static byte[] readFenced(InputStream in) throws IOException {
        // read size
        int size = readInt32(in);
        // read size bytes
        byte[] bytes = new byte[size];
        readFully(in, bytes);

        int checksum = readInt32(in);

        Adler32 adler32 = new Adler32();
        adler32.update(size >> 24);
        adler32.update(size >> 16);
        adler32.update(size >> 8);
        adler32.update(size);

        adler32.update(bytes);

        if(((int)adler32.getValue()) != checksum) {
            throw new IOException("Checksum failed");
        }

        return bytes;
    }

    private static int readFully(InputStream is, byte[] bytes) throws IOException {
        int len = bytes.length;
        int n = 0;
        while (n < len) {
            int count = is.read(bytes, n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
        return n;
    }
}
