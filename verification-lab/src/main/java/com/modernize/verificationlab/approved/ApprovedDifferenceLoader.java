package com.modernize.verificationlab.approved;

import com.modernize.verificationlab.model.ApprovedDifference;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads the approved-differences file for a dataset from the test classpath.
 *
 * Files live at: approved-differences/{datasetId}-approved.yml
 *
 * A missing file is not an error — datasets with no approved differences
 * simply have no exceptions, and any divergence will be classified as FAIL.
 *
 * An entry with approvedBy empty or null is treated as a WARNING: it
 * acknowledges the difference but has not been signed off.
 */
public class ApprovedDifferenceLoader {

    private ApprovedDifferenceLoader() {}

    @SuppressWarnings("unchecked")
    public static List<ApprovedDifference> load(String datasetId) {
        String path = "approved-differences/" + datasetId + "-approved.yml";
        InputStream stream = ApprovedDifferenceLoader.class.getClassLoader().getResourceAsStream(path);

        if (stream == null) {
            return Collections.emptyList();
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(stream);
            if (root == null || !root.containsKey("approvedDifferences")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("approvedDifferences");
            return entries.stream().map(ApprovedDifferenceLoader::mapToApprovedDifference).toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse approved differences at " + path + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ApprovedDifference mapToApprovedDifference(Map<String, Object> map) {
        ApprovedDifference ad = new ApprovedDifference();
        ad.setId((String) map.get("id"));
        ad.setField((String) map.get("field"));
        ad.setScope((String) map.get("scope"));
        ad.setExpectedValue(String.valueOf(map.getOrDefault("expectedValue", "")));
        ad.setActualValue(String.valueOf(map.getOrDefault("actualValue", "")));
        ad.setMatchType((String) map.getOrDefault("matchType", "exact"));
        ad.setClassification((String) map.get("classification"));
        ad.setRationale((String) map.get("rationale"));
        ad.setReviewCriteria((List<String>) map.get("reviewCriteria"));
        ad.setApprovedBy((String) map.get("approvedBy"));
        ad.setApprovedDate((String) map.get("approvedDate"));
        ad.setTicket((String) map.get("ticket"));
        return ad;
    }
}
