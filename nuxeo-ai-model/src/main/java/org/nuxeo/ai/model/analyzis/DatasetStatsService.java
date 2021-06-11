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

import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_COUNT;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.nuxeo.ai.sdk.objects.FieldStatistics;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ai.sdk.objects.Statistic;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;

/**
 * For a given dataset provides statistics.
 */
public interface DatasetStatsService {

    List<String> VOCABULARY_TYPES = Arrays.asList("vocabulary", "xvocabulary", "l10nxvocabulary");

    String AGG_TOTAL = "total";

    /**
     * Checks if given type is a vocabulary.
     *
     * @param type {@link Type} to retrieve information
     * @return {@link Boolean#TRUE} if type is a vocabulary
     */
    static boolean isVocabulary(Type type) {
        if (type instanceof ListType) {
            type = ((ListType) type).getFieldType();
        }
        ObjectResolver resolver = type.getObjectResolver();
        if (resolver instanceof DirectoryEntryResolver) {
            DirectoryEntryResolver directoryEntryResolver = (DirectoryEntryResolver) resolver;
            return VOCABULARY_TYPES.contains(directoryEntryResolver.getDirectory().getSchema());
        }
        return false;
    }

    /**
     * Resolve type of
     *
     * @param field {@link Field}
     * @return {@link String} representing one of the field types img|txt|cat
     */
    static String getInputType(Field field) {
        String type;
        if (field != null && isVocabulary(field.getType())) {
            type = CATEGORY_TYPE;
        } else if (field != null && TypeConstants.isContentType(field.getType())) {
            type = IMAGE_TYPE;
        } else {
            type = TEXT_TYPE;
        }
        return type;
    }

    Collection<Statistic> getStatistics(CoreSession session, String nxql, Set<PropertyType> inputProperties,
            Set<PropertyType> outputProperties);

    /**
     * Transforms statistics between ES and UI needs
     *
     * @param statistics {@link Collection} of {@link Statistic} to transofrm
     * @return {@link Set} of `unique` {@link FieldStatistics}
     */
    default Set<FieldStatistics> transform(Collection<Statistic> statistics) {
        if (statistics.isEmpty()) {
            return Collections.emptySet();
        }

        Map<String, List<Statistic>> collect = statistics.stream().collect(Collectors.groupingBy(Statistic::getField));
        List<Statistic> totalStat = collect.remove(AGG_TOTAL);
        collect.remove(AGG_COUNT);

        Set<FieldStatistics> result = new HashSet<>(collect.size());
        for (Statistic statistic : totalStat) {
            Number total = statistic.getNumericValue();

            Collection<FieldStatistics> values = collect.entrySet()
                                                        .stream()
                                                        .flatMap(entry -> entry.getValue().stream())
                                                        .collect(Collectors.toMap(Statistic::getField,
                                                                stat -> FieldStatistics.from(stat, total.longValue()),
                                                                FieldStatistics::merge)) // merge all stats to include different aggregates terms|missing|cardinality
                                                        .values();
            result.addAll(values);
        }

        return result;
    }
}
