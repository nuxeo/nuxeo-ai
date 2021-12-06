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
 */
package org.nuxeo.ai.model;

import java.util.Objects;
import javax.annotation.Nonnull;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * A property used by a model.
 */
@XObject("property")
public class ModelProperty extends PropertyType {

    @XNode("@name")
    protected String name;

    @XNode("@type")
    protected String type;

    public ModelProperty() {
        super();
    }

    public ModelProperty(String name, String type) {
        super(name, type);
        this.name = name;
        this.type = type;
    }

    /**
     * Factory constructor
     *
     * @param name of the property
     * @param type of the property
     * @return new instance of {@link ModelProperty}
     */
    public static ModelProperty of(@Nonnull String name, @Nonnull String type) {
        return new ModelProperty(name, type);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModelProperty that = (ModelProperty) o;
        return Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "Property{" + "name='" + name + '\'' + ", type='" + type + '\'' + '}';
    }
}
