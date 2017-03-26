package fk.prof.backend.http.handler;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.request.CompositeByteBufInputStream;
import fk.prof.backend.request.profile.RecordedProfileProcessor;
import fk.prof.backend.http.HttpHelper;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RecordedProfileRequestHandler implements Handler<Buffer> {
  private static Logger logger = LoggerFactory.getLogger(RecordedProfileRequestHandler.class);

  private final RoutingContext context;
  private final RecordedProfileProcessor profileParser;
  private final CompositeByteBufInputStream inputStream;

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(ConfigManager.METRIC_REGISTRY);
  private final Histogram histChunkSize = metricRegistry.histogram(MetricRegistry.name(RecordedProfileRequestHandler.class, "chunk", "size"));
  private final Timer tmrChunkIdle = metricRegistry.timer(MetricRegistry.name(RecordedProfileRequestHandler.class, "chunk", "idle"));

  private Long chunkReceivedTime = null;

  public RecordedProfileRequestHandler(RoutingContext context, CompositeByteBufInputStream inputStream, RecordedProfileProcessor profileParser) {
    this.context = context;
    this.profileParser = profileParser;
    this.inputStream = inputStream;
  }

  @Override
  public void handle(Buffer requestBuffer) {
    //    try { logger.debug(String.format("buffer=%d, chunk=%d", inputStream.available(), requestBuffer.length())); } catch (Exception ex) {}
    histChunkSize.update(requestBuffer.length());
    long currentTime = System.nanoTime();
    if(chunkReceivedTime != null) {
      tmrChunkIdle.update(currentTime - chunkReceivedTime, TimeUnit.NANOSECONDS);
    }
    chunkReceivedTime = currentTime;

    if (!context.response().ended()) {
      inputStream.accept(requestBuffer.getByteBuf());
      try {
        profileParser.process(inputStream);
      } catch (Exception ex) {
        try {
          inputStream.close();
        } catch (IOException ex1) {
          logger.error("Error closing inputstream", ex1);
        }
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

}
