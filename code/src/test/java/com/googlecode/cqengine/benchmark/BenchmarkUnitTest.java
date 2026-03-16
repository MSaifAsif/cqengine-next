/**
 * Copyright 2012-2015 Niall Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.cqengine.benchmark;

import com.googlecode.cqengine.benchmark.tasks.*;
import com.googlecode.cqengine.testutil.Car;
import com.googlecode.cqengine.testutil.CarFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the benchmark itself.
 *
 * @author Niall Gallagher
 */
public class BenchmarkUnitTest {

    private final Collection<Car> collection = CarFactory.createCollectionOfCars(1000);

    @ParameterizedTest
    @MethodSource("benchmarkCases")
    void testBenchmarkTask(BenchmarkTask task, int expectedCount) {
        task.init(collection);
        assertAll(
                () -> assertEquals(expectedCount, task.runQueryCountResults_IterationNaive()),
                () -> assertEquals(expectedCount, task.runQueryCountResults_IterationOptimized()),
                () -> assertEquals(expectedCount, task.runQueryCountResults_CQEngine()),
                () -> assertEquals(expectedCount, task.runQueryCountResults_CQEngineStatistics())
        );
    }
    static Stream<Arguments> benchmarkCases() {
        return Stream.of(
                Arguments.of(new HashIndex_ModelFocus(), 100),
                Arguments.of(new HashIndex_ManufacturerFord(), 300),
                Arguments.of(new NavigableIndex_PriceBetween(), 200),
                Arguments.of(new CompoundIndex_ManufacturerToyotaColorBlueDoorsThree(), 100),
                Arguments.of(new StandingQueryIndex_ManufacturerToyotaColorBlueDoorsNotFive(), 100),
                Arguments.of(new RadixTreeIndex_ModelStartsWithP(), 100),
                Arguments.of(new SuffixTreeIndex_ModelContainsG(), 100),
                Arguments.of(new HashIndex_CarId(), 1),
                Arguments.of(new Quantized_HashIndex_CarId(), 1),
                Arguments.of(new Quantized_NavigableIndex_CarId(), 3),
                Arguments.of(new NonOptimalIndexes_ManufacturerToyotaColorBlueDoorsThree(), 100),
                Arguments.of(new MaterializedOrder_CardId(), 100)
        );
    }
}
