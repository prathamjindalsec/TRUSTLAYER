package com.trustlayer.repository.projection;

import com.trustlayer.entity.enums.RiskClassification;

/** Closed Spring Data projection for a database GROUP BY risk classification query. */
public interface RiskClassificationCount {

    RiskClassification getRiskClassification();

    long getTotal();
}
