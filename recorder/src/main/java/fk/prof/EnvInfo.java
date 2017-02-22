package fk.prof;

/**
 * @understands release or running process level information about perf-ctx
 */
public class EnvInfo {
    public static native int maxScopedDepthSupported();
    public static native int maxDuplicationWidthSupported();
    
    public static native boolean maxContextsExceeded();
    public static native int maxContextsSupported();
    public static native int currentContextCount();
}
