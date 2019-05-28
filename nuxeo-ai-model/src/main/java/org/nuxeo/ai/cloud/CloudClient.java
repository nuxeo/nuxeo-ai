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
package org.nuxeo.ai.cloud;

import java.io.IOException;
import org.nuxeo.ecm.core.api.DocumentModel;

import okhttp3.Response;

/**
 * A client that connects to Nuxeo Cloud AI
 */
public interface CloudClient {

    /**
     * Is the cloud available to call?
     */
    boolean isAvailable();

    /**
     * Upload the blobs to the cloud, using data from the corpus document.
     */
    boolean uploadedDataset(DocumentModel corpusDoc);

    /*
     * Make a http POST request to the cloud using the provided parameters.
     */
    <T> T post(String url, String jsonBody, ResponseHandler<T> handler);

    /*
     * Make a http PUT request to the cloud using the provided parameters.
     */
    <T> T put(String putUrl, String jsonBody, ResponseHandler<T> handler);

    /*
     * Make a http GET request to the cloud.
     */
    <T> T get(String url, ResponseHandler<T> handler);

    /*
     * Make a http GET request to the cloud by project path.
     */
    <T> T getByPath(String url, ResponseHandler<T> handler);

    /**
     * The url for all calls to the cloud prefixed by the project id.
     */
    String byProjectId(String url);

    /**
     * A callback to handle a response from a call to the cloud.
     */
    interface ResponseHandler<T> {
        T handleResponse(Response response) throws IOException;
    }
}
