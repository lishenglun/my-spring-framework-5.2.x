/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 条件
 *
 * 一般用于：根据条件判断是否解析配置类；是否注入bean到容器中
 *
 * A single {@code condition} that must be {@linkplain #matches matched} in order
 * for a component to be registered.
 *
 * <p>Conditions are checked immediately before the bean-definition is due to be
 * registered and are free to veto registration based on any criteria that can
 * be determined at that point.
 *
 * <p>Conditions must follow the same restrictions as {@link BeanFactoryPostProcessor}
 * and take care to never interact with bean instances. For more fine-grained control
 * of conditions that interact with {@code @Configuration} beans consider the
 * {@link ConfigurationCondition} interface.
 *
 * @author Phillip Webb
 * @since 4.0
 * @see ConfigurationCondition
 * @see Conditional
 * @see ConditionContext
 */
@FunctionalInterface
public interface Condition {

	/**
	 * 判断条件是否匹配
	 *
	 * Determine if the condition matches. —— 判断条件是否匹配
	 *
	 * @param context the condition context
	 *
	 *                是一个{@link ConditionEvaluator.ConditionContextImpl}，是在执行条件对象matches()时，条件判断所使用的上下文环境，
	 *                里面包含了BeanDefinitionRegistry、ConfigurableListableBeanFactory、Environment等对，方便我们进行条件判断！
	 *
	 * @param metadata the metadata of the {@link org.springframework.core.type.AnnotationMetadata class}
	 * or {@link org.springframework.core.type.MethodMetadata method} being checked
	 *
	 *                 @Condition所在标注类的注解元数据
	 *
	 * @return {@code true} if the condition matches and the component can be registered,
	 * or {@code false} to veto the annotated component's registration
	 *
	 * {@code true} 如果条件匹配并且组件可以注册，或 {@code false} 否决注解组件的注册
	 *
	 * true：代表匹配，不跳过，接着往下进行解析
	 * false：代表不匹配，跳过，不往下进行解析了
	 *
	 */
	boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);

}
