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
package org.nuxeo.ai.pipes.functions;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

/**
 * Helper class containing document-related predicates for use with Filter Functions.
 */
public class Predicates {

    private Predicates() {
    }

    public static Predicate<Event> event() {
        return Objects::nonNull;
    }

    public static Predicate<DocumentModel> doc() {
        return Objects::nonNull;
    }

    public static Predicate<Event> docEvent(Predicate<DocumentModel> predicate) {
        return docEvent(event(), predicate);
    }

    public static Predicate<Event> docEvent(Predicate<Event> eventPredicate, Predicate<DocumentModel> predicate) {
        Objects.requireNonNull(eventPredicate);
        Objects.requireNonNull(predicate);
        return eventPredicate.and(e -> {
            DocumentEventContext docCtx = (DocumentEventContext) e.getContext();
            if (docCtx == null)  {
                return false;
            }
            DocumentModel doc = docCtx.getSourceDocument();
            return doc != null && predicate.test(doc);
        });
    }

    public static Predicate<DocumentModel> isNotProxy() {
        return doc().and(d -> !d.isProxy());
    }

    public static Predicate<DocumentModel> hasFacets(String... facets) {
        return doc().and(d -> Arrays.stream(facets).allMatch(d::hasFacet));
    }

    public static Predicate<DocumentModel> isPicture() {
        return isNotProxy().and(d -> d.hasFacet("Picture"));
    }

}
