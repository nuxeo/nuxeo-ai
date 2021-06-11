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
 *     jgarzon <jgarzon@nuxeo.com>
 */
package org.nuxeo.ai.imagequality.pojo;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Text {

    private final float hasArtificial;

    private final float hasNatural;

    private final List<Box> boxes;

    @JsonCreator
    public Text(@JsonProperty("has_artificial") Float hasArtificial, @JsonProperty("has_natural") Float hasNatural,
            @JsonProperty("boxes") List<Box> boxes) {
        this.hasArtificial = hasArtificial;
        this.hasNatural = hasNatural;
        this.boxes = boxes;
    }

    public float getHasArtificial() {
        return hasArtificial;
    }

    public float getHasNatural() {
        return hasNatural;
    }

    public List<Box> getBoxes() {
        return boxes;
    }
}
