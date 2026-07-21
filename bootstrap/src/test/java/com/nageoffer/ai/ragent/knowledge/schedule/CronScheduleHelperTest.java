/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.knowledge.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class CronScheduleHelperTest {

    @Test
    void shouldReturnNullForBlankCronOrMissingStartTime() {
        Date now = dateTime(2026, 1, 1, 10, 0, 0);

        assertThat(CronScheduleHelper.nextRunTime(null, now)).isNull();
        assertThat(CronScheduleHelper.nextRunTime("   ", now)).isNull();
        assertThat(CronScheduleHelper.nextRunTime("0 * * * * *", null)).isNull();
    }

    @Test
    void shouldCalculateNextRunTimeFromCron() {
        Date from = dateTime(2026, 1, 1, 10, 15, 30);

        Date next = CronScheduleHelper.nextRunTime("0 30 10 * * *", from);

        assertThat(next).isEqualTo(dateTime(2026, 1, 1, 10, 30, 0));
    }

    @Test
    void shouldUseNextDayWhenCronTimeAlreadyPassed() {
        Date from = dateTime(2026, 1, 1, 10, 30, 0);

        Date next = CronScheduleHelper.nextRunTime("0 30 10 * * *", from);

        assertThat(next).isEqualTo(dateTime(2026, 1, 2, 10, 30, 0));
    }

    @Test
    void shouldTreatBlankCronOrMissingStartAsTooFrequent() {
        Date now = dateTime(2026, 1, 1, 10, 0, 0);

        assertThat(CronScheduleHelper.isIntervalLessThan("", now, 60)).isTrue();
        assertThat(CronScheduleHelper.isIntervalLessThan("0 * * * * *", null, 60)).isTrue();
    }

    @Test
    void shouldCompareConsecutiveCronIntervals() {
        Date from = dateTime(2026, 1, 1, 10, 0, 0);

        assertThat(CronScheduleHelper.isIntervalLessThan("0 * * * * *", from, 120)).isTrue();
        assertThat(CronScheduleHelper.isIntervalLessThan("0 * * * * *", from, 60)).isFalse();
    }

    private static Date dateTime(int year, int month, int day, int hour, int minute, int second) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }
}
