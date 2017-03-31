package fk.prof.backend.http.handler;

import com.codahale.metrics.*;
import fk.prof.aggregation.ProcessGroupTag;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.exception.AggregationFailure;
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
  private final RecordedProfileProcessor profileProcessor;
  private final CompositeByteBufInputStream inputStream;

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(ConfigManager.METRIC_REGISTRY);
  private Histogram histChunkSize = metricRegistry.histogram(MetricRegistry.name(RecordedProfileRequestHandler.class, "chunk", "size", ProcessGroupTag.EMPTY.toString()));
  private Timer tmrChunkIdle = metricRegistry.timer(MetricRegistry.name(RecordedProfileRequestHandler.class, "chunk", "idle", ProcessGroupTag.EMPTY.toString()));
  private Meter mtrChunkBytes = metricRegistry.meter(MetricRegistry.name(RecordedProfileRequestHandler.class, "chunk", "bytes", ProcessGroupTag.EMPTY.toString()));

  private Long chunkReceivedTime = null;

  public RecordedProfileRequestHandler(RoutingContext context, CompositeByteBufInputStream inputStream, RecordedProfileProcessor profileProcessor) {
    this.context = context;
    this.profileProcessor = profileProcessor;
    this.inputStream = inputStream;
  }

  @Override
  public void handle(Buffer requestBuffer) {
    try {
//      try { logger.debug(String.format("buffer=%d, chunk=%d", inputStream.available(), requestBuffer.length())); } catch (Exception ex) {}
      histChunkSize.update(requestBuffer.length());
      long currentTime = System.nanoTime();
      if (chunkReceivedTime != null) {
        tmrChunkIdle.update(currentTime - chunkReceivedTime, TimeUnit.NANOSECONDS);
      }
      chunkReceivedTime = currentTime;

      if (!context.response().ended()) {
        inputStream.accept(requestBuffer.getByteBuf());
//        profileProcessor.process(inputStream);
      }
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    } finally {
      mtrChunkBytes.mark(requestBuffer.length());
    }
  }

}
