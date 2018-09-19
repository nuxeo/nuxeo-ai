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

import org.nuxeo.ecm.core.api.CoreSession;
import java.util.Collection;

/**
 * For a given dataset provides statistics and exporting capabilities.
 */
public interface DatasetExportService {

    /**
     * Export the dataset matched by the nxql query and property names.
     * Splits the dataset into 2 random groups based on the percentage split value.
     * @param session core session
     * @param nxql a valid query to use as a filter
     * @param inputProperties list of document property names
     * @param outputProperties list of document property names
     * @param split a number between 1 and 100.
     * @return a bulk command id reference
     */
    String export(CoreSession session, String nxql,
                  Collection<String> inputProperties, Collection<String> outputProperties, int split);
}
