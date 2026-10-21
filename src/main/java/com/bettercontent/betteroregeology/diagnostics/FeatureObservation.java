package com.bettercontent.betteroregeology.diagnostics;

/** Pure projection of values already observed by the owning native decision. */
public final class FeatureObservation {
    private FeatureObservation() {}
    public static String fields(int candidates, int unavailable, int calls, int returnedTrue) { return PlaytestLog.fields("candidates", candidates, "bounds_or_write_veto", unavailable, "host_or_exposure_veto", candidates - unavailable - calls, "placement_calls", calls, "native_return_true", returnedTrue); }
    
}
