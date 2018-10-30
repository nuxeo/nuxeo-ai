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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.logs.model.UnrecognizedClientException;
import com.amazonaws.services.rekognition.model.AccessDeniedException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared helper methods for enrichment.
 */
public class EnrichmentHelper {

    public static final Set<String> FATAL_ERRORS = new HashSet<>(Arrays.asList(
            UnrecognizedClientException.class.getSimpleName(),
            AccessDeniedException.class.getSimpleName()));

    private EnrichmentHelper() {
    }

    /**
     * Is this exception unrecoverable?
     */
    public static boolean isFatal(AmazonServiceException e) {
        return FATAL_ERRORS.contains(e.getErrorCode());
    }
}
