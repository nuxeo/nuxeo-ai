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
package org.nuxeo.ai.pipes.types;

import java.io.Serializable;
import java.util.Objects;

public class ExportStatus implements Serializable {

    private static final long serialVersionUID = 22012020291221L;

    protected String id;

    protected String commandId;

    protected boolean isTraining;

    protected long processed = 0L;

    protected long errored = 0L;

    public ExportStatus() {
    }

    public static ExportStatus of(String commandId, String id, long processed, long errored) {
        ExportStatus eb = new ExportStatus();
        eb.setCommandId(commandId);
        eb.setId(id);
        eb.setProcessed(processed);
        eb.setErrored(errored);

        return eb;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public boolean isTraining() {
        return isTraining;
    }

    public void setTraining(boolean training) {
        isTraining = training;
    }

    public long getProcessed() {
        return processed;
    }

    public void setProcessed(long processed) {
        this.processed = processed;
    }

    public long getErrored() {
        return errored;
    }

    public void setErrored(long errored) {
        this.errored = errored;
    }

    @Override
    public String toString() {
        return "ExportStatus{" + "id='" + id + '\'' + ", commandId='" + commandId + '\'' + ", isTraining=" + isTraining
                + ", processed=" + processed + ", errored=" + errored + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExportStatus that = (ExportStatus) o;
        return isTraining == that.isTraining && processed == that.processed && errored == that.errored
                && Objects.equals(id, that.id) && Objects.equals(commandId, that.commandId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, commandId, isTraining, processed, errored);
    }
}
