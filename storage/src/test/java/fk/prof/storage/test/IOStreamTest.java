package fk.prof.storage.test;

import fk.prof.storage.AsyncStorage;
import fk.prof.storage.FileNamingStrategy;
import fk.prof.storage.ObjectNotFoundException;
import fk.prof.storage.buffer.ByteBufferPoolFactory;
import fk.prof.storage.buffer.StorageBackedInputStream;
import fk.prof.storage.buffer.StorageBackedOutputStream;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link StorageBackedOutputStream} and {@link StorageBackedInputStream} using a simple hashmap based storage.
 * @author gaurav.ashok
 */
@RunWith(MockitoJUnitRunner.class)
public class IOStreamTest {

    Util.StringStorage storage;

    OutputStream os;
    InputStream is;

    FileNamingStrategy fileName = new Util.TrivialFileNameStrategy();

    final int partSize = 13; // weird size in bytes

    // some content of 200 bytes
    final String content = "27zu6ozrh553p62j5e598xtioyy8bm83cmulyyev9sgf4jljluk8nb21fjd3g1iul2jefvj03masosngk2zw0rp2xtkb" +
            "29i9a8swogo98lryqyigeuwsvk76z6qhfhcpkr8agk3fm8m0di591yuetua4x5yiv8itpfl4la9iafk40rapb6uibqpa3lt0t3wujutbx5nl";
    final int contentSize = content.length();

    GenericObjectPoolConfig poolConfig = null;
    GenericObjectPool<ByteBuffer> pool = null;

    @Before
    public void setBefore() {
        storage = spy(new Util.StringStorage());

        if(poolConfig == null) {
            poolConfig = new GenericObjectPoolConfig();
            poolConfig.setMaxTotal(5);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(0);

            pool = new GenericObjectPool<>(new ByteBufferPoolFactory(partSize , false), poolConfig);
        }

        os = new StorageBackedOutputStream(pool, storage, fileName);
        is = new StorageBackedInputStream(storage, fileName);
    }

    @Test
    public void testOutputStream_shouldWriteChunksUsingAsyncStorage() throws Exception {
        os.write(content.getBytes());
        os.close();

        verify(storage, times(contentSize/partSize)).storeAsync(any(), any(), eq(13L));
        verify(storage, times(1)).storeAsync(any(), any(), eq(5L));
        assertEquals(contentSize/partSize + 1, storage.writtenContent.size());

        // verify that we got the correct chunks
        for(int i = 0; i < contentSize/partSize; ++i) {
            String contentPart = storage.writtenContent.get(fileName.getFileName(i));
            assertNotNull(contentPart);
            assertEquals(content.substring(i * partSize, Math.min((i + 1) * partSize, contentSize)), contentPart);
        }
    }

    @Test
    public void testInputStream_shouldReadAllContentFromChunkedStorage() throws Exception {
        // init the storage
        initStorage();

        // read from InputStream
        byte[] bytes = new byte[contentSize];
        int bytesRead = is.read(bytes);

        assertEquals(contentSize, bytesRead);

        verify(storage, times(storage.writtenContent.size())).fetch(any());
        // verify the read content
        assertEquals(content, new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testInputStream_shouldThrowUnderlyingErrorInCaseOfStorageFetchException() {
        // init the storage
        initStorage();

        RuntimeException expectedEx = new ObjectNotFoundException("not found");

        // exception when getting 5th part
        when(storage.fetch(fileName.getFileName(0))).thenThrow(expectedEx);

        byte[] bytes = new byte[contentSize];
        try {
            is.read(bytes);
            fail("should have thrown IOException");
        } catch (IOException e) {
            assertEquals(FileNotFoundException.class, e.getClass());
        }
    }

    @Test
    public void testInputStream_shouldNotThrowObjectNotFoundInCaseOfExtraBytesFetch() throws IOException {
        // init the storage
        initStorage();

        // read some extra bytes. here 10 extra bytes
        byte[] bytes = new byte[contentSize + 10];
        int bytesRead = is.read(bytes);

        assertEquals(contentSize, bytesRead);

        bytesRead = is.read(bytes);
        assertEquals(-1, bytesRead);
    }

    @Test
    public void testInputStreamClose_shouldReliablyCloseInCaseOfInterruption() throws Exception {
        int wait = 100;
        ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);

        AsyncStorage storage = mock(AsyncStorage.class);
        StorageBackedInputStream in = new StorageBackedInputStream(storage, fileName);

        Thread mainThread = Thread.currentThread();

        // spy the inputStream to check whether you can close after interruption.
        InputStream spyContent1 = spy(strAsInputStream(content));
        InputStream spyContent2 = spy(strAsInputStream(content));

        when(storage.fetchAsync("0")).thenReturn(CompletableFuture.completedFuture(spyContent1));
        when(storage.fetchAsync("1")).thenReturn(CompletableFuture.supplyAsync(() -> {
            // create a task, that interrupts main thread while waiting on the delayed response
            scheduled.schedule(() -> mainThread.interrupt(), wait / 2, TimeUnit.MILLISECONDS);

            // create a delayed response by waiting for some time
            try {
                return scheduled.schedule(() -> spyContent2, wait, TimeUnit.MILLISECONDS).get();
            }
            catch (ExecutionException | InterruptedException e) {
                fail("storage fetch got interrupted instead");
            }
            return null;
        }, scheduled));

        byte[] bytes = new byte[2 * contentSize];

        try {
            in.read(bytes);
            fail("unreachable. should get interrupted");
        }
        catch (InterruptedIOException e) {
            assertEquals(200, e.bytesTransferred);
        }
        catch (RuntimeException e) {
            fail("unexpected exception while reading");
        }

        // lets close
        in.close();

        // lets wait for the scheduled task to finish
        scheduled.awaitTermination(wait, TimeUnit.MILLISECONDS);

        verify(spyContent1).read();
        verify(spyContent1).read(eq(bytes), eq(1), eq(399));
        verify(spyContent1).read(eq(bytes), eq(200), eq(200));
        verify(spyContent1).close();
        verify(spyContent2).close();
        verifyNoMoreInteractions(spyContent1, spyContent2);
    }

    private void initStorage() {
        for(int i = 0; i < contentSize/partSize; ++i) {
            String chunkedContent = content.substring(i * partSize, (i + 2) * partSize > contentSize ? contentSize : (i + 1) * partSize);
            storage.writtenContent.put(fileName.getFileName(i), chunkedContent);
        }
    }

    private InputStream strAsInputStream(String str) {
        return new ByteArrayInputStream(str.getBytes(Charset.forName("utf-8")));
    }
}