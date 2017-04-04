package fk.prof.recorder.e2e;

import fk.prof.recorder.utils.FileResolver;
import fk.prof.recorder.utils.Util;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by gaurav.ashok on 06/03/17.
 */
public class UserapiProcess {

    private static final Logger logger = LoggerFactory.getLogger(UserapiProcess.class);

    static String fqdn = "fk.prof.userapi.UserapiApplication";
    Path confPath;
    Process process;

    public UserapiProcess(Path confPath) {
        this.confPath = confPath;
    }

    public void start() throws IOException {
        String java = System.getProperty("java.home") + "/bin/java";

        List<String> classpath = Util.discoverClasspath(getClass());
        String[] command = new String[] {java, "-cp", String.join(":", classpath), fqdn, "--conf", confPath.toString()};

        process = new ProcessBuilder()
                .command(command)
                .redirectError(new File("/tmp/fkprof_userapi_stderr.log"))
                .redirectOutput(new File("/tmp/fkprof_userapi_stdout.log"))
                .start();
    }

    public void stop() {
        process.destroy();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.info(e.getMessage(), e);
        }
    }
}
