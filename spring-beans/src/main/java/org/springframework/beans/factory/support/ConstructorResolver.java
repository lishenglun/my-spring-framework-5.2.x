/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import java.beans.ConstructorProperties;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * Delegate for resolving constructors and factory methods.
 * <p>Performs constructor resolution through argument matching.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 2.0
 * @see #autowireConstructor
 * @see #instantiateUsingFactoryMethod
 * @see AbstractAutowireCapableBeanFactory
 */
class ConstructorResolver {

	private static final Object[] EMPTY_ARGS = new Object[0];

	/**
	 * 缓存的参数数组中自动装配的参数标记，以后将由解析的自动装配参数替换
	 *
	 * Marker for autowired arguments in a cached argument array, to be later replaced
	 * by a {@linkplain #resolveAutowiredArgument resolved autowired argument}.
	 */
	private static final Object autowiredArgumentMarker = new Object();

	private static final NamedThreadLocal<InjectionPoint> currentInjectionPoint =
			new NamedThreadLocal<>("Current injection point");


	private final AbstractAutowireCapableBeanFactory beanFactory;

	private final Log logger;


	/**
	 * Create a new ConstructorResolver for the given factory and instantiation strategy.
	 * @param beanFactory the BeanFactory to work with
	 */
	public ConstructorResolver(AbstractAutowireCapableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.logger = beanFactory.getLogger();
	}


	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param chosenCtors chosen candidate constructors (or {@code null} if none) - 除了默认构造函数之外的候选构造函数，没有则为null
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
			@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {

		/**
		 * 实例一个BeanWrapperImpl对象
		 * 前面外部返回的BeanWrapper，其实就是这个BeanWrapperImpl
		 * 因为BeanWrapper接口
		 *
		 * bw.wrappedObject为真实的对象
		 */
		// 实例化BeanWrapper，是包装bean的容器
		BeanWrapperImpl bw = new BeanWrapperImpl();
		// 初始化包装器（给包装对象设置一些属性）
		this.beanFactory.initBeanWrapper(bw);

		/**
		 * ⚠️在外层的判断使得，Spring不存在无参构造方法进行初始化才会进入到这里
		 * 不存在无参构造方法，有可能用户存在多个有参构造方法，那到底采用哪个构造方法来实例化呢？Spring有自己的一套规则
		 * 当它找到一个构造方法，它就会把这个构造方法赋值给constructorToUse
		 */
		// spring对这个bean进行实例化使用的构造函数对象
		// 要使用的构造函数是哪一个
		Constructor<?> constructorToUse = null;
		// 对构造函数所使用的参数值进行了一层封装
		// spring执行构造函数使用的是参数封装类
		// 我的参数封装类到底是哪一个
		ArgumentsHolder argsHolderToUse = null;
		// 参与构造函数实例化过程的参数
		// 构造器使用的参数
		Object[] argsToUse = null;

		/**
		 * 确定参数值列表
		 * argsToUse可以有两种办法设置
		 * 第一种通过beanDefinition设置
		 * 第二种通过xml配置
		 */
		// 如果传入参数的话，就直接使用传入的参数
		if (explicitArgs != null) {
			// 有没有显示的参数，有的话就使用显示的参数
			argsToUse = explicitArgs;
		}
		// 没有传入参数的话就走else
		else {
			// 如果在调用getBean方法的时候没有指定，则尝试从配置文件中解析
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock) {
				/**
				 * mbd.resolvedConstructorOrFactoryMethod：缓存已解析的构造函数或工厂方法
				 * 当Spring事先确定了一个构造函数或工厂方法的时候，就会把这个构造函数或工厂方法存放在mbd.resolvedConstructorOrFactoryMethod
				 * 		一般没有
				 */
				// 获取BeanDefinition中解析完成的构造函数
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				// BeanDefinition中存在构造函数并且存在构造函数的参数，赋值进行使用
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached constructor...
					// 从缓存中找到了构造器，那么继续从缓存中寻找缓存的构造器参数
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						// mbd.preparedConstructorArguments：缓存部分准备好的构造函数参数
						// 没有缓存的参数，就需要获取配置文件中配置的参数
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			// 如果缓存中没有缓存的参数的话，即argsToResolve不为空，就需要解析配置的参数
			if (argsToResolve != null) {
				// 解析参数类型，比如将配置的String类型转换为list、boolean等类型
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);
			}
		}

