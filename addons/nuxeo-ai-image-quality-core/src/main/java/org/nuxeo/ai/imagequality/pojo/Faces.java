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

import java.util.List;
import java.util.Map;

public class Faces {

    private float x1;
    private float y1;
    private float x2;
    private float y2;
    private Map<String, FeatureXY> features;
    private Map<String, Float> attributes;
    private List<Celebrity> celebrity;

    public float getX1() {
        return x1;
    }

    public void setX1(float x1) {
        this.x1 = x1;
    }

    public float getY1() {
        return y1;
    }

    public void setY1(float y1) {
        this.y1 = y1;
    }

    public float getX2() {
        return x2;
    }

    public void setX2(float x2) {
        this.x2 = x2;
    }

    public float getY2() {
        return y2;
    }

    public void setY2(float y2) {
        this.y2 = y2;
    }

    public Map<String, FeatureXY> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, FeatureXY> features) {
        this.features = features;
    }

    public Map<String, Float> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Float> attributes) {
        this.attributes = attributes;
    }

    public List<Celebrity> getCelebrity() {
        return celebrity;
    }

    public void setCelebrity(List<Celebrity> celebrity) {
        this.celebrity = celebrity;
    }

}
