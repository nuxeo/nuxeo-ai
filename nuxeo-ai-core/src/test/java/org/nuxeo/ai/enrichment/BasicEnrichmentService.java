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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

public class BasicEnrichmentService extends AbstractEnrichmentService {

    private List<EnrichmentMetadata.Label> labels = new ArrayList<>();

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        String labelsList = descriptor.options.getOrDefault("labels", "king,kong");
        if (StringUtils.isNotBlank(labelsList)) {
            String[] theLabels = labelsList.split(",");
            labels = Arrays.stream(theLabels).map(l -> new EnrichmentMetadata.Label(l, 0.8f))
                           .collect(Collectors.toList());
        }
    }

    @Override
    public EnrichmentMetadata enrich(BlobTextStream blobTextStream) {
        return new EnrichmentMetadata.Builder("BasicEnrichment", modelVersion, blobTextStream.getId())
                .withLabels(labels).build();
    }
}
