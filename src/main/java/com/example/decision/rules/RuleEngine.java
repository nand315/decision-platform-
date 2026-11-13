package com.example.decision.rules;

import com.example.decision.config.ConditionConfig;
import com.example.decision.config.RuleStepConfig;
import com.example.decision.model.RuleResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class RuleEngine {

    /**
     * Evaluates an ordered list of rule steps against the given payload.
     * Rules with 'when' conditions that do not match are skipped.
     */
    public List<RuleResult> evaluate(List<RuleStepConfig> ruleSteps, Map<String, Object> payload) {
        List<RuleResult> results = new ArrayList<>();
        if (ruleSteps == null || ruleSteps.isEmpty()) {
            return results;
        }

        for (RuleStepConfig step : ruleSteps) {
            // Check conditional branching: applies only when condition holds
            if (step.getWhen() != null && !evaluateCondition(step.getWhen(), payload)) {
                // Condition not met, skip this rule
                continue;
            }

            RuleResult result = evaluateStep(step, payload);
            results.add(result);
        }

        return results;
    }

    public boolean evaluateCondition(ConditionConfig condition, Map<String, Object> payload) {
        if (condition == null || condition.getField() == null) {
            return true;
        }
        Object actual = payload.get(condition.getField());
        return checkComparison(actual, condition.getOperator(), condition.getValue());
    }

    private RuleResult evaluateStep(RuleStepConfig step, Map<String, Object> payload) {
        String field = step.getField();
        Object actual = payload.get(field);
        String op = step.getOperator() != null ? step.getOperator().trim().toLowerCase() : "==";
        Object expected = step.getValue();

        boolean pass = checkComparison(actual, op, expected);
        String reason;
        if (pass) {
            reason = String.format("Rule '%s' passed: %s %s %s (actual: %s)",
                    step.getId(), field, step.getOperator(), expected != null ? expected : "", actual);
        } else {
            if (step.getFailureReason() != null && !step.getFailureReason().isBlank()) {
                reason = step.getFailureReason();
            } else {
                reason = String.format("Rule '%s' failed: %s %s %s (actual: %s)",
                        step.getId(), field, step.getOperator(), expected != null ? expected : "", actual);
            }
        }

        return new RuleResult(
                step.getId(),
                step.getStage(),
                field,
                actual,
                step.getOperator(),
                expected,
                pass,
                reason
        );
    }

    public boolean checkComparison(Object actual, String operator, Object expected) {
        if (operator == null) {
            return Objects.equals(actual, expected);
        }

        String op = operator.trim().toLowerCase();

        switch (op) {
            case "present":
            case "not_empty":
            case "notempty":
                return actual != null && !actual.toString().trim().isEmpty();

            case "in":
                return checkIn(actual, expected);

            case "==":
            case "=":
            case "eq":
                return checkEquals(actual, expected);

            case "!=":
            case "<>":
            case "neq":
                return !checkEquals(actual, expected);

            case ">":
                return compareNumbers(actual, expected) > 0;

            case ">=":
                return compareNumbers(actual, expected) >= 0;

            case "<":
                return compareNumbers(actual, expected) < 0;

            case "<=":
                return compareNumbers(actual, expected) <= 0;

            default:
                throw new IllegalArgumentException("Unsupported rule operator: " + operator);
        }
    }

    private boolean checkEquals(Object actual, Object expected) {
        if (actual == null && expected == null) return true;
        if (actual == null || expected == null) return false;

        // Try numeric comparison if both look like numbers
        BigDecimal numA = tryParseBigDecimal(actual);
        BigDecimal numB = tryParseBigDecimal(expected);
        if (numA != null && numB != null) {
            return numA.compareTo(numB) == 0;
        }

        // Try boolean comparison
        if (actual instanceof Boolean || expected instanceof Boolean) {
            return Boolean.valueOf(actual.toString()).equals(Boolean.valueOf(expected.toString()));
        }

        // String comparison case-insensitive
        return actual.toString().trim().equalsIgnoreCase(expected.toString().trim());
    }

    @SuppressWarnings("rawtypes")
    private boolean checkIn(Object actual, Object expected) {
        if (actual == null || expected == null) return false;
        Collection collection = null;
        if (expected instanceof Collection) {
            collection = (Collection) expected;
        } else if (expected.getClass().isArray()) {
            collection = Arrays.asList((Object[]) expected);
        }

        if (collection == null) return false;

        for (Object item : collection) {
            if (checkEquals(actual, item)) {
                return true;
            }
        }
        return false;
    }

    private int compareNumbers(Object actual, Object expected) {
        BigDecimal numA = tryParseBigDecimal(actual);
        BigDecimal numB = tryParseBigDecimal(expected);

        if (numA == null || numB == null) {
            throw new IllegalArgumentException(String.format(
                    "Numeric comparison requires numbers, got actual='%s' and expected='%s'",
                    actual, expected));
        }
        return numA.compareTo(numB);
    }

    private BigDecimal tryParseBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return new BigDecimal(obj.toString());
        try {
            return new BigDecimal(obj.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

