package fk.prof;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @understands calling test-jni functions
 */
public class TestJni {
    private static final AtomicBoolean loaded = new AtomicBoolean(false);

    public native boolean generateCpusampleSimpleProfile(String filePath);
        
    public native int getAndStubCtxIdStart(int startValue);
    
    public static void loadJniLib() {
        if (loaded.compareAndSet(false, true)) {
            String linkTargetPath = new File("build/libtestjni" + Platforms.getDynamicLibraryExtension()).getAbsolutePath();
            System.load(linkTargetPath);
        }
    }

    public native int getCurrentCtx();

    public native String getLastRegisteredCtxName();

    public native int getLastRegisteredCtxCoveragePct();
}
