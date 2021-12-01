/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */

package org.nuxeo.ai.similar.content.pipelines.objects;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for index computations
 */
public class IndexRecord implements Serializable {

    private static final long serialVersionUID = -953200611356728736L;

    protected String id;

    protected String docId;

    protected String commandId;

    protected String xpath;

    public IndexRecord() {
    }

    @JsonCreator
    public IndexRecord(@JsonProperty("id") String id, @JsonProperty("doc_id") String docId,
            @JsonProperty("command_id") String commandId, @JsonProperty("xpath") String xpath) {
        this.id = id;
        this.docId = docId;
        this.commandId = commandId;
        this.xpath = xpath;
    }

    public static IndexRecord of(String docId, String commandId, String xpath) {
        return new IndexRecord(docId, docId, commandId, xpath);
    }

    public String getId() {
        return id;
    }

    public String getDocId() {
        return docId;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getXpath() {
        return xpath;
    }
}
