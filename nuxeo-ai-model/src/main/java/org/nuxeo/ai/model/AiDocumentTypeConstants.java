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

    public static final String CORPUS_SCHEMA = "ai_corpus";

    public static final String CORPUS_MODEL_ID = CORPUS_SCHEMA + ":model_id";

    public static final String CORPUS_MODEL_NAME = CORPUS_SCHEMA + ":model_name";

    public static final String CORPUS_MODEL_START_DATE = CORPUS_SCHEMA + ":model_start_date";

    public static final String CORPUS_MODEL_END_DATE = CORPUS_SCHEMA + ":model_end_date";

    public static final String CORPUS_JOBID = CORPUS_SCHEMA + ":job_id";

    public static final String CORPUS_QUERY = CORPUS_SCHEMA + ":query";

    public static final String CORPUS_SPLIT = CORPUS_SCHEMA + ":split";

    public static final String CORPUS_DOCUMENTS_COUNT = CORPUS_SCHEMA + ":documents_count";

    public static final String CORPUS_INPUTS = CORPUS_SCHEMA + ":inputs";

    public static final String CORPUS_OUTPUTS = CORPUS_SCHEMA + ":outputs";

    public static final String CORPUS_TRAINING_DATA = CORPUS_SCHEMA + ":training_data";

    public static final String CORPUS_EVALUATION_DATA = CORPUS_SCHEMA + ":evaluation_data";

    public static final String CORPUS_STATS = CORPUS_SCHEMA + ":statistics";

    private AiDocumentTypeConstants() {
        // just Constants
    }
}
