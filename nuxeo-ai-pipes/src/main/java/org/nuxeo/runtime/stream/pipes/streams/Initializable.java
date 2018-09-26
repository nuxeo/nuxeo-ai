package org.nuxeo.runtime.stream.pipes.streams;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * An interface to indicate the class can be initialized with a Map of options
 */
public interface Initializable {

    void init(Map<String, String> options);

    default List<String> propsList(String propsList) {
        if (StringUtils.isNotBlank(propsList)) {
            String[] props = propsList.split(",");
            return Arrays.asList(props);
        } else {
            return Collections.emptyList();
        }
    }

}
