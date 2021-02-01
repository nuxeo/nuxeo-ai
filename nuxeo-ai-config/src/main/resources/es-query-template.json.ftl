{
  <#if agg??>
  "aggs": {
    "by": {
      "date_histogram": {
        "field": "eventDate",
        "format": "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "interval": "day",
        "min_doc_count": 0
      }
    }
  },
  </#if>
  "query": {
    "bool": {
      "must": [
        <#if from?? && to??>
          {
            "range": {
              "eventDate": {
                "from": "${from}",
                "to": "${to}"
              }
            }
          },
        </#if>
        <#if value??>
          {
            "term": {
              "extended.value": ${value}
            }
          },
        </#if>
        <#if modelName??>
          {
            "term": {
              "extended.model": "${modelName}"
            }
          },
        </#if>
        {
          "terms": {
            "eventId": [${eventIds}]
          }
        }
      ]
    }
  }
}