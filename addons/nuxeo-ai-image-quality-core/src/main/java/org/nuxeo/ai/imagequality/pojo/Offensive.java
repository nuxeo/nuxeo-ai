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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing Sightengine's offensive content assessment for an image.
 * <p>
 * All probability scores returned by the Sightengine API are normalized floats in the range
 * 0.0 (no likelihood) to 1.0 (very high likelihood).
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Offensive {

    /**
     * Overall probability that the image contains offensive content (any of the categories below).
     * Expected range: 0.0–1.0. Kept as primitive for backward compatibility with {@link Scam#getProb()}.
     */
    private float prob;

    /**
     * Probability that the image contains Nazi-related symbols or flags.
     * Expected range: 0.0–1.0.
     */
    private Float nazi;

    /**
     * Probability that the image contains the Confederate flag or related symbols.
     * Expected range: 0.0–1.0.
     */
    private Float confederate;

    /**
     * Probability that the image contains white supremacist or extremist symbols.
     * Expected range: 0.0–1.0.
     */
    private Float supremacist;

    /**
     * Probability that the image contains terrorist-related symbols (e.g., flags, insignia).
     * Expected range: 0.0–1.0.
     */
    private Float terrorist;

    /**
     * Probability that the image depicts the middle finger gesture.
     * Expected range: 0.0–1.0.
     */
    @JsonProperty("middle_finger")
    private Float middleFinger;

    /**
     * Detected bounding boxes for regions associated with the offensive categories above.
     * Each {@link Box} typically includes coordinates and a label/category.
     */
    private List<Box> boxes = null;

    public float getProb() {
        return prob;
    }

    public void setProb(float prob) {
        this.prob = prob;
    }

    public Float getNazi() {
        return nazi;
    }

    public void setNazi(Float nazi) {
        this.nazi = nazi;
    }

    public Float getConfederate() {
        return confederate;
    }

    public void setConfederate(Float confederate) {
        this.confederate = confederate;
    }

    public Float getSupremacist() {
        return supremacist;
    }

    public void setSupremacist(Float supremacist) {
        this.supremacist = supremacist;
    }

    public Float getTerrorist() {
        return terrorist;
    }

    public void setTerrorist(Float terrorist) {
        this.terrorist = terrorist;
    }

    public Float getMiddleFinger() {
        return middleFinger;
    }

    public void setMiddleFinger(Float middleFinger) {
        this.middleFinger = middleFinger;
    }

    @JsonIgnore
    public float getNaziAsFloat() {
        return nazi != null ? nazi : 0.0f;
    }

    @JsonIgnore
    public float getConfederateAsFloat() {
        return confederate != null ? confederate : 0.0f;
    }

    @JsonIgnore
    public float getSupremacistAsFloat() {
        return supremacist != null ? supremacist : 0.0f;
    }

    @JsonIgnore
    public float getTerroristAsFloat() {
        return terrorist != null ? terrorist : 0.0f;
    }

    @JsonIgnore
    public float getMiddleFingerAsFloat() {
        return middleFinger != null ? middleFinger : 0.0f;
    }

    public List<Box> getBoxes() {
        return boxes;
    }

    public void setBoxes(List<Box> boxes) {
        this.boxes = boxes;
    }
}
