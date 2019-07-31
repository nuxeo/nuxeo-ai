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
 * Simple class to store constants related to AI Document Types: Ai_Model and DatasetExport
 */
public class AiDocumentTypeConstants {

    // corpus type constants
    public static final String DATASET_EXPORT_TYPE = "DatasetExport";

    public static final String DATASET_EXPORT_SCHEMA = "dataset_export";

    public static final String DATASET_EXPORT_MODEL_ID = DATASET_EXPORT_SCHEMA + ":model_id";

    public static final String DATASET_EXPORT_MODEL_NAME = DATASET_EXPORT_SCHEMA + ":model_name";

    public static final String DATASET_EXPORT_MODEL_START_DATE = DATASET_EXPORT_SCHEMA + ":model_start_date";

    public static final String DATASET_EXPORT_MODEL_END_DATE = DATASET_EXPORT_SCHEMA + ":model_end_date";

    public static final String DATASET_EXPORT_JOB_ID = DATASET_EXPORT_SCHEMA + ":job_id";

    public static final String DATASET_EXPORT_QUERY = DATASET_EXPORT_SCHEMA + ":query";

    public static final String DATASET_EXPORT_SPLIT = DATASET_EXPORT_SCHEMA + ":split";

    public static final String DATASET_EXPORT_DOCUMENTS_COUNT = DATASET_EXPORT_SCHEMA + ":documents_count";

    public static final String DATASET_EXPORT_INPUTS = DATASET_EXPORT_SCHEMA + ":inputs";

    public static final String DATASET_EXPORT_OUTPUTS = DATASET_EXPORT_SCHEMA + ":outputs";

    public static final String DATASET_EXPORT_TRAINING_DATA = DATASET_EXPORT_SCHEMA + ":training_data";

    public static final String DATASET_EXPORT_EVALUATION_DATA = DATASET_EXPORT_SCHEMA + ":evaluation_data";

    public static final String DATASET_EXPORT_STATS = DATASET_EXPORT_SCHEMA + ":statistics";

    private AiDocumentTypeConstants() {
        // just Constants
    }
}
