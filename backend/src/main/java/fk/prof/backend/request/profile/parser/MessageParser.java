package fk.prof.backend.request.profile.parser;

import com.google.common.io.ByteStreams;
import com.google.protobuf.*;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.request.CompositeByteBufInputStream;

import java.io.IOException;

/**
 * @author gaurav.ashok
 */
public abstract class MessageParser {

    public static int readRawVariantInt(CompositeByteBufInputStream in, String tag) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) {
            throw new UnexpectedEOFException();
        }

        try {
            return CodedInputStream.readRawVarint32(firstByte, in);
        }
        catch (InvalidProtocolBufferException e) {
            if(in.available() > 0) {
                throw new AggregationFailure("Error while parsing " + tag);
            }
            throw new UnexpectedEOFException();
        }
    }

    public static <T extends AbstractMessage> T readDelimited(Parser<T> parser, CompositeByteBufInputStream in, int maxMessageSize, String tag) throws IOException {
        try {
            int msgSize = readRawVariantInt(in, tag + ":size");
            if(msgSize > maxMessageSize) {
                throw new AggregationFailure("invalid length for " + tag);
            }

            if(in.available() > msgSize) {
                return parser.parseFrom(ByteStreams.limit(in, msgSize));
            }
            else {
                throw new UnexpectedEOFException();
            }
        }
        catch (InvalidProtocolBufferException e) {
            throw new AggregationFailure("Error while parsing " + tag);
        }
    }
}
