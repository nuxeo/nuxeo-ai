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

import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_BATCH_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_INPUTS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_END_DATE;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_NAME;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_START_DATE;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_OUTPUTS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_QUERY;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_SPLIT;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_STATS;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import javax.validation.constraints.NotNull;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;

public class DatasetExport implements AIAdapter {

    protected DocumentModel doc;

    public DatasetExport(@NotNull DocumentModel doc) {
        this.doc = doc;
    }

    @Override
    public DocumentModel getDocument() {
        return doc;
    }

    public void setModelId(String id) {
        doc.setPropertyValue(DATASET_EXPORT_MODEL_ID, id);
    }

    public String getModelId() {
        return (String) doc.getPropertyValue(DATASET_EXPORT_MODEL_ID);
    }

    public void setModelName(String name) {
        doc.setPropertyValue(DATASET_EXPORT_MODEL_NAME, name);
    }

    public String getModelName() {
        return (String) doc.getPropertyValue(DATASET_EXPORT_MODEL_NAME);
    }

    public void setModelStartDate(Date start) {
        doc.setPropertyValue(DATASET_EXPORT_MODEL_START_DATE, start);
    }

    public Date getModelStartDate() {
        return (Date) doc.getPropertyValue(DATASET_EXPORT_MODEL_START_DATE);
    }

    public void setModelEndDate(Date start) {
        doc.setPropertyValue(DATASET_EXPORT_MODEL_END_DATE, start);
    }

    public Date getModelEndDate() {
        return (Date) doc.getPropertyValue(DATASET_EXPORT_MODEL_END_DATE);
    }

    public void setQuery(String query) {
        doc.setPropertyValue(DATASET_EXPORT_QUERY, query);
    }

    public String getQuery() {
        return (String) doc.getPropertyValue(DATASET_EXPORT_QUERY);
    }

    public void setSplit(int split) {
        doc.setPropertyValue(DATASET_EXPORT_SPLIT, split);
    }

    public int getSplit() {
        return (int) doc.getPropertyValue(DATASET_EXPORT_SPLIT);
    }

    public void setInputs(List<IOParam> inputs) {
        doc.setPropertyValue(DATASET_EXPORT_INPUTS, (Serializable) inputs);
    }

    @SuppressWarnings("unchecked")
    public List<IOParam> getInputs() {
        return (List<IOParam>) doc.getPropertyValue(DATASET_EXPORT_INPUTS);
    }

    public void setOutputs(List<IOParam> outputs) {
        doc.setPropertyValue(DATASET_EXPORT_OUTPUTS, (Serializable) outputs);
    }

    @SuppressWarnings("unchecked")
    public List<IOParam> getOutputs() {
        return (List<IOParam>) doc.getPropertyValue(DATASET_EXPORT_OUTPUTS);
    }

    public void setStatistics(Blob blob) {
        doc.setPropertyValue(DATASET_EXPORT_STATS, (Serializable) blob);
    }

    public Blob getStatistics() {
        return (Blob) doc.getPropertyValue(DATASET_EXPORT_STATS);
    }

    public void setJobId(String id) {
        doc.setPropertyValue(DATASET_EXPORT_JOB_ID, id);
    }

    public String getJobId() {
        return (String) doc.getPropertyValue(DATASET_EXPORT_JOB_ID);
    }

    public void setBatchId(String id) {
        doc.setPropertyValue(DATASET_EXPORT_BATCH_ID, id);
    }

    public String getBatchId() {
        return (String) doc.getPropertyValue(DATASET_EXPORT_BATCH_ID);
    }

    public static class IOParam extends HashMap<String, String> {
    }
}
