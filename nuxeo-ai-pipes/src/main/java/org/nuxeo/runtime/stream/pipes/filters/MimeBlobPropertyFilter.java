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
package org.nuxeo.runtime.stream.pipes.filters;


import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.nuxeo.ecm.core.api.Blob;

/**
 * Tests to see if a Blob mimetype matches the regular expression.
 */
public class MimeBlobPropertyFilter extends PropertyFilter.BlobPropertyFilter {

    protected Predicate<String> mimeRegex;

    @Override
    public void init(Map<String, String> options) {
        super.init(options);
        mimeRegex = Pattern.compile(options.get("mimePattern")).asPredicate();
    }

    @Override
    public boolean testBlob(Blob blob) {
        String mimeType = blob.getMimeType();
        return mimeType != null && mimeRegex.test(mimeType);
    }
}
