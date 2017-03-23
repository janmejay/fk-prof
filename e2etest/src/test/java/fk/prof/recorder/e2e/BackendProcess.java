package fk.prof.recorder.e2e;

import fk.prof.recorder.utils.FileResolver;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by gaurav.ashok on 06/03/17.
 */
public class BackendProcess {

    private static final Logger logger = LoggerFactory.getLogger(BackendProcess.class);

    static String fqdn = "fk.prof.backend.BackendApplication";
    Path confPath;
    Process process;

    public BackendProcess(Path confPath) {
        this.confPath = confPath;
    }

    public void start() throws IOException {
        String java = System.getProperty("java.home") + "/bin/java";

        Mutable<Boolean> isFatJar = new MutableBoolean();

        String dir = "../backend/target/";
        Path jarFile = FileResolver.jarFile("backend", dir, "backend-.*.jar", isFatJar);

        String[] command;
        if(isFatJar.getValue()) {
            command = new String[] {java, "-jar", jarFile.toAbsolutePath().toString(), "--conf", confPath.toString()};
        }
        else {
            List<String> classpath = Arrays.asList(jarFile.toAbsolutePath().toString(), dir + "lib/*");
            command = new String[] {java, "-cp", String.join(":", classpath), fqdn, "--conf", confPath.toString()};
        }

        process = new ProcessBuilder()
                .command(command)
                .redirectError(new File("/tmp/fkprof_backend_stderr.log"))
                .redirectOutput(new File("/tmp/fkprof_backend_stdout.log"))
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
