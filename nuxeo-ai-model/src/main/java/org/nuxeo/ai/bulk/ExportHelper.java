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
package org.nuxeo.ai.bulk;

import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.NAME_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TYPE_PROP;
import static org.nuxeo.ecm.core.bulk.BulkCodecs.DEFAULT_CODEC;
import static org.nuxeo.ecm.core.schema.TypeConstants.isContentType;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ai.pipes.types.PropertyType;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.codec.CodecService;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.transaction.TransactionHelper;

public abstract class ExportHelper {

    private static final Logger log = LogManager.getLogger(ExportHelper.class);

    private static final int TIMEOUT = 3600 * 24;

    /**
     * Helper for getting KVS
     * @return
     */
    public static KeyValueStore getKVS() {
        KeyValueService kvs = Framework.getService(KeyValueService.class);
        return kvs.getKeyValueStore("default");
    }

    /**
     * A helper method for getting Avro Codec from
     * @param clazz
     */
    public static <T> Codec<T> getAvroCodec(Class<T> clazz) {
        CodecService cs = Framework.getService(CodecService.class);
        return cs.getCodec(DEFAULT_CODEC, clazz);
    }

    /**
     * Runs a method with Nuxeo Tx
     */
    public static  <R> R runInTransaction(Supplier<R> supplier) {
        if (TransactionHelper.isTransactionMarkedRollback()) {
            throw new NuxeoException("Cannot run supplier when current transaction is marked rollback.");
        }
        boolean txActive = TransactionHelper.isTransactionActive();
        boolean txStarted = false;
        try {
            if (txActive) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            txStarted = TransactionHelper.startTransaction(TIMEOUT);
            return supplier.get();
        } finally {
            if (txStarted) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            if (txActive) {
                // go back to default transaction timeout
                TransactionHelper.startTransaction();
            }
        }
    }

    /**
     * For a given Collection of property names, return a list of features with the property name and type.
     */
    public static List<DatasetExport.IOParam> propsToTypedList(Collection<String> properties) {
        return properties.stream()
                .map(ExportHelper::getPropertyWithType)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    public static PropertyType addTypeIfNull(PropertyType prop) {
        if (prop.getType() == null) {
            DatasetExport.IOParam propType = getPropertyWithType(prop.getName());
            return new PropertyType(prop.getName(), propType.get(TYPE_PROP));
        }
        return prop;
    }
    
    /**
     * For the given property, find out if it exists and determine if its text or content
     */
    public static DatasetExport.IOParam getPropertyWithType(String prop) {
        Field field = Framework.getService(SchemaManager.class).getField(prop);
        DatasetExport.IOParam feature = new DatasetExport.IOParam();

        feature.put(NAME_PROP, prop);
        if (field == null) {
            if (NXQL.ECM_FULLTEXT.equals(prop)) {
                log.debug("Skipping {} because its not possible to get stats on it.", NXQL.ECM_FULLTEXT);
                return null;
            } else {
                log.warn(prop + " does not exist as a type, defaulting to txt type.");
                feature.put(TYPE_PROP, TEXT_TYPE);
            }
            return feature;
        }
        String type = isContentType(field.getType()) ? IMAGE_TYPE : TEXT_TYPE;
        if (field.getType().isListType()) {
            type = CATEGORY_TYPE;
        }
        feature.put(TYPE_PROP, type);
        return feature;
    }
}
