package com.possible.dhis2int.date;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class DateConverter {

    public static ReportDateRange getDateRange(String period) throws IllegalArgumentException {
        ReportDateRange dateRange = null;
        try {
            if (period.length() == 8) {
                dateRange = getDayDateRange(period);
            } else if (period.length() == 6) {
                dateRange = getMonthDateRange(period);
            } else if (period.length() == 4) {
                dateRange = getYearDateRange(period);
            } else {
                throw new IllegalArgumentException("Period " + period + " is not valid");
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Period " + period + " is not valid");
        }

        return dateRange;
    }

    private static ReportDateRange getDayDateRange(String day) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMMdd");
        DateTime dt = formatter.parseDateTime(day);
        DateTime startDate = dt.withTimeAtStartOfDay();
        DateTime endDate = dt.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        return new ReportDateRange(startDate, endDate);
    }

    private static ReportDateRange getMonthDateRange(String month) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMM");
        DateTime dt = formatter.parseDateTime(month);
        DateTime startDate = dt.withDayOfMonth(1).withTimeAtStartOfDay();
        DateTime endDate = dt.dayOfMonth().withMaximumValue().withHourOfDay(23).withMinuteOfHour(59)
                .withSecondOfMinute(59);
        return new ReportDateRange(startDate, endDate);
    }

    private static ReportDateRange getYearDateRange(String year) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy");
        DateTime dt = formatter.parseDateTime(year);
        DateTime startDate = dt.withDayOfYear(1).withTimeAtStartOfDay();
        DateTime endDate = dt.dayOfYear().withMaximumValue().withHourOfDay(23).withMinuteOfHour(59)
                .withSecondOfMinute(59);
        return new ReportDateRange(startDate, endDate);
    }

}