package fk.prof.backend.util;

import com.google.common.io.BaseEncoding;
import recording.Recorder;

import java.nio.charset.Charset;

import static fk.prof.backend.util.ZookeeperUtil.DELIMITER;

/**
 * Utility methods for encoding and decoding strings.
 * Created by rohit.patiyal on 22/05/17.
 */
public class PathNamingUtil {
    public static String decode32(String str) {
        return new String(BaseEncoding.base32().decode(str), Charset.forName("utf-8"));
    }

    public static String encode32(String str) {
        return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));

    }

    public static String getDirectoryPath(Recorder.ProcessGroup processGroup) {
        return DELIMITER + encode32(processGroup.getAppId()) + DELIMITER + encode32(processGroup.getCluster()) + DELIMITER + encode32(processGroup.getProcName());
    }
}
