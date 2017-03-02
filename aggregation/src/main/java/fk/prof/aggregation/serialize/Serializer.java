package fk.prof.aggregation.serialize;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

/**
 * Utility methods for serializers.
 * @author gaurav.ashok
 */
public interface Serializer {

    void serialize(OutputStream out) throws IOException;

    static void writeFixedWidthInt32(int value, OutputStream os) throws IOException {
        byte[] bytes = {(byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value};
        os.write(bytes);
    }

    static void writeVariantInt32(int value, OutputStream os) throws IOException {
        byte[] bytes = new byte[8];

        CodedOutputStream cout = CodedOutputStream.newInstance(bytes);
        cout.writeInt32NoTag(value);
        cout.flush();

        os.write(bytes, 0, cout.getTotalBytesWritten());
    }

    static void writeCheckedDelimited(AbstractMessage msg, CheckedOutputStream out) throws IOException {
        Checksum checksum = out.getChecksum();
        checksum.reset();

        msg.writeDelimitedTo(out);
        writeFixedWidthInt32((int)checksum.getValue(), out);
    }
}
