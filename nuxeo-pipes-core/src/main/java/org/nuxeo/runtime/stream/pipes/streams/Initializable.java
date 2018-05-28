package org.nuxeo.runtime.stream.pipes.streams;

import java.util.Map;

/**
 * A marker interface to indicate the class can be initialized with a Map of options
 */
public interface Initializable {

    void init(Map<String, String> options);

}
