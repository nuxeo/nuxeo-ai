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

/**
 * The Properties end point helps you determine the quality of an image
 * (blurriness, contrast, brightness) along with the main colors
 * <p>
 * ================================
 * Sharpness / Blurriness Detection
 * ================================
 * <p>
 * The Image Properties API can help you determine the perceived sharpness or blurriness
 * of an Image through the "sharpness" property.
 * <p>
 * The returned value is between 0 and 1. Images with a sharpness value closer to 1 will
 * be sharper while images with a sharpness value closer to 0 will be perceived as blurrier.
 *
 * <strong>RECOMMENDED THRESHOLDS</strong>
 * <ul>
 * <li>Below 0.4: Very blurry</li>
 * <li>Between 0.4 and 0.6: Blurry</li>
 * <li>Between 0.6 and 0.8: Slightly blurry</li>
 * <li>Between 0.8 and 0.9: Sharp</li>
 * <li>Between 0.9 and 1: Very sharp</li>
 * </ul>
 * <p>
 * =======================
 * Brightness Detection
 * =======================
 * <p>
 * The brightness of the image is returned as a between 0 and 1. Images with a value closer to 1 will be
 * brighter while images with a value closer to 0 will be darker.
 *
 * <strong>RECOMMENDED THRESHOLDS</strong>
 * <ul>
 * <li>Equal or inferior to 0.2: Very dark</li>
 * <li>Between 0.2 and 0.4: Dark</li>
 * <li>Between 0.4 and 0.6: Low brightness</li>
 * <li>Between 0.6 and 0.8: Bright</li>
 * <li>Between 0.8 and 1: Very bright</li>
 * </ul>
 * <p>
 * =======================
 * Contrast Detection
 * =======================
 * The returned value is between 0 and 1.
 *
 * <strong>RECOMMENDED THRESHOLDS</strong>
 * <ul>
 * <li>Equal or inferior to 0.3: Low contrast</li>
 * <li>Between 0.3 and 0.7: Average contrast</li>
 * <li>Between 0.7 and 1: High contrast</li>
 * </ul>
 * <p>
 * SEE:
 * https://sightengine.com/docs/reference#image-properties
 * https://sightengine.com/docs/getstarted?signup=1
 */
public class ImageProperties {
    /**
     * Below 0.4: Very blurry
     */
    public static final float SHARPNESS_VERY_BLURRY = 0.4F;


    // Sharpness / Blurriness Detection

    /**
     * Between 0.4 and 0.6: Blurry
     */
    public static final float SHARPNESS_BLURRY = 0.6F;

    /**
     * Between 0.6 and 0.8: Slightly blurry
     */
    public static final float SHARPNESS_SLIGHTLY_BLURRY = 0.8F;

    /**
     * Between 0.8 and 0.9: Sharp
     */
    public static final float SHARPNESS_SHARP = 0.9F;

    /**
     * Between 0.9 and 1: Very sharp
     */
    public static final float SHARPNESS_VERY_SHARP = 1;

    /**
     * Below 0.4: Very blurry
     */
    public static final String SHARPNESS_TXT_VERY_BLURRY = "Very blurry";

    /**
     * Between 0.4 and 0.6: Blurry
     */
    public static final String SHARPNESS_TXT_BLURRY = "Blurry";

    /**
     * Between 0.6 and 0.8: Slightly blurry
     */
    public static final String SHARPNESS_TXT_SLIGHTLY_BLURRY = "Slightly blurry";

    /**
     * Between 0.8 and 0.9: Sharp
     */
    public static final String SHARPNESS_TXT_SHARP = "Sharp";

    /**
     * Between 0.9 and 1: Very sharp
     */
    public static final String SHARPNESS_TXT_VERY_SHARP = "Very sharp";

    /**
     * Unknown sharp
     */
    public static final String SHARPNESS_TXT_UNKNOWN = "Unknown sharp";

    /**
     * Equal or inferior to 0.2: Very dark
     */
    public static final float BRIGHTNESS_VERY_DARK = 0.2F;


    // Brightness Detection

    /**
     * Between 0.2 and 0.4: Dark
     */
    public static final float BRIGHTNESS_DARK = 0.4F;

    /**
     * Between 0.4 and 0.6: Low brightness
     */
    public static final float BRIGHTNESS_LOW_BRIGHTNESS = 0.6F;

    /**
     * Between 0.6 and 0.8: Bright
     */
    public static final float BRIGHTNESS_BRIGHT = 0.8F;

    /**
     * Between 0.8 and 1: Very bright
     */
    public static final float BRIGHTNESS_VERY_BRIGHT = 1;

    /**
     * Equal or inferior to 0.2: Very dark
     */
    public static final String BRIGHTNESS_TXT_VERY_DARK = "Very dark";

