/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.model.export;

import org.nuxeo.ecm.core.bulk.message.BulkStatus;

/**
 * POJO for embedding {@link BulkStatus} and extra fields required for UI
 */
public class ExportProgressStatus {

    protected String id;

    protected String name;

    protected String state;

    protected long total;

    protected long errorCount;

    protected long processed;

    public ExportProgressStatus() {}

    public ExportProgressStatus(BulkStatus status) {
        this.id = status.getId();
        this.name = status.getId();
        this.state = status.getState().name();
        this.total = status.getTotal();
        this.errorCount = status.getErrorCount();
        this.processed = status.getProcessed();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getProcessed() {
        return processed;
    }

    public void setProcessed(long processed) {
        this.processed = processed;
    }
}
