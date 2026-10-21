package com.bettercontent.betteroregeology.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class FeatureObservationTest {
    @Test public void nativeScalarsHaveExplicitStage() { var fields = FeatureObservation.fields(10, 2, 3, 3); assertTrue(fields.contains("host_or_exposure_veto=5")); assertTrue(fields.contains("placement_calls=3")); assertTrue(fields.contains("native_return_true=3")); }
    @Test public void uncertaintyAndPrivacyStayExplicit() { var fields = FeatureObservation.fields(1, 0, 1, 0); assertTrue(fields.contains("placement_calls=1")); assertTrue(fields.contains("native_return_true=0")); assertFalse(fields.contains("seed")); assertFalse(fields.contains("salt")); }
}
