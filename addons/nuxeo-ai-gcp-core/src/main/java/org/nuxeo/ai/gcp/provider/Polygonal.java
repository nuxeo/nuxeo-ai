/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.gcp.provider;

import java.util.List;
import org.nuxeo.ai.metadata.AIMetadata;
import com.google.cloud.vision.v1.BoundingPoly;
import com.google.cloud.vision.v1.Vertex;

/**
 * Marker interface for Enrichments with bounding box available
 */
public interface Polygonal {

    /**
     * @param poly {@link BoundingPoly}
     * @return {@link org.nuxeo.ai.metadata.AIMetadata.Box}
     */
    default AIMetadata.Box getBox(BoundingPoly poly) {
        List<Vertex> vertices = poly.getVerticesList();
        if (vertices.size() != 4) {
            return new AIMetadata.Box(0, 0, 0, 0);
        }

        float x0 = vertices.get(0).getX();
        float y0 = vertices.get(0).getY();
        float x1 = vertices.get(1).getX();
        float width = x1 - x0;
        float y2 = vertices.get(2).getY();
        float height = y2 - y0;

        return new AIMetadata.Box(width, height, x0, y0);
    }
}
