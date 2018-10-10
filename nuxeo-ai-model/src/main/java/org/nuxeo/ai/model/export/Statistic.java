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
package org.nuxeo.ai.model.export;

import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.elasticsearch.aggregate.MultiBucketAggregate;
import org.nuxeo.elasticsearch.aggregate.SingleBucketAggregate;
import org.nuxeo.elasticsearch.aggregate.SingleValueMetricAggregate;
import java.util.List;

/**
 * A POJO representation of a Dataset Statistic
 */
public class Statistic {

    protected final String id;

    protected final String field;

    protected final String type;

    @JsonRawValue
    protected final String value;

    protected final Number numericValue;

    public Statistic(String id, String field, String type, String value, Number numericValue) {
        this.id = id;
        this.field = field;
        this.type = type;
        this.value = value;
        this.numericValue = numericValue;
    }

    public static Statistic of(String id, String field, String type, String value, Number numericValue) {
        return new Statistic(id, field, type, value, numericValue);
    }

    public static Statistic from(Aggregate agg) {
        Number numericValue = null;
        String value = null;
        if (agg instanceof SingleValueMetricAggregate) {
            Double val = ((SingleValueMetricAggregate) agg).getValue();
            numericValue = Double.isFinite(val) ? val : -1;
        } else if (agg instanceof SingleBucketAggregate) {
            numericValue = ((SingleBucketAggregate) agg).getDocCount();
        } else if (agg instanceof MultiBucketAggregate) {
            List buckets = agg.getBuckets();
            try {
                value = MAPPER.writeValueAsString(buckets);
            } catch (JsonProcessingException e) {
                throw new UnsupportedOperationException("Unable to create a statistic for " + agg.getType(), e);
            }
        } else {
            throw new UnsupportedOperationException("Unable to create a statistic for " + agg.getType());
        }
        return new Statistic(agg.getId(), agg.getField(), agg.getType(), value, numericValue);
    }

    public String getId() {
        return id;
    }

    public String getField() {
        return field;
    }

    public String getType() {
        return type;
    }

    public Number getNumericValue() {
        return numericValue;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
