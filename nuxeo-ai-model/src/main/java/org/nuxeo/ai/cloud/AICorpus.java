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
package org.nuxeo.ai.cloud;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A POJO for AI_Corpus document in AI cloud
 */
public class AICorpus {

    protected static final String entity = "document";

    protected static final String type = "AI_Corpus";

    @JsonProperty("name")
    protected String name;

    @JsonProperty("properties")
    protected Properties props;

    public AICorpus(@JsonProperty("name") String name, @JsonProperty("properties") Properties props) {
        this.name = name;
        this.props = props;
    }

    @JsonProperty("entity-type")
    public String getEntity() {
        return entity;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Properties getProps() {
        return props;
    }

    public void setProps(Properties props) {
        this.props = props;
    }

    public static final class Properties {

        @JsonProperty("dc:title")
        protected String title;

        @JsonProperty("ai_corpus:documents_count")
        protected long docCount;

        @JsonProperty("ai_corpus:evaluation_documents_count")
        protected long evaluationDocCount;

        @JsonProperty("ai_corpus:query")
        protected String query;

        @JsonProperty("ai_corpus:split")
        protected int split;

        @JsonProperty("ai_corpus:fields")
        protected List<Map<String, Object>> fields;

        @JsonProperty("ai_corpus:training_data")
        protected Batch trainData;

        @JsonProperty("ai_corpus:evaluation_data")
        protected Batch evalData;

        @JsonProperty("ai_corpus:statistics")
        protected Batch stats;

        @JsonProperty("ai_corpus:import_info")
        protected Info info;

        public Properties() {}

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public long getDocCount() {
            return docCount;
        }

        public void setDocCount(long docCount) {
            this.docCount = docCount;
        }

        public long getEvaluationDocCount() {
            return evaluationDocCount;
        }

        public void setEvaluationDocCount(long evaluationDocCount) {
            this.evaluationDocCount = evaluationDocCount;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public int getSplit() {
            return split;
        }

        public void setSplit(int split) {
            this.split = split;
        }

        public List<Map<String, Object>> getFields() {
            return fields;
        }

        public void setFields(List<Map<String, Object>> fields) {
            this.fields = fields;
        }

        public Batch getTrainData() {
            return trainData;
        }

        public void setTrainData(Batch trainData) {
            this.trainData = trainData;
        }

        public Batch getEvalData() {
            return evalData;
        }

        public void setEvalData(Batch evalData) {
            this.evalData = evalData;
        }

        public Batch getStats() {
            return stats;
        }

        public void setStats(Batch stats) {
            this.stats = stats;
        }

        public Info getInfo() {
            return info;
        }

        public void setInfo(Info info) {
            this.info = info;
        }
    }

    public static final class Batch {

        @JsonProperty("upload-fileId")
        protected String fileId;

        @JsonProperty("upload-batch")
        protected String upload;

        public Batch(@JsonProperty("upload-fileId") String fileId, @JsonProperty("upload-batch") String upload) {
            this.fileId = fileId;
            this.upload = upload;
        }

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getUpload() {
            return upload;
        }

        public void setUpload(String upload) {
            this.upload = upload;
        }
    }

    public static final class Info {

        @JsonProperty("start")
        protected String start;

        @JsonProperty("end")
        protected String end;

        public Info(@JsonProperty("start") String start, @JsonProperty("end") String end) {
            this.start = start;
            this.end = end;
        }

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }
    }
}
