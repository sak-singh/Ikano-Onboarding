package com.ikano.onboarding.config;

import java.util.List;

/** Full flow definition for a country (private individual only, for now). */
public record FlowDefinition(String title, List<StepDefinition> steps) {

    public StepDefinition stepAt(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new IndexOutOfBoundsException("No step at index " + index);
        }
        return steps.get(index);
    }

    public boolean isLastStep(int index) {
        return index == steps.size() - 1;
    }
}

