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
package org.nuxeo.ai;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.amazonaws.services.translate.model.UnsupportedLanguagePairException;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.aws.aws-core", "org.nuxeo.ai.aws.aws-core:OSGI-INF/translate-test.xml"})
public class TestTranslateService {

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testTranslate() {

        AWS.assumeCredentials();

        assertTranslation("aws.translate.en_es", "I am very disappointed", "Estoy muy decepcionado");
        assertTranslation("aws.translate.en_pt", "I am very happy", "Estou muito feliz");
        assertTranslation("aws.translate.en_fr", "I am very happy", "Je suis très ");
        try {
            assertTranslation("aws.translate.pt_unknown", "hoje faz bom tempo", "");
            fail();
        } catch (UnsupportedLanguagePairException e) {
            assertNotNull(e);
        }
    }

    protected void assertTranslation(String serviceName, String sourceText, String translatedText) {
        EnrichmentProvider service = aiComponent.getEnrichmentProvider(serviceName);
        assertNotNull(service);
        BlobTextFromDocument textStream = new BlobTextFromDocument();
        textStream.setId("docId");
        textStream.setRepositoryName("test");
        textStream.addProperty("my:text", sourceText);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(textStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata result = metadataCollection.iterator().next();
        assertEquals(1, result.getLabels().size());
        assertThat(result.getLabels().get(0).getValues().get(0).getName()).contains(translatedText);
    }
}
