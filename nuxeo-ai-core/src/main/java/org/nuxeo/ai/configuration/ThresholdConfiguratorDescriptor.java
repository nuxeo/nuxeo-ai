/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

@XObject("thresholdConfiguration")
public class ThresholdConfiguratorDescriptor implements Descriptor {


    @XNode("@type")
    protected String type;

    @XNode("@global")
    protected float global;

    @XNodeList(value = "thresholds/threshold", type = ArrayList.class, componentType = Threshold.class)
    protected List<Threshold> thresholds = new ArrayList<>();

    /**
     * @return global threshold for the {@link ThresholdConfiguratorDescriptor#type}
     */
    public float getGlobal() {
        return global;
    }

    /**
     * @return String as a Document Type or Facet
     */
    public String getType() {
        return type;
    }

    /**
     * @return List of {@link Threshold} objects
     */
    public List<Threshold> getThresholds() {
        return thresholds;
    }

    @Override
    public String getId() {
        return UUID.randomUUID().toString();
    }

    @XObject("threshold")
    public static class Threshold {

        @XNode("@xpath")
        protected String xpath;

        @XNode("@value")
        protected float value = -1.f;

        @XNode("@autofill")
        protected float autofillValue = -1.f;

        @XNode("@autocorrect")
        protected float autocorrect = -1.f;

        /**
         * Merges with another {@link Threshold} prioritizing max value for each value
         * @param that another {@link Threshold}
         */
        public void merge(Threshold that) {
            if (that == null) {
                return;
            }
            value = Math.max(value, that.value);
            autofillValue = Math.max(autofillValue, that.autofillValue);
            autocorrect = Math.max(autocorrect, that.autocorrect);
        }

        /**
         * @return XPath to property
         */
        public String getXPath() {
            return xpath;
        }

        /**
         * @return threshold value
         */
        public float getValue() {
            return value;
        }

        /**
         * @return Threshold for autofill
         */
        public float getAutofillValue() {
            return autofillValue;
        }

        /**
         * @return Threshold for auto-correct
         */
        public float getAutocorrect() {
            return autocorrect;
        }
    }
}
