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
package org.nuxeo.ai.metadata;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class Caption implements Serializable {

    private static final long serialVersionUID = 4112019210021231L;

    public static final String VTT_KEY_PROP = "vtt";

    public static final String CAPTIONS_PROP = "cap:captions";

    protected long start;

    protected long end;

    protected List<String> lines;

    public Caption(long start, long end, List<String> lines) {
        Objects.requireNonNull(lines);
        this.start = start;
        this.end = end;
        this.lines = lines;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }
}
