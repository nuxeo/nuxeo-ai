/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */
package org.nuxeo.ai.enrichment;

import com.amazonaws.services.comprehend.model.DetectEntitiesResult;
import net.jodah.failsafe.RetryPolicy;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.comprehend.ComprehendService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.runtime.api.Framework;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingProperties;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

public class TextEntitiesProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String LANGUAGE_CODE = "language";

    public static final String DEFAULT_LANGUAGE = "en";

    public static final String ENTITIES_KEY = "Entities";

    protected String languageCode;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        languageCode = descriptor.options.getOrDefault(LANGUAGE_CODE, DEFAULT_LANGUAGE);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<String, String> prop : blobTextFromDoc.getProperties().entrySet()) {
                DetectEntitiesResult result = Framework.getService(ComprehendService.class)
                                                       .detectEntities(prop.getValue(), languageCode);
                if (result != null && !result.getEntities().isEmpty()) {
                    enriched.addAll(processResult(blobTextFromDoc, prop.getKey(), result));
                }
            }
            return enriched;
        });
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument doc, String xPath,
            DetectEntitiesResult result) {
        List<AIMetadata.Label> labels = result.getEntities()
                                              .stream()
                                              .map(kp -> new AIMetadata.Label(kp.getText(), kp.getScore()))
                                              .collect(Collectors.toList());
        String raw = toJsonString(jg -> {
            jg.writeObjectField(ENTITIES_KEY, result.getEntities());
        });

        String rawKey = saveJsonAsRawBlob(raw);
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder(kind, name, doc).withLabels(asLabels(labels))
                                                                                     .withRawKey(rawKey)
                                                                                     .withDocumentProperties(
                                                                                             Collections.singleton(
                                                                                                     xPath))
                                                                                     .build();
        return Collections.singletonList(metadata);
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy()
                    .abortOn(throwable -> throwable.getMessage().contains("is not authorized to perform"));
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingProperties(blobTextFromDoc, name);
    }
}
