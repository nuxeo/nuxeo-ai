/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.transcribe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.enrichment.EnrichmentProvider.UNSET;
import static org.nuxeo.ai.enrichment.async.TranscribeEnrichmentProvider.PROVIDER_KIND;
import static org.nuxeo.ai.enrichment.async.TranscribeEnrichmentProvider.PROVIDER_NAME;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, EnrichmentTestFeature.class })
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.runtime.aws")
public class TestTranscribeService {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected static final String json = "{\n" +//
            "    \"jobName\": \"EnUS_033178297efa75a1b0add2acaea8639e\",\n" +//
            "    \"accountId\": \"783725821734\",\n" + //
            "    \"results\": {\n" +//
            "        \"language_code\": \"en-US\",\n" + //
            "        \"transcripts\": [\n" +//
            "            {\n" +//
            "                \"transcript\": \"this guy. I really familiar. He was a long time.\",\n" +//
            "                \"language_identification\": [{\"score\":\"0.829\", \"ode\":\"es-ES\"},{\"score\":\"0.0729\",\"code\":\"it-IT\"}]\n" +//
            "            }\n" +//
            "        ],\n" +//
            "        \"items\": [\n" +//
            "            {\n" +//
            "                \"start_time\": \"0.62\",\n" +//
            "                \"end_time\": \"0.88\",\n" +//
            "                \"alternatives\": [\n" +//
            "                    {\n" +//
            "                        \"confidence\": \"0.9542\",\n" +//
            "                        \"content\": \"this\"\n" +//
            "                    }\n" +//
            "                ],\n" +//
            "                \"type\": \"pronunciation\"\n" +//
            "            },\n" +//
            "            {\n" +//
            "                \"start_time\": \"0.88\",\n" +//
            "                \"end_time\": \"1.32\",\n" +//
            "                \"alternatives\": [\n" +//
            "                    {\n" +//
            "                        \"confidence\": \"0.956\",\n" +//
            "                        \"content\": \"guy\"\n" +//
            "                    }\n" +//
            "                ],\n" +//
            "                \"type\": \"pronunciation\"\n" +//
            "            }\n" +//
            "        ]\n" +//
            "    },\n" +//
            "    \"status\": \"COMPLETED\"\n" +//
            "}";

    @Inject
    protected AIComponent ai;

    @Inject
    protected CoreSession session;

    @Inject
    protected DocMetadataService dms;

    @Inject
    protected TransactionalFeature txf;

    @Inject
    protected BlobManager blobManager;

    @Inject
    protected TranscribeService ts;

    @Test
    public void shouldTranscribeVideo() throws IOException {
        AudioTranscription transcription = OBJECT_MAPPER.readValue(json, AudioTranscription.class);
        assertNotNull(transcription);
        assertEquals(2, transcription.results.items.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldAddTranscribeAsEnrichment() throws IOException {
        EnrichmentProvider provider = ai.getEnrichmentProvider("aws.transcribe");
        assertNotNull(provider);

        AudioTranscription transcription = OBJECT_MAPPER.readValue(json, AudioTranscription.class);
        assertNotNull(transcription);
        List<AIMetadata.Label> labels = ts.asLabels(transcription);

        DocumentModel doc = session.createDocumentModel("/", "Test File", "File");
        doc.addFacet(ENRICHMENT_FACET);
        doc = session.createDocument(doc);
        session.save();

        String rawKey = EnrichmentUtils.saveRawBlob(Blobs.createJSONBlob(json), "default");
        BlobTextFromDocument btfd = new BlobTextFromDocument(doc);

        BlobProvider blobProvider = blobManager.getBlobProvider("test");
        Blob blob = Blobs.createBlob("A string blob here");
        String blobKey = blobProvider.writeBlob(blob);
        ManagedBlob managedBlob = new BlobMetaImpl("test", blob.getMimeType(), blobKey, blobKey, blob.getEncoding(),
                blob.getLength());
        btfd.addBlob("file:content", "img", managedBlob);
        AIMetadata metadata = new EnrichmentMetadata.Builder(PROVIDER_KIND, PROVIDER_NAME,
                new BlobTextFromDocument(doc)).withLabels(
                Collections.singletonList(new LabelSuggestion(UNSET + PROVIDER_NAME, labels)))
                                              .withRawKey(rawKey)
                                              .build();

        doc = dms.saveEnrichment(session, (EnrichmentMetadata) metadata);
        doc = session.saveDocument(doc);
        session.save();

        txf.nextTransaction();

        doc = session.getDocument(doc.getRef());

        List<Object> items = (List<Object>) doc.getPropertyValue("enrichment:items");
        assertThat(items).isNotEmpty().hasSize(1);

        Map<String, Serializable> map = (Map<String, Serializable>) items.get(0);
        List<Serializable> suggestions = (List<Serializable>) map.get("suggestions");
        assertThat(suggestions).isNotEmpty().hasSize(1);
        List<Serializable> sLabels = (List<Serializable>) ((Map<String, Serializable>) suggestions.get(0)).get(
                "labels");
        assertThat(sLabels).hasSize(2);
    }
}
