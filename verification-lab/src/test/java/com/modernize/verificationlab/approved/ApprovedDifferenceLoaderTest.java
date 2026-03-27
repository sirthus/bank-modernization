package com.modernize.verificationlab.approved;

import com.modernize.verificationlab.model.ApprovedDifference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedDifferenceLoaderTest {

    @Test
    void load_returnsEmptyList_whenNoFileExists() {
        // DS-001 has no approved-differences file — this must not throw
        List<ApprovedDifference> result = ApprovedDifferenceLoader.load("ds-001");
        assertThat(result).isEmpty();
    }

    @Test
    void load_returnsEmptyList_forUnknownDataset() {
        List<ApprovedDifference> result = ApprovedDifferenceLoader.load("ds-nonexistent");
        assertThat(result).isEmpty();
    }
}
