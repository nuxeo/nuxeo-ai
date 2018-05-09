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
package org.nuxeo.ai;

import java.time.Instant;

/**
 * Additional metadata created using Artificial Intelligence
 */
public class AIMetadata {

    public final Instant created;
    public final String creator;
    public final String raw;
    public final String predictionModelVersion;
    public final boolean human;
    public final String targetDocumentRef; //Document reference

    public AIMetadata(String predictionModelVersion, String targetDocumentRef, boolean human, String creator, Instant created, String raw) {
        this.targetDocumentRef = targetDocumentRef;
        this.created = created;
        this.creator = creator;
        this.raw = raw;
        this.predictionModelVersion = predictionModelVersion;
        this.human = human;
    }

    public Instant getCreated() {
        return created;
    }

    public String getCreator() {
        return creator;
    }

    public String getRaw() {
        return raw;
    }

    public String getPredictionModelVersion() {
        return predictionModelVersion;
    }

    public boolean isHuman() {
        return human;
    }

    public String getTargetDocumentRef() {
        return targetDocumentRef;
    }
}
