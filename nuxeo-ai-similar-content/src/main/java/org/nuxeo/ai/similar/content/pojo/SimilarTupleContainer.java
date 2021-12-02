/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */
package org.nuxeo.ai.similar.content.pojo;

import java.io.Serializable;
import org.nuxeo.ai.sdk.objects.deduplication.SimilarTuple;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Container class for passing {@link SimilarTuple} with required meta through Streams
 */
public class SimilarTupleContainer implements Serializable {

    private static final long serialVersionUID = -6196533782397157763L;

    protected String user;

    protected String repository;

    protected SimilarTuple tuple;

    protected SimilarTupleContainer(String user, String repository, SimilarTuple tuple) {
        this.user = user;
        this.repository = repository;
        this.tuple = tuple;
    }

    /**
     * Factory constructor
     */
    @JsonCreator
    public static SimilarTupleContainer of(@JsonProperty("user") String user,
            @JsonProperty("repository") String repository, @JsonProperty("tuple") SimilarTuple tuple) {
        return new SimilarTupleContainer(user, repository, tuple);
    }

    public String getUser() {
        return user;
    }

    public String getRepository() {
        return repository;
    }

    public SimilarTuple getTuple() {
        return tuple;
    }
}
