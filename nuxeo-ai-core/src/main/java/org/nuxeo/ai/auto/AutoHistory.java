/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.auto;

import java.util.Date;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A POJO representation of an auto-changed value.
 */
public class AutoHistory {

    protected final long id;

    protected final String property;

    protected final String previousValue;

    @JsonCreator
    public AutoHistory(@JsonProperty("id") long id, @JsonProperty("property") String property,
                       @JsonProperty("previousValue") String previousValue) {
        this.id = id;
        this.property = property;
        this.previousValue = previousValue;
    }

    public AutoHistory(String property, String previousValue) {
        this(new Date().getTime(), property, previousValue);
    }

    public long getId() {
        return id;
    }

    public String getProperty() {
        return property;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("property", property)
                .append("previousValue", previousValue)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        AutoHistory that = (AutoHistory) o;
        return id == that.id &&
                Objects.equals(property, that.property) &&
                Objects.equals(previousValue, that.previousValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, property, previousValue);
    }
}
