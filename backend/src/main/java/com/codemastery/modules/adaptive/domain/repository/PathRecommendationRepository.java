package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.PathRecommendation;

public interface PathRecommendationRepository {

    void append(PathRecommendation recommendation);
}