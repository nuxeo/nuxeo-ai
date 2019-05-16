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

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;

import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;

import net.jodah.failsafe.RetryPolicy;

/**
 * An enricher that throws errors
 */
public class ErroringEnrichmentService extends AbstractEnrichmentService {

    private RuntimeException exception;

    private int numFailures = 1;

    private int numRetries = 0;

    private int attempts = 0;

    public ErroringEnrichmentService() {
    }

    public ErroringEnrichmentService(RuntimeException exception, int numFailures, int numRetries) {
        super();
        this.exception = exception;
        this.numFailures = numFailures;
        this.numRetries = numRetries;
        this.maxSize = EnrichmentDescriptor.DEFAULT_MAX_SIZE;
        this.name = "EnRich1";
    }

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        this.numFailures = Integer.parseInt(descriptor.options.get("failures"));
        this.numRetries = Integer.parseInt(descriptor.options.get("retries"));
        try {
            String exceptionClass = descriptor.options.get("exception");
            exception = (RuntimeException)
                    Class.forName(exceptionClass).getDeclaredConstructor(String.class)
                         .newInstance(String.format("Deliberate Enrichment error for %s, retries %s. failures %s.",
                                                    descriptor.name, numFailures, numRetries));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                | ClassNotFoundException | NoSuchMethodException e) {
            throw new NuxeoException("Failed to configure ErroringEnrichmentService", e);
        }
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().withMaxRetries(numRetries);
    }

    @Override
    public Collection<AIMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        if (++attempts <= numFailures) {
            throw exception;
        }
        return Collections.singletonList(
                new EnrichmentMetadata.Builder("test",
                                               name,
                                               blobTextFromDoc).build());
    }
}
