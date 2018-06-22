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

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.enrichment.SentimentEnrichmentService;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.amazonaws.services.comprehend.model.SentimentScore;
import com.amazonaws.services.comprehend.model.SentimentType;
import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.aws.aws-core"})
public class TestComprehendService {

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testSentiment() {

        EnrichmentService service = aiComponent.getEnrichmentService("aws.sentiment");
        assertNotNull(service);
        DetectSentimentResult results = Framework.getService(ComprehendService.class)
                                                 .detectSentiment("I am happy", "en");
        assertNotNull(results);
        assertTrue(SentimentType.POSITIVE.toString().equals(results.getSentiment()));

        BlobTextStream textStream = new BlobTextStream();
        textStream.setId("docId");
        textStream.setRepositoryName("test");
        textStream.setText("I am very disappointed");
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(textStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata result = metadataCollection.iterator().next();
        assertTrue(result.isSingleLabel());
        assertEquals(SentimentType.NEGATIVE.toString(), result.getLabels().get(0).getName());
        textStream.setText("A car");
        metadataCollection = service.enrich(textStream);
        result = metadataCollection.iterator().next();
        assertEquals(SentimentType.NEUTRAL.toString(), result.getLabels().get(0).getName());
    }

    @Test
    public void testGetLabel() throws IOException {

        EnrichmentService service = aiComponent.getEnrichmentService("aws.sentiment");
        SentimentEnrichmentService sentimentService = (SentimentEnrichmentService) service;
        try {
            sentimentService.getSentimentLabel(new DetectSentimentResult().withSentiment("snowy"));
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("java.lang.IllegalArgumentException: No enum constant"));
            assertTrue(e.getMessage().contains("SentimentType.SNOWY"));
        }

        try {
            sentimentService.getSentimentLabel(new DetectSentimentResult()
                                                       .withSentiment("negative")
                                                       .withSentimentScore(new SentimentScore().withMixed(0.3f)));
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("A NEGATIVE sentiment has been returned without any confidence score"));
        }
    }


}
