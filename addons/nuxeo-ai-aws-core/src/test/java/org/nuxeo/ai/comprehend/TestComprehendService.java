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
package org.nuxeo.ai.comprehend;

import com.amazonaws.services.comprehend.model.DetectEntitiesResult;
import com.amazonaws.services.comprehend.model.DetectKeyPhrasesResult;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.amazonaws.services.comprehend.model.SentimentScore;
import com.amazonaws.services.comprehend.model.SentimentType;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.enrichment.SentimentEnrichmentProvider;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import java.io.IOException;
import java.util.Collection;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.ai.aws.aws-core" })
public class TestComprehendService {

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testSentiment() {
        AWS.assumeCredentials();
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("aws.textSentiment");
        assertNotNull(service);
        DetectSentimentResult results = Framework.getService(ComprehendService.class)
                                                 .detectSentiment("I am happy", "en");
        assertNotNull(results);
        assertTrue(SentimentType.POSITIVE.toString().equals(results.getSentiment()));

        BlobTextFromDocument textStream = new BlobTextFromDocument();
        textStream.setId("docId");
        textStream.setRepositoryName("test");
        textStream.addProperty("dc:title", "I am very disappointed");
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(textStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata result = metadataCollection.iterator().next();
        assertEquals(SentimentType.NEGATIVE.toString(), result.getLabels().get(0).getValues().get(0).getName());
        textStream.addProperty("dc:title", "A car");
        metadataCollection = service.enrich(textStream);
        result = metadataCollection.iterator().next();
        assertEquals(SentimentType.NEUTRAL.toString(), result.getLabels().get(0).getValues().get(0).getName());
    }

    @Test
    public void testExtractKeyphrase() {
        AWS.assumeCredentials();
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("aws.textKeyphrase");
        assertNotNull(service);

        DetectKeyPhrasesResult results = Framework.getService(ComprehendService.class)
                                                  .extractKeyphrase("power and convenience", "en");
        assertNotNull(results);
        assertThat(results.getKeyPhrases()).isNotEmpty();

        BlobTextFromDocument textStream = new BlobTextFromDocument();
        textStream.setId("docId");
        textStream.setRepositoryName("test");
        textStream.addProperty("dc:title", "power and convenience");
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(textStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata result = metadataCollection.iterator().next();
        assertEquals("power", result.getLabels().get(0).getValues().get(0).getName());
        textStream.addProperty("dc:title", "Instagram and Facebook");
        metadataCollection = service.enrich(textStream);
        result = metadataCollection.iterator().next();
        assertEquals("Instagram", result.getLabels().get(0).getValues().get(0).getName());
    }

    @Test
    public void testTextEntities() {
        AWS.assumeCredentials();
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("aws.textEntities");
        assertNotNull(service);

        DetectEntitiesResult results = Framework.getService(ComprehendService.class)
                                                .detectEntities("One of the Nuxeo headquarters located in Paris", "en");
        assertNotNull(results);
        assertThat(results.getEntities()).isNotEmpty();

        BlobTextFromDocument textStream = new BlobTextFromDocument();
        textStream.setId("docId");
        textStream.setRepositoryName("test");
        textStream.addProperty("dc:title", "Another one in London and one more in New York");
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(textStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata result = metadataCollection.iterator().next();
        assertEquals("London", result.getLabels().get(0).getValues().get(0).getName());
        textStream.addProperty("dc:title", "Nuxeo is a young and promising company");
        metadataCollection = service.enrich(textStream);
        result = metadataCollection.iterator().next();
        assertEquals("Nuxeo", result.getLabels().get(0).getValues().get(0).getName());
    }

    @Test
    public void testGetLabel() throws IOException {
        AWS.assumeCredentials();
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("aws.textSentiment");
        SentimentEnrichmentProvider sentimentService = (SentimentEnrichmentProvider) service;
        try {
            sentimentService.getSentimentLabel(new DetectSentimentResult().withSentiment("snowy"));
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("java.lang.IllegalArgumentException: No enum constant"));
            assertTrue(e.getMessage().contains("SentimentType.SNOWY"));
        }

        try {
            sentimentService.getSentimentLabel(new DetectSentimentResult().withSentiment("negative")
                                                                          .withSentimentScore(
                                                                                  new SentimentScore().withMixed(
                                                                                          0.3f)));
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("A NEGATIVE sentiment has been returned without any confidence score"));
        }
    }

}
