/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.ToStringBuilder;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A suggestion, made up of a property name and one or more labels
 */
public class LabelSuggestion implements Serializable {

    private static final long serialVersionUID = 7549317566844895574L;

    protected final String property;

    protected List<AIMetadata.Label> values;

    @JsonCreator
    public LabelSuggestion(@JsonProperty("property") String property,
            @JsonProperty("values") List<AIMetadata.Label> values) {
        this.property = property;
        this.values = values != null ? unmodifiableList(values) : emptyList();
    }

    public String getProperty() {
        return property;
    }

    public List<AIMetadata.Label> getValues() {
        return values;
    }

    public void keepUniqueOnly() {
        values = getValues().stream().filter(distinctByKey(AIMetadata.Label::getName)).collect(Collectors.toList());
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LabelSuggestion that = (LabelSuggestion) o;
        return Objects.equals(property, that.property) && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, values);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("property", property).append("values", values).toString();
    }
}
