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

import java.util.List;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;

/**
 * Works with AWS Textract
 *
 * @since 2.1.2
 */
public interface TextractService {

    /**
     * Detect text for the provided blob
     */
    DetectDocumentTextResult detectText(ManagedBlob blob);

    /**
     * Analyzes the provided blob as a text document
     */
    AnalyzeDocumentResult analyzeDocument(ManagedBlob blob, String... features);

    /**
     * Return any processors that act on the specified service
     */
    List<TextractProcessor> getProcessors(String serviceName);

}
