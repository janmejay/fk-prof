package fk.prof.storage.buffer;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import java.nio.ByteBuffer;

/**
 * @author gaurav.ashok
 */
public class ByteBufferPoolFactory extends BasePooledObjectFactory<ByteBuffer> {

    private static final int DEFAULT_SIZE_IN_BYTES = 20_000_000;   // TODO figure out the appropriate size.

    private int arraySizeInBytes = DEFAULT_SIZE_IN_BYTES;
    private boolean allocateDirect = false;

    public ByteBufferPoolFactory() {
        this(DEFAULT_SIZE_IN_BYTES, false);
    }

    public ByteBufferPoolFactory(int arraySizeInBytes, boolean allocateDirect) {
        this.arraySizeInBytes = arraySizeInBytes;
        this.allocateDirect = allocateDirect;
    }

    @Override
    public ByteBuffer create() throws Exception {
        return allocateDirect ? ByteBuffer.allocateDirect(arraySizeInBytes) : ByteBuffer.allocate(arraySizeInBytes);
    }

    @Override
    public PooledObject<ByteBuffer> wrap(ByteBuffer bytes) {
        return new DefaultPooledObject<>(bytes);
    }

    @Override
    public void activateObject(PooledObject<ByteBuffer> p) throws Exception {
        p.getObject().clear();
    }
}
