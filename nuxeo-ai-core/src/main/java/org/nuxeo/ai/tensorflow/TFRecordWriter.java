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
 *     Pedro Cardoso
 */
package org.nuxeo.ai.tensorflow;

import static org.nuxeo.ai.enrichment.EnrichmentUtils.CONVERSION_SERVICE;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_CONVERTER;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.optionAsInteger;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.LIST_DELIMITER_PATTERN;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.bulk.AbstractRecordWriter;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.ExportRecord;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ai.tensorflow.ext.TensorflowWriter;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.api.ConverterNotRegistered;
import org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants;
import org.nuxeo.runtime.api.Framework;
import org.tensorflow.example.BytesList;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;
import org.tensorflow.example.Int64List;
import com.google.protobuf.ByteString;

/**
 * Write TFRecords
 */
public class TFRecordWriter extends AbstractRecordWriter {

    public static final String TFRECORD_MIME_TYPE = "application/x-tensorflow-record";

    private static final Logger log = LogManager.getLogger(TFRecordWriter.class);

    protected String imageConversionService;

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
        this.imageConversionService = options.getOrDefault(CONVERSION_SERVICE, DEFAULT_CONVERTER);
        try {
            Framework.getService(ConversionService.class).isConverterAvailable(imageConversionService);
        } catch (ConverterNotRegistered e) {
            log.warn(imageConversionService + " converter is not registered.  You will not be able to export images.");
        }
        this.imageWidth = optionAsInteger(options, ImagingConvertConstants.OPTION_RESIZE_WIDTH,
                EnrichmentUtils.DEFAULT_IMAGE_WIDTH);
        this.imageHeight = optionAsInteger(options, ImagingConvertConstants.OPTION_RESIZE_HEIGHT,
                EnrichmentUtils.DEFAULT_IMAGE_HEIGHT);
        this.imageDepth = optionAsInteger(options, ImagingConvertConstants.OPTION_RESIZE_DEPTH,
                EnrichmentUtils.DEFAULT_IMAGE_DEPTH);
        this.imageFormat = options.getOrDefault(ImagingConvertConstants.CONVERSION_FORMAT,
                EnrichmentUtils.DEFAULT_CONVERSATION_FORMAT);
    }

    @Override
    public long write(List<ExportRecord> list) throws IOException {
        int written = 0;
        int skipped = 0;
        if (list != null && !list.isEmpty()) {
            for (ExportRecord record : list) {
                File file = getFile(record.getId());
                try (FileOutputStream fos = new FileOutputStream(file, true);
                        BufferedOutputStream bos = new BufferedOutputStream(fos, bufferSize);
                        DataOutputStream dos = new DataOutputStream(bos)) {

                    TensorflowWriter writer = new TensorflowWriter(dos);

                    if (write(writer, record)) {
                        record.setFailed(false);
                        written++;
                    } else {
                        record.setFailed(true);
                        skipped++;
                    }
                }
            }
            if (list.size() != written) {
                log.warn("{} writer had {} records, {} were written, {} were skipped.", name, list.size(), written,
                        skipped);
            }
        }

        return skipped;
    }

    @Override
    public boolean write(ExportRecord record) throws IOException {
        File file = getFile(record.getId());
        try (FileOutputStream fos = new FileOutputStream(file, true);
                BufferedOutputStream bos = new BufferedOutputStream(fos, bufferSize);
                DataOutputStream dos = new DataOutputStream(bos)) {

            TensorflowWriter writer = new TensorflowWriter(dos);
            if (write(writer, record)) {
                return true;
            }
        }

        log.warn("Record for {} was skipped.", name);
        return false;
    }

    protected boolean write(TensorflowWriter writer, ExportRecord record) throws IOException {
        if (record.isFailed()) {
            return false;
        }

        try {
            BlobTextFromDocument blobText = MAPPER.readValue(record.getData(), BlobTextFromDocument.class);
            Optional<Features> allFeatures = writeFeatures(blobText);
            if (allFeatures.isPresent() && allFeatures.get().getFeatureCount() > 0) {
                TFRecord tfRecord = new TFRecord(blobText.getId(), allFeatures.get());
                writer.write(tfRecord.toByteArray());
                return true;
            } else {
                return false;
            }
        } catch (NuxeoException e) {
            log.warn("Failed to process record {}", record.getId(), e);
            return false;
        }
    }

    /**
     * Write the features based on the supplied data
     */
    protected Optional<Features> writeFeatures(BlobTextFromDocument blobTextFromDoc) throws IOException {
        Features.Builder features = Features.newBuilder();
        for (Map.Entry<PropertyType, ManagedBlob> blobEntry : blobTextFromDoc.computePropertyBlobs().entrySet()) {
            Blob blob = getConvertedBlob(blobEntry.getValue(), blobEntry.getKey().getType());
            if (blob != null) {
                features.putFeature(blobEntry.getKey().getName(), blobFeature(blob));
            } else {
                return Optional.empty();
            }
        }
        blobTextFromDoc.getProperties().forEach((k, v) -> {
            String[] values = v.split(LIST_DELIMITER_PATTERN);
            features.putFeature(k, textFeature(values));
        });
        return Optional.of(features.build());
    }

    @Override
    protected Blob createBlob(File file) throws IOException {
        return Blobs.createBlob(file, TFRECORD_MIME_TYPE);
    }

    protected Blob getConvertedBlob(ManagedBlob sourceBlob, String type) {
        if (sourceBlob != null) {
            Blob source = getBlobFromProvider(sourceBlob);
            if (source != null) {
                if (IMAGE_TYPE.equals(type)) {
                    // if the blob is an image
                    return EnrichmentUtils.convertImageBlob(imageConversionService, source, imageWidth, imageHeight,
                            imageDepth, imageFormat);
                } else if (TEXT_TYPE.equals(type)) {
                    // the Blob is text
                    return EnrichmentUtils.convertTextBlob(source);
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