    /**
     * Between 0.2 and 0.4: Dark
     */
    public static final String BRIGHTNESS_TXT_DARK = "Dark";

    /**
     * Between 0.4 and 0.6: Low brightness
     */
    public static final String BRIGHTNESS_TXT_LOW_BRIGHTNESS = "Low brightness";

    /**
     * Between 0.6 and 0.8: Bright
     */
    public static final String BRIGHTNESS_TXT_BRIGHT = "Bright";

    /**
     * Between 0.8 and 1: Very bright
     */
    public static final String BRIGHTNESS_TXT_VERY_BRIGHT = "Very bright";

    /**
     * Unknown bright
     */
    public static final String BRIGHTNESS_TXT_UNKNOWN = "Unknown bright";

    /**
     * Equal or inferior to 0.3: Low contrast
     */
    public static final float CONTRAST_LOW_CONTRAST = 0.3F;

    // Contrast Detection

    /**
     * Between 0.3 and 0.7: Average contrast
     */
    public static final float CONTRAST_AVERAGE_CONTRAST = 0.7F;

    /**
     * Between 0.7 and 1: High contrast
     */
    public static final float CONTRAST_HIGH_CONTRAST = 1;

    /**
     * Equal or inferior to 0.3: Low contrast
     */
    public static final String CONTRAST_TXT_LOW_CONTRAST = "Low contrast";

    /**
     * Between 0.3 and 0.7: Average contrast
     */
    public static final String CONTRAST_TXT_AVERAGE_CONTRAST = "Average contrast";

    /**
     * Between 0.7 and 1: High contrast
     */
    public static final String CONTRAST_TXT_HIGH_CONTRAST = "High contrast";

    /**
     * Unknown contrast
     */
    public static final String CONTRAST_TXT_UNKNOWN_CONTRAST = "Unknown contrast";

    /**
     * Success if the request was successfully handled, failure otherwise
     */
    private String status;

    /**
     * a JSON dictionary containing meta data on the request (identifying id, timestamp and number of operations performed)
     */
    private Request request;

    /**
     * a value between 0 (very blurry) and 1 (very sharp)
     *
     * <strong>RECOMMENDED THRESHOLDS</strong>
     * <ul>
     * <li>Below 0.4: Very blurry</li>
     * <li>Between 0.4 and 0.6: Blurry</li>
     * <li>Between 0.6 and 0.8: Slightly blurry</li>
     * <li>Between 0.8 and 0.9: Sharp</li>
     * <li>Between 0.9 and 1: Very sharp</li>
     * </ul>
     */
    private float sharpness;

    /**
     * a value between 0 (low contrast) and 1 (high contrast)
     *
     * <strong>RECOMMENDED THRESHOLDS</strong>
     * <ul>
     * <li>Equal or inferior to 0.2: Very dark</li>
     * <li>Between 0.2 and 0.4: Dark</li>
     * <li>Between 0.4 and 0.6: Low brightness</li>
     * <li>Between 0.6 and 0.8: Bright</li>
     * <li>Between 0.8 and 1: Very bright</li>
     * </ul>
     */
    private float brightness;

    /**
     * a value between 0 (very dark) and 1 (very bright)
     *
     * <strong>RECOMMENDED THRESHOLDS</strong>
     * <ul>
     * <li>Equal or inferior to 0.3: Low contrast</li>
     * <li>Between 0.3 and 0.7: Average contrast</li>
     * <li>Between 0.7 and 1: High contrast</li>
     * </ul>
     */
    private float contrast;

    /**
     * a JSON dictionary describing the colors of the image received
     */
    private Colors colors;

    /**
     * a JSON dictionary describing the image received
     */
    private Media media;

    private List<Faces> faces;

    /**
     * Probability that it contains a weapon
     */
    private float weapon;

    /**
     * Probability that it contains alcohol
     */
    private float alcohol;

    /**
     * Probability that it contains drugs
     */
    private float drugs;

    private Nudity nudity;

    public Nudity getNudity() {
        return nudity;
    }

    private Type type;
    private Face face;
    private Scam scam;
    private Text text;
    private Offensive offensive;

    public Face getFace() {
        return face;
    }

