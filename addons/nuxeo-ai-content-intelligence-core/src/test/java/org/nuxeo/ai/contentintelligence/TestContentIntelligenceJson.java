/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optArray;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optBoolean;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optObject;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optString;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.readObject;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Pins the {@code org.json} {@code opt*} semantics that {@link ContentIntelligenceJson} replaces.
 */
public class TestContentIntelligenceJson {

    protected static ObjectNode node(String json) throws JsonProcessingException {
        return readObject(json);
    }

    @Test
    public void optBooleanFollowsOrgJson() throws JsonProcessingException {
        for (boolean def : new boolean[] { true, false }) {
            assertTrue(optBoolean(node("{\"v\":true}"), "v", def));
            assertFalse(optBoolean(node("{\"v\":false}"), "v", def));
            assertTrue(optBoolean(node("{\"v\":\"TRUE\"}"), "v", def));
            assertFalse(optBoolean(node("{\"v\":\"False\"}"), "v", def));
            for (String value : new String[] { "\"yes\"", "\" true\"", "1", "0", "null", "{}" }) {
                assertEquals(value, def, optBoolean(node("{\"v\":" + value + "}"), "v", def));
            }
            assertEquals(def, optBoolean(node("{}"), "v", def));
        }
    }

    @Test
    public void optStringFollowsOrgJson() throws JsonProcessingException {
        assertEquals("D", optString(node("{}"), "v", "D"));
        assertEquals("D", optString(node("{\"v\":null}"), "v", "D"));
        assertEquals("", optString(node("{\"v\":\"\"}"), "v", "D"));
        assertEquals("null", optString(node("{\"v\":\"null\"}"), "v", "D"));
        assertEquals("42", optString(node("{\"v\":42}"), "v", "D"));
        assertEquals("true", optString(node("{\"v\":true}"), "v", "D"));
        assertEquals("[1,\"a\"]", optString(node("{\"v\":[1,\"a\"]}"), "v", "D"));
    }

    @Test
    public void optObjectAndArrayRejectNullAndWrongTypes() throws JsonProcessingException {
        ObjectNode n = node("{\"o\":{},\"a\":[],\"n\":null,\"s\":\"x\"}");
        assertNotNull(optObject(n, "o"));
        assertNull(optObject(n, "a"));
        assertNull(optObject(n, "n"));
        assertNull(optObject(n, "missing"));
        assertNotNull(optArray(n, "a"));
        assertNull(optArray(n, "o"));
        assertNull(optArray(n, "n"));
        assertNull(optArray(n, "s"));
    }

    @Test
    public void readObjectReturnsNullForNonObjects() throws JsonProcessingException {
        for (String json : new String[] { null, "", "   ", "null", "[]", "42", "\"s\"" }) {
            assertNull(String.valueOf(json), readObject(json));
        }
        assertEquals(0, readObject("{}").size());
        assertThrows(JsonProcessingException.class, () -> readObject("{bad"));
    }

    @Test
    public void readObjectAcceptsOrgJsonLenientSyntax() throws JsonProcessingException {
        assertEquals(2, readObject("{\"a\":[1,2,],}").get("a").size());
        assertTrue(readObject("{\"a\":NaN}").get("a").isNumber());
        assertEquals("A\tcat", readObject("{\"a\":\"A\tcat\"}").get("a").textValue());
        assertEquals("x", readObject("{'a':'x'}").get("a").textValue());
        assertEquals("x", readObject("{a:\"x\"}").get("a").textValue());
    }
}
