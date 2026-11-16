package com.pawly.app.care.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightMeasurementParserTest {
    @Test fun parsesDecimalCommaWithUnit() {
        assertEquals(18.4f, WeightMeasurementParser.parse("Peso: 18,4 kg")!!, 0.001f)
    }

    @Test fun parsesDecimalDotWithUnit() {
        assertEquals(18.4f, WeightMeasurementParser.parse("18.4 kg")!!, 0.001f)
    }

    @Test fun parsesDecimalWithoutUnit() {
        assertEquals(7.25f, WeightMeasurementParser.parse("Peso 7.25")!!, 0.001f)
    }

    @Test fun ignoresValuesOutsideSupportedRange() {
        assertNull(WeightMeasurementParser.parse("0 kg"))
        assertNull(WeightMeasurementParser.parse("151 kg"))
    }

    @Test fun ignoresTextWithoutMeasurement() {
        assertNull(WeightMeasurementParser.parse("Peso pendiente de registrar"))
    }
}
