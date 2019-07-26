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
package org.nuxeo.ai.adapters;

import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_INPUTS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_JOBID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_MODEL_END_DATE;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_MODEL_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_MODEL_NAME;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_MODEL_START_DATE;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_OUTPUTS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_QUERY;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_SPLIT;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_STATS;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import javax.validation.constraints.NotNull;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;

public class AICorpus implements AIAdapter {

    protected DocumentModel doc;

    public AICorpus(@NotNull DocumentModel doc) {
        this.doc = doc;
    }

    @Override
    public DocumentModel getDocument() {
        return doc;
    }

    public void setModelId(String id) {
        doc.setPropertyValue(CORPUS_MODEL_ID, id);
    }

    public String getModelId() {
        return (String) doc.getPropertyValue(CORPUS_MODEL_ID);
    }

    public void setModelName(String name) {
        doc.setPropertyValue(CORPUS_MODEL_NAME, name);
    }

    public String getModelName() {
        return (String) doc.getPropertyValue(CORPUS_MODEL_NAME);
    }

    public void setModelStartDate(Date start) {
        doc.setPropertyValue(CORPUS_MODEL_START_DATE, start);
    }

    public Date getModelStartDate() {
        return (Date) doc.getPropertyValue(CORPUS_MODEL_START_DATE);
    }

    public void setModelEndDate(Date start) {
        doc.setPropertyValue(CORPUS_MODEL_END_DATE, start);
    }

    public Date getModelEndDate() {
        return (Date) doc.getPropertyValue(CORPUS_MODEL_END_DATE);
    }

    public void setQuery(String query) {
        doc.setPropertyValue(CORPUS_QUERY, query);
    }

    public String getQuery() {
        return (String) doc.getPropertyValue(CORPUS_QUERY);
    }

    public void setSplit(int split) {
        doc.setPropertyValue(CORPUS_SPLIT, split);
    }

    public int getSplit() {
        return (int) doc.getPropertyValue(CORPUS_SPLIT);
    }

    public void setInputs(List<IOParam> inputs) {
        doc.setPropertyValue(CORPUS_INPUTS, (Serializable) inputs);
    }

    @SuppressWarnings("unchecked")
    public List<IOParam> getInputs() {
        return (List<IOParam>) doc.getPropertyValue(CORPUS_INPUTS);
    }

    public void setOutputs(List<IOParam> outputs) {
        doc.setPropertyValue(CORPUS_OUTPUTS, (Serializable) outputs);
    }

    @SuppressWarnings("unchecked")
    public List<IOParam> getOutputs() {
        return (List<IOParam>) doc.getPropertyValue(CORPUS_OUTPUTS);
    }

    public void setStatistics(Blob blob) {
        doc.setPropertyValue(CORPUS_STATS, (Serializable) blob);
    }

    public Blob getStatistics() {
        return (Blob) doc.getPropertyValue(CORPUS_STATS);
    }

    public void setJobId(String id) {
        doc.setPropertyValue(CORPUS_JOBID, id);
    }

    public String getJobId() {
        return (String) doc.getPropertyValue(CORPUS_JOBID);
    }

    public static class IOParam extends HashMap<String, String> {
    }
}
