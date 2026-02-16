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
 *     abhishek-gupta-nuxeo <gupta.abhishek@hyland.com>
 */
package org.nuxeo.ai.imagequality.pojo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestOffensive {

    private static final float DELTA = 0.0001f;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testProbIsPrimitiveFloat() {
        Offensive o = new Offensive();
        assertEquals(0.0f, o.getProb(), DELTA);
    }

    @Test
    public void testProbSetAndGet() {
        Offensive o = new Offensive();
        o.setProb(0.85f);
        assertEquals(0.85f, o.getProb(), DELTA);
    }

    @Test
    public void testProbComparisonSafe() {
        Offensive o = new Offensive();
        float minConfidence = 0.7f;
        // primitive float comparison: no NPE risk
        boolean result = o.getProb() > minConfidence;
        assertEquals(false, result);
    }

    @Test
    public void testNewFieldsDefaultToNull() {
        Offensive o = new Offensive();
        assertNull(o.getNazi());
        assertNull(o.getConfederate());
        assertNull(o.getSupremacist());
        assertNull(o.getTerrorist());
        assertNull(o.getMiddleFinger());
    }

    @Test
    public void testNullSafeHelpers() {
        Offensive o = new Offensive();
        assertEquals(0.0f, o.getNaziAsFloat(), DELTA);
        assertEquals(0.0f, o.getConfederateAsFloat(), DELTA);
        assertEquals(0.0f, o.getSupremacistAsFloat(), DELTA);
        assertEquals(0.0f, o.getTerroristAsFloat(), DELTA);
        assertEquals(0.0f, o.getMiddleFingerAsFloat(), DELTA);
    }

    @Test
    public void testNullSafeHelpersWithValues() {
        Offensive o = new Offensive();
        o.setNazi(0.1f);
        o.setConfederate(0.2f);
        o.setSupremacist(0.3f);
        o.setTerrorist(0.4f);
        o.setMiddleFinger(0.5f);
        assertEquals(0.1f, o.getNaziAsFloat(), DELTA);
        assertEquals(0.2f, o.getConfederateAsFloat(), DELTA);
        assertEquals(0.3f, o.getSupremacistAsFloat(), DELTA);
        assertEquals(0.4f, o.getTerroristAsFloat(), DELTA);
        assertEquals(0.5f, o.getMiddleFingerAsFloat(), DELTA);
    }

    @Test
    public void testJsonDeserializationWithAllFields() throws IOException {
        String json = "{\"prob\":0.92,\"nazi\":0.1,\"confederate\":0.8,\"supremacist\":0.05,"
                + "\"terrorist\":0.02,\"middle_finger\":0.3}";
        Offensive o = MAPPER.readValue(json, Offensive.class);
        assertEquals(0.92f, o.getProb(), DELTA);
        assertEquals(0.1f, o.getNaziAsFloat(), DELTA);
        assertEquals(0.8f, o.getConfederateAsFloat(), DELTA);
        assertEquals(0.05f, o.getSupremacistAsFloat(), DELTA);
        assertEquals(0.02f, o.getTerroristAsFloat(), DELTA);
        assertEquals(0.3f, o.getMiddleFingerAsFloat(), DELTA);
    }

    @Test
    public void testJsonDeserializationWithOnlyProb() throws IOException {
        String json = "{\"prob\":0.5}";
        Offensive o = MAPPER.readValue(json, Offensive.class);
        assertEquals(0.5f, o.getProb(), DELTA);
        assertNull(o.getNazi());
        assertNull(o.getConfederate());
        assertEquals(0.0f, o.getNaziAsFloat(), DELTA);
    }

    @Test
    public void testJsonSerializationExcludesNullFields() throws IOException {
        ObjectMapper nonNullMapper = new ObjectMapper();
        nonNullMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        Offensive o = new Offensive();
        o.setProb(0.7f);
        String json = nonNullMapper.writeValueAsString(o);
        assertEquals(false, json.contains("nazi"));
        assertEquals(false, json.contains("confederate"));
        assertEquals(true, json.contains("\"prob\""));
    }
}
