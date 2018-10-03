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

/**
 * The Colors object contains the following parameters:
 * <ul>
 * <li><strong>dominant	</strong> dominant color, defined by its RGB values and HEX code</li>
 * <li><strong>accent</strong>	list of accent colors</li>
 * <li><strong>other</strong> list of secondary colors</li>
 *
 * @author jgarzon@nuxeo.com
 */
public class Colors {
    /**
     * dominant color, defined by its RGB values and HEX code
     */
    private Color dominant;

    /**
     * array of accent colors
     */
    private List<Color> accent;

    /**
     * array of secondary colors
     */
    private List<Color> other;

    public Color getDominant() {
        return dominant;
    }

    public void setDominant(Color dominant) {
        this.dominant = dominant;
    }

    public List<Color> getAccent() {
        return accent;
    }

    public void setAccent(List<Color> accent) {
        this.accent = accent;
    }

    public List<Color> getOther() {
        return other;
    }

    public void setOther(List<Color> other) {
        this.other = other;
    }
}
