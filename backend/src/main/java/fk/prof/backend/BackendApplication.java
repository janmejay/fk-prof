package fk.prof.backend;

import fk.prof.backend.worker.BackendDaemon;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.cli.*;

public class BackendApplication {
  private static Logger logger = LoggerFactory.getLogger(BackendDaemon.class);

  public static void main(String[] args) throws Exception {
//    ConfigManager.setDefaultSystemProperties();
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption(Option.builder("c")
        .longOpt("conf")
        .required()
        .hasArg()
        .optionalArg(false)
        .desc("specifies json file to be used as configuration for vertx")
        .argName("json file")
        .build());

    CommandLine cmd = parser.parse(options, args);
    String confPath = cmd.getOptionValue("c");

    BackendManager backendManager = new BackendManager(confPath);
    backendManager.launch().setHandler(ar -> {
      if(ar.succeeded())  {
        logger.info("Backend launched");
      } else {
        logger.error("Error launching backend: ", ar.cause());
      }
    });
  }
}
