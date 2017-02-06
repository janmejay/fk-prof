package fk.prof.userapi.api;

import fk.prof.aggregation.AggregatedProfileFileNamingStrategy;
import fk.prof.aggregation.Constants;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.storage.AsyncStorage;
import fk.prof.storage.FileNamingStrategy;
import fk.prof.storage.buffer.StorageBackedInputStream;
import fk.prof.userapi.Deserializer;
import fk.prof.userapi.model.*;
import io.vertx.core.Future;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gaurav.ashok
 */
public class AggregatedProfileLoader {

    private AsyncStorage asyncStorage;

    public AggregatedProfileLoader(AsyncStorage asyncStorage) {
        this.asyncStorage = asyncStorage;
    }

    public void load(Future<AggregatedProfileInfo> future, AggregatedProfileModel.Header header) {
        if(header.getFormatVersion() != 1) {
            future.fail("file format version is not supported");
            return;
        }

        FileNamingStrategy fileNamingStrategy = new AggregatedProfileFileNamingStrategy(header);
        StorageBackedInputStream in = new StorageBackedInputStream(asyncStorage, fileNamingStrategy);

        try {
            int magicNum = Deserializer.readFixedInt32(in);

            if (magicNum != Constants.AGGREGATION_FILE_MAGIC_NUM) {
                future.fail("Unknown file. Unexpected first 4 bytes");
                return;
            }

            // read header
            AggregatedProfileModel.Header parsedHeader = AggregatedProfileModel.Header.parseDelimitedFrom(in);
            Deserializer.readFixedInt32(in);

            // read traceCtx list
            AggregatedProfileModel.TraceCtxList traceCtxList = AggregatedProfileModel.TraceCtxList.parseDelimitedFrom(in);
            Deserializer.readFixedInt32(in);

            // read profiles summary
            AggregatedProfileModel.ProfilesSummary profilesSummary = AggregatedProfileModel.ProfilesSummary.parseDelimitedFrom(in);
            Deserializer.readFixedInt32(in);

            // read method lookup table
            AggregatedProfileModel.MethodLookUp methodLookUp = AggregatedProfileModel.MethodLookUp.parseDelimitedFrom(in);
            Deserializer.readFixedInt32(in);

            Map<String, AggregatedSamplesPerTraceCtx> samplesPerTrace = new HashMap<>();

            switch (header.getWorkType()) {
                case cpu_sample_work:
                    for(AggregatedProfileModel.TraceCtxDetail traceCtx: traceCtxList.getAllTraceCtxList()) {
                        samplesPerTrace.put(traceCtx.getName(),
                                new AggregatedSamplesPerTraceCtx(methodLookUp, new AggregatedCpuSamplesData(parseStacktraceTree(in))));
                    }
                    break;
                default:
                    break;
            }

            AggregatedProfileInfo profileInfo = new AggregatedProfileInfo(parsedHeader,
                    new ScheduledProfilesSummary(traceCtxList, profilesSummary), samplesPerTrace);

            future.complete(profileInfo);
        }
        catch (IOException e) {
            future.fail(e);
        }
        finally {
            try {
                in.close();
            }
            catch (IOException e) {
                // log the error
            }
        }
    }

    private StacktraceTreeIterable parseStacktraceTree(InputStream in) throws IOException {
        // tree is serialized in DFS manner. First node being the root.
        int nodeCount = 1;  // 1 for root node
        int parsedNodeCount = 0;
        List<AggregatedProfileModel.FrameNodeList> parsedFrameNodes = new ArrayList<>();

        do {
            AggregatedProfileModel.FrameNodeList frameNodeList = AggregatedProfileModel.FrameNodeList.parseDelimitedFrom(in);
            for(AggregatedProfileModel.FrameNode node: frameNodeList.getFrameNodesList()) {
                nodeCount += node.getChildCount();
            }
            parsedNodeCount += frameNodeList.getFrameNodesCount();
            parsedFrameNodes.add(frameNodeList);
        } while(parsedNodeCount < nodeCount && parsedNodeCount > 0);

        return new StacktraceTreeIterable(parsedFrameNodes);
    }
}
