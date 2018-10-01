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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ai.pipes.types.BlobTextStream;

/**
 * Reverse a text field and add it to the raw response
 */
public class TextReversingEnrichmentService extends AbstractEnrichmentService {

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextStream blobTextStream) {
        String reversedText = StringUtils.reverse(blobTextStream.getProperties().values().iterator().next());
        List<EnrichmentMetadata.Label> labels = Stream.of(reversedText)
                                                      .map(l -> new EnrichmentMetadata.Label(l, 1))
                                                      .collect(Collectors.toList());

        String rawKey = saveJsonAsRawBlob(reversedText);
        //Return 2 records
        return Arrays.asList(
                new EnrichmentMetadata.Builder("/classification/custom",
                                               name,
                                               blobTextStream)
                    .withLabels(labels)
                    .withRawKey(rawKey)
                    .withCreator(SecurityConstants.SYSTEM_USERNAME).build(),
                new EnrichmentMetadata.Builder("/classification/custom",
                                               name,
                                               blobTextStream)
                    .withLabels(labels)
                    .withRawKey(rawKey)
                    .withDocumentProperties(Stream.of("dc:description").collect(Collectors.toSet()))
                    .withCreator(SecurityConstants.SYSTEM_USERNAME).build()
        );
    }
}
