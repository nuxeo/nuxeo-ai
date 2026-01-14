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
import com.fasterxml.jackson.annotation.JsonProperty;

public class Offensive {

    private float prob;

    private float nazi;

    private float confederate;

    private float supremacist;

    private float terrorist;

    @JsonProperty("middle_finger")
    private float middleFinger;

    private List<Box> boxes = null;

    public float getProb() {
        return prob;
    }

    public void setProb(float prob) {
        this.prob = prob;
    }

    public float getNazi() {
        return nazi;
    }

    public void setNazi(float nazi) {
        this.nazi = nazi;
    }

    public float getConfederate() {
        return confederate;
    }

    public void setConfederate(float confederate) {
        this.confederate = confederate;
    }

    public float getSupremacist() {
        return supremacist;
    }

    public void setSupremacist(float supremacist) {
        this.supremacist = supremacist;
    }

    public float getTerrorist() {
        return terrorist;
    }

    public void setTerrorist(float terrorist) {
        this.terrorist = terrorist;
    }

    public float getMiddleFinger() {
        return middleFinger;
    }

    public void setMiddleFinger(float middleFinger) {
        this.middleFinger = middleFinger;
    }

    public List<Box> getBoxes() {
        return boxes;
    }

    public void setBoxes(List<Box> boxes) {
        this.boxes = boxes;
    }

}
