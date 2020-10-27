/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.gcp;

import java.io.IOException;

import com.google.cloud.vision.v1.ImageAnnotatorClient;

/**
 * AI Google Service
 */
public interface AIGoogleService {

    /**
     * @return {@link ImageAnnotatorClient} that conforms to {@link AutoCloseable} and must be used within
     *         try-with-resources or manually closed
     * @throws IOException in case Credentials weren't available in the system
     */
    ImageAnnotatorClient getOrCreateClient() throws IOException;

}
