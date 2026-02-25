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

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TestHsv {

    private static final float DELTA = 0.0001f;

    @Test
    public void testDefaultConstructorAndSetters() {
        Hsv hsv = new Hsv();
        hsv.setH(180f);
        hsv.setS(0.5f);
        hsv.setV(0.8f);
        assertEquals(180f, hsv.getH(), DELTA);
        assertEquals(0.5f, hsv.getS(), DELTA);
        assertEquals(0.8f, hsv.getV(), DELTA);
    }

    @Test
    public void testStrictConstructor() {
        Hsv hsv = new Hsv(120f, 0.3f, 0.9f);
        assertEquals(120f, hsv.getH(), DELTA);
        assertEquals(0.3f, hsv.getS(), DELTA);
        assertEquals(0.9f, hsv.getV(), DELTA);
    }

    @Test
    public void testSettersClamping() {
        Hsv hsv = new Hsv();
        hsv.setH(-10f);
        assertEquals(0f, hsv.getH(), DELTA);

        hsv.setH(400f);
        assertEquals(360f, hsv.getH(), DELTA);

        hsv.setS(-0.5f);
        assertEquals(0f, hsv.getS(), DELTA);

        hsv.setS(1.5f);
        assertEquals(1f, hsv.getS(), DELTA);

        hsv.setV(-1f);
        assertEquals(0f, hsv.getV(), DELTA);

        hsv.setV(2f);
        assertEquals(1f, hsv.getV(), DELTA);
    }

    @Test
    public void testFromListUsesLenientClamping() {
        List<Float> outOfRange = Arrays.asList(400f, 1.5f, -0.1f);
        Hsv hsv = Hsv.fromList(outOfRange);
        assertEquals(360f, hsv.getH(), DELTA);
        assertEquals(1f, hsv.getS(), DELTA);
        assertEquals(0f, hsv.getV(), DELTA);
    }

    @Test
    public void testFromListNormalValues() {
        List<Float> values = Arrays.asList(200f, 0.7f, 0.4f);
        Hsv hsv = Hsv.fromList(values);
        assertEquals(200f, hsv.getH(), DELTA);
        assertEquals(0.7f, hsv.getS(), DELTA);
        assertEquals(0.4f, hsv.getV(), DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromListNull() {
        Hsv.fromList(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromListWrongSize() {
        Hsv.fromList(Arrays.asList(1f, 2f));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromListNullElement() {
        Hsv.fromList(Arrays.asList(1f, null, 0.5f));
    }

    @Test
    public void testToList() {
        Hsv hsv = new Hsv(90f, 0.5f, 0.5f);
        List<Float> list = hsv.toList();
        assertEquals(3, list.size());
        assertEquals(90f, list.get(0), DELTA);
        assertEquals(0.5f, list.get(1), DELTA);
        assertEquals(0.5f, list.get(2), DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStrictConstructorRejectsOutOfRangeHue() {
        new Hsv(400f, 0.5f, 0.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStrictConstructorRejectsOutOfRangeSaturation() {
        new Hsv(180f, 1.5f, 0.5f);
    }
}
