package fk.prof.storage.test;

import fk.prof.storage.FileNamingStrategy;
import fk.prof.storage.StorageBackedInputStream;
import fk.prof.storage.StorageBackedOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.zip.Adler32;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link StorageBackedOutputStream} and {@link StorageBackedInputStream} using a simple hashmap based storage.
 * @author gaurav.ashok
 */
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

    @Before
    public void setBefore() {
        storage = spy(new Util.StringStorage());
        os = new StorageBackedOutputStream(storage, fileName, partSize);
        is = new StorageBackedInputStream(storage, fileName);
    }

    @Test
    public void testOutputStream_shouldWriteChunksUsingAsyncStorage() throws Exception {

        os.write(content.getBytes());
        os.close();

        verify(storage, times(contentSize/partSize)).store(any(), any());
        assertEquals(contentSize/partSize, storage.writtenContent.size());

        // verify that we got the correct chunks
        for(int i = 0; i < contentSize/partSize; ++i) {
            String contentPart = storage.writtenContent.get(fileName.getFileName(i + 1));
            assertNotNull(contentPart);
            assertEquals(content.substring(i * partSize, (i + 2) * partSize > contentSize ? contentSize : (i + 1) * partSize), contentPart);
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

        RuntimeException expectedEx = new UncheckedIOException(new IOException("Socket timeout"));

        // exception when getting 5th part
        when(storage.fetch("5")).
                thenReturn(CompletableFuture.supplyAsync(() -> {
                    throw expectedEx;
                }));

        byte[] bytes = new byte[contentSize];
        try {
            is.read(bytes);
            fail("should have thrown IOException");
        } catch (IOException e) {
            assertSame(expectedEx, e.getCause());
        }
    }

    private void initStorage() {
        for(int i = 0; i < contentSize/partSize; ++i) {
            String chunkedContent = content.substring(i * partSize, (i + 2) * partSize > contentSize ? contentSize : (i + 1) * partSize);
            storage.writtenContent.put(fileName.getFileName(i + 1), chunkedContent);
        }
    }
}
