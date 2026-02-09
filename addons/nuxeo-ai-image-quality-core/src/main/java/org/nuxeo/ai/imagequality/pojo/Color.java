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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The RGB color model is an additive color model in which red, green and blue light are
 * added together in various ways to reproduce a broad array of colors. The name of the
 * model comes from the initials of the three additive primary colors, red, green, and blue.
 *
 * @author jgarzon@nuxeo.com
 */
public class Color {

    /**
     * Red
     */
    private int r;

    /**
     * Green
     */
    private int g;

    /**
     * blue
     */
    private int b;

    /**
     * Color in hexadecimal
     */
    private String hex;

    /**
     * Internal HSV representation using a dedicated value object to avoid index-order misuse.
     */
    @JsonIgnore
    private Hsv hsvObj;

    /**
     * HSV (Hue, Saturation, Value) color representation.
     * <p>
     * Ordering and ranges:
     * <ul>
     *   <li>Index 0: Hue (H) in degrees, expected range 0–360.</li>
     *   <li>Index 1: Saturation (S) as a normalized float, expected range 0.0–1.0.</li>
     *   <li>Index 2: Value (V) as a normalized float, expected range 0.0–1.0.</li>
     * </ul>
     * Consumers should read values in this exact order. Values outside these ranges are not expected
     * from the provider and should be treated cautiously.
     * </p>
     */
    public List<Float> getHsv() {
        return hsvObj == null ? null : hsvObj.toList();
    }

    /**
     * Sets HSV components in the order [H, S, V]. See field Javadoc for ranges.
     * <p>
     * Passing {@code null} clears/unsets the HSV value.
     * </p>
     *
     * @param hsv the HSV components as a list [H, S, V]
     * @throws IllegalArgumentException if {@code hsv} is non-null but does not contain exactly three
     *                                  non-null elements in the expected order
     */
    public void setHsv(List<Float> hsv) {
        if (hsv == null) {
            // Explicitly clear HSV when no data is provided
            this.hsvObj = null;
            return;
        }
        if (hsv.size() != 3) {
            throw new IllegalArgumentException(
                    "HSV list must contain exactly 3 elements (H, S, V); got size=" + hsv.size());
        }
        // Defensive null checks on elements
        if (hsv.get(0) == null || hsv.get(1) == null || hsv.get(2) == null) {
            throw new IllegalArgumentException("HSV list elements must be non-null (expected [H, S, V]).");
        }

        // Use lenient construction with clamping setters to preserve data
        // even when values have minor drift outside expected ranges
        Hsv hsvValue = new Hsv();
        hsvValue.setH(hsv.get(0));
        hsvValue.setS(hsv.get(1));
        hsvValue.setV(hsv.get(2));
        this.hsvObj = hsvValue;
    }

    /**
     * Returns the structured HSV value object (h,s,v). Preferred for internal use.
     */
    @JsonIgnore
    public Hsv getHsvObject() {
        return hsvObj;
    }

    /**
     * Sets the structured HSV value object (h,s,v). Preferred for internal use.
     */
    @JsonIgnore
    public void setHsvObject(Hsv hsv) {
        this.hsvObj = hsv;
    }

    public int getR() {
        return r;
    }

    public void setR(int r) {
        this.r = r;
    }

    public int getG() {
        return g;
    }

    public void setG(int g) {
        this.g = g;
    }

    public int getB() {
        return b;
    }

    public void setB(int b) {
        this.b = b;
    }

    public String getHex() {
        return hex;
    }

    public void setHex(String hex) {
        this.hex = hex;
    }
}
