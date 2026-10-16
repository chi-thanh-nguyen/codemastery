package com.codemastery.modules.course.domain.model;

import java.util.UUID;

public record Chapter(UUID id,
                      UUID courseId,
                      String title,
                      int position) {
}