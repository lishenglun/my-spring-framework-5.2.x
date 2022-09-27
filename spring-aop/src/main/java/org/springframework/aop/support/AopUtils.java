/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.aop.support;

import org.springframework.aop.*;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for AOP support code.
 *
 * <p>Mainly for internal use within Spring's AOP support.
 *
 * <p>See {@link org.springframework.aop.framework.AopProxyUtils} for a
 * collection of framework-specific AOP utility methods which depend
 * on internals of Spring's AOP framework implementation.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see org.springframework.aop.framework.AopProxyUtils
 */
public abstract class AopUtils {

	/**
	 * Check whether the given object is a JDK dynamic proxy or a CGLIB proxy.
	 * <p>This method additionally checks if the given object is an instance
	 * of {@link SpringProxy}.
	 * @param object the object to check
	 * @see #isJdkDynamicProxy
	 * @see #isCglibProxy
	 */
	public static boolean isAopProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && (Proxy.isProxyClass(object.getClass()) ||
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)));
	}

	/**
	 * Check whether the given object is a JDK dynamic proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link Proxy#isProxyClass(Class)} by additionally checking if the
	 * given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see java.lang.reflect.Proxy#isProxyClass
	 */
	public static boolean isJdkDynamicProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
	}

	/**
	 * Check whether the given object is a CGLIB proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link ClassUtils#isCglibProxy(Object)} by additionally checking if
	 * the given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see ClassUtils#isCglibProxy(Object)
	 */
	public static boolean isCglibProxy(@Nullable Object object) {
		return (object instanceof SpringProxy &&
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR));
	}

	/**
	 * Determine the target class of the given bean instance which might be an AOP proxy.
	 * <p>Returns the target class for an AOP proxy or the plain class otherwise.
	 * @param candidate the instance to check (might be an AOP proxy)
	 * @return the target class (or the plain class of the given object as fallback;
	 * never {@code null})
	 * @see org.springframework.aop.TargetClassAware#getTargetClass()
	 * @see org.springframework.aop.framework.AopProxyUtils#ultimateTargetClass(Object)
	 */
	public static Class<?> getTargetClass(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Class<?> result = null;
		if (candidate instanceof TargetClassAware) {
			result = ((TargetClassAware) candidate).getTargetClass();
		}
		if (result == null) {
			result = (isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
		}
		return result;
	}

	/**
	 * Select an invocable method on the target type: either the given method itself
	 * if actually exposed on the target type, or otherwise a corresponding method
	 * on one of the target type's interfaces or on the target type itself.
	 *
	 * 选择目标类型上的可调用方法：给定方法本身（如果实际暴露在目标类型上），或者目标类型的接口之一或目标类型本身上的相应方法。
	 *
	 * @param method the method to check
	 * @param targetType the target type to search methods on (typically an AOP proxy)
	 * @return a corresponding invocable method on the target type
	 * @throws IllegalStateException if the given method is not invocable on the given
	 * target type (typically due to a proxy mismatch)
	 * @since 4.3
	 * @see MethodIntrospector#selectInvocableMethod(Method, Class)
	 */
	public static Method selectInvocableMethod(Method method, @Nullable Class<?> targetType) {
		if (targetType == null) {
			return method;
		}
		Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
		if (Modifier.isPrivate(methodToUse.getModifiers()) && !Modifier.isStatic(methodToUse.getModifiers()) &&
				SpringProxy.class.isAssignableFrom(targetType)) {
			throw new IllegalStateException(String.format(
					"Need to invoke method '%s' found on proxy for target class '%s' but cannot " +
					"be delegated to target bean. Switch its visibility to package or protected.",
					method.getName(), method.getDeclaringClass().getSimpleName()));
		}
		return methodToUse;
	}

	/**
	 * Determine whether the given method is an "equals" method.
	 * @see java.lang.Object#equals
	 */
	public static boolean isEqualsMethod(@Nullable Method method) {
		return ReflectionUtils.isEqualsMethod(method);
	}

	/**
	 * Determine whether the given method is a "hashCode" method.
	 * @see java.lang.Object#hashCode
	 */
	public static boolean isHashCodeMethod(@Nullable Method method) {
		return ReflectionUtils.isHashCodeMethod(method);
	}

	/**
	 * Determine whether the given method is a "toString" method.
	 * @see java.lang.Object#toString()
	 */
	public static boolean isToStringMethod(@Nullable Method method) {
		return ReflectionUtils.isToStringMethod(method);
	}

	/**
	 * Determine whether the given method is a "finalize" method.
	 * @see java.lang.Object#finalize()
	 */
	public static boolean isFinalizeMethod(@Nullable Method method) {
		return (method != null && method.getName().equals("finalize") &&
				method.getParameterCount() == 0);
	}

	/**
	 * Given a method, which may come from an interface, and a target class used
	 * in the current AOP invocation, find the corresponding target method if there
	 * is one. E.g. the method may be {@code IFoo.bar()} and the target class
	 * may be {@code DefaultFoo}. In this case, the method may be
	 * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
	 * <p><b>NOTE:</b> In contrast to {@link org.springframework.util.ClassUtils#getMostSpecificMethod},
	 * this method resolves Java 5 bridge methods in order to retrieve attributes
	 * from the <i>original</i> method definition.
	 * @param method the method to be invoked, which may come from an interface
	 * @param targetClass the target class for the current invocation.
	 * May be {@code null} or may not even implement the method.
	 * @return the specific target method, or the original method if the
	 * {@code targetClass} doesn't implement it or is {@code null}
	 * @see org.springframework.util.ClassUtils#getMostSpecificMethod
	 */
	public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
		Class<?> specificTargetClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
		Method resolvedMethod = ClassUtils.getMostSpecificMethod(method, specificTargetClass);
		// If we are dealing with method with generic parameters, find the original method.
		return BridgeMethodResolver.findBridgedMethod(resolvedMethod);
	}

	/**
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 * @param pc the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass) {
		return canApply(pc, targetClass, false);
	}

	/**
	 * 判断当前Advisor是否能够增强当前bean对象
	 *
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 *
	 * 给定的切入点可以完全应用于给定的类吗？ <p>这是一个重要的测试，因为它可以用来优化一个类的切入点。
	 *
	 * @param pc the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @param hasIntroductions whether or not the advisor chain
	 *                         是否存在能够增强当前bean的引介增强Advisor
	 * for this bean includes any introductions
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
		Assert.notNull(pc, "Pointcut must not be null");
		/*

		1、先匹配这个类是不是符合规则 —— ClassFilter#matches()

		如果类不匹配，那么直接返回false，代表不匹配；如果匹配，那么接着匹配类中的方法

		 */
		/**
		 * 进行切点表达式的匹配最重要的就是ClassFilter和MethodMatcher这两个方法的实现。
		 * MethodMatcher中有两个matches()。一个参数是只有Method对象和targetClass；另一个参数有Method对象和targetCLass对象还有一个Method的方法参数
		 * 他们两个的区别是：两个参数的matches是用于静态的方法匹配，三个参数的matches是在运行期动态的进行方法匹配的
		 */
		// 先进行ClassFilter的matches方法校验，首先这个类要在所匹配的规则下
		if (!pc.getClassFilter()/* AspectJExpressionPointcut */.matches(targetClass)) {
			return false;
		}

		/*

		2、如果类匹配，那再判断类中的方法是不是匹配 —— IntroductionAwareMethodMatcher#matches()

		 */
		/**
		 * pc可能存在的值：{@link org.springframework.transaction.interceptor.TransactionAttributeSourcePointcut}
		 *
		 * 1、TransactionAttributeSourcePointcut#getMethodMatcher()返回的是自身(this)
		 */
		// 再进行MethodMatcher方法级别的校验
		MethodMatcher methodMatcher = pc.getMethodMatcher();
		if (methodMatcher == MethodMatcher.TRUE) {
			// No need to iterate the methods if we're matching any method anyway... —— 如果我们仍然匹配任何方法，则无需迭代方法......
			return true;
		}

		// 判断匹配是不是IntroductionAwareMethodMatcher
		IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
		if (methodMatcher instanceof IntroductionAwareMethodMatcher) {
			introductionAwareMethodMatcher = (IntroductionAwareMethodMatcher) methodMatcher;
		}

		// 创建一个集合用于保存targetClass的class对象
		Set<Class<?>> classes = new LinkedHashSet<>();
		// 判断当前class是不是代理的class对象
		if (!Proxy.isProxyClass(targetClass)) {
			// 不是的话，就加入到集合中去
			classes.add(ClassUtils.getUserClass(targetClass));
		}
		// 获取到targetClass所实现的接口的class对象，然后加入到集合中
		classes.addAll(ClassUtils.getAllInterfacesForClassAsSet/* 获取类的所有接口作为集合 */(targetClass));

		for (Class<?> clazz : classes) {
			// ⚠️获取class所有的方法（既包含自身的方法，也包含父类的方法）
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			// 循环我们的方法
			for (Method method : methods) {
				/**
				 * 1、疑问：⚠️只要有一个方法能匹配到就返回true，这里就会有一个问题：
				 * 因为在一个目标中可能会有多个方法，有的方法是满足这个切点的匹配规则的，有的方法是不满足切点的匹配规则的，
				 * 这里匹配到只要有一个Method满足切点规则就返回true了，所以在运行时进行方法拦截的时候还会有一次"运行时的方法切点规则匹配"，具体检测调用的方法是否应该被增强
				 *
				 * 之所以这么做是因为，不管是匹配一个方法也好，还是n多个方法也好，只要有一个匹配，这个advice就要被代理，
				 * 所以只需要匹配一个方法，可以确认advice需要进行代理即可。生成代理了，在实际进行调用的时候，只需要去看一下我的拦截器链里面是否包含这个方法即可
				 *
				 * 简单概括：只要当前bean对象中的一个方法被某个Advisor中的切入点所匹配，这个bean对象就需要被代理，这个Advisor也就作为代理中的拦截器。
				 * 那肯定有疑惑，为什么一个方法被匹配了，就要对整个bean对象进行代理，其余的方法可能是不需要增强的？因为代理粒度是对象级别的，所以一个方法匹配了就对整个对象进行代理。
				 * 后续在代理对象内部再判断，当前方法是不是要被拦截的！
				 */
				/**
				 * 1、{@link org.springframework.transaction.interceptor.TransactionAttributeSourcePointcut#matches(Method, Class)}
				 *
				 * （1）由来
				 * 如果是开启了注解事务方式(<tx:annotation-driven"/>或者是@EnableTransactionManagement)，会注册一个BeanFactoryTransactionAttributeSourceAdvisor；
				 * BeanFactoryTransactionAttributeSourceAdvisor属于PointcutAdvisor类型；
				 * 通过BeanFactoryTransactionAttributeSourceAdvisor#getPointcut()，可以得到TransactionAttributeSourcePointcut。
				 *
				 * （2）逻辑
				 *
				 * 获取方法所声明的TransactionAttribute(事务属性)，然后通过判断方法是否有声明的TransactionAttribute，来得知当前方法是不是需要被增强，
				 * 如果方法有声明的TransactionAttribute，则证明当前方法是事务方法，需要被增强。
				 *
				 * 典型的：会寻找和解析方法所声明的@Transactional，构建成一个TransactionAttribute进行缓存和返回。
				 * 如果能够获取得到一个TransactionAttribute，则证明当前方法是被@Transactional修饰的方法，是一个事务方法，需要被增强
				 *
				 * 疑问：为什么寻找和解析@Transactional，构建其TransactionAttribute的逻辑在这里？为什么不是像<tx:advice>中的<tx:attributes>中的<tx:method>标签一样，一开始就构建其TransactionAttribute？
				 * >>> (1)因为<tx:method>是集中式的，书写<tx:advice>标签会有一个Advisor bd，里面包含了<tx:attributes>所代表的TransactionAttributeSource，TransactionAttributeSource包含了<tx:method>所代表的TransactionAttribute，
				 * >>> 所以在实例化<tx:advice>标签所对应的Advisor时，由于它们集中在一起，所以会连带把<tx:method>所对应的TransactionAttribute一起实例化了。
				 * >>> (2)但是@Transaction不一样，@Transaction是分散在各个类中的方法上的，最好的方式，就是随着bean生命周期的生产线处理，处理到哪个类，就检测哪个类上的方法是否存在@Transaction，这样是最简便，不仅顺生产线，逻辑清晰，而且节约性能。
				 * >>> 如果不在生产线上进行处理，而是一开始拎出一块逻辑，专门遍历所有的bd识别@Transaction；后续是必须要实例化bean的，在实例化bean时，也是会遍历所有的bd。这样就相当于遍历了2次bd，增加了性能开销，何必呢。
				 * >>> 而如果，由于@Transaction是属于某个类中方法的，我可以把识别@Transaction的逻辑融入实例化bean的处理流程中，轮到处理某个类，就识别这个类中方法上的@Transaction。这样不仅顺生产线，逻辑清晰；而且只需要遍历1次bd，节约了性能。
				 */
				if (introductionAwareMethodMatcher != null ?
						introductionAwareMethodMatcher.matches/* 方法匹配 */(method, targetClass, hasIntroductions) :
						// 通过方法匹配器进行匹配
						methodMatcher.matches(method, targetClass)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Can the given advisor apply at all on the given class?
	 * This is an important test as it can be used to optimize
	 * out a advisor for a class.
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass) {
		return canApply(advisor, targetClass, false);
	}

	/**
	 * 判断当前Advisor是否能够增强当前bean对象
	 *
	 * Can the given advisor apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize out a advisor for a class.
	 * This version also takes into account introductions (for IntroductionAwareMethodMatchers).
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @param hasIntroductions whether or not the advisor chain for this bean includes
	 *                         是否存在能够增强当前bean的引介增强Advisor
	 * any introductions
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
		/*

		1、类粒度的匹配 —— 引介增强

		如果是IntroductionAdvisor引介增强，就进行类粒度的匹配 —— 通过IntroductionAdvisor获取ClassFilter，然后调用ClassFilter#matches()判断当前类是否匹配

		*/
		// 如果是IntroductionAdvisor引介增强的话，则进行类粒度的匹配
		if (advisor instanceof IntroductionAdvisor) {
			// 先通过IntroductionAdvisor获取ClassFilter；
			// 然后调用ClassFilter#matches()，判断当前类是否匹配
			return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
		}
		/*

		2、方法粒度的匹配 —— 切入点增强

		如果是PointcutAdvisor切入点增强，就进行方法粒度的匹配，会先调用ClassFilter#matches()匹配类是不是符合规则，
		再调用IntroductionAwareMethodMatcher#matches()匹配方法是不是符合规则

		*/
		// 通常我们的Advisor都是PointcutAdvisor类型
		else if (advisor instanceof PointcutAdvisor) {
			// 转为PointcutAdvisor类型
			PointcutAdvisor pca = (PointcutAdvisor) advisor;
			// ⚠️这里从Advisor中获取Pointcut(AspectJExpressionPointcut)
			return canApply/* ⚠️ */(pca.getPointcut(), targetClass, hasIntroductions);
		}
		/* 3、既不是IntroductionAdvisor，也不是PointcutAdvisor，则直接适用 */
		else {
			// It doesn't have a pointcut so we assume it applies. —— 它没有切入点，因此我们假设它适用。
			return true;
		}
	}

	/**
	 * 寻找适用于当前class的Advisor
	 *
	 * Determine the sublist of the {@code candidateAdvisors} list
	 * that is applicable to the given class.
	 * @param candidateAdvisors the Advisors to evaluate
	 *                          候选的Advisor
	 * @param clazz the target class
	 *              			目标类
	 * @return sublist of Advisors that can apply to an object of the given class
	 * (may be the incoming List as-is)
	 */
	public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
		// 若候选的增强器集合为空，直接返回
		if (candidateAdvisors.isEmpty()) {
			return candidateAdvisors;
		}

		// 定义一个合适的增强器集合对象
		List<Advisor> eligibleAdvisors = new ArrayList<>();

		/*

		1、类粒度的匹配 —— 引介增强

		如果是IntroductionAdvisor引介增强，就进行类粒度的匹配。调用ClassFilter#matches()匹配类是不是符合规则

		*/
		// 循环我们候选的增强器对象
		for (Advisor candidate : candidateAdvisors) {
			/**
			 * Introduction：引介，引入。对于一个现存的类，Introduction可以为其增加行为，而不用修改该类的程序。
  			 *
			 * ⚠️pointcut对应的动态代理生成的粒度是什么粒度，是在哪个纬度去进行相关的修改和操作的？方法，而Introduction可以理解为类级别，
			 */
			// 判断我们的增强器对象是不是实现了IntroductionAdvisor(很明显，我们事务的没有实现，所以不会走下面的逻辑)
			if (candidate instanceof IntroductionAdvisor/* 引介增强 */ && /* ⚠️ */canApply(candidate, clazz)) {
				eligibleAdvisors.add(candidate);
			}
		}

		/*

		2、方法粒度的匹配 —— 切入点增强

		如果是PointcutAdvisor切入点增强，就进行方法粒度的匹配。会先调用ClassFilter#matches()匹配类是不是符合规则，
		再调用IntroductionAwareMethodMatcher#matches()匹配方法是不是符合规则

		*/
		// 是否存在能够增强当前bean的引介增强Advisor（类粒度的匹配）
		// 因为上面是获取能够增强当前bean的引介增强Advisor，所以如果eligibleAdvisors集合不为空，就代表获取到了！
		boolean hasIntroductions = !eligibleAdvisors.isEmpty();

		// 遍历Advisor
		for (Advisor candidate : candidateAdvisors) {
			// 判断我们的增强对象是不是实现了IntroductionAdvisor，如果实现了，则跳过，因为在上面已经处理过，不需要再处理了
			// 排除引介增强，因为引介增强已经处理
			if (candidate instanceof IntroductionAdvisor) {
				// already processed
				continue;
			}
			// 真正的判断增强器是否适合当前类型 —— 是否能够进行一个正常的使用，是否符合当前被代理类
			// 我们现在存在的所有advisor bean，都是method级别的，需要通过表达式去匹配对应的方法
			// 在匹配方法的时候需要匹配类，所以应该包含一个类的匹配和方法的匹配
			if (/* ⚠️ */canApply(candidate, clazz, hasIntroductions)) {
				eligibleAdvisors.add(candidate);
			}
		}

		return eligibleAdvisors;
	}

	/**
	 * Invoke the given target via reflection, as part of an AOP method invocation.
	 * @param target the target object
	 * @param method the method to invoke
	 * @param args the arguments for the method
	 * @return the invocation result, if any
	 * @throws Throwable if thrown by the target method
	 * @throws org.springframework.aop.AopInvocationException in case of a reflection error
	 */
	@Nullable
	public static Object invokeJoinpointUsingReflection(@Nullable Object target, Method method, Object[] args)
			throws Throwable {

		// Use reflection to invoke the method.
		try {
			ReflectionUtils.makeAccessible(method);
			return method.invoke(target, args);
		}
		catch (InvocationTargetException ex) {
			// Invoked method threw a checked exception.
			// We must rethrow it. The client won't see the interceptor.
			throw ex.getTargetException();
		}
		catch (IllegalArgumentException ex) {
			throw new AopInvocationException("AOP configuration seems to be invalid: tried calling method [" +
					method + "] on target [" + target + "]", ex);
		}
		catch (IllegalAccessException ex) {
			throw new AopInvocationException("Could not access method [" + method + "]", ex);
		}
	}

}
