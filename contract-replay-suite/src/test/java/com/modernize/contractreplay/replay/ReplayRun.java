package com.modernize.contractreplay.replay;

import java.util.List;

public record ReplayRun(long runId, List<Integer> batchIds) {
}
