package fk.prof.userapi.api;

import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.aggregation.Constants;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.storage.AsyncStorage;
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
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

/**
 * @author gaurav.ashok
 */
public class AggregatedProfileLoader {

    private AsyncStorage asyncStorage;

    public AggregatedProfileLoader(AsyncStorage asyncStorage) {
        this.asyncStorage = asyncStorage;
    }

    public void load(Future<AggregatedProfileInfo> future, AggregatedProfileNamingStrategy filename) {
        if(filename.version != 1) {
            future.fail("file format version is not supported");
            return;
        }

        Adler32 checksum = new Adler32();
        InputStream in = new StorageBackedInputStream(asyncStorage, filename);
        in = new CheckedInputStream(in, checksum);

        try {
            int magicNum = Deserializer.readFixedInt32(in);

            if (magicNum != Constants.AGGREGATION_FILE_MAGIC_NUM) {
                future.fail("Unknown file. Unexpected first 4 bytes");
                return;
            }

            // read header
            checksumReset(checksum);
            AggregatedProfileModel.Header parsedHeader = AggregatedProfileModel.Header.parseDelimitedFrom(in);
            checksumVerify((int)checksum.getValue(), Deserializer.readFixedInt32(in), "checksum error header");

            // read traceCtx list
            checksumReset(checksum);
            AggregatedProfileModel.TraceCtxList traceCtxList = AggregatedProfileModel.TraceCtxList.parseDelimitedFrom(in);
            checksumVerify((int)checksum.getValue(), Deserializer.readFixedInt32(in), "checksum error traceCtxList");

            // read profiles summary
            checksumReset(checksum);
            AggregatedProfileModel.ProfilesSummary profilesSummary = AggregatedProfileModel.ProfilesSummary.parseDelimitedFrom(in);
            checksumVerify((int)checksum.getValue(), Deserializer.readFixedInt32(in), "checksum error profileSummary");

            // read method lookup table
            checksumReset(checksum);
            AggregatedProfileModel.MethodLookUp methodLookUp = AggregatedProfileModel.MethodLookUp.parseDelimitedFrom(in);
            checksumVerify((int)checksum.getValue(), Deserializer.readFixedInt32(in), "checksum error methodLookup");

            Map<String, AggregatedSamplesPerTraceCtx> samplesPerTrace = new HashMap<>();

            checksumReset(checksum);
            switch (filename.workType) {
                case cpu_sample_work:
                    for(AggregatedProfileModel.TraceCtxDetail traceCtx: traceCtxList.getAllTraceCtxList()) {
                        samplesPerTrace.put(traceCtx.getName(),
                                new AggregatedSamplesPerTraceCtx(methodLookUp, new AggregatedCpuSamplesData(parseStacktraceTree(in))));
                    }
                    break;
                default:
                    break;
            }

            checksumVerify((int)checksum.getValue(), Deserializer.readFixedInt32(in), "checksum error " + filename.workType.name() + " aggregated samples");

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

    private void checksumReset(Checksum checksum) {
        checksum.reset();
    }

    private void checksumVerify(int actualChecksum, int expectedChecksum, String msg) {
        assert actualChecksum == expectedChecksum : msg;
    }

    private StacktraceTreeIterable parseStacktraceTree(InputStream in) throws IOException {
        // tree is serialized in DFS manner. First node being the root.
        int nodeCount = 1; // for root node
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
