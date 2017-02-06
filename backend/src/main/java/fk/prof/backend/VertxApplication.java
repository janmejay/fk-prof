package fk.prof.backend;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.commons.cli.*;

import java.io.IOException;

public class VertxApplication {
  public static void main(String[] args) throws ParseException, IOException {
    ConfigManager.setDefaultSystemProperties();
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
    JsonObject config = ConfigManager.loadFileAsJson(confPath);
    JsonObject vertxConfig = config.getJsonObject(ConfigManager.VERTX_OPTIONS_KEY);
    JsonObject deploymentConfig = config.getJsonObject(ConfigManager.DEPLOYMENT_OPTIONS_KEY);
    if (deploymentConfig == null) {
      throw new RuntimeException("Deployment options are required to be present");
    }

    Vertx vertx = VertxManager.setup(vertxConfig);
    VertxManager.launch(vertx, deploymentConfig);
  }
}
