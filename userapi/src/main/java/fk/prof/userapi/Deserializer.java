package fk.prof.userapi;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

/**
 * @author gaurav.ashok
 */
public abstract class Deserializer<T> {
    abstract public T deserialize(InputStream in);

    public static int readVariantInt32(InputStream in) throws IOException {
        int firstByte = in.read();
        if(firstByte == -1) {
            throw new EOFException("Expecting variantInt32");
        }
        return CodedInputStream.readRawVarint32(firstByte, in);
    }

    public static int readFixedInt32(InputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public static <T extends AbstractMessage> T readCheckedDelimited(Parser<T> parser, CheckedInputStream cin, String tag) throws IOException {
        Checksum checksum = cin.getChecksum();

        checksum.reset();
        T msg = parser.parseDelimitedFrom(cin);

        int chksmValue = (int)checksum.getValue();
        int expectedChksmValue = readFixedInt32(cin);

        assert chksmValue == expectedChksmValue : "Checksum did not match for " + tag;

        return msg;
    }
}
