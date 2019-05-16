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

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;

public class BasicEnrichmentService extends AbstractEnrichmentService implements EnrichmentCachable {

    protected List<EnrichmentMetadata.Label> labels = new ArrayList<>();

    protected List<EnrichmentMetadata.Tag> tags = new ArrayList<>();

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        String labelsList = descriptor.options.getOrDefault("labels", "king,kong");
        if (StringUtils.isNotBlank(labelsList)) {
            String[] theLabels = labelsList.split(",");
            labels = Arrays.stream(theLabels).map(l -> new EnrichmentMetadata.Label(l, 0.8f))
                           .collect(Collectors.toList());
            tags = Collections
                    .singletonList(new EnrichmentMetadata.Tag(name, "/classification/custom", null, null, labels, 0.75f));
        }
    }

    @Override
    public Collection<AIMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return Collections.singletonList(
                new EnrichmentMetadata.Builder("/classification/custom",
                                               name,
                                               blobTextFromDoc)
                        .withLabels(labels)
                        .withTags(tags)
                        .build());
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, "basic");
    }
}
