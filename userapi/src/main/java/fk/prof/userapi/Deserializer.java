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

    public static boolean verifyChecksum(byte[] buffer, int off, int len, long checksum) throws IOException {
        Adler32 adler32 = new Adler32();
        adler32.update(len >> 24);
        adler32.update(len >> 16);
        adler32.update(len >> 8);
        adler32.update(len);

        adler32.update(buffer, off, len);

        return adler32.getValue() != checksum;
    }

    private static int readFully(InputStream is, byte[] bytes, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int count = is.read(bytes, n, len - n);
            if (count < 0)
                throw new EOFException("EOF reached before reading " + n + " bytes");
            n += count;
        }
        return n;
    }
}
