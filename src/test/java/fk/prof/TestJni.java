package fk.prof;

import java.io.File;

/**
 * @understands calling test-jni functions
 */
public class TestJni {
    public native boolean generateCpusampleSimpleProfile(String filePath);

    public static void loadJniLib() {
        String linkTargetPath = new File("build/libtestjni" + Platforms.getDynamicLibraryExtension()).getAbsolutePath();
        System.load(linkTargetPath);
    }
}
