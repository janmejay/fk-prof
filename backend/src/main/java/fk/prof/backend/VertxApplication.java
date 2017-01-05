package fk.prof.backend;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.cli.*;

public class VertxApplication {
  private static Logger logger = LoggerFactory.getLogger(VertxApplication.class);

  public static void main(String[] args) {
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

    try {
      CommandLine cmd = parser.parse(options, args);
      String confPath = cmd.getOptionValue("c");

      Vertx vertx = Vertx.vertx();
      VertxManager.launch(vertx, confPath);
    } catch (ParseException ex) {
      logger.error("Error parsing cli arguments", ex);
    }
  }


}
