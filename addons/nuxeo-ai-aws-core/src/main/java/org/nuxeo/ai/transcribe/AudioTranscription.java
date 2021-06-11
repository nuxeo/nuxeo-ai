/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.transcribe;

import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A POJO for marshaling response from Amazon Transcribe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AudioTranscription {

    protected String jobName;

    protected String accountId;

    protected Result results;

    protected String status;

    public AudioTranscription(@JsonProperty("jobName") String jobName, @JsonProperty("accountId") String accountId,
            @JsonProperty("results") Result results, @JsonProperty("status") String status) {
        this.jobName = jobName;
        this.accountId = accountId;
        this.results = results;
        this.status = status;
    }

    public List<String> getTranscripts() {
        return results.transcripts.stream().map(t -> t.transcript).collect(Collectors.toList());
    }

    public Result getResults() {
        return results;
    }

    public enum Type {
        PRONUNCIATION, PUNCTUATION;

        public static Type of(String name) {
            return Type.valueOf(name.toUpperCase());
        }

        public String realName() {
            return super.name().toLowerCase();
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        protected String languageCode;

        protected List<Transcript> transcripts;

        protected List<Item> items;

        public Result(@JsonProperty("language_code") String languageCode,
                @JsonProperty("transcripts") List<Transcript> transcripts, @JsonProperty("items") List<Item> items) {
            this.languageCode = languageCode;
            this.transcripts = transcripts;
            this.items = items;
        }

        public String getLanguageCode() {
            return languageCode;
        }

        public List<Transcript> getTranscripts() {
            return transcripts;
        }

        public List<Item> getItems() {
            return items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Alternative {

        protected double confidence;

        protected String content;

        public Alternative(@JsonProperty("confidence") double confidence, @JsonProperty("content") String content) {
            this.confidence = confidence;
            this.content = content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        protected String startTime;

        protected String endTime;

        protected List<Alternative> alternatives;

        protected String type;

        public Item(@JsonProperty("start_time") String startTime, @JsonProperty("end_time") String endTime,
                @JsonProperty("alternatives") List<Alternative> alternatives, @JsonProperty("type") String type) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.alternatives = alternatives;
            this.type = type;
        }

        /**
         * @return content of highest confidence {@link Alternative}
         */
        public String getContent() {
            return alternatives.isEmpty() ? "" : alternatives.get(0).content;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public List<Alternative> getAlternatives() {
            return alternatives;
        }

        public Type getType() {
            return Type.of(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transcript {

        protected String transcript;

        public Transcript(@JsonProperty("transcript") String transcript) {
            this.transcript = transcript;
        }
    }
}
