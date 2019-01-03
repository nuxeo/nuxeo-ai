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
 *     Pedro Cardoso
 */
package org.nuxeo.ai.model;

/**
 * Simple class to store constants related to AI Document Types: Ai_Model and Ai_Corpus
 */
public class AiDocumentTypeConstants {

    // corpus type constants
    public static final String CORPUS_TYPE = "AI_Corpus";

    public static final String CORPUS_JOBID = "ai_corpus:job_id";

    public static final String CORPUS_QUERY = "ai_corpus:query";

    public static final String CORPUS_SPLIT = "ai_corpus:split";

    public static final String CORPUS_DOCUMENTS_COUNT = "ai_corpus:documents_count";

    public static final String CORPUS_INPUTS = "ai_corpus:inputs";

    public static final String CORPUS_OUTPUTS = "ai_corpus:outputs";

    public static final String CORPUS_TRAINING_DATA = "ai_corpus:training_data";

    public static final String CORPUS_EVALUATION_DATA = "ai_corpus:evaluation_data";

    public static final String CORPUS_STATS = "ai_corpus:statistics";

    private AiDocumentTypeConstants() {
        // just Constants
    }
}
