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
import java.util.Arrays;
import java.util.Objects;

public class ExportRecord implements Serializable {

    private static final long serialVersionUID = 210120202341213L;

    protected String id;

    protected String commandId;

    protected byte[] data;

    protected boolean isFailed;

    protected boolean isTraining;

    public static ExportRecord of(String id, String cmdId, byte[] data) {
        ExportRecord rec = new ExportRecord();
        rec.setId(id);
        rec.setCommandId(cmdId);
        rec.setData(data);

        return rec;
    }

    public static ExportRecord fail(String id, String cmdId) {
        ExportRecord rec = new ExportRecord();
        rec.setId(id);
        rec.setCommandId(cmdId);
        rec.setFailed(true);
        rec.setData(new byte[0]);

        return rec;
    }

    public ExportRecord() {
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

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isFailed() {
        return isFailed;
    }

    public void setFailed(boolean failed) {
        isFailed = failed;
    }

    public boolean isTraining() {
        return isTraining;
    }

    public void setTraining(boolean training) {
        isTraining = training;
    }

    @Override
    public String toString() {
        return "ExportRecord{" + "id='" + id + '\'' + ", commandId='" + commandId + '\'' + ", isFailed=" + isFailed
                + ", isTraining=" + isTraining + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExportRecord that = (ExportRecord) o;
        return isFailed == that.isFailed && isTraining == that.isTraining && Objects.equals(id, that.id)
                && Objects.equals(commandId, that.commandId) && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, commandId, isFailed, isTraining);
        if (data != null) {
            result = 31 * result + Arrays.hashCode(data);
        }
        return result;
    }
}
