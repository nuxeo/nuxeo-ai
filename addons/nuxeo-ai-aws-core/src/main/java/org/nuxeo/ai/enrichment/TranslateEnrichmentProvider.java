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
package org.nuxeo.ai.enrichment;

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingProperties;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.translate.TranslateService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.translate.model.TranslateTextResult;

/**
 * An enrichment provider using AWS Translate.
 */
public class TranslateEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String SOURCE_LANGUAGE_CODE = "sourceLanguage";

    public static final String TARGET_LANGUAGE_CODE = "targetLanguage";

    public static final String DEFAULT_SOURCE_LANGUAGE = "en";

    public static final String DEFAULT_TARGET_LANGUAGE = "es";

    public static final float CONFIDENCE = 0.95F;

    protected String sourceLanguageCode;

    protected String targetLanguageCode;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        sourceLanguageCode = descriptor.options.getOrDefault(SOURCE_LANGUAGE_CODE, DEFAULT_SOURCE_LANGUAGE);
        targetLanguageCode = descriptor.options.getOrDefault(TARGET_LANGUAGE_CODE, DEFAULT_TARGET_LANGUAGE);
    }

    @Override
    public Collection<AIMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        try {
            return Collections.singletonList(translate(blobTextFromDoc));
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    /**
     * Translate the specified text properties using the blobTextFromDoc for context info.
     */
    protected AIMetadata translate(BlobTextFromDocument blobTextFromDoc) throws IOException {

        List<EnrichmentMetadata.Label> labels = new ArrayList<>();
        List<String> raw = new ArrayList<>();

        for (Map.Entry<String, String> textEntry : blobTextFromDoc.getProperties().entrySet()) {
            try {
                TranslateTextResult result = Framework.getService(TranslateService.class)
                                                      .translateText(textEntry.getValue(), sourceLanguageCode,
                                                              targetLanguageCode);
                if (result != null && StringUtils.isNotEmpty(result.getTranslatedText())) {
                    labels.add(new AIMetadata.Label(result.getTranslatedText(), CONFIDENCE));
                    raw.add(processRaw(textEntry.getValue(), result));
                }
            } catch (AmazonServiceException e) {
                throw AWSHelper.isFatal(e) ? new FatalEnrichmentError(e) : e;
            }
        }

        EnrichmentMetadata.Builder builder = new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc);
        if (!raw.isEmpty()) {
            String rawKey = saveJsonAsRawBlob(MAPPER.writeValueAsString(raw));
            builder.withRawKey(rawKey);
        }

        return builder.withLabels(asLabels(labels)).build();
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingProperties(blobTextFromDoc, name);
    }

    /**
     * Processes the result of the call to AWS returning raw json
     */
    protected String processRaw(String text, TranslateTextResult result) {
        return toJsonString(jg -> {
            jg.writeObjectField("source", result.getSourceLanguageCode());
            jg.writeObjectField("target", result.getTargetLanguageCode());
            jg.writeStringField("text", text);
            jg.writeStringField("translated", result.getTranslatedText());
        });
    }

}
