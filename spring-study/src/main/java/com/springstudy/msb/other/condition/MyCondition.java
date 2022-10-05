package com.springstudy.msb.other.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/5 17:33
 */
public class MyCondition implements Condition {

	/**
	 * Determine if the condition matches.
	 * <p>
	 * 判断条件是否匹配。
	 *
	 * @param context  the condition context
	 *                 判断条件能使用的上下文环境
	 * @param metadata the metadata of the {@link AnnotationMetadata class}
	 *                 or {@link MethodMetadata method} being checked
	 *                 注解所在位置的注解元数据信息
	 * @return {@code true} if the condition matches and the component can be registered,
	 * or {@code false} to veto the annotated component's registration
	 * <p>
	 * {@code true} 如果条件匹配并且组件可以注册，或 {@code false} 否决注解组件的注册
	 * <p>
	 * true：代表匹配，不跳过，接着往下进行解析
	 * false：代表不匹配，跳过，不往下进行解析了
	 */
	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return false;
	}

}