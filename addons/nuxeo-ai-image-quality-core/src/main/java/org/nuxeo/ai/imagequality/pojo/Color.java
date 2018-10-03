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