    public void setFace(Face face) {
        this.face = face;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Scam getScam() {
        return scam;
    }

    public void setScam(Scam scam) {
        this.scam = scam;
    }

    public Text getText() {
        return text;
    }

    public void setText(Text text) {
        this.text = text;
    }

    public Offensive getOffensive() {
        return offensive;
    }

    public void setOffensive(Offensive offensive) {
        this.offensive = offensive;
    }

    public void setNudity(Nudity nudity) {
        this.nudity = nudity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public float getSharpness() {
        return sharpness;
    }

    public void setSharpness(float sharpness) {
        this.sharpness = sharpness;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    public Colors getColors() {
        return colors;
    }

    public void setColors(Colors colors) {
        this.colors = colors;
    }

    public Media getMedia() {
        return media;
    }

    public void setMedia(Media media) {
        this.media = media;
    }

    public float getWeapon() {
        return weapon;
    }

    public void setWeapon(float weapon) {
        this.weapon = weapon;
    }

    public float getAlcohol() {
        return alcohol;
    }

    public void setAlcohol(float alcohol) {
        this.alcohol = alcohol;
    }

    public float getDrugs() {
        return drugs;
    }

    public void setDrugs(float drugs) {
        this.drugs = drugs;
    }

    /**
     * Images with a sharpness value closer to 1 will be sharper while images with a sharpness
     * value closer to 0 will be perceived as blurrier.
     * <p>
     * RECOMMENDED THRESHOLDS
     * <p>
     * Below 0.4: Very blurry
     * Between 0.4 and 0.6: Blurry
     * Between 0.6 and 0.8: Slightly blurry
     * Between 0.8 and 0.9: Sharp
     * Between 0.9 and 1: Very sharp
     */
    public String getSharpnessDescription() {
        if (sharpness < SHARPNESS_VERY_BLURRY) {
            return SHARPNESS_TXT_VERY_BLURRY;
        } else if (sharpness < SHARPNESS_BLURRY) {
            return SHARPNESS_TXT_BLURRY;
        } else if (sharpness < SHARPNESS_SLIGHTLY_BLURRY) {
            return SHARPNESS_TXT_SLIGHTLY_BLURRY;
        } else if (sharpness < SHARPNESS_SHARP) {
            return SHARPNESS_TXT_SHARP;
        } else if (sharpness <= SHARPNESS_VERY_SHARP) {
            return SHARPNESS_TXT_VERY_SHARP;
        }

        return SHARPNESS_TXT_UNKNOWN;
    }


    /**
     * Images with a value closer to 1 will be brighter while images with a value closer to 0 will be darker.
     * <p>
     * RECOMMENDED THRESHOLDS
     * <p>
     * Equal or inferior to 0.2: Very dark
     * Between 0.2 and 0.4: Dark
     * Between 0.4 and 0.6: Low brightness
     * Between 0.6 and 0.8: Bright
     * Between 0.8 and 1: Very bright
     */
    public String getBrightnessDescription() {
        if (brightness < BRIGHTNESS_VERY_DARK) {
            return BRIGHTNESS_TXT_VERY_DARK;
        } else if (brightness < BRIGHTNESS_DARK) {
            return BRIGHTNESS_TXT_DARK;
        } else if (brightness < BRIGHTNESS_LOW_BRIGHTNESS) {
            return BRIGHTNESS_TXT_LOW_BRIGHTNESS;
        } else if (brightness < BRIGHTNESS_BRIGHT) {
            return BRIGHTNESS_TXT_BRIGHT;
        } else if (brightness <= BRIGHTNESS_VERY_BRIGHT) {
            return BRIGHTNESS_TXT_VERY_BRIGHT;
        }

        return BRIGHTNESS_TXT_UNKNOWN;
    }


    /**
     * The returned value is between 0 and 1.
     * <p>
     * RECOMMENDED THRESHOLDS
     * <p>
     * Equal or inferior to 0.3: Low contrast
     * Between 0.3 and 0.7: Average contrast
     * Between 0.7 and 1: High contrast
     */
    public String getContrastDescription() {
        if (contrast < CONTRAST_LOW_CONTRAST) {
            return CONTRAST_TXT_LOW_CONTRAST;
        } else if (contrast < CONTRAST_AVERAGE_CONTRAST) {
            return CONTRAST_TXT_AVERAGE_CONTRAST;
        } else if (contrast <= CONTRAST_HIGH_CONTRAST) {
            return CONTRAST_TXT_HIGH_CONTRAST;
        }

        return CONTRAST_TXT_UNKNOWN_CONTRAST;
    }

    public boolean isSuitableForCommercialUse() {
        short metricsFailed = 0;

        if (contrast < CONTRAST_AVERAGE_CONTRAST) {
            metricsFailed++;
        }

        if (brightness < BRIGHTNESS_BRIGHT) {
            metricsFailed++;
        }

        if (sharpness < SHARPNESS_SHARP) {
            metricsFailed++;
        }

        return metricsFailed < 2;
    }

    public boolean hasFaces() {
        return faces != null && !faces.isEmpty();
    }

    public List<Faces> getFaces() {
        return faces;
    }

    public void setFaces(List<Faces> faces) {
        this.faces = faces;
    }
}
