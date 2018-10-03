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
 *     jgarzon <jgarzon@nuxeo.com>
 */
package org.nuxeo.ai.imagequality.pojo;

/**
 * Dictionary containing meta data on the request (identifying id, timestamp and number of operations performed)
 *
 * @author jgarzon@nuxeo.com
 */
public class Request {

    /**
     * identifying id
     */
    private String id;

    /**
     * Timestamp of operations performed
     */
    private float timestamp;

    /**
     * Number of operations performed
     */
    private int operations;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public float getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(float timestamp) {
        this.timestamp = timestamp;
    }

    public int getOperations() {
        return operations;
    }

    public void setOperations(int operations) {
        this.operations = operations;
    }
}
