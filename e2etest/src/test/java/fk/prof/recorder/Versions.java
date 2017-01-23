package fk.prof.recorder;

/**
 * @understands various values of various versioning fields used in RPC or storage payloads
 * 
 * This should eventually be moved to the prod source-dir, keeping it here for now because prod-impl is not ready yet.
 */
public class Versions {
    /**
     * TODO: write a test that reflectively changes these and records a profile, so we know they are not hardcoded in the wrong places
     */
    public static final int RECORDER_ENCODING_VERSION = 1;
    public static final int RECORDER_VERSION = 1;
    public static final int CONTROLLER_VERSION = 1;
}