		// 如果没有缓存，就需要从构造函数开始解析
		if (constructorToUse/* 要使用的构造器 */ == null || argsToUse/* 要使用的参数 */ == null) {
			// Take specified constructors, if any. - 采用指定的构造函数（如果有）。
			// 如果传入的构造器数组不为空，就使用传入的过后早期参数，否则通过反射获取class中定义的构造器
			Constructor<?>[] candidates = chosenCtors;
			/* 获取所有构造方法 */
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
					// 使用public的构造器或者所有构造器
					candidates = (mbd.isNonPublicAccessAllowed() ?
							beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Resolution of declared constructors on bean Class [" + beanClass.getName() +
							"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
				}
			}

			// 对候选构造器进行判断
			// 如果只有一个构造方法，并且显示参数为null，并且没有构造参数值，就直接返回了
			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues/* 具有构造函数参数值 */()) {
				Constructor<?> uniqueCandidate = candidates[0];
				if (uniqueCandidate.getParameterCount() == 0) {
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}

			/**
			 * autowiring：判断是否需要为构造方法注入对象
			 * chosenCtors是外面传入即那里的，存放的是采用哪个构造方法来
			 */
			// Need to resolve the constructor. - 需要解析构造函数。
			// 是否需要自动装配
			// 自动装配标识，以下有一种情况成立则为true，
			// 1、传进来构造函数，证明spring根据之前代码的判断，知道应该用哪个构造函数，
			// 2、BeanDefinition中设置为构造函数注入模型
			boolean autowiring = (chosenCtors != null ||
					mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR /* 构造方法自动注入模型 */);
			ConstructorArgumentValues resolvedValues = null;

			// 代表构造方法需要几个参数
			// 构造函数的最小参数个数
			int minNrOfArgs;
			// 如果传入了参与构造函数实例化的参数值，那么参数的数量即为最小参数个数
			if (explicitArgs != null) {
				// 显示参数不为空，直接把显示参数的个数，赋给最小构造参数个数值
				minNrOfArgs = explicitArgs.length;
			}
			else {
				/* 处理构造器的参数值 */

				/**
				 * 实例一个对象，用来存放构造方法的参数值
				 * 当中主要存放了参数值和对应的下标
				 *
				 * 例如：beanDefinition.getConstructorArgumentValues().addGenericArgumentValue("com.springstudy.dao.SystemDao"); 那么参数值为com.springstudy.dao.SystemDao
				 * 		⚠️minNrOfArgs也会等于1
				 *
				 */
				// 提取配置文件中的配置的构造函数参数
				ConstructorArgumentValues/* 构造器的参数值 */ cargs = mbd.getConstructorArgumentValues();
				// 用于承载解析后的构造函数参数的值
				resolvedValues = new ConstructorArgumentValues();	// 存放的是实际的构造参数值
				/**
				 * 已经指定给了几个参数，例如：beanDefinition.getConstructorArgumentValues().addGenericArgumentValue("com.springstudy.dao.SystemDao"); //设置构造参数
				 * 于是获取最小的构造参数，在接下来做判断，如果构造函数的参数个数小于最小的参数个数就代表该构造函数不符合选举
				 */
				// 能解析到的参数个数
				// 判断一下我的参数到底有几个
				minNrOfArgs = resolveConstructorArguments/* 处理构造器的参数值 */(beanName, mbd, bw, cargs, resolvedValues);
			}

			// 对候选的构造函数进行排序，先是访问权限后是参数个数
			// 题外：public权限参数数量由多到少
			// 题外：为什么要排序？因为要进行构造器的筛选，所以有一个排序的规则：先根据访问权限，再根据参数个数
			AutowireUtils.sortConstructors(candidates);
			// 定义一个差异变量，变量的大小决定着构造函数是否能够被使用（根据差异变量来计算出来，我到底选择哪个构造函数是最合适的）
			int minTypeDiffWeight/* 最小类型差异权重 */ = Integer.MAX_VALUE;
			// 模棱两可的构造函数
			// 不明确的构造函数集合，正常情况下差异值不可能相同
			Set<Constructor<?>> ambiguousConstructors = null;
			LinkedList<UnsatisfiedDependencyException> causes = null;

			// 遍历候选的构造器，筛选出来合适的构造器进行构造！
			for (Constructor<?> candidate : candidates) {
				// 获取构造器参数的个数
				int parameterCount = candidate.getParameterCount();

				/**
				 * 首先constructorToUse != null这个很好理解
				 * 前面已经说过首先constructorToUse主要是用来装类所有的无参构造方法
				 * 只有在他等于空的情况下，才有继续的意义，因为下面如果解析到了一个符合的构造方法就会赋值给这个变量。
				 * 故而如果这个变量不等于null就不需要再进行解析
				 * 找到一个合适的构造方法，直接使用便可以
				 */
				// 如果已经找到选用的构造函数或者需要的参数个数小于当前的构造函数参数个数则终止，前面已经经过了排序操作
				if (constructorToUse != null /* 找到了所采用的构造函数就会赋值给constructorToUse */
						&& argsToUse != null
						&& argsToUse.length > parameterCount /* 如果参数的个数比已经选定的构造函数的参数个数小就break */) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					break;
				}
				// 如果当前构造器的参数个数，小于要求的最小参数个数，则忽略当前构造器，遍历下一个
				if (parameterCount < minNrOfArgs) {
					continue;
				}

				// 存放构造函数解析完成的参数列表
				ArgumentsHolder argsHolder;
				// 获取参数列表的类型
				Class<?>[] paramTypes = candidate.getParameterTypes();
				// 存在需要解析的构造函数参数
				if (resolvedValues != null) {
					try {
						/**
						 * 判断是否加了@ConstructProperties，如果加了就把值取出来
						 */
						// 获取构造函数上的ConstructorProperties注解中的参数
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, parameterCount);
						// 如果没有上面的注解，则获取构造函数参数列表中属性的名称
						if (paramNames == null) {
							// 获取参数名称探索器
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
								/**
								 * 获取构造方法参数列表
								 */
								// 获取当前构造器的参数名称
								paramNames = pnd.getParameterNames(candidate);
							}
						}
						/**
						 * xml中由于Spring只能提供字符串的参数值，所以需要进行转换
						 * argsHolder所包含的值就是转换之后的值
						 */
						// 根据名称和数据类型创建参数持有者
						argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
								getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
					}
					catch (UnsatisfiedDependencyException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
						}
						// Swallow and try next constructor.
						if (causes == null) {
							causes = new LinkedList<>();
						}
						causes.add(ex);
						continue;
					}
				}
				else {
					// Explicit arguments given -> arguments length must match exactly.
					if (parameterCount != explicitArgs.length) {
						continue;
					}
					argsHolder = new ArgumentsHolder(explicitArgs);
				}

				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				// Choose this constructor if it represents the closest match.
				if (typeDiffWeight < minTypeDiffWeight) {
					// ⚠️赋予具体的构造方法
					constructorToUse = candidate;
					argsHolderToUse = argsHolder;
					// ⚠️构造函数的参数值
					argsToUse = argsHolder.arguments;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousConstructors = null;
				}
				else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					if (ambiguousConstructors == null) {
						ambiguousConstructors = new LinkedHashSet<>();
						ambiguousConstructors.add(constructorToUse);
					}
					ambiguousConstructors.add(candidate);
				}
			}

			// 以下两种情况会抛异常
			// 1、没有确定使用的构造函数
			// 2、存在模糊的构造函数并且不允许存在模糊的构造函数
			if (constructorToUse == null) {
				if (causes != null) {
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Could not resolve matching constructor " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
			}
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous constructor matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousConstructors);
			}

			/**
			 * 没有传入参与构造函数参数列表的参数时，对构造函数缓存到BeanDefinition中
			 * 1、缓存BeanDefinition进行实例化时使用的构造函数
			 * 2、缓存BeanDefinition代表的Bean的构造函数已解析完标识
			 * 3、缓存参与构造函数参数列表值的参数列表
			 */
			// 【argsHolderToUse != null】意味着已经取到具体的构造器对象了
			if (explicitArgs == null && argsHolderToUse != null) {
				// 将解析的构造函数加入缓存
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}

		Assert.state(argsToUse != null, "Unresolved constructor arguments");
		/**
		 * ⚠️进入instantiate()
		 */
		// 将构造的实例加入BeanWrapper中
		bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse)/* 获取到构造器之后，直接实例化了 */);
		return bw;
	}

	private Object instantiate(
			String beanName, RootBeanDefinition mbd, Constructor<?> constructorToUse, Object[] argsToUse) {

		try {
			InstantiationStrategy strategy = this.beanFactory.getInstantiationStrategy();
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse),
						this.beanFactory.getAccessControlContext());
			}
			else {
				/**
				 * instantiate=CglibSubclassingInstantiation，StrategySimpleInstantiationStrategy
				 * 		CglibSubclassingInstantiation extends StrategySimpleInstantiationStrategy，
				 * 		instantiate()的具体实现在StrategySimpleInstantiationStrategy
				 * 	 	⚠️所以走StrategySimpleInstantiationStrategy+instantiate()
				 */
				return strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via constructor failed", ex);
		}
	}

	/**
	 * Resolve the factory method in the specified bean definition, if possible.
	 * {@link RootBeanDefinition#getResolvedFactoryMethod()} can be checked for the result.
	 * @param mbd the bean definition to check
	 */
	public void resolveFactoryMethodIfPossible(RootBeanDefinition mbd) {
		Class<?> factoryClass;
		boolean isStatic;
		if (mbd.getFactoryBeanName() != null) {
			factoryClass = this.beanFactory.getType(mbd.getFactoryBeanName());
			isStatic = false;
		}
		else {
			factoryClass = mbd.getBeanClass();
			isStatic = true;
		}
		Assert.state(factoryClass != null, "Unresolvable factory class");
		factoryClass = ClassUtils.getUserClass(factoryClass);

		Method[] candidates = getCandidateMethods(factoryClass, mbd);
		Method uniqueCandidate = null;
		for (Method candidate : candidates) {
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
				if (uniqueCandidate == null) {
					uniqueCandidate = candidate;
				}
				else if (isParamMismatch(uniqueCandidate, candidate)) {
					uniqueCandidate = null;
					break;
				}
			}
		}
		mbd.factoryMethodToIntrospect = uniqueCandidate;
	}

	private boolean isParamMismatch(Method uniqueCandidate, Method candidate) {
		int uniqueCandidateParameterCount = uniqueCandidate.getParameterCount();
		int candidateParameterCount = candidate.getParameterCount();
		return (uniqueCandidateParameterCount != candidateParameterCount ||
				!Arrays.equals(uniqueCandidate.getParameterTypes(), candidate.getParameterTypes()));
	}

	/**
	 * 获取所有候选方法，包括父类的
	 *
	 * Retrieve all candidate methods for the given class, considering
	 * the {@link RootBeanDefinition#isNonPublicAccessAllowed()} flag.
	 * Called as the starting point for factory method determination.
	 */
	private Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
		if (System.getSecurityManager() != null) {
			return AccessController.doPrivileged((PrivilegedAction<Method[]>) () ->
					(mbd.isNonPublicAccessAllowed() ?
						ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods()));
		}
		else {
			return (mbd.isNonPublicAccessAllowed() ?
					ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods());
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * bean definition parameter specifies a class, rather than a "factory-bean", or
	 * an instance variable on a factory object itself configured using Dependency Injection.
	 * <p>Implementation requires iterating over the static or instance methods with the
	 * name specified in the RootBeanDefinition (the method may be overloaded) and trying
	 * to match with the parameters. We don't have the types attached to constructor args,
	 * so trial and error is the only way to go here. The explicitArgs array may contain
	 * argument values passed in programmatically via the corresponding getBean method.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean
	 * method, or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	public BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		/* 这个方法之所以这么多代码，是因为可能会有很多重载的工厂方法，它要做一系列复杂的判断，考虑多种情况，从中选择出来最合适的一个工厂方法！ */

		// 创建包装类（包装器、持有器）
		BeanWrapperImpl bw = new BeanWrapperImpl();
		// 初始化包装类
		this.beanFactory.initBeanWrapper(bw);

		// 工厂实例 —— 表示我当前工厂的bean是哪个实例
		Object factoryBean;
		// 工厂类型 —— 是哪个具体的类型的
		Class<?> factoryClass;
		// 判断是否是静态的 —— 区分到底用的是实例工厂，还是静态工厂
		boolean isStatic;

		/*

		1、判断是采用实例工厂还是静态工厂
		如果factoryBeanName(实例工厂的名字)存在，就采用实例工厂，否则采用静态工厂

		*/

		// 获取<bean factory-bean="">标签的名字 —— 实例工厂的名字，也是工厂方法所属的对象的简单名字
		// factoryBeanName：实例工厂的名字
		// 题外：静态工厂和实例工厂走的是同一套逻辑，只是根据factoryBeanName来做一个区分！
		String factoryBeanName = mbd.getFactoryBeanName();

		/* 1.1、如果factoryBeanName不为空，就代表采用的是实例工厂！ */

		if (factoryBeanName != null) {
			/**

			如果实例工厂的名字和当前要创建的bean名字相同，就抛出异常进行阻止，因为不阻止会无限递归往下创建！
			例如：

			<bean id="person2" class="com.springstudy.mashibing.s_15.factoryMethod.Person" factory-bean="person2" factory-method="getPerson">
					<constructor-arg value="wangwu"></constructor-arg>
			</bean>

			我创建当前person2对象，发现有factory-method和factory-bean，我就先去创建factory-bean实例工厂，factory-bean="person2"，我创建的实例工厂是person2，
			在创建实例工厂person2时，又发现实例工厂有factory-method和factory-bean，我就又去创建实例工厂的实例工厂！factory-bean="person2"，实例工厂的实例工厂是person2，
			我去创建实例工厂的实例工厂时，又发现实现工厂的实例工厂有factory-method和factory-bean，我就又得去创建实例工厂的实例工厂的实例工厂，如此，无限循环递归下去！

			*/
			if (factoryBeanName.equals(beanName)) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						// factory-bean 引用指向同一个 bean 定义
						"factory-bean reference points back to the same bean definition");
			}
			// 获取实例工厂对象！
			// 也就是创建<bean id="" factory-bean="">中factory-bean所指向的对象(实例工厂)，因为要依靠实例工厂去创建对象，所以要先创建实例工厂！
			factoryBean = this.beanFactory.getBean(factoryBeanName);
			// 判断是否已经创建了，如果已经创建了，就抛出异常
			// 当前要创建的bean是单例 && 当前容器(一级缓存：singletonObjects)里面已经包含了当前要创建的beanName对应的bean，就抛出异常
			if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
				throw new ImplicitlyAppearedSingletonException/* 隐式出现的单例异常 */();
			}
			// 获取实例工厂的Class对象
			factoryClass = factoryBean.getClass();
			// 表示非静态工厂
			isStatic = false;
		}
		/* 1.2、如果factoryBeanName为空，则代表采用静态工厂！ */
		else {
			// It's a static factory method on the bean class.
			// 静态方法，没有factoryBean实例

			// 判断一下有没有要实例化bean对象的Class值，没有就抛异常
			// 也就是有没有<bean id="" class="">标签中的class属性值！
			if (!mbd.hasBeanClass()) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"bean definition declares neither a bean class nor a factory-bean reference");
			}
			factoryBean = null;
			// 获取<bean id="" class="">标签中的class属性值
			factoryClass = mbd.getBeanClass();
			// 表示静态工厂
			isStatic = true;
		}

		// 准备使用的工厂方法 —— 我们要使用哪个工厂方法
		Method factoryMethodToUse = null;
		// 准备使用的参数包装器 —— 要使用的参数包装器是哪一个
		ArgumentsHolder argsHolderToUse = null;
		// 准备使用的参数 —— 哪些是我们对应的参数值
		Object[] argsToUse = null;

		/* 2、尝试解析工厂方法factoryMethodToUser和参数argsToUse */

		// 判断一下我们对应的一些参数值，参数有两种：1、传过来的显示参数；2、标签里面所带的参数

		if (explicitArgs != null) {
			// 如果有显示参数就使用这些参数
			argsToUse = explicitArgs;
		}
		else {
			// 解析出来的参数
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock/* 构造函数参数锁 */) {
				factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod/* 已解决的构造函数或工厂方法 */;
				// 如果有工厂方法，且构造函数已经解析了
				if (factoryMethodToUse != null && mbd.constructorArgumentsResolved/* 构造函数参数已解决 */) {
					// Found a cached factory method... —— 找到一个缓存的工厂方法...
					argsToUse = mbd.resolvedConstructorArguments/* 已解决的构造函数参数 */;
					if (argsToUse == null) {
						argsToResolve = mbd.preparedConstructorArguments/* 准备好的构造函数参数 */;
					}
				}
			}
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve, true);
			}
		}

		// 如果没解析过，就获取factoryClass的用户定义类型，因为此时factoryClass可能是CGLIB动态代理类型，所以要获取用父类的类型。
		// 如果工厂方法是唯一的，就是没重载的，就获取解析的工厂方法，如果不为空，就添加到一个不可变列表里，
		// 如果为空的话，就要去找出factoryClass的以及父类的所有的方法，进一步找出方法修饰符一致且名字跟工厂方法名字相同的且是bean注解的方法，并放入列表里。

		// 是否有未解析出的工厂方法和参数
		if (factoryMethodToUse == null || argsToUse == null) {
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.
			// 获取用户定义的类
			factoryClass = ClassUtils.getUserClass(factoryClass);

			// 工厂方法集合 —— 候选的工厂方法集合
			List<Method> candidates = null;
			// 如果工厂方法是唯一的，没有重载的
			if (mbd.isFactoryMethodUnique) {
				if (factoryMethodToUse == null) {
					// 尝试获取工厂方法 —— 获取解析的工厂方法
					factoryMethodToUse = mbd.getResolvedFactoryMethod/* 获取已解析的工厂方法 */();
				}
				// 是否获取工厂方法
				if (factoryMethodToUse != null) {
					// 获取到工厂方法，存在的话，返回仅包含这一个工厂方法的单一不可变集合
					candidates = Collections.singletonList(factoryMethodToUse);
				}
			}
			// 工厂方法集合是否为空 —— 如果没有找到工厂方法，那么工厂集合就为空，可能有重载的工厂方法，也就是说工厂方法并不是唯一的
			if (candidates == null) {
				candidates = new ArrayList<>();
				// 获取factoryClass以及父类的所有方法，作为原始候选的方法
				Method[] rawCandidates/* 原始候选人 */ = getCandidateMethods/* 获取候选方法 */(factoryClass, mbd);
				// 过滤出和isStatic所代表的修饰符一样，且方法名字和工厂方法名字是一样的，且是bean注解的方，放入工厂方法集合中
				for (Method candidate : rawCandidates) {
					// 判断和isStatic所代表的修饰符(静态/非静态)一样的方法，例如isStatic=true，那么就是判断该方法是不是静态方法，只有静态方法才为true
					// ⚠️只有和isStatic所代表的修饰符一样的方法，并且方法名字跟工厂方法名字一样就添加到候选方法集合里面
					// 工厂方法：<bean id="" factory-method="">中factory-method属性所指定的方法
					if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
						candidates.add(candidate);
					}
				}
			}

			// 如果只获取到一个工厂方法。且传入的显示参数为空，且没有设置构造方法参数值
			if (candidates.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues/* 有没有具体的构造参数值 */()) {
				// 获取方法
				Method uniqueCandidate = candidates.get(0);
				// 如果没有参数的话
				if (uniqueCandidate.getParameterCount() == 0) {
					// 设置工厂方法
					mbd.factoryMethodToIntrospect = uniqueCandidate;
					synchronized (mbd.constructorArgumentLock) {
						// 设置解析出来的方法
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						// 参数也已经解析了
						mbd.constructorArgumentsResolved = true;
						// 方法参数为空
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					// ⚠️创建实例并设置到BeanWrapperImpl中
					bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}

			// 如果有多个工厂方法的话进行排序操作：根据修饰符public优先，参数个数多的优先
			if (candidates.size() > 1) {  // explicitly skip immutable singletonList —— 显式跳过不可变的单例列表
				candidates.sort(AutowireUtils.EXECUTABLE_COMPARATOR);
			}

			// 构造器参数值
			// 获取处理的结果值
			ConstructorArgumentValues resolvedValues/* 解析值 */ = null;
			// 判断一下是否需要自动装配
			boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR/* 3 */);
			// 最小的类型差距
			int minTypeDiffWeight = Integer.MAX_VALUE;
			// 模糊的工厂方法集合
			Set<Method> ambiguousFactoryMethods/* 模棱两可的工厂方法 */ = null;

			/* 得到，最小参数个数(最小数量的参数) */
			int minNrOfArgs;
			if (explicitArgs != null) {
				// 如果存在显式参数，那么最小参数个数就是显式参数的个数
				minNrOfArgs = explicitArgs.length;
			}
			else {
				// We don't have arguments passed in programmatically, so we need to resolve the
				// arguments specified in the constructor arguments held in the bean definition.
				// 如果存在构造器参数值，就解析出最小参数个数
				if (mbd.hasConstructorArgumentValues()) {
					ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues/* 获取构造函数参数值 */();
					resolvedValues = new ConstructorArgumentValues();
					minNrOfArgs = resolveConstructorArguments/* 解析出构造函数参数个数 */(beanName, mbd, bw, cargs, resolvedValues);
				}
				else {
					// 没有就为0
					minNrOfArgs = 0;
				}
			}

			LinkedList<UnsatisfiedDependencyException/* 不满足的依赖异常 */> causes/* 原因 */ = null;

			/* 遍历"候选的工厂方法集合"里面的所有工厂方法，获取方法的参数名和参数类型，根据参数，创建参数持有器 */

			// 遍历每个候选的方法，查看可以获取实例的匹配度
			for (Method candidate : candidates) {
				// 获取方法对应的参数个数
				int parameterCount = candidate.getParameterCount();
				// 方法参数的个数，要大于或者等于"最小参数个数"，才有可能是这个方法！
				// 比如：我"最小参数个数"是2个，但是方法的参数是1个，明显该方法不符合，我多出的一个参数放到哪里去？如果是我方法参数大于等于2个，那么我就可以包容进这两个参数！
				if (parameterCount >= minNrOfArgs) {
					// 参数持有器
					ArgumentsHolder argsHolder;
					// 获取方法的所有参数的类型
					Class<?>[] paramTypes = candidate.getParameterTypes/* 获取参数类型 */();
					// 显示参数存在，如果长度不对，就直接下一个，否则就创建参数持有器，持有这些显示参数
					if (explicitArgs != null) {
						// Explicit arguments given -> arguments length must match exactly. —— 给定的显式参数 -> 参数长度必须完全匹配。
						if (paramTypes.length != explicitArgs.length) {
							continue;
						}
						argsHolder = new ArgumentsHolder(explicitArgs);
					}
					else {
						// Resolved constructor arguments: type conversion and/or autowiring necessary. —— 已解决的构造函数参数：需要类型转换和自动装配。
						try {
							String[] paramNames = null;
							// ParameterNameDiscoverer作用：解析方法参数的名称
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer/* 获取参数名称发现器 */();
							// 存在的话，进行探测
							if (pnd != null) {
								// 获取方法的所有参数名称
								paramNames = pnd.getParameterNames/* 获取参数名称 */(candidate);
							}
							// ⚠️创建参数持有器
							argsHolder = createArgumentArray/* 创建参数数组 */(beanName, mbd, resolvedValues, bw,
									paramTypes, paramNames, candidate, autowiring, candidates.size() == 1);
						}
						catch (UnsatisfiedDependencyException ex) {
							if (logger.isTraceEnabled()) {
								logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
							}
							// Swallow and try next overloaded factory method. —— 吞并尝试下一个重载的工厂方法。
							if (causes == null) {
								causes = new LinkedList<>();
							}
							causes.add(ex);
							continue;
						}
					}

					/* 获取每个工厂方法的参数类型的差异值 */

					// 根据参数类型匹配，获取类型的差异值
					// 判断我们对应的一个参数类型的权重不同，就是说，我在选择一个合适的工厂方法的时候，它会计算一个权重值，有对应的计算逻辑，最终计算完成之后，会选择一个相对而言比较合适的方法
					int typeDiffWeight/* 类型差异权重 */ = (mbd.isLenientConstructorResolution() ?
							argsHolder.getTypeDifferenceWeight/* 获取类型差异权重 */(paramTypes)
							: argsHolder.getAssignabilityWeight/* 获得可分配权重 */(paramTypes));
					// Choose this factory method if it represents the closest match.
					// 保存最小的，说明参数类型相近
					if (typeDiffWeight < minTypeDiffWeight/* 最小类型差异权重 */) {
						factoryMethodToUse = candidate;
						argsHolderToUse = argsHolder;
						argsToUse = argsHolder.arguments;
						minTypeDiffWeight = typeDiffWeight;
						ambiguousFactoryMethods/* 模棱两可的工厂方法 */ = null;
					}
					// Find out about ambiguity: In case of the same type difference weight
					// for methods with the same number of parameters, collect such candidates
					// and eventually raise an ambiguity exception.
					// However, only perform that check in non-lenient constructor resolution mode,
					// and explicitly ignore overridden methods (with the same parameter signature).
					// 如果出现，参数类型差异相同，参数个数也相同的，而且需要严格判断，参数长度也一样，常数类型也一样，就可能会无法判定要实例化哪个，就会报异常
					// 如果存在参数类型差异值相同的，则报异常，因为不知道该用哪个工厂方法实例化
					else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
							!mbd.isLenientConstructorResolution/* 是宽松的构造函数解析 */() &&
							paramTypes.length == factoryMethodToUse.getParameterCount() &&
							!Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
						if (ambiguousFactoryMethods == null) {
							ambiguousFactoryMethods = new LinkedHashSet<>();
							ambiguousFactoryMethods.add(factoryMethodToUse);
						}
						ambiguousFactoryMethods.add(candidate);
					}
				}
			}

			/* 存在未解析出的工厂方法和参数，就报错 */
			if (factoryMethodToUse == null || argsToUse == null) {
				if (causes != null) {
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				List<String> argTypes = new ArrayList<>(minNrOfArgs);
				if (explicitArgs != null) {
					for (Object arg : explicitArgs) {
						argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
					}
				}
				else if (resolvedValues != null) {
					Set<ValueHolder> valueHolders = new LinkedHashSet<>(resolvedValues.getArgumentCount());
					valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());
					valueHolders.addAll(resolvedValues.getGenericArgumentValues());
					for (ValueHolder value : valueHolders) {
						String argType = (value.getType() != null ? ClassUtils.getShortName(value.getType()) :
								(value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null"));
						argTypes.add(argType);
					}
				}
				String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"No matching factory method found: " +
						(mbd.getFactoryBeanName() != null ?
							"factory bean '" + mbd.getFactoryBeanName() + "'; " : "") +
						"factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. " +
						"Check that a method with the specified name " +
						(minNrOfArgs > 0 ? "and arguments " : "") +
						"exists and that it is " +
						(isStatic ? "static" : "non-static") + ".");
			}
			/* 返回值类型是void，就报错 */
			else if (void.class == factoryMethodToUse.getReturnType()) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid factory method '" + mbd.getFactoryMethodName() +
						"': needs to have a non-void return type!");
			}
			/* 存在模凌两可的工厂方法，就报错 */
			else if (ambiguousFactoryMethods != null) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous factory method matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousFactoryMethods);
			}

			// 显示参数为null，要使用的参数包装器不为null
			if (explicitArgs == null && argsHolderToUse != null) {
				mbd.factoryMethodToIntrospect/* 工厂方法自省 */ = factoryMethodToUse;
				argsHolderToUse.storeCache(mbd, factoryMethodToUse);
			}
		}

		// ⚠️当获取到合适的方法之后，开始进行实例化
		// ⚠️创建实例并设置到BeanWrapperImpl中
		bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
		return bw;
	}

	private Object instantiate(String beanName, RootBeanDefinition mbd,
			@Nullable Object factoryBean, Method factoryMethod, Object[] args) {

		try {
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						this.beanFactory.getInstantiationStrategy().instantiate(
								mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args),
						this.beanFactory.getAccessControlContext());
			}
			else {
				// 获取实例化策略，进行实例化
				// 获取实例化策略，然后进行相关的实例化
				return this.beanFactory.getInstantiationStrategy().instantiate(
						mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via factory method failed", ex);
		}
	}

	/**
	 * 将cargs解析后，值保存到resolveValues中，并返回解析后的最小(索引参数值数+乏型参数值数)
	 *
	 * Resolve the constructor arguments for this bean into the resolvedValues object.
	 * This may involve looking up other beans.
	 * <p>This method is also used for handling invocations of static factory methods.
	 */
	private int resolveConstructorArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues) {

		// 获取Bean工厂的类型转换器（自定义的类型转换器）
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		// 定义一个TypeConverter对象，如果有customConverter，就引用customConverter；否则引用bw
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		// BeanDefinitionValueResolver：在bean工厂实现中使用Helper类，它将beanDefinition对象中包含的值解析为应用于目标bean实例的实际值
		// 新建一个BeanDefinitionValueResolver对象
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);

		// 返回此实例中保存的参数值的数量，同时计算索引参数值和泛型参数值
		// 获取cargs的参数值数量和泛型参数值数量作为：最小(索引参数值数+泛型参数值数)
		int minNrOfArgs = cargs.getArgumentCount();

		/*

		如果构造器的参数值是以索引标注位置的，那么就以索引的方式，遍历处理构造函数值

		 */

		// ConstructorArgumentValues.ValueHolder：构造函数参数值的Holder，带有可选的type属性，指示实际构造函数参数的目标类型
		// 遍历cargs所封装的索引参数值的Map，元素为entry(key=参数值的参数索引，value=ConstructorArgumentValues.ValueHolder对象)
		for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cargs.getIndexedArgumentValues().entrySet()) {
			// 获取参数值的参数索引
			int index = entry.getKey();
			// 如果index小于0
			if (index < 0) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid constructor argument index: " + index);
			}
			// 如果index大于最小参数值数量
			if (index + 1 > minNrOfArgs) {
				// minNrOfArgs就为index+1
				minNrOfArgs = index + 1;
			}
			// 获取ConstructorArgumentValues.ValueHolder对象
			ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();
			// 如果ValueHolder已经包含转换后的值
			if (valueHolder.isConverted()) {
				// 将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addIndexedArgumentValue(index, valueHolder);
			}
			else {
				// ⚠️
				// 使用valueResolver解析出ValueHolder实例的构造函数参数值所封装的对象
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				// 使用ValueHolder所封装的type，name属性以及解析出来的resolvedValue，构造一个ConstructorArgumentValues.ValueHolder对象
				ConstructorArgumentValues.ValueHolder resolvedValueHolder =
						new ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.getType(), valueHolder.getName());
				// 将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中
				resolvedValueHolder.setSource(valueHolder);
				// 将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder);
			}
		}

		/*

		以value的方式，遍历处理构造函数值

		 */

		// 遍历cargs的泛型参数值的列表，元素为ConstructorArgumentValues.ValueHolder对象
		for (ConstructorArgumentValues.ValueHolder valueHolder : cargs.getGenericArgumentValues()) {
			// 如果valueHolder已经包含转换后的值
			if (valueHolder.isConverted()) {
				// 将index和valueHolder添加到resolvedValues的泛型参数值的列表中
				resolvedValues.addGenericArgumentValue(valueHolder);
			}
			else {
				// ⚠️
				// 使用valueResolver解析出vaLueHolder实例的构造函数参数值所封装的对象
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary/* 必要时解析值 */("constructor argument", valueHolder.getValue()/* ⚠️ */);
				// 使用valueHolder所封装的type，name属性以及解析出来的resolvedValue，构造出来一个ConstructorArgumentValues.ValueHolder对象
				ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(
						resolvedValue, valueHolder.getType(), valueHolder.getName());
				// 将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中
				resolvedValueHolder.setSource(valueHolder);
				// 将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addGenericArgumentValue(resolvedValueHolder);
			}
		}

		// 返回最小(索引参数值+泛型参数值)
		return minNrOfArgs;
	}

	/**
	 * Create an array of arguments to invoke a constructor or factory method,
	 * given the resolved constructor argument values.
	 *
	 * 给定解析的构造函数参数值，创建一个参数数组以调用构造函数或工厂方法。
	 *
	 */
	private ArgumentsHolder createArgumentArray(
			String beanName, RootBeanDefinition mbd, @Nullable ConstructorArgumentValues resolvedValues,
			BeanWrapper bw, Class<?>[] paramTypes, @Nullable String[] paramNames, Executable executable,
			boolean autowiring, boolean fallback) throws UnsatisfiedDependencyException {

		// 获取类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);

		// 参数持有器
		ArgumentsHolder args = new ArgumentsHolder(paramTypes.length);
		// 构造器参数值集合
		Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
		// 装配的bean名字
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);

		// 如果有参数的话
		for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
			Class<?> paramType = paramTypes[paramIndex];
			// 获取参数名字
			String paramName = (paramNames != null ? paramNames[paramIndex] : "");
			// Try to find matching constructor argument value, either indexed or generic.
			ConstructorArgumentValues.ValueHolder valueHolder = null;
			if (resolvedValues != null) {
				valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);
				// If we couldn't find a direct match and are not supposed to autowire,
				// let's try the next generic, untyped argument value as fallback:
				// it could match after type conversion (for example, String -> int).
				if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
					valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
				}
			}
			if (valueHolder != null) {
				// We found a potential match - let's give it a try.
				// Do not consider the same value definition multiple times!
				usedValueHolders.add(valueHolder);
				Object originalValue = valueHolder.getValue();
				Object convertedValue;
				if (valueHolder.isConverted()) {
					convertedValue = valueHolder.getConvertedValue();
					args.preparedArguments[paramIndex] = convertedValue;
				}
				else {
					// 获取统一的方法参数类型
					MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
					try {
						convertedValue = converter.convertIfNecessary(originalValue, paramType, methodParam);
					}
					catch (TypeMismatchException ex) {
						throw new UnsatisfiedDependencyException(
								mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
								"Could not convert argument value of type [" +
										ObjectUtils.nullSafeClassName(valueHolder.getValue()) +
										"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
					}
					Object sourceHolder = valueHolder.getSource();
					if (sourceHolder instanceof ConstructorArgumentValues.ValueHolder) {
						Object sourceValue = ((ConstructorArgumentValues.ValueHolder) sourceHolder).getValue();
						args.resolveNecessary = true;
						args.preparedArguments[paramIndex] = sourceValue;
					}
				}
				args.arguments[paramIndex] = convertedValue;
				args.rawArguments[paramIndex] = originalValue;
			}
			else {
				MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
				// No explicit match found: we're either supposed to autowire or
				// have to fail creating an argument array for the given constructor.
				if (!autowiring) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
							"Ambiguous argument values for parameter of type [" + paramType.getName() +
							"] - did you specify the correct bean references as arguments?");
				}
				try {
					// 解析自动装配参数，找到会进行实例化
					Object autowiredArgument = resolveAutowiredArgument(
							methodParam, beanName, autowiredBeanNames, converter, fallback);
					args.rawArguments[paramIndex] = autowiredArgument;
					args.arguments[paramIndex] = autowiredArgument;
					args.preparedArguments[paramIndex] = autowiredArgumentMarker;
					args.resolveNecessary = true;
				}
				catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam), ex);
				}
			}
		}

		// 注册依赖的bean
		for (String autowiredBeanName : autowiredBeanNames) {
			this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Autowiring by type from bean name '" + beanName +
						"' via " + (executable instanceof Constructor ? "constructor" : "factory method") +
						" to bean named '" + autowiredBeanName + "'");
			}
		}

		return args;
	}

	/**
	 * 解析缓存在mbd中准备好的参数值
	 *
	 * Resolve the prepared arguments stored in the given bean definition. —— 解析存储在给定 bean 定义中的准备好的参数。
	 */
	private Object[] resolvePreparedArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			Executable executable, Object[] argsToResolve, boolean fallback) {

		// 获取bean工厂的自定义类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		// 如果customConverter不为null，converter就引用customConverter，否则引用bw
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		// BeanDefinitionValueResolver主要是用于将bean定义对象中包含的值解析为应用于目标bean实例的实际值
		// 新建一个BeanDefinitionValue解析器对象
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
		// 从executable中获取其参数类型
		Class<?>[] paramTypes = executable.getParameterTypes();

		// 定义一个解析后的参数值数组，长度argsToResolve的长度
		Object[] resolvedArgs = new Object[argsToResolve.length];
		// 遍历argsToResolve(for i)形式
		for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
			// 获取argsToResolve的第argIndex个参数值
			Object argValue = argsToResolve[argIndex];
			// 为executable的argIndex位置参数创建一个新的MethodParameter对象
			MethodParameter methodParam = MethodParameter.forExecutable(executable, argIndex);
			// 如果argValue是自动装配的参数标记
			if (argValue == autowiredArgumentMarker) {
				// 解析出应该自动装配的methodParam的Bean对象
				argValue = resolveAutowiredArgument(methodParam, beanName, null, converter, fallback);
			}
			// BeanMetadataElement：由包含配置源对象的bean元数据元素实现的接口，BeanDefinition的父接口
			// 如果argValue是BeanMetadataElement对象
			else if (argValue instanceof BeanMetadataElement) {
				// 交由valueResolver解析出value所封装的对象
				// ⚠️
				argValue = valueResolver.resolveValueIfNecessary("constructor argument", argValue);
			}
			// 如果argValue是String对象
			else if (argValue instanceof String) {
				// 评估bd中包含的argValue，如果argValue是可解析表达式，会对其进行解析，否则得到的还是argValue
				argValue = this.beanFactory.evaluateBeanDefinitionString((String) argValue, mbd);
			}
			// 获取第argValue个的参数类型
			Class<?> paramType = paramTypes[argIndex];
			try {
				// 将argValue转换为paramType类型对象并赋值给第i个resolved元素
				resolvedArgs[argIndex] = converter.convertIfNecessary(argValue, paramType, methodParam);
			}
			// 捕捉转换类型时抛出的"类型不匹配异常"
			catch (TypeMismatchException ex) {
				throw new UnsatisfiedDependencyException(
						mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
						"Could not convert argument value of type [" + ObjectUtils.nullSafeClassName(argValue) +
						"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
			}
		}
		return resolvedArgs;
	}

	protected Constructor<?> getUserDeclaredConstructor(Constructor<?> constructor) {
		Class<?> declaringClass = constructor.getDeclaringClass();
		Class<?> userClass = ClassUtils.getUserClass(declaringClass);
		if (userClass != declaringClass) {
			try {
				return userClass.getDeclaredConstructor(constructor.getParameterTypes());
			}
			catch (NoSuchMethodException ex) {
				// No equivalent constructor on user class (superclass)...
				// Let's proceed with the given constructor as we usually would.
			}
		}
		return constructor;
	}

	/**
	 * Template method for resolving the specified argument which is supposed to be autowired.
	 */
	@Nullable
	protected Object resolveAutowiredArgument(MethodParameter param, String beanName,
			@Nullable Set<String> autowiredBeanNames, TypeConverter typeConverter, boolean fallback) {

		Class<?> paramType = param.getParameterType();
		if (InjectionPoint.class.isAssignableFrom(paramType)) {
			InjectionPoint injectionPoint = currentInjectionPoint.get();
			if (injectionPoint == null) {
				throw new IllegalStateException("No current InjectionPoint available for " + param);
			}
			return injectionPoint;
		}
		try {
			return this.beanFactory.resolveDependency(
					new DependencyDescriptor(param, true), beanName, autowiredBeanNames, typeConverter);
		}
		catch (NoUniqueBeanDefinitionException ex) {
			throw ex;
		}
		catch (NoSuchBeanDefinitionException ex) {
			if (fallback) {
				// Single constructor or factory method -> let's return an empty array/collection
				// for e.g. a vararg or a non-null List/Set/Map parameter.
				if (paramType.isArray()) {
					return Array.newInstance(paramType.getComponentType(), 0);
				}
				else if (CollectionFactory.isApproximableCollectionType(paramType)) {
					return CollectionFactory.createCollection(paramType, 0);
				}
				else if (CollectionFactory.isApproximableMapType(paramType)) {
					return CollectionFactory.createMap(paramType, 0);
				}
			}
			throw ex;
		}
	}

	static InjectionPoint setCurrentInjectionPoint(@Nullable InjectionPoint injectionPoint) {
		InjectionPoint old = currentInjectionPoint.get();
		if (injectionPoint != null) {
			currentInjectionPoint.set(injectionPoint);
		}
		else {
			currentInjectionPoint.remove();
		}
		return old;
	}


	/**
	 * Private inner class for holding argument combinations. - 私有内部类，用于保存参数组合。
	 */
	private static class ArgumentsHolder {

		public final Object[] rawArguments;

		public final Object[] arguments;

		public final Object[] preparedArguments;

		public boolean resolveNecessary = false;

		public ArgumentsHolder(int size) {
			this.rawArguments = new Object[size];
			this.arguments = new Object[size];
			this.preparedArguments = new Object[size];
		}

		public ArgumentsHolder(Object[] args) {
			this.rawArguments = args;
			this.arguments = args;
			this.preparedArguments = args;
		}

		public int getTypeDifferenceWeight(Class<?>[] paramTypes) {
			// If valid arguments found, determine type difference weight.
			// Try type difference weight on both the converted arguments and
			// the raw arguments. If the raw weight is better, use it.
			// Decrease raw weight by 1024 to prefer it over equal converted weight.
			int typeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.arguments);
			int rawTypeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.rawArguments) - 1024;
			return Math.min(rawTypeDiffWeight, typeDiffWeight);
		}

		public int getAssignabilityWeight(Class<?>[] paramTypes) {
			for (int i = 0; i < paramTypes.length; i++) {
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.arguments[i])) {
					return Integer.MAX_VALUE;
				}
			}
			for (int i = 0; i < paramTypes.length; i++) {
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.rawArguments[i])) {
					return Integer.MAX_VALUE - 512;
				}
			}
			return Integer.MAX_VALUE - 1024;
		}

		public void storeCache(RootBeanDefinition mbd, Executable constructorOrFactoryMethod) {
			synchronized (mbd.constructorArgumentLock) {
				// 1、缓存BeanDefinition进行实例化时使用的构造函数
				// 将我们当前获取到的构造器对象赋值给了构造器缓存
				mbd.resolvedConstructorOrFactoryMethod = constructorOrFactoryMethod;
				// 2、缓存BeanDefinition代表的Bean的构造函数已解析完标识
				mbd.constructorArgumentsResolved = true;
				if (this.resolveNecessary) {
					mbd.preparedConstructorArguments = this.preparedArguments;
				}
				else {
					// 3、缓存参与构造函数参数列表值的参数列表
					mbd.resolvedConstructorArguments = this.arguments;
				}
			}
		}
	}


	/**
	 * Delegate for checking Java 6's {@link ConstructorProperties} annotation.
	 */
	private static class ConstructorPropertiesChecker {

		@Nullable
		public static String[] evaluate(Constructor<?> candidate, int paramCount) {
			ConstructorProperties cp = candidate.getAnnotation(ConstructorProperties.class);
			if (cp != null) {
				String[] names = cp.value();
				if (names.length != paramCount) {
					throw new IllegalStateException("Constructor annotated with @ConstructorProperties but not " +
							"corresponding to actual number of parameters (" + paramCount + "): " + candidate);
				}
				return names;
			}
			else {
				return null;
			}
		}
	}

}
