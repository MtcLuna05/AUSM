package com.l.ausm.impl.pipeline.shader;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CustomUniformSetTest {
    @Test
    void logicalOperatorsConsumeRightHandSideWhenShortCircuitResultIsKnown() throws Exception {
        Map<String, float[]> variables = new HashMap<>();
        variables.put("worldTime", new float[]{6000.0f});

        assertArrayEquals(
                new float[]{0.0f},
                evaluate("worldTime >= 12485 && worldTime < 13085", variables)
        );
        assertArrayEquals(
                new float[]{1.0f},
                evaluate("(worldTime >= 0 && worldTime < 12485) || worldTime >= 23515", variables)
        );
    }

    @Test
    void evaluatesMakeUpStyleLightMixExpressions() throws Exception {
        Map<String, float[]> variables = new HashMap<>();
        variables.put("worldTime", new float[]{6000.0f});

        variables.put("light_mix_a", evaluate("if((worldTime >= 0 && worldTime < 12485) || worldTime >= 23515, 1.0, 0.0)", variables));
        variables.put("light_mix_b", evaluate("if(worldTime >= 12485 && worldTime < 13085, 1.0 - ((worldTime - 12485) * 0.0016666666666666668), 0.0)", variables));
        variables.put("light_mix_c", new float[]{0.0f});
        variables.put("light_mix_d", evaluate("if(worldTime >= 22915 && worldTime < 23515, (worldTime - 22915) * 0.0016666666666666668, 0.0)", variables));
        variables.put("light_mix_e", evaluate("max(light_mix_a, light_mix_b)", variables));
        variables.put("light_mix_f", evaluate("max(light_mix_c, light_mix_d)", variables));

        assertArrayEquals(
                new float[]{1.0f},
                evaluate("max(light_mix_e, light_mix_f)", variables)
        );
    }

    @Test
    void supportsOptiFineChainedIfAndAdditionalMathFunctions() throws Exception {
        Map<String, float[]> variables = new HashMap<>();
        variables.put("frameMod", new float[]{3.0f});
        variables.put("softLodScale", new float[]{4.0f});

        assertArrayEquals(
                new float[]{0.625f},
                evaluate("if(frameMod == 0, 0.0625, frameMod == 3, 0.625, 0.0)", variables)
        );
        assertArrayEquals(
                new float[]{2.0f},
                evaluate("log(softLodScale) / 0.69314718", variables),
                0.0001f
        );
        assertArrayEquals(
                new float[]{(float) Math.atan(2.0f)},
                evaluate("atan(2.0)", variables),
                0.0001f
        );
    }

    @Test
    void supportsOptiFineMatrixComponentScalarNames() throws Exception {
        Map<String, float[]> variables = new HashMap<>();
        variables.put("gbufferProjection.1.1", new float[]{6.0f});

        assertArrayEquals(
                new float[]{6.0f},
                evaluate("gbufferProjection.1.1", variables)
        );
        assertArrayEquals(
                new float[]{1.0f / (float) Math.atan(1.0f / 6.0f) * 0.5f},
                evaluate("1.0 / atan(1.0 / gbufferProjection.1.1) * 0.5", variables),
                0.0001f
        );
    }

    private static float[] evaluate(String expression, Map<String, float[]> variables) throws Exception {
        Class<?> evaluator = Class.forName("com.l.ausm.impl.pipeline.shader.CustomUniformSet$ExpressionEvaluator");
        Method method = evaluator.getDeclaredMethod("tryEvaluateAny", String.class, Map.class);
        method.setAccessible(true);
        float[] result = (float[]) method.invoke(null, expression, variables);
        if (result.length == 0) {
            throw new AssertionError("Expression did not evaluate: " + expression + " with " + Arrays.toString(variables.keySet().toArray()));
        }
        return result;
    }
}
