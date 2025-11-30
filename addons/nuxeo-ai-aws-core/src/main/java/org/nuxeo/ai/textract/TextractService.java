/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.textract;

import java.util.Collections;
import java.util.List;

import org.nuxeo.ai.aws.dto.DocumentAnalysisResult;
import org.nuxeo.ecm.core.blob.ManagedBlob;

/**
 * Works with AWS Textract - Now using domain DTOs instead of AWS SDK models This interface is completely independent of
 * AWS SDK implementation details
 *
 * @since 2.1.2
 */
public interface TextractService {

    /**
     * Detect text for the provided blob
     */
    DocumentAnalysisResult detectText(ManagedBlob blob);

    /**
     * Analyzes the provided blob as a text document
     */
    DocumentAnalysisResult analyzeDocument(ManagedBlob blob, String... features);

    /**
     * Process blocks using the provided processor
     */
    <T> List<T> processBlocks(DocumentAnalysisResult result, TextractProcessor<T> processor);

    /** Retrieve registered processors by name (backward compatibility). */
    default List<TextractProcessor> getProcessors(String name) {
        return Collections.emptyList();
    }
}
