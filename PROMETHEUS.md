# Nuxeo AI Prometheus metrics (LTS 2027)

Since Nuxeo LTS 2027 the Prometheus reporter no longer goes through OpenCensus: it reads the Dropwizard
registry directly, so the **Prometheus names** of the Nuxeo AI metrics change. The metrics themselves (what is
counted, when) are unchanged, and the JMX, Graphite, Datadog, Dynatrace, Stream and CloudWatch reporters keep
their current names. Update Prometheus dashboards and alerts that use the old names.

The platform rules are described in the Nuxeo Platform `PROMETHEUS.md`
(`modules/runtime/nuxeo-runtime-metrics/PROMETHEUS.md` in nuxeo-lts, branch `2027`). In short:

- the `dropwizard5_` prefix and the per-type suffixes (`_gauge`, `_counter`, `_meter`, ...) are dropped;
- the dotted name is **lowercased** and every character outside `[a-zA-Z0-9_]` becomes `_`
  (`nuxeo.ai.aws.rekognition.rekognitionImgCalls` → `nuxeo_ai_aws_rekognition_rekognitionimgcalls`);
- gauges and counters have no suffix (a Dropwizard counter is exported as a Prometheus `gauge`);
- timers and histograms become a Prometheus summary: the base name with a `quantile` label plus a `_count`
  series; `min`/`max`/`mean`/`stddev` and the `m1`/`m5`/`m15` rates are no longer exported (use `rate()` on
  `_count`); **timers are in seconds** (they were in nanoseconds);
- the quantiles follow the `<deny><expansion>` metrics filter (`default` filter: 0.5, 0.95, 0.99).

Example: `dropwizard5_nuxeo_ai_aws_rekognition_rekognitionImgCalls_counter` →
`nuxeo_ai_aws_rekognition_rekognitionimgcalls`.

## Nuxeo AI metrics

`<computation>` is the stream computation name built by Nuxeo AI: `ai/<name>_<stream-in>[_<stream-out>]`, where
`<name>` is the enrichment provider name (enrichment metrics) or the function class simple name (function metrics),
and `/`, `.` and `,` inside each part are replaced by `-`. `<event>` and `<supplier>` are the event pipe event and
supplier names. In Prometheus names all of them are lowercased and sanitized like the rest of the name.

For example, the `aws.imageLabels` provider reading `ai/images` and writing `ai/enrichment-in` registers
`nuxeo.ai.enrichment.ai/aws-imageLabels_ai-images_ai-enrichment-in.called`, previously exported as
`dropwizard5_nuxeo_ai_enrichment_ai_aws_imageLabels_ai_images_ai_enrichment_in_called_gauge` and now as
`nuxeo_ai_enrichment_ai_aws_imagelabels_ai_images_ai_enrichment_in_called`.

| Dropwizard metric | Kind | Prometheus name (LTS 2027) |
|---|---|---|
| `nuxeo.ai.enrichment.<computation>.called` / `success` / `retries` / `errors` / `fatal` / `circuitbreaker` / `produced` / `unsupported` / `cacheHit` | gauge | `nuxeo_ai_enrichment_<computation>_called` / `_success` / `_retries` / `_errors` / `_fatal` / `_circuitbreaker` / `_produced` / `_unsupported` / `_cachehit` |
| `nuxeo.ai.streams.func.<computation>.called` / `errors` / `produced` | gauge | `nuxeo_ai_streams_func_<computation>_called` / `_errors` / `_produced` |
| `nuxeo.ai.streams.<event>.<supplier>.events` / `consumed` | gauge | `nuxeo_ai_streams_<event>_<supplier>_events` / `_consumed` |
| `nuxeo.ai.insight.insightPredictionTime` | timer | `nuxeo_ai_insight_insightpredictiontime{quantile}` (seconds) + `_count` |
| `nuxeo.ai.insight.insightPreConversionTime` | timer | `nuxeo_ai_insight_insightpreconversiontime{quantile}` (seconds) + `_count` |
| `nuxeo.ai.aws.rekognition.rekognitionGlobalCalls` | counter | `nuxeo_ai_aws_rekognition_rekognitionglobalcalls` |
| `nuxeo.ai.aws.rekognition.rekognitionImgCalls` | counter | `nuxeo_ai_aws_rekognition_rekognitionimgcalls` |
| `nuxeo.ai.aws.rekognition.rekognitionImg{Label,Text,Face,Celebrities,Unsafe}DetectionCounter` | counter | `nuxeo_ai_aws_rekognition_rekognitionimg{label,text,face,celebrities,unsafe}detectioncounter` |
| `nuxeo.ai.aws.rekognition.rekognitionVideoCalls` | timer | `nuxeo_ai_aws_rekognition_rekognitionvideocalls{quantile}` (seconds) + `_count` |
| `nuxeo.ai.aws.rekognition.rekognitionVideo{Face,Label,Celebrities,Unsafe,Segment}DetectionCall` | timer | `nuxeo_ai_aws_rekognition_rekognitionvideo{face,label,celebrities,unsafe,segment}detectioncall{quantile}` (seconds) + `_count` |
| `nuxeo.ai.aws.comprehend.comprehend{Total,Sentiment,Keyphrase,Entities}Units` | histogram | `nuxeo_ai_aws_comprehend_comprehend{total,sentiment,keyphrase,entities}units{quantile}` + `_count` |
| `nuxeo.ai.aws.textract.textractGlobalCalls` | counter | `nuxeo_ai_aws_textract_textractglobalcalls` |
| `nuxeo.ai.aws.transcribe.transcribeGlobalCalls` | counter | `nuxeo_ai_aws_transcribe_transcribeglobalcalls` |
| `nuxeo.ai.aws.translate.translateTotalChars` | histogram | `nuxeo_ai_aws_translate_translatetotalchars{quantile}` + `_count` |
| `nuxeo.ai.gcp.vision.visionGlobalCalls` | counter | `nuxeo_ai_gcp_vision_visionglobalcalls` |
| `nuxeo.ai.gcp.vision.{face,imageProperties,labels,landmark,logo,objectLocalization,text,cropHints,safeSearch}Calls` | counter | `nuxeo_ai_gcp_vision_{face,imageproperties,labels,landmark,logo,objectlocalization,text,crophints,safesearch}calls` |
| `nuxeo.ai.sightengine.visionGlobalCalls` | counter | `nuxeo_ai_sightengine_visionglobalcalls` |

The Nuxeo AI pipelines also feed the platform stream metrics (`nuxeo.streams.*`); their new names are listed in
the platform `PROMETHEUS.md`.
