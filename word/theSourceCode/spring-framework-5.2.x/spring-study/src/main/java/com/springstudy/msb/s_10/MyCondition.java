package com.springstudy.msb.s_10;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/24 4:37 下午
 */
public class MyCondition implements Condition {

	/**
	 * Determine if the condition matches. —— 判断条件是否匹配。
	 *
	 * @param context  the condition context
	 * @param metadata the metadata of the {@link AnnotationMetadata class}
	 *                 or {@link MethodMetadata method} being checked
	 * @return {@code true} if the condition matches and the component can be registered,
	 * or {@code false} to veto the annotated component's registration
	 */
	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return false;
	}

}