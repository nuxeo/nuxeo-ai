/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.tensorflow;

import static com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED;

import java.io.IOException;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.tensorflow.example.Features;
import com.google.protobuf.AbstractParser;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Parser;
import com.google.protobuf.UnknownFieldSet;

/**
 * Partial implementation of a Protobuf object that represents Tensorflow record
 */
public class TFRecord extends GeneratedMessageV3 implements MessageOrBuilder {

    protected static final int FEATURES_FIELD_NUMBER = 1;

    protected static final int DOC_ID_FIELD_NUMBER = 2;

    // Some magic way PB uses to calculate the tag
    protected static final int DOC_ID_TAG = (DOC_ID_FIELD_NUMBER << 3) | WIRETYPE_LENGTH_DELIMITED;

    protected static final Parser<TFRecord> PARSER = new RecordParser();

    protected Features features;

    protected String docId;

    public static class RecordParser extends AbstractParser<TFRecord> {

        @Override
        public TFRecord parsePartialFrom(CodedInputStream is, ExtensionRegistryLite registry)
                throws InvalidProtocolBufferException {
            try {
                return new TFRecord(is, registry);
            } catch (IOException e) {
                throw new NuxeoException(e);
            }
        }
    }

    public TFRecord(String docId, Features features) {
        this.docId = docId;
        this.features = features;
    }

    /**
     * Must be never called explicitly. For PB internal use only
     *
     * @throws IOException in case of broken record
     */
    protected TFRecord(CodedInputStream is, ExtensionRegistryLite registry) throws IOException {
        Objects.requireNonNull(registry);

        UnknownFieldSet.Builder unknownBuilder = UnknownFieldSet.newBuilder();
        try {
            boolean done = false;
            while (!done) {
                int tag = is.readTag();
                switch (tag) {
                case 0: // 0 means EOF
                    done = true;
                    break;
                case DOC_ID_TAG:
                    String id = is.readString();
                    if (StringUtils.isEmpty(docId)) {
                        this.docId = id;
                    }
                    break;
                case 10: {
                    Features.Builder subBuilder = null;
                    if (features != null) {
                        subBuilder = features.toBuilder();
                    }
                    features = is.readMessage(Features.parser(), registry);
                    if (subBuilder != null) {
                        subBuilder.mergeFrom(features);
                        features = subBuilder.buildPartial();
                    }
                    break;
                }
                default: {
                    // We don't really use unknown fields. It's more to know all ingested data
                    if (!parseUnknownFieldProto3(is, unknownBuilder, registry, tag)) {
                        done = true;
                    }
                    break;
                }
                }
            }
        } catch (InvalidProtocolBufferException e) {
            throw e.setUnfinishedMessage(this);
        } catch (IOException e) {
            throw new InvalidProtocolBufferException(e).setUnfinishedMessage(this);
        } finally {
            this.unknownFields = unknownBuilder.build();
            makeExtensionsImmutable();
        }
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public Features getFeatures() {
        return features;
    }

    public void setFeatures(Features features) {
        this.features = features;
    }

    public boolean hasFeatures() {
        return features != null;
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        if (StringUtils.isNotEmpty(docId)) {
            output.writeString(2, docId);
        }
        if (features != null) {
            output.writeMessage(1, getFeatures());
        }
    }

    @Override
    public int getSerializedSize() {
        int size = memoizedSize;
        if (size != -1)
            return size;

        size = 0;
        if (features != null) {
            size += CodedOutputStream.computeMessageSize(FEATURES_FIELD_NUMBER, features);
        }

        if (StringUtils.isNotEmpty(docId)) {
            size += CodedOutputStream.computeStringSize(DOC_ID_FIELD_NUMBER, docId);
        }

        memoizedSize = size;
        return size;
    }

    /**
     * Deserialization method
     *
     * @param bytes to restore the object
     * @return restored {@link TFRecord}
     * @throws InvalidProtocolBufferException in case of broken record
     */
    public static TFRecord from(byte[] bytes) throws InvalidProtocolBufferException {
        return PARSER.parsePartialFrom(bytes);
    }

    // TODO: Implement the following methods
    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        return null;
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent builderParent) {
        return null;
    }

    @Override
    public Message.Builder newBuilderForType() {
        return null;
    }

    @Override
    public Message.Builder toBuilder() {
        return null;
    }

    @Override
    public Message getDefaultInstanceForType() {
        return null;
    }

    @Override
    public Parser<TFRecord> getParserForType() {
        return PARSER;
    }
}
