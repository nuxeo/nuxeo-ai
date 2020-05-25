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
package org.nuxeo.ai.bulk;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.*;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.ExportRecord;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ai.tensorflow.TFRecord;
import org.nuxeo.ai.tensorflow.ext.TFRecordReader;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.tensorflow.example.Feature;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/recordwriter-test.xml" })
public class TensorTest {

    @Inject
    protected BlobManager blobManager;

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testWriter() throws IOException {
        RecordWriter writer = aiComponent.getRecordWriter("ai/training");
        assertNotNull(writer);
        BlobProvider blobProvider = blobManager.getBlobProvider("test");
        assertNotNull(blobProvider);

        String test_key = "k345";
        int numberOfRecords = 500;
        List<ExportRecord> records = new ArrayList<>();

        for (int i = 0; i < numberOfRecords; ++i) {
            BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument("ixi" + i, "test", "aaf", "Picture", null);
            blobTextFromDoc.addProperty("dc:title", "my text " + i);
            blobTextFromDoc.getProperties().put("ecm:primaryType", "Picture");
            byte[] bytes = MAPPER.writeValueAsBytes(blobTextFromDoc);

            records.add(ExportRecord.of(test_key, test_key, bytes));
        }

        writer.write(records);
        assertTrue(writer.exists(test_key));
        Optional<Blob> blob = writer.complete(test_key);
        assertTrue(blob.isPresent());
        assertTrue(blob.get().getLength() > 0);
        // System.out.println("File: " + blob.getFile().getAbsolutePath());
        assertEquals(numberOfRecords, countNumberOfExamples(blob.get(), 2));
    }

    /**
     * Count the number of tensorflow record example records.
     */
    public static int countNumberOfExamples(Blob blob, int numOfFeatures) throws IOException {
        if (blob == null) {
            return 0;
        }
        DataInput input = new DataInputStream(new FileInputStream(blob.getFile()));
        TFRecordReader tfRecordReader = new TFRecordReader(input, true);
        byte[] exampleData;
        int countExamples = 0;
        while ((exampleData = tfRecordReader.read()) != null) {
            TFRecord tfRecord = TFRecord.from(exampleData);
            if (numOfFeatures > 0) {
                assertEquals(numOfFeatures, tfRecord.getFeatures().getFeatureCount());
            }
            assertThat(tfRecord.getDocId()).isNotBlank();
            countExamples++;
        }
        return countExamples;
    }

    @Test
    public void testBlobWriter() throws IOException {
        RecordWriter writer = aiComponent.getRecordWriter("ai/validation");
        assertNotNull(writer);
        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider("test");
        assertNotNull(blobProvider);

        String test_key = "blobby";
        int numberOfRecords = 2;
        List<ExportRecord> records = new ArrayList<>();

        for (int i = 0; i < numberOfRecords; ++i) {
            BlobTextFromDocument blobTextFromDoc = blobTestImage(blobManager);
            blobTextFromDoc.getProperties().put("ecm:primaryType", "Picture");
            byte[] bytes = MAPPER.writeValueAsBytes(blobTextFromDoc);
            records.add(ExportRecord.of(test_key, test_key, bytes));
        }

        writer.write(records);
        assertTrue(writer.exists(test_key));
        Optional<Blob> blobWritten = writer.complete(test_key);
        assertTrue(blobWritten.isPresent());
        Blob blob = blobWritten.get();
        // System.out.println("File: " + blob.getFile().getAbsolutePath());
        DataInput input = new DataInputStream(new FileInputStream(blob.getFile()));
        TFRecordReader tfRecordReader = new TFRecordReader(input, true);
        byte[] exampleData;
        while ((exampleData = tfRecordReader.read()) != null) {
            TFRecord tfRecord = TFRecord.from(exampleData);
            assertEquals(2, tfRecord.getFeatures().getFeatureCount());
            Feature pictureBlob = tfRecord.getFeatures().getFeatureMap().get(FILE_CONTENT);
            File picFile = Framework.createTempFile("tf_image", "jpeg");
            try (FileOutputStream fos = new FileOutputStream(picFile)) {
                fos.write(pictureBlob.getBytesList().getValue(0).toByteArray());
                assertTrue(picFile.length() > 0);
            }
        }
    }

    @Test
    public void testTextBlobWriter() throws IOException {
        RecordWriter writer = aiComponent.getRecordWriter("ai/validation");
        assertNotNull(writer);
        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider("test");
        assertNotNull(blobProvider);

        String test_key = "blobby2";
        int numberOfRecords = 2;
        List<ExportRecord> records = new ArrayList<>();

        for (int i = 0; i < numberOfRecords; ++i) {
            BlobTextFromDocument blobTextFromDoc = blobTestPdf(blobManager);
            blobTextFromDoc.getProperties().put("ecm:primaryType", "TextPDF");
            byte[] bytes = MAPPER.writeValueAsBytes(blobTextFromDoc);
            records.add(ExportRecord.of(test_key, test_key, bytes));
        }

        writer.write(records);
        assertTrue(writer.exists(test_key));
        Optional<Blob> blobWritten = writer.complete(test_key);
        assertTrue(blobWritten.isPresent());
        Blob blob = blobWritten.get();
        // System.out.println("File: " + blob.getFile().getAbsolutePath());
        DataInput input = new DataInputStream(new FileInputStream(blob.getFile()));
        TFRecordReader tfRecordReader = new TFRecordReader(input, true);
        byte[] exampleData;
        while ((exampleData = tfRecordReader.read()) != null) {
            TFRecord tfRecord = TFRecord.from(exampleData);
            assertEquals(2, tfRecord.getFeatures().getFeatureCount());
            Feature textBlob = tfRecord.getFeatures().getFeatureMap().get(FILE_CONTENT);
            File txtFile = Framework.createTempFile("tf_Text", "txt");
            try (FileOutputStream fos = new FileOutputStream(txtFile)) {
                fos.write(textBlob.getBytesList().getValue(0).toByteArray());
                assertTrue(txtFile.length() > 0);
                String content = new String(textBlob.getBytesList().getValue(0).toByteArray());
                assertTrue(content.startsWith("See discussions,"));
            }
        }
    }

    @Test
    public void testMissing() throws IOException {
        AbstractRecordWriter writer = (AbstractRecordWriter) aiComponent.getRecordWriter("ai/validation");
        assertNotNull(writer);
        assertFalse(writer.complete("madeup").isPresent());

        File newFile = writer.getFile("notexisting");
        assertNotNull(newFile);

        File reuseFile = writer.getFile("notexisting");
        assertEquals(newFile, reuseFile);
    }

    @Test(expected = NuxeoException.class)
    public void testBadDescriptor() {
        RecordWriterDescriptor descriptor = new RecordWriterDescriptor();
        descriptor.writer = BadRecordWriter.class;
        descriptor.getWriter("mywriter");
    }
}
