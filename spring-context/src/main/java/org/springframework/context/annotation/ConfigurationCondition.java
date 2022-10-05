/*
 * Copyright 2002-2018 the original author or authors.
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

/**
 * A {@link Condition} that offers more fine-grained control when used with
 * {@code @Configuration}. Allows certain {@link Condition Conditions} to adapt when they match
 * based on the configuration phase. For example, a condition that checks if a bean
 * has already been registered might choose to only be evaluated during the
 * {@link ConfigurationPhase#REGISTER_BEAN REGISTER_BEAN} {@link ConfigurationPhase}.
 *
 * @author Phillip Webb
 * @since 4.0
 * @see Configuration
 */
public interface ConfigurationCondition extends Condition {

	/**
	 * 返回条件对象所作用的阶段，一共有2个阶段：解析配置阶段、注册bean解析，参考{@link ConfigurationPhase}
	 *
	 * Return the {@link ConfigurationPhase} in which the condition should be evaluated. —— 返回应在其中评估条件的 {@link ConfigurationPhase}。
	 */
	ConfigurationPhase getConfigurationPhase();


	/**
	 * 条件对象的作用阶段
	 * 例如：当前的ConditionEvaluator#shouldSkip()阶段是解析配置，但是条件对象的作用阶段是注册Bean，那么就不会执行条件对象的matches()方法
	 *
	 * The various configuration phases where the condition could be evaluated.
	 *
	 * 可以评估条件的各种配置阶段
	 */
	enum ConfigurationPhase {

		/**
		 * The {@link Condition} should be evaluated as a {@code @Configuration}
		 * class is being parsed.
		 * <p>If the condition does not match at this point, the {@code @Configuration}
		 * class will not be added.
		 *
		 * {@link Condition} 应该被评估为 {@code @Configuration} 类正在被解析。
		 * <p>如果此时条件不匹配，则不会添加 {@code @Configuration} 类。
		 */
		PARSE_CONFIGURATION/* 解析配置 */,

		/**
		 * The {@link Condition} should be evaluated when adding a regular
		 * (non {@code @Configuration}) bean. The condition will not prevent
		 * {@code @Configuration} classes from being added.
		 * <p>At the time that the condition is evaluated, all {@code @Configuration}s
		 * will have been parsed.
		 *
		 * 在添加常规（非 {@code @Configuration}）bean 时，应评估 {@link Condition}。该条件不会阻止添加 {@code @Configuration} 类。
		 * <p>在评估条件时，所有 {@code @Configuration} 都将被解析。
		 */
		REGISTER_BEAN /* 注册Bean */
	}

}
