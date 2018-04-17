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
package org.nuxeo.runtime.stream.pipes.functions;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class Predicates {

    private Predicates() {
    }

    public static Predicate<Event> event() {
        return Objects::nonNull;
    }

    public static Predicate<DocumentModel> doc() {
        return Objects::nonNull;
    }

    public static Predicate<Event> docEvent(Predicate<DocumentModel> doc) {
        Objects.requireNonNull(doc);
        return event().and(e -> {
            DocumentEventContext docCtx = (DocumentEventContext) e.getContext();
            if (docCtx == null) return false;
            DocumentModel d = docCtx.getSourceDocument();
            if (d == null) return false;
            return doc.test(d);
        });
    }

    public static Predicate<DocumentModel> isNotProxy() {
        return doc().and(d -> !d.isProxy());
    }

    public static Predicate<DocumentModel> hasFacets(String... facets) {
        return doc().and(d -> Arrays.asList(facets).stream().allMatch(d::hasFacet));
    }

    public static Predicate<DocumentModel> isPicture() {
        return isNotProxy().and(d -> d.hasFacet("Picture"));
    }

    public static Predicate<DocumentModel> notSystem() {
        return isNotProxy().and(d -> !d.hasFacet("SystemDocument"));
    }

}
