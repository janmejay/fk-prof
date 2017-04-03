package fk.prof.userapi;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.cli.*;

public class UserapiApplication {
  private static Logger logger = LoggerFactory.getLogger(UserapiApplication.class);

  public static void main(String[] args) throws Exception {
    UserapiConfigManager.setDefaultSystemProperties();
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

    UserapiManager userapiManager = new UserapiManager(confPath);
    userapiManager.launch().setHandler(ar -> {
      if (ar.succeeded()) {
        logger.info("Userapi launched");
      } else {
        logger.error("Error launching Userapi: ", ar.cause());
      }
    });
  }
}
