/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction;

import org.nuxeo.ai.aws.abstraction.dto.TextractRequest;
import org.nuxeo.ai.aws.dto.TextractResult;

/**
 * AWS SDK-independent facade for Textract operations.
 */
public interface TextractServiceFacade {

    /**
     * Detect text in document
     */
    TextractResult detectDocumentText(TextractRequest.DetectDocumentText request);

    /**
     * Analyze document with specified features
     */
    TextractResult analyzeDocument(TextractRequest.AnalyzeDocument request);
}
