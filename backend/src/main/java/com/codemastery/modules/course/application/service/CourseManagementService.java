package com.codemastery.modules.course.application.service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class CourseManagementService {

    public <T> T withCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
        throw new UnsupportedOperationException();
    }

    public <T> T tryWithCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
        throw new UnsupportedOperationException();
    }

    public AdaptiveConfiguration readAdaptiveConfiguration(UUID courseId) {
        throw new UnsupportedOperationException();
    }

    public void writeAdaptiveConfiguration(UUID courseId, AdaptiveConfiguration expected,
                                           boolean adaptiveEnabled, Map<String, Object> adaptiveSettings) {
        throw new UnsupportedOperationException();
    }

    public record AdaptiveConfiguration(UUID courseId, boolean adaptiveEnabled, Map<String, Object> adaptiveSettings) {}

    public static final class ConfigurationBusy extends RuntimeException {
        public ConfigurationBusy() {
            super("Course configuration is busy", null, false, false);
        }
    }
}
