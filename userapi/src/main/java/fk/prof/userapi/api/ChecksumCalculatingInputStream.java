package fk.prof.userapi.api;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Checksum;

/**
 * @author gaurav.ashok
 */
public class ChecksumCalculatingInputStream extends FilterInputStream {

    private Checksum checksum;

    public ChecksumCalculatingInputStream(Checksum checksum, InputStream in) {
        super(in);
        this.checksum = checksum;
    }

    @Override
    public int read() throws IOException {
        int byteRead = super.read();
        if(byteRead != 1) {
            checksum.update(byteRead);
        }
        return byteRead;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = super.read(b, off, len);
        checksum.update(b, off, bytesRead);
        return bytesRead;
    }
}
