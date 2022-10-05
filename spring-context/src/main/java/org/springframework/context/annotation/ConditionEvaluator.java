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

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	private final ConditionContextImpl context;

	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
			@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
	}


	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
	 * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		return shouldSkip(metadata, null);
	}

	/**
	 * 1、根据{@link Conditional}注解中的条件对象，判定是否应该跳过(忽略)当前项（配置类/bd）：
	 *
	 * （1）true：跳过。如果当前是个配置类，则代表，不再解析当前配置类了！
	 * 只有有一个@Conditional中的条件对象#matches()返回false，就代表不匹配，则跳过当前项
	 *
	 * （2）false：不跳过。如果当前是个配置类，则代表，会往下解析当前配置类！
	 * 不存在@Conditional，或者@Conditional中的所有条件对象#matches()都返回true，都匹配，才不跳过当前项
	 *
	 * 注意：⚠️当前方法，一般用于2个场景判断：1、不解析配置类，以及由于不解析配置类而导致的不注入配置类到IOC容器中；2、阻断bean注入Spring容器
	 * 注意：⚠️解析完配置类之后，是会把配置类注入到IOC容器中的；如果跳过，不解析某个配置类，那么该配置类也不会注入到IOC容器中！
	 *
	 * Determine if an item should be skipped based on {@code @Conditional} annotations. —— 根据 {@code @Conditional} 注解，确定是否应跳过某个项目
	 *
	 * @param metadata the meta data
	 * @param phase the phase of the call
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata/* 配置类的注解元数据 */, @Nullable ConfigurationPhase phase) {

		/* 1、配置类上不存在注解；或者是存在注解，但是不存在@Conditional，就直接不跳过当前配置类（不跳过当前配置类，意味着要解析当前配置类） */

		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			// 元数据为空，或者元数据不为空，且配置类中"不存在@Conditional"，则返回false
			// false在上层函数中代表不跳过！true代表跳过！
			return false;
		}

		/* 2、存在@Conditional */

		/*

		2.1、当前配置阶段为null的话，就评估当前@Conditional的配置阶段(作用阶段)。例如是配置在@Configuration类上，那么就是解析配置阶段，否则其它的话，则是注册bean阶段。
		（1）
		（2）

		*/

		/**
		 * 1、如果是{@link ConfigurationClassParser#processConfigurationClass(ConfigurationClass, Predicate)}进来的话，
		 * 那么 phase = ConfigurationPhase.PARSE_CONFIGURATION，代表当前是解析配置类
		 */
		if (phase == null) {
			/*

			(1)如果当前@@Conditional修饰的类中修饰了@Component、@ComponentScan、@Import、@ImportResource、@Bean中的任一个注解，就代表是配置类，
			就将当前的配置阶段设置为"解析配置阶段"，然后进行递归，带着明确的配置阶段，重新判断是否应该跳过当前项

			*/
			// 判断metadata是否是AnnotationMetadata实例，是AnnotationMetadata实例，就代表当前项有注解修饰，可以进行下一步ConfigurationClassUtils.isConfigurationCandidate()判断操作
			if (metadata instanceof AnnotationMetadata &&
					// 判断是不是一个配置类
					//（1）检查bean是不是一个接口，如果是，返回false，代表当前类不是一个配置类
					//（2）检查bean中是否包含@Component、@ComponentScan、@Import、@ImportResource中的任意一个，包含的话，就返回true，代表当前类是一个配置类，按照配置类的形式进行解析！
					//（3）检查bean中是否有@Bean，是的话，就返回true，也代表当前类是一个配置类，按照配置类的形式进行解析！
					ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {

				// 将当前配置阶段，标记为"解析配置类阶段"，然后递归调用，继续判定是否应该跳过当前项
				// 题外：递归调用，判断是否应该跳过
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION/* 解析配置 */);
			}

			/*

			(2)如果当前@@Conditional修饰的类，如果不包含上述注解，也就是不是一个配置类，
			那么就将当前的配置阶段设置为"注册Bean阶段"，然后进行递归，带着明确的配置阶段，重新判断是否应该跳过当前项

			*/
			// 将当前配置阶段，标记为"注册bean阶段"，然后递归调用，继续判定是否应该跳过当前项
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN/* 注册Bean */);
		}

		/* 2.2、获取@Conditional中配置的所有条件类的全限定类名，然后通过反射，创建条件对象 */
		// 存放条件对象
		List<Condition> conditions = new ArrayList<>();
		/**
		 * 1、一个类上只能写一个@Conditional，不过一个@Conditional可以配置多个条件类
		 *
		 * 2、getConditionClasses(metadata)：获取@Conditional中配置的所有条件类的全限定类名
		 */
		// 遍历@Conditional中配置的所有条件类的全限定类名
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			for (String conditionClass/* @Condition中条件类的全限定类名 */ : conditionClasses) {
				// ⚠️通过@Condition中条件类的全限定类名和反射，创建条件对象
				Condition condition = getCondition(conditionClass/* 条件类的全限定类名，用于反射创建条件对象 */, this.context.getClassLoader());
				conditions.add(condition);
			}
		}

		// 对相关的条目进行排序操作
		AnnotationAwareOrderComparator.sort(conditions);

		/*

		2.3、遍历条件对象，如果"条件对象没有作用阶段 || 条件对象的执行阶段和当前阶段相同"，那么就执行条件对象的matches()，判断当前项是否满足条件，是否需要跳过
		（1）只要其中有一个条件类对象的matches()返回false，则代表不匹配，那么当前函数就返回true，跳过当前项的解析
		（2）只有所有的条件对象的matches()都返回true，也就是都匹配，当前函数才会返回false，也就是不跳过当前项，进行当前项的解析！

		*/

		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;

			// 如果条件对象实现了ConfigurationCondition，则执行ConfigurationCondition#getConfigurationPhase()，获取ConfigurationPhase
			// 注意：⚠️ConfigurationCondition extends Condition
			if (condition instanceof ConfigurationCondition) {
				requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase/* 获取配置阶段 */();
			}

			/**
			 * 1、condition.matches(this.context, metadata)：执行条件对象的matches()方法
			 * >>> true：代表匹配，满足条件，不跳过
			 * >>> false：代表不匹配，不满足条件，跳过
			 * （由于会取反，所以matches()返回true代表匹配，所以不跳过；返回false代表不匹配，所以跳过）
			 */
			// 1、"当前条件对象没有声明作用的阶段 || 或者当前条件对象声明的作用阶段和当前阶段正好相同"，那就去执行条件对象的matches()，来判断当前项是否应该跳过！
			// 2、如果当前条件对象声明的作用阶段，与当前阶段不符合，则不去执行条件对象的matches()，也就是跳过不执行当前条件对象！
			if ((requiredPhase == null || requiredPhase == phase) && /* ⚠️ */!condition.matches(this.context, metadata)) {
				// 一共有的逻辑是：
				// 1、当前条件对象没有声明作用的阶段
				// 2、当前条件对象声明的作用阶段和当前阶段正好相同
				// 3、执行条件对象的matches()，不匹配，返回false
				// ⚠️如果【1、2】或者【1、3】成立，则在此函数的上层跳过当前项
				return true;
			}
		}

		return false;
	}

	/**
	 * 获取@Conditional的value属性值，也就是配置的所有条件类的全限定类名
	 */
	@SuppressWarnings("unchecked")
	private List<String[]> getConditionClasses/* 获取条件类 */(AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes/* 获取所有注解属性 */(Conditional.class.getName(), true);
		// ⚠️获取@Conditional的value属性值，也就是条件类数组
		Object values = (attributes != null ? attributes.get("value") : null);
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

	/**
	 * 根据条件类的全限定类名，反射实例化条件对象
	 *
	 * @param conditionClassName			条件类的全限定类名
	 * @param classloader					类加载器
	 */
	private Condition getCondition(String conditionClassName/* 条件类的全限定类名 */, @Nullable ClassLoader classloader) {
		// ⚠️通过全限定类名称得到对应的Class对象
		Class<?> conditionClass = ClassUtils.resolveClassName/* 解析类名 */(conditionClassName, classloader);
		// ⚠️通过Class对象，反射创建实例
		return (Condition) BeanUtils.instantiateClass/* 实例化类 */(conditionClass);
	}


	/**
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;

		private final Environment environment;

		private final ResourceLoader resourceLoader;

		@Nullable
		private final ClassLoader classLoader;

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
				@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

			this.registry = registry;
			this.beanFactory = deduceBeanFactory(registry);
			this.environment = (environment != null ? environment : deduceEnvironment(registry));
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory) {
				return (ConfigurableListableBeanFactory) source;
			}
			if (source instanceof ConfigurableApplicationContext) {
				return (((ConfigurableApplicationContext) source).getBeanFactory());
			}
			return null;
		}

		private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable) {
				return ((EnvironmentCapable) source).getEnvironment();
			}
			return new StandardEnvironment();
		}

		private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader) {
				return (ResourceLoader) source;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
				@Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
