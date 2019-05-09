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
package org.nuxeo.ai.pipes.filters;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * A Document filter that uses the Path.
 */
public class DocumentPathFilter implements Filter.DocumentFilter, Initializable {

    protected List<String> startsWith;

    protected List<String> endsWith;

    protected List<String> contains;

    protected Predicate<String> pattern;

    @Override
    public void init(Map<String, String> options) {
        startsWith = propsList(options.get("startsWith"));
        endsWith = propsList(options.get("endsWith"));
        contains = propsList(options.get("contains"));
        String regex = options.get("pattern");
        if (StringUtils.isNotEmpty(regex)) {
            pattern = Pattern.compile(regex).asPredicate();
        }
    }

    /**
     * A predicate that check the path against the provided filter parameters.
     */
    @Override
    public boolean test(DocumentModel doc) {
        String path = doc.getPathAsString();
        return (startsWith.isEmpty() || startsWith.stream().anyMatch(path::startsWith)
                && (endsWith.isEmpty() || endsWith.stream().anyMatch(path::endsWith))
                && (contains.isEmpty() || contains.stream().anyMatch(path::contains))
                && (pattern == null || pattern.test(path)));
    }

}
