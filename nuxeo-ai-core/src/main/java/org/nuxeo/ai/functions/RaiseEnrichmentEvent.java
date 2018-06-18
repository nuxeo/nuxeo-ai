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
package org.nuxeo.ai.functions;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.runtime.api.Framework;

/**
 * Raises an event when new enrichment data is added
 */
public class RaiseEnrichmentEvent extends AbstractEnrichmentConsumer {

    public static final String ENRICHMENT_CREATED = "enrichmentMetadataCreated";
    public static final String ENRICHMENT_METADATA = "enrichmentMetadata";

    @Override
    public void accept(EnrichmentMetadata metadata) {
        EventContextImpl eCtx = new EventContextImpl();
        eCtx.setProperty(ENRICHMENT_METADATA, metadata);
        Event event = eCtx.newEvent(ENRICHMENT_CREATED);
        Framework.getService(EventProducer.class).fireEvent(event);
    }
}
