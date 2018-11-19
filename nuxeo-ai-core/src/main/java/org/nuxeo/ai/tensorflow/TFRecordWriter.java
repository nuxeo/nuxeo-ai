/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.tensorflow;

import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.optionAsInteger;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ai.bulk.AbstractRecordWriter;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.tensorflow.ext.TensorflowWriter;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants;
import org.nuxeo.lib.stream.computation.Record;
import org.tensorflow.example.BytesList;
import org.tensorflow.example.Example;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;
import org.tensorflow.example.Int64List;
import com.google.protobuf.ByteString;

/**
 * Write TFRecords
 */
public class TFRecordWriter extends AbstractRecordWriter {

    public static final String TFRECORD_MIME_TYPE = "application/x-tensorflow-record";

    protected int imageWidth;

    protected int imageHeight;

    protected int imageDepth;

    protected String imageFormat;

    public TFRecordWriter(String name) {
        super(name);
    }

    /**
     * Create a blob feature
     */
    public static Feature blobFeature(Blob... blob) throws IOException {
        BytesList.Builder bytesList = BytesList.newBuilder();
        for (Blob aBlob : blob) {
            try (InputStream blobStream = aBlob.getStream()) {
                bytesList.addValue(ByteString.readFrom(blobStream));
            }
        }
        return Feature.newBuilder().setBytesList(bytesList).build();
    }

    /**
     * Create a text feature
     */
    public static Feature textFeature(String... text) {
        BytesList.Builder bytesList = BytesList.newBuilder();
        for (String txt : text) {
            bytesList.addValue(ByteString.copyFromUtf8(txt));
        }
        return Feature.newBuilder().setBytesList(bytesList).build();
    }

    /**
     * Create an int feature
     */
    public static Feature intFeature(Long... values) {
        Int64List.Builder intList = Int64List.newBuilder();
        for (long val : values) {
            intList.addValue(val);
        }
        return Feature.newBuilder().setInt64List(intList).build();
    }

    @Override
    public void init(Map<String, String> options) {
        super.init(options);
        this.imageWidth = optionAsInteger(options, ImagingConvertConstants.OPTION_RESIZE_WIDTH, EnrichmentUtils.DEFAULT_IMAGE_WIDTH);
        this.imageHeight = optionAsInteger(options, ImagingConvertConstants.OPTION_RESIZE_HEIGHT, EnrichmentUtils.DEFAULT_IMAGE_HEIGHT);
        this.imageDepth = optionAsInteger(options, ImagingConvertConstants.OPTION_RESIZE_DEPTH, EnrichmentUtils.DEFAULT_IMAGE_DEPTH);
        this.imageFormat = options
                .getOrDefault(ImagingConvertConstants.CONVERSION_FORMAT, EnrichmentUtils.DEFAULT_CONVERSATION_FORMAT);
    }

    @Override
    public void write(List<Record> list) throws IOException {
        if (list != null && !list.isEmpty()) {

            File file = getFile(list.get(0).getKey());

            try (BufferedOutputStream buffy = new BufferedOutputStream(new FileOutputStream(file, true), bufferSize);
                 DataOutputStream dataOutputStream = new DataOutputStream(buffy)) {

                TensorflowWriter tensorflowWriter = new TensorflowWriter(dataOutputStream);

                for (Record record : list) {
                    try {
                        BlobTextFromDocument blobText = fromRecord(record, BlobTextFromDocument.class);
                        Features allFeatures = writeFeatures(blobText);
                        if (allFeatures.getFeatureCount() > 0) {
                            Example example = Example.newBuilder().setFeatures(allFeatures).build();
                            tensorflowWriter.write(example.toByteArray());
                        }
                    } catch (NuxeoException nux) {
                        log.warn(String.format("Failed to process record %s ", record.getWatermark()), nux);
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug(String.format("%s writer has %d records", name, list.size()));
            }
        }
    }

    /**
     * Write the features based on the supplied data
     */
    protected Features writeFeatures(BlobTextFromDocument blobTextFromDoc) throws IOException {
        Features.Builder features = Features.newBuilder();
        for (Map.Entry<String, ManagedBlob> blobEntry : blobTextFromDoc.getBlobs().entrySet()) {
            Blob blob = convertImageBlob(blobEntry.getValue());
            if (blob != null) {
                features.putFeature(blobEntry.getKey(), blobFeature(blob));
            }
        }
        blobTextFromDoc.getProperties().forEach((k, v) -> features.putFeature(k, textFeature(v)));
        return features.build();
    }

    @Override
    protected Blob createBlob(File file) throws IOException {
        return Blobs.createBlob(file, TFRECORD_MIME_TYPE);
    }

    /**
     * Converts a managed blob to Tensorflow record format
     */
    protected Blob convertImageBlob(ManagedBlob sourceBlob) {
        if (sourceBlob != null) {
            Blob source = getBlobFromProvider(sourceBlob);
            if (source != null) {
                return EnrichmentUtils.convertImageBlob(source, imageWidth, imageHeight, imageDepth, imageFormat);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
