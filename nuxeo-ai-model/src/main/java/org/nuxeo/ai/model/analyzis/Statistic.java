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
package org.nuxeo.ai.model.analyzis;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.elasticsearch.aggregate.MultiBucketAggregate;
import org.nuxeo.elasticsearch.aggregate.SingleBucketAggregate;
import org.nuxeo.elasticsearch.aggregate.SingleValueMetricAggregate;
import org.nuxeo.runtime.api.Framework;

import java.util.List;

import static org.nuxeo.ai.model.analyzis.DatasetStatsService.getInputType;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

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

    protected String aggType;

    public Statistic(String id, String field, String type, String value, Number numericValue) {
        this.id = id;
        this.field = field;
        this.type = type;
        this.value = value;
        this.numericValue = numericValue;
        this.aggType = type;
    }

    /**
     * Factory constructor
     * @return {@link Statistic} new object
     */
    public static Statistic of(String id, String field, String type, String value, Number numericValue) {
        return new Statistic(id, field, type, value, numericValue);
    }

    /**
     * Factory constructor
     * @param agg {@link Aggregate} from which to construct
     * @return {@link Statistic} new object
     */
    public static Statistic from(Aggregate<?> agg) {
        Number numericValue = null;
        String value = null;
        if (agg instanceof SingleValueMetricAggregate) {
            Double val = ((SingleValueMetricAggregate) agg).getValue();
            numericValue = Double.isFinite(val) ? val : -1;
        } else if (agg instanceof SingleBucketAggregate) {
            numericValue = ((SingleBucketAggregate) agg).getDocCount();
        } else if (agg instanceof MultiBucketAggregate) {
            List<?> buckets = agg.getBuckets();
            try {
                value = MAPPER.writeValueAsString(buckets);
            } catch (JsonProcessingException e) {
                throw new UnsupportedOperationException("Unable to create a statistic for " + agg.getType(), e);
            }
        } else {
            throw new UnsupportedOperationException("Unable to create a statistic for " + agg.getType());
        }

        String fieldName = agg.getField();
        if (fieldName.endsWith("/length") || fieldName.endsWith(".length")) {
            fieldName = fieldName.substring(0, fieldName.length() - "/length".length());
        }

        SchemaManager ts = Framework.getService(SchemaManager.class);
        Field field = ts.getField(fieldName);

        String type = getInputType(field);

        Statistic statistic = new Statistic(agg.getId(), fieldName, type, value, numericValue);
        statistic.aggType = agg.getType();

        return statistic;
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

    public String getAggType() {
        return aggType;
    }

    public void setAggType(String aggType) {
        this.aggType = aggType;
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
