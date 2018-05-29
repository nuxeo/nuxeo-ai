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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

/**
 * Reverse a text field and add it to the raw response
 */
public class TextReversingEnrichmentService extends AbstractEnrichmentService {

    @Override
    public EnrichmentMetadata enrich(BlobTextStream blobTextStream) {
        String reversedText = StringUtils.reverse(blobTextStream.getText());
        List<EnrichmentMetadata.Label> labels = Stream.of(reversedText)
                                                      .map(l -> new EnrichmentMetadata.Label(l, 1))
                                                      .collect(Collectors.toList());
        return new EnrichmentMetadata.Builder( "/classification/custom", name, "test", blobTextStream.getId())
                .withRawKey(saveJsonAsRawBlob(reversedText, "test"))
                .withLabels(labels)
                .withCreator(SecurityConstants.SYSTEM_USERNAME)
                .withTargetDocumentProperties(blobTextStream.getXPaths()).build();
    }
}
