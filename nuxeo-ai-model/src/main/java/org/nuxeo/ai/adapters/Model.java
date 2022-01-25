/*
 * (C) Copyright 2006-2022 Nuxeo (http://nuxeo.com/) and others.
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

package org.nuxeo.ai.adapters;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI Model reference object that should be used uniquely for distinctive representation of AI Models
 * retrieved from Insight.
 * {@link Model#equals(Object)} and {@link Model#hashCode()} implemented in the way to exclude
 * any duplicates among versions
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Model {

    protected final String uid;

    protected final String versionableId;

    protected final String name;

    protected final String path;

    protected final Properties properties;

    public Model(@JsonProperty("uid") String uid, @JsonProperty("versionableId") String versionableId,
            @JsonProperty("name") String name, @JsonProperty("path") String path,
            @JsonProperty("properties") Properties properties) {
        this.uid = uid;
        this.versionableId = versionableId;
        this.name = name;
        this.path = path;
        this.properties = properties;
    }

    public String getUid() {
        return uid;
    }

    public String getVersionableId() {
        return versionableId;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public Properties getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Model model = (Model) o;
        return Objects.equals(uid, model.uid) || Objects.equals(uid, model.versionableId) || Objects.equals(
                versionableId, model.uid) || Objects.equals(versionableId, model.versionableId);
    }

    @Override
    public int hashCode() {
        return versionableId != null ? Objects.hash(versionableId) : Objects.hash(uid);
    }

    /**
     * Placeholder for properties of the document
     * values will be defined on demand if needed
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {

        public Properties() {
        }

    }
}
