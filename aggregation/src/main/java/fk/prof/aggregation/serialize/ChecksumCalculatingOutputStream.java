package fk.prof.aggregation.serialize;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Checksum;

/**
 * @author gaurav.ashok
 */
public class ChecksumCalculatingOutputStream extends FilterOutputStream {

    private Checksum checksum;

    public ChecksumCalculatingOutputStream(Checksum checksum, OutputStream out) {
        super(out);
        this.checksum = checksum;
    }

    @Override
    public void write(int b) throws IOException {
        super.write(b);
        checksum.update(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        checksum.update(b, off, len);
    }
}
