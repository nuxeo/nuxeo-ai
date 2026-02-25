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

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object representing HSV (Hue, Saturation, Value).
 * <ul>
 *   <li>h: Hue in degrees, 0–360</li>
 *   <li>s: Saturation as a normalized float, 0.0–1.0</li>
 *   <li>v: Value (brightness) as a normalized float, 0.0–1.0</li>
 * </ul>
 */
public class Hsv {

    /** Hue in degrees, range 0–360. */
    private float h;

    /** Saturation as a normalized float, range 0.0–1.0. */
    private float s;

    /** Value (brightness) as a normalized float, range 0.0–1.0. */
    private float v;

    public Hsv() {
        // default constructor for Jackson
    }

    /** Strict constructor for internal usage */
    public Hsv(float h, float s, float v) {
        validateHue(h);
        validateUnitRange(s, "Saturation (s)");
        validateUnitRange(v, "Value (v)");
        this.h = h;
        this.s = s;
        this.v = v;
    }

    @JsonProperty("h")
    public float getH() {
        return h;
    }

    /**
     * Lenient setter for deserialization.
     * Values are clamped to [0, 360].
     */
    public void setH(float h) {
        this.h = clamp(h, 0f, 360f);
    }

    @JsonProperty("s")
    public float getS() {
        return s;
    }

    /**
     * Lenient setter for deserialization.
     * Values are clamped to [0.0, 1.0].
     */
    public void setS(float s) {
        this.s = clamp(s, 0f, 1f);
    }

    @JsonProperty("v")
    public float getV() {
        return v;
    }

    /**
     * Lenient setter for deserialization.
     * Values are clamped to [0.0, 1.0].
     */
    public void setV(float v) {
        this.v = clamp(v, 0f, 1f);
    }

    public static Hsv fromList(List<Float> list) {
        if (list == null) {
            throw new IllegalArgumentException("HSV list must not be null");
        }
        if (list.size() != 3) {
            throw new IllegalArgumentException(
                    "HSV list must contain exactly 3 elements (h, s, v), but size was " + list.size());
        }

        Float h = list.get(0);
        Float s = list.get(1);
        Float v = list.get(2);

        if (h == null || s == null || v == null) {
            throw new IllegalArgumentException("HSV values must not be null");
        }

        Hsv hsv = new Hsv();
        hsv.setH(h);
        hsv.setS(s);
        hsv.setV(v);
        return hsv;
    }

    public List<Float> toList() {
        return List.of(h, s, v);
    }

    /* ---------- helpers ---------- */

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static void validateHue(float h) {
        if (h < 0f || h > 360f) {
            throw new IllegalArgumentException("Hue (h) must be in range [0, 360], got: " + h);
        }
    }

    private static void validateUnitRange(float v, String name) {
        if (v < 0f || v > 1f) {
            throw new IllegalArgumentException(name + " must be in range [0.0, 1.0], got: " + v);
        }
    }
}
