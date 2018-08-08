package org.nuxeo.ai.custom;

public class AiDocumentTypeConstants {
    // model type constants
    public static final String MODEL_NAME = "ai_model:name";
    public static final String MODEL_ACCURACY = "ai_model:result_accuracy";
    public static final String MODEL_INPUTS = "ai_model:inputs";
    public static final String MODEL_OUTPUTS = "ai_model:outputs";
    public static final String MODEL_TRAINING_DATA = "ai_model:training_data";
    public static final String MODEL_EVALUATION_DATA = "ai_model:evaluation_data";

    // corpus type constants
    public static final String CORPUS_DATA_LOCATION = "ai_corpus:data_location";
    public static final String CORPUS_DOCUMENTS_COUNT = "ai_corpus:documents_count";
    public static final String CORPUS_TRAINING_DATA = "ai_corpus:training_data";
    public static final String CORPUS_INPUT_FIELDS = "ai_corpus:inputs";
    public static final String CORPUS_OUTPUT_FIELDS = "ai_corpus:outputs";
    public static final String CORPUS_INPUT_HISTOGRAM = "ai_corpus:inputs_histogram";
    public static final String CORPUS_OUTPUT_HISTOGRAM = "ai_corpus:outputs_histogram";

    private AiDocumentTypeConstants() {
        // just Constants
    }
}
