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

package org.springframework.web.servlet.handler;

import kotlin.reflect.KFunction;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 抽象的基础类通过map的映射关系来存储request和handlerMethod之间的关系
 *
 * Abstract base class for {@link HandlerMapping} implementations that define
 * a mapping between a request and a {@link HandlerMethod}.
 *
 * <p>For each registered handler method, a unique mapping is maintained with
 * subclasses defining the details of the mapping type {@code <T>}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 * @param <T> the mapping for a {@link HandlerMethod} containing the conditions
 * needed to match the handler method to an incoming request.
 *
 * 此泛型类型用来代表匹配handler的条件专门使用的一种类，这里的条件不只是url，还可以是其他条件，如请求方式，请求参数等都可以作为条件，默认使用的RequestMappingInfo
 *
 *
 * 我在进行请求的时候，需要有一些匹配的条件的验证，所以，这个时候，T可以是其它的类型
 *
 */
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {

	/**
	 * Bean name prefix for target beans behind scoped proxies. Used to exclude those
	 * targets from handler method detection, in favor of the corresponding proxies.
	 * <p>We're not checking the autowire-candidate status here, which is how the
	 * proxy target filtering problem is being handled at the autowiring level,
	 * since autowire-candidate may have been turned to {@code false} for other
	 * reasons, while still expecting the bean to be eligible for handler methods.
	 * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
	 * but duplicated here to avoid a hard dependency on the spring-aop module.
	 */
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

	private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH =
			new HandlerMethod(new EmptyHandler(), ClassUtils.getMethod(EmptyHandler.class, "handle"));

	private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration();

	static {
		ALLOW_CORS_CONFIG.addAllowedOrigin("*");
		ALLOW_CORS_CONFIG.addAllowedMethod("*");
		ALLOW_CORS_CONFIG.addAllowedHeader("*");
		ALLOW_CORS_CONFIG.setAllowCredentials(true);
	}

	/**
	 * 是否只扫描可访问的 HandlerMethod 们
	 */
	private boolean detectHandlerMethodsInAncestorContexts = false;

	/**
	 * Mapping命名策略
	 *
	 * 题外：HandlerMethod的Mapping命名策略
	 */
	@Nullable
	private HandlerMethodMappingNamingStrategy<T>/* 处理程序方法映射命名策略 */ namingStrategy;

	/**
	 * Mapping注册表
	 *
	 * 题外：注册mapping与HandlerMethod关系、以及url与mapping的关系用的
	 */
	private final MappingRegistry mappingRegistry = new MappingRegistry();


	/**
	 * Whether to detect handler methods in beans in ancestor ApplicationContexts.
	 * <p>Default is "false": Only beans in the current ApplicationContext are
	 * considered, i.e. only in the context that this HandlerMapping itself
	 * is defined in (typically the current DispatcherServlet's context).
	 * <p>Switch this flag on to detect handler beans in ancestor contexts
	 * (typically the Spring root WebApplicationContext) as well.
	 * @see #getCandidateBeanNames()
	 */
	public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
		this.detectHandlerMethodsInAncestorContexts = detectHandlerMethodsInAncestorContexts;
	}

	/**
	 * Configure the naming strategy to use for assigning a default name to every
	 * mapped handler method.
	 * <p>The default naming strategy is based on the capital letters of the
	 * class name followed by "#" and then the method name, e.g. "TC#getFoo"
	 * for a class named TestController with method getFoo.
	 */
	public void setHandlerMethodMappingNamingStrategy(HandlerMethodMappingNamingStrategy<T> namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	/**
	 * Return the configured naming strategy or {@code null}.
	 */
	@Nullable
	public HandlerMethodMappingNamingStrategy<T> getNamingStrategy() {
		// RequestMappingInfoHandlerMethodMappingNamingStrategy
		return this.namingStrategy;
	}

	/**
	 * Return a (read-only) map with all mappings and HandlerMethod's.
	 */
	public Map<T, HandlerMethod> getHandlerMethods() {
		this.mappingRegistry.acquireReadLock();
		try {
			return Collections.unmodifiableMap(this.mappingRegistry.getMappings());
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Return the handler methods for the given mapping name.
	 * @param mappingName the mapping name
	 * @return a list of matching HandlerMethod's or {@code null}; the returned
	 * list will never be modified and is safe to iterate.
	 * @see #setHandlerMethodMappingNamingStrategy
	 */
	@Nullable
	public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
		return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
	}

	/**
	 * Return the internal mapping registry. Provided for testing purposes.
	 */
	MappingRegistry getMappingRegistry() {
		return this.mappingRegistry;
	}

	/**
	 * Register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping for the handler method
	 * @param handler the handler
	 * @param method the method
	 */
	public void registerMapping(T mapping, Object handler, Method method) {
		if (logger.isTraceEnabled()) {
			logger.trace("Register \"" + mapping + "\" to " + method.toGenericString());
		}
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Un-register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping to unregister
	 */
	public void unregisterMapping(T mapping) {
		if (logger.isTraceEnabled()) {
			logger.trace("Unregister mapping \"" + mapping + "\"");
		}
		this.mappingRegistry.unregister(mapping);
	}


	// Handler method detection

	/**
	 * Detects handler methods at initialization.
	 * @see #initHandlerMethods
	 */
	@Override
	public void afterPropertiesSet() {
		// ⚠️初始化HandlerMethod
		initHandlerMethods();
	}

	/**
	 * Scan beans in the ApplicationContext, detect and register handler methods.
	 * @see #getCandidateBeanNames()
	 * @see #processCandidateBean
	 * @see #handlerMethodsInitialized
	 */
	protected void initHandlerMethods() {
		/*

		1、整条链路大概做的事情：
		（1）获取所有容器中的所有beanName；
		（2）找到所有被标注了@Controller或者@RequestMapping注解的bean，作为Handler；
		（3）然后解析Handler，找出标注了@RequestMapping的方法，封装为HandlerMethod；并用@RequestMapping信息，创建一个RequestMappingInfo对象；
		（4）最后注册mapping与HandlerMethod的关系

		 */
		// 获取所有容器中的所有beanName
		for (String beanName : getCandidateBeanNames()/* 获取所有容器中所有的Bean的beanName */) {
			// 排除目标代理类(AOP相关，可查看注释)
			if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX/* scopedTarget. */)) {
				// ⚠️注册整个系统的请求映射关系
				processCandidateBean(beanName);
			}
		}
		// 打印下日志，记录下，处理程序方法已初始化
		handlerMethodsInitialized(getHandlerMethods());
	}

	/**
	 * Determine the names of candidate beans in the application context.
	 * @since 5.1
	 * @see #setDetectHandlerMethodsInAncestorContexts
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors
	 */
	protected String[] getCandidateBeanNames() {
		// 获取所有容器中所有的Bean的beanName
		return (this.detectHandlerMethodsInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(obtainApplicationContext(), Object.class) :
				obtainApplicationContext().getBeanNamesForType(Object.class));
	}

	/**
	 * Determine the type of the specified candidate bean and call
	 * {@link #detectHandlerMethods} if identified as a handler type.
	 * <p>This implementation avoids bean creation through checking
	 * {@link org.springframework.beans.factory.BeanFactory#getType}
	 * and calling {@link #detectHandlerMethods} with the bean name.
	 * @param beanName the name of the candidate bean
	 * @since 5.1
	 * @see #isHandler
	 * @see #detectHandlerMethods
	 */
	protected void processCandidateBean(String beanName) {
		// 获得beanName对应的Class对象
		Class<?> beanType = null;
		try {
			beanType = obtainApplicationContext().getType(beanName);
		}
		catch (Throwable ex) {
			// An unresolvable bean type, probably from a lazy bean - let's ignore it.
			if (logger.isTraceEnabled()) {
				logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
			}
		}

		/*

		1、判断类上是否存在@Controller或者@RequestMapping。
		有的话，就代表是一个Handler，然后筛选出Handler里面的HandlerMethod方法，和注册对应的映射关系

		*/
		// RequestMappingHandlerMapping#isHandler()
		if (beanType != null && isHandler(beanType)/* 根据类型，判断是不是一个处理器 */) {
			// ⚠️检查处理方法
			// 如果是一个处理器的话，就检查一下该处理器里面包含的处理方法是哪些
			detectHandlerMethods/* 检查HandlerMethod */(beanName);
		}
	}

	/**
	 * 检查一个处理器里面包含的处理方法是哪些
	 *
	 * Look for handler methods in the specified handler bean. —— 在指定的处理程序bean中查找处理程序方法。
	 * @param handler either a bean name or an actual handler instance
	 * @see #getMappingForMethod
	 */
	protected void detectHandlerMethods(Object handler) {
		// 获取处理器的类型
		Class<?> handlerType = (handler instanceof String ?
				obtainApplicationContext().getType((String) handler) : handler.getClass());

		// 如果处理器类型不为空
		if (handlerType != null) {
			// 获取到用户定义的处理器类
			// >>> 判断一下处理器类是不是cglib生成的类，是的话就获取父类；否则返回当前处理器类
			Class<?> userType = ClassUtils.getUserClass(handlerType);

			/* 1、获取Handler中的HandlerMethod，也就是标注了@RequestMapping的方法；以及根据方法上的@RequestMapping属性值创建一个RequestMappingInfo对象 */

			// 保存handler与匹配条件的对应关系，用于给registerHandlerMethod传入匹配条件
			Map<Method, T> methods = MethodIntrospector.selectMethods/* 获取userType类里面的所有方法 */(userType,
					// 遍历类中的方法对象
					(MethodIntrospector.MetadataLookup<T>/* 元数据查找 */) method/* 方法对象 */ -> {
						try {
							/**
							 * 获取方法映射（RequestMappingInfo）对象，具体做法：
							 * >>> 先查找方法上的@RequestMapping。如果有，就根据@RequestMapping中设置的属性值信息，创建RequestMappingInfo对象；没有的话就返回null
							 * >>> 如果方法上有@RequestMapping，则解析处理器上的@RequestMapping，并与方法上的@RequestMapping数据进行合并，得到一个新的RequestMappingInfo对象。
							 */
							// 检测方法是不是HandlerMethod，也就是标注了@RequestMapping的方法。
							// 如果是，就根据方法上的@RequestMapping创建对应的RequestMappingInfo，否则返回null。
							// RequestMappingHandlerMapping#getMappingForMethod()
							return getMappingForMethod/* 获取方法映射 */(method, userType);
						}
						catch (Throwable ex) {
							throw new IllegalStateException("Invalid mapping on handler class [" +
									userType.getName() + "]: " + method, ex);
						}
					});
			if (logger.isTraceEnabled()) {
				logger.trace(formatMappings(userType, methods));
			}

			/* 2、注册HandlerMethod的请求映射关系（根据mapping与HandlerMethod） */

			// 将符合要求的method注册起来，也就是保存到三个map中
			methods.forEach((method, mapping) -> {
				Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
				// ⚠️注册HandlerMethod
				// AbstractHandlerMethodMapping
				registerHandlerMethod(handler, invocableMethod, mapping);
			});
		}
	}

	private String formatMappings(Class<?> userType, Map<Method, T> methods) {
		String formattedType = Arrays.stream(ClassUtils.getPackageName(userType).split("\\."))
				.map(p -> p.substring(0, 1))
				.collect(Collectors.joining(".", "", "." + userType.getSimpleName()));
		Function<Method, String> methodFormatter = method -> Arrays.stream(method.getParameterTypes())
				.map(Class::getSimpleName)
				.collect(Collectors.joining(",", "(", ")"));
		return methods.entrySet().stream()
				.map(e -> {
					Method method = e.getKey();
					return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
				})
				.collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
	}

	/**
	 * Register a handler method and its unique mapping. Invoked at startup for
	 * each detected handler method.
	 * @param handler the bean name of the handler or the handler instance
	 *
	 *               Controller对象，或者Controller对象名称
	 *
	 * @param method the method to register
	 *
	 *               Controller方法
	 *
	 * @param mapping the mapping conditions associated with the handler method —— 与处理程序方法关联的映射条件
	 *
	 *                方法的mapping对象（映射条件）
	 *
	 * @throws IllegalStateException if another method was already registered
	 * under the same mapping
	 */
	protected void registerHandlerMethod(Object handler, Method method, T mapping) {
		// ⚠️
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Create the HandlerMethod instance.
	 * @param handler either a bean name or an actual handler instance
	 *
	 *                Controller bean，或者 Controller beanName
	 *
	 * @param method the target method
	 *
	 *               标注了@RequestMapping的Conntroller方法
	 *
	 * @return the created HandlerMethod
	 */
	protected HandlerMethod createHandlerMethod(Object handler, Method method) {
		/* 1、下面无论是哪种构建方式，都是根据handler和method创建一个HandlerMethod，代表了请求映射的方法 */

		// 如果handler为String类型，则说明handler是一个Controller beanName
		if (handler instanceof String) {
			return new HandlerMethod((String) handler,
					obtainApplicationContext().getAutowireCapableBeanFactory()/* 获取BeanFactory */, method/* 标注了@RequestMapping的Conntroller方法 */);
		}
		// 如果handler为非String类型 ，说明是一个Controller bean，就无需获取BeanFactory
		return new HandlerMethod(handler, method);
	}

	/**
	 * Extract and return the CORS configuration for the mapping.
	 */
	@Nullable
	protected CorsConfiguration initCorsConfiguration(Object handler, Method method, T mapping) {
		return null;
	}

	/**
	 * Invoked after all handler methods have been detected.
	 * @param handlerMethods a read-only map with handler methods and mappings.
	 */
	protected void handlerMethodsInitialized(Map<T, HandlerMethod> handlerMethods) {
		// Total includes detected mappings + explicit registrations via registerMapping —— Total包括通过registerMapping检测到的映射 + 显式注册
		int total = handlerMethods.size();
		if ((logger.isTraceEnabled() && total == 0) || (logger.isDebugEnabled() && total > 0) ) {
			logger.debug(total + " mappings in " + formatMappingName());
		}
	}


	// Handler method lookup

	/**
	 * 查找给定请求的处理程序方法。
	 *
	 * Look up a handler method for the given request.
	 */
	@Override
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
		/* 1、获取请求路径 */
		/**
		 * 一般类似于request.getServletPath()，返回不含contextPath的访问路径。
		 * 例如：http://localhost:8080/springmvc/getUser，项目路径是"/springmvc"，那么得到的请求路径是/getUser
		 */
		// 获取请求路径
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		// 设置请求路径到request的lookupPath属性中
		request.setAttribute(LOOKUP_PATH/* org.springframework.web.servlet.HandlerMapping.lookupPath */, lookupPath);
		// 获得读锁
		this.mappingRegistry.acquireReadLock();
		try {

			/*

			2、️通过请求路径获取HandlerMethod，作为handler对象（ 根据请求路径查找最合适的处理方法，如果查找到了多个，那么就返回最佳的那个）
			（1）先根据请求路径，从"请求地址映射缓存urlLookup"中，获取到mapping；
			（2）然后根据mapping，去"请求方法映射缓存mappingLockup"中，获取到对应的HandlerMethod
			 urlLookup和mappingLockup都是MappingRegistry中的map集合。

			*/
			// 题外：这里涉及到路径匹配的优先级，优先级: 精确匹配 > 最长路径匹配 > 扩展名匹配
			HandlerMethod handlerMethod/* 处理器方法 */ = lookupHandlerMethod(lookupPath, request);

			/*

			3、如果获取到了对应的HandlerMethod，就根据这个HandlerMethod以及内部的Controller bean，创建新的HandlerMethod返回。

			题外：handlerMethod内部包含有bean对象，其实指的是对应的controller

			 */
			return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
		}
		finally {
			// 释放读锁
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Look up the best-matching handler method for the current request.
	 * If multiple matches are found, the best match is selected.
	 *
	 * 查找当前请求的最佳匹配"处理程序方法"HandlerMethod。如果找到多个匹配项，则选择最佳匹配项。
	 *
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @return the best-matching handler method, or {@code null} if no match
	 * @see #handleMatch(Object, String, HttpServletRequest)
	 * @see #handleNoMatch(Set, String, HttpServletRequest)
	 */
	@Nullable
	protected HandlerMethod lookupHandlerMethod/* 查找 */(String lookupPath, HttpServletRequest request) throws Exception {
		// 题外：当前对象是：RequestMappingHandlerMapping
		/*

		一、根据请求方法查找最合适的处理方法，如果查找到了多个，那么就返回最佳的那个。
		（1）先根据请求路径，从"请求地址映射缓存urlLookup"中，获取到mapping；
		（2）然后根据mapping，去"请求方法映射缓存mappingLockup"中，获取到对应的HandlerMethod
		 urlLookup和mappingLockup都是MappingRegistry中的map集合。

		 */

		// 存储匹配上当前请求的结果（Mapping + HandlerMethod）
		List<Match> matches = new ArrayList<>();

		// 先根据请求路径，从"请求地址映射缓存urlLookup"中，获取到mapping
		List<T> directPathMatches/* 直接路径匹配 */ = this.mappingRegistry.getMappingsByUrl(lookupPath);
		if (directPathMatches != null) {
			// 将找到的匹配条件添加到matches
			// 根据mapping，去"请求方法映射缓存mappingLockup"中，获取到对应的HandlerMethod。然后创建一个Match对象，保存mapping和HandlerMethod。
			addMatchingMappings/* 添加匹配映射 */(directPathMatches, matches, request);
		}
		// 如果无法根据请求路径获取到匹配的条件，则将所有匹配条件加入matches
		if (matches.isEmpty()) {
			// No choice but to go through all mappings... —— 别无选择，只能遍历所有映射...

			// 将mappingLookup"请求方法映射缓存"的所有结果放入matches中
			addMatchingMappings(this.mappingRegistry.getMappings().keySet(), matches, request);
		}

		// 将包含匹配条件和handler的matches排序，并取第一个作为bestMatch，如果前面两个排序相同则抛出异常
		if (!matches.isEmpty()) {
			Match bestMatch = matches.get(0);
			if (matches.size() > 1) {
				// 创建MatchComparator对象，排序matches结果，排序器
				Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
				matches.sort(comparator);
				// 获得首个Match对象，也就是最匹配的
				bestMatch = matches.get(0);
				if (logger.isTraceEnabled()) {
					logger.trace(matches.size() + " matching mappings: " + matches);
				}
				if (CorsUtils.isPreFlightRequest(request)) {
					return PREFLIGHT_AMBIGUOUS_MATCH;
				}
				// 比较bestMatch和secondBestMatch，如果相等，说明有问题，抛出IllegalStateException异常
				// 因为，两个优先级一样高，说明无法判断谁更优先
				Match secondBestMatch = matches.get(1);
				if (comparator.compare(bestMatch, secondBestMatch) == 0) {
					Method m1 = bestMatch.handlerMethod.getMethod();
					Method m2 = secondBestMatch.handlerMethod.getMethod();
					String uri = request.getRequestURI();
					throw new IllegalStateException(
							"Ambiguous handler methods mapped for '" + uri + "': {" + m1 + ", " + m2 + "}");
				}
			}
			// 设置最佳匹配是哪个
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE/* org.springframework.web.servlet.HandlerMapping.bestMatchingHandler */,
					bestMatch.handlerMethod);
			/**
			 * RequestMappingHandlerMapping走的是RequestMappingInfoHandlerMapping
			 */
			// 处理首个Match对象
			handleMatch(bestMatch.mapping, lookupPath, request);
			// 返回首个Match对象的handlerMethod属性
			return bestMatch.handlerMethod;
		}
		// 如果匹配不到，则处理不匹配的情况
		else {
			return handleNoMatch(this.mappingRegistry.getMappings().keySet(), lookupPath, request);
		}
	}

	/**
	 * @param mappings	根据url获取到的mapping
	 * @param matches
	 * @param request
	 */
	private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
		/*

		1、去"请求方法映射缓存mappingLockup"中，根据mapping获取到对应的HandlerMethod。然后创建一个Match对象，保存mapping和HandlerMethod。

		*/
		for (T mapping/* ⚠️此时的mapping就是一个RequestMappingInfo */ : mappings) {
			/**
			 * 走RequestMappingInfoHandlerMapping
			 */
			// 获取匹配的mapping（实质上是，通过刚刚获取到的RequestMappingInfo,获取一个新的RequestMappingInfo）
			T/* RequestMappingInfo */ match = getMatchingMapping(mapping, request);
			if (match != null) {
				/**
				 * this.mappingRegistry.getMappings().get(mapping)：去"请求方法映射缓存mappingLockup"中，根据mapping获取到对应的HandlerMethod
				 */
				// 如果匹配，则创建Match对象，添加到matches中
				matches.add(new Match(match/* RequestMappingInfo */, this.mappingRegistry.getMappings().get(mapping))/* HandlerMethod */);
			}
		}
	}

	/**
	 * Invoked when a matching mapping is found.
	 * @param mapping the matching mapping
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 */
	protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
		request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE/* .pathWithinHandlerMapping */, lookupPath);
	}

	/**
	 * Invoked when no matching mapping is not found.
	 * @param mappings all registered mappings
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @throws ServletException in case of errors
	 */
	@Nullable
	protected HandlerMethod handleNoMatch(Set<T> mappings, String lookupPath, HttpServletRequest request)
			throws Exception {

		return null;
	}

	@Override
	protected boolean hasCorsConfigurationSource(Object handler) {
		return super.hasCorsConfigurationSource(handler) ||
				(handler instanceof HandlerMethod && this.mappingRegistry.getCorsConfiguration((HandlerMethod) handler) != null);
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		CorsConfiguration corsConfig = super.getCorsConfiguration(handler, request);
		if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			if (handlerMethod.equals(PREFLIGHT_AMBIGUOUS_MATCH)) {
				return AbstractHandlerMethodMapping.ALLOW_CORS_CONFIG;
			}
			else {
				CorsConfiguration corsConfigFromMethod = this.mappingRegistry.getCorsConfiguration(handlerMethod);
				corsConfig = (corsConfig != null ? corsConfig.combine(corsConfigFromMethod) : corsConfigFromMethod);
			}
		}
		return corsConfig;
	}


	// Abstract template methods

	/**
	 * Whether the given type is a handler with handler methods.
	 * @param beanType the type of the bean being checked
	 * @return "true" if this a handler type, "false" otherwise.
	 */
	protected abstract boolean isHandler(Class<?> beanType);

	/**
	 * Provide the mapping for a handler method. A method for which no
	 * mapping can be provided is not a handler method.
	 * @param method the method to provide a mapping for
	 * @param handlerType the handler type, possibly a sub-type of the method's
	 * declaring class
	 * @return the mapping, or {@code null} if the method is not mapped
	 */
	@Nullable
	protected abstract T getMappingForMethod(Method method, Class<?> handlerType);

	/**
	 * Extract and return the URL paths contained in the supplied mapping.
	 */
	protected abstract Set<String> getMappingPathPatterns(T mapping);

	/**
	 * Check if a mapping matches the current request and return a (potentially
	 * new) mapping with conditions relevant to the current request.
	 * @param mapping the mapping to get a match for
	 * @param request the current HTTP servlet request
	 * @return the match, or {@code null} if the mapping doesn't match
	 */
	@Nullable
	protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);

	/**
	 * Return a comparator for sorting matching mappings.
	 * The returned comparator should sort 'better' matches higher.
	 * @param request the current request
	 * @return the comparator (never {@code null})
	 */
	protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);


	/**
	 * A registry that maintains all mappings to handler methods, exposing methods
	 * to perform lookups and providing concurrent access.
	 * <p>Package-private for testing purposes.
	 */
	class MappingRegistry {

		/**
		 * 注册表
		 *
		 * key：mapping（RequestMappingInfo）
		 * value：MappingRegistration
		 *
		 */
		private final Map<T, MappingRegistration<T>> registry = new HashMap<>();

		/**
		 * 保存RequestCondition（RequestMappingInfo）和handlerMethod的对应关系
		 */
		// 请求方法映射缓存（请求路径信息和请求方法的映射缓存）
		// 题外：T虽然不直接是请求路径，但是T对象里面包含了请求路径，核心也是请求路径
		private final Map<T, HandlerMethod> mappingLookup/* 映射查找 */ = new LinkedHashMap<>();

		/**
		 * 保存url与RequestCondition的对应关系
		 *
		 * Key：直接请求URL地址（具体路径，没有*、？号之类通配符的路径）
		 * value：Mapping对象，例如：RequestMappingInfo
		 */
		// 请求地址映射缓存
		private final MultiValueMap<String, T> urlLookup = new LinkedMultiValueMap<>();

		/**
		 * Mapping的名字与HandlerMethod的映射
		 *
		 * Key：Mapping的名字
		 * Value：HandlerMethod集合
		 */
		// 请求名称映射缓存
		private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();

		// 放的是跨域请求相关的处理工作
		// key：HandlerMethod
		// value：跨域配置
		// 题外：Cors指跨域请求
		private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();

		/**
		 * 读写锁
		 */
		private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

		/**
		 * Return all mappings and handler methods. Not thread-safe.
		 * @see #acquireReadLock()
		 */
		public Map<T, HandlerMethod> getMappings() {
			return this.mappingLookup;
		}

		/**
		 * Return matches for the given URL path. Not thread-safe.
		 * @see #acquireReadLock()
		 */
		@Nullable
		public List<T> getMappingsByUrl(String urlPath) {
			return this.urlLookup.get(urlPath);
		}

		/**
		 * Return handler methods by mapping name. Thread-safe for concurrent use.
		 */
		public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
			return this.nameLookup.get(mappingName);
		}

		/**
		 * Return CORS configuration. Thread-safe for concurrent use.
		 */
		@Nullable
		public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
			HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
			return this.corsLookup.get(original != null ? original : handlerMethod);
		}

		/**
		 * 获得读锁
		 *
		 * Acquire the read lock when using getMappings and getMappingsByUrl.
		 */
		public void acquireReadLock() {
			this.readWriteLock.readLock().lock();
		}

		/**
		 * 释放读锁
		 *
		 * Release the read lock after using getMappings and getMappingsByUrl.
		 */
		public void releaseReadLock() {
			this.readWriteLock.readLock().unlock();
		}

		/**
		 *
		 * @param mapping	方法的mapping对象（映射条件），例如：RequestMappingInfo
		 * @param handler	Controller对象，或者Controller对象名称
		 * @param method	标注了@RequestMapping的Controller方法
		 */
		public void register(T mapping, Object handler, Method method) {
			// Assert that the handler method is not a suspending one. —— 断言处理程序方法不是暂停方法。
			if (KotlinDetector.isKotlinType(method.getDeclaringClass())) {
				Class<?>[] parameterTypes = method.getParameterTypes();
				if ((parameterTypes.length > 0) && "kotlin.coroutines.Continuation".equals(parameterTypes[parameterTypes.length - 1].getName())) {
					throw new IllegalStateException("Unsupported suspending handler method detected: " + method);
				}
			}
			// 获得写锁
			this.readWriteLock.writeLock().lock();
			try {

				/* 1、创建method对应的HandlerMethod，代表了处理请求的方法 */
				// 根据handler和method创建一个HandlerMethod，代表了请求映射的方法
				// 题外：现在要根据方法来进行映射，所以要创建一个处理器方法的对象
				HandlerMethod handlerMethod = createHandlerMethod(handler, method);

				/* 2、检验方法映射是否唯一(mapping的HandlerMethod是否唯一)，也就是从请求映射缓存中查找是否存在当前mapping对应的HandlerMethod，如果存在，并且不是当前方法，则报错。 */
				// 校验当前mapping是否存在对应的HandlerMethod对象，如果已存在但不是当前的handlerMethod对象则抛出异常
				validateMethodMapping(handlerMethod, mapping);

				/* 3、检验通过后，就将mapping与HandlerMethod的映射关系保存至"请求方法映射缓存"(key=mapping,value=HandlerMethod) */
				// 将mapping与handlerMethod的映射关系保存至this.mappingLookup
				// mapping=请求方法+URL，例如：POST /submit
				this.mappingLookup.put(mapping, handlerMethod);

				/* 4、获取mapping里面的请求路径，将url和mapping的映射关系保存至"请求url映射缓存"(key=url,value=mapping) */
				// 获取直接请求路径（刚刚mapping中的请求路径），也就是具体路径，没有*、？号之类通配符的路径
				List<String> directUrls = getDirectUrls(mapping);
				// 将url和mapping的映射关系保存至this.urlLookup
				for (String url : directUrls) {
					this.urlLookup.add(url, mapping);
				}

				/*

				5、获取mapping的名称。
				如果在@RequestMapping注解中设置了name，就使用设置的。
				如果没设置，就用生成一个名称（生成规则是：类名中的大写字符#方法名，例如：UserListController中的getUser()方法，得到的是ULC#getUser）

				*/

				// 初始化nameLookup
				String name = null;
				/**
				 * getNamingStrategy()：获取命名策略对象。RequestMappingInfoHandlerMethodMappingNamingStrategy。
				 * 这个对象是在创建RequestMappingHandlerMapping时，其父类RequestMappingInfoHandlerMapping的构造器中设置的，
				 * 它是RequestMappingInfoHandlerMethodMappingNamingStrategy的实例。
				 * 如果在@RequestMapping注解中设置了name就直接使用，否则将通过"类名中的大写字符#方法名"组成映射名字。
				 */
				if (getNamingStrategy() != null) {
					// 获取mapping的名称
					// 如果在@RequestMapping注解中设置了name就直接使用，否则将通过"类名中的大写字符#方法名"组成映射名字
					name = getNamingStrategy().getName(handlerMethod, mapping);
					// 将"mapping的名称"与HandlerMethod的映射关系保存至"请求名称映射器"
					addMappingName(name, handlerMethod);
				}

				/* 6、初始化跨域配置 */
				// 初始化跨域配置
				// 初始化CorsConfiguration配置对象
				CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
				if (corsConfig != null) {
					this.corsLookup.put(handlerMethod, corsConfig);
				}

				// 创建MappingRegistration对象
				// 并与mapping映射添加到registry注册表中
				this.registry.put(mapping, new MappingRegistration<>(mapping, handlerMethod, directUrls, name));
			}
			finally {
				// 释放写锁
				this.readWriteLock.writeLock().unlock();
			}
		}

		/**
		 * 检验mapping的HandlerMethod是否唯一
		 *
		 * @param handlerMethod
		 * @param mapping
		 */
		private void validateMethodMapping(HandlerMethod handlerMethod, T mapping) {
			// Assert that the supplied mapping is unique. —— 断言提供的映射是唯一的。

			/* 1、从请求映射缓存中查找是否存在当前mapping对应的HandlerMethod，如果存在，并且不是当前方法，则报错。 */

			HandlerMethod existingHandlerMethod = this.mappingLookup.get(mapping);
			if (existingHandlerMethod != null && !existingHandlerMethod.equals(handlerMethod)) {
				throw new IllegalStateException(
						"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
						handlerMethod + "\nto " + mapping + ": There is already '" +
						existingHandlerMethod.getBean() + "' bean method\n" + existingHandlerMethod + " mapped.");
			}
		}

		private List<String> getDirectUrls(T mapping) {
			List<String> urls = new ArrayList<>(1);
			// 遍历 Mapping 对应的路径 AbstractHandlerMethodMapping
			for (String path : getMappingPathPatterns(mapping)) {
				// 非**模式**路径
				if (!getPathMatcher().isPattern(path)) {
					urls.add(path);
				}
			}
			return urls;
		}

		/**
		 * 添加HandlerMethod到请求名称映射缓存中
		 *
		 * @param name					mapping名称
		 * @param handlerMethod			标注了@RequestMapping的Controller方法
		 */
		private void addMappingName(String name, HandlerMethod handlerMethod) {
			/* 1、从请求名称映射缓存中，根据"mapping名称"，获取对应的HandlerMethod集合 */
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				oldList = Collections.emptyList();
			}

			/* 2、判断当前"mapping名称"对应的handler是否已经存在"请求名称映射缓存中"，如果已经存在，则不用添加 */
			for (HandlerMethod current : oldList) {
				if (handlerMethod.equals(current)) {
					return;
				}
			}

			/* 3、如果不存在，就添加到请求名称映射缓存中 */
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
			// 保留老的
			newList.addAll(oldList);
			// 添加新的
			newList.add(handlerMethod);

			// 添加到请求名称映射缓存中
			// 题外：会覆盖，所以新的handlerMethod就添加进去了
			this.nameLookup.put(name, newList);
		}

		public void unregister(T mapping) {
			// 获得写锁
			this.readWriteLock.writeLock().lock();
			try {
				// 从 registry 中移除
				MappingRegistration<T> definition = this.registry.remove(mapping);
				if (definition == null) {
					return;
				}

				// 从 mappingLookup 中移除
				this.mappingLookup.remove(definition.getMapping());

				// 从 urlLookup 移除
				for (String url : definition.getDirectUrls()) {
					List<T> list = this.urlLookup.get(url);
					if (list != null) {
						list.remove(definition.getMapping());
						if (list.isEmpty()) {
							this.urlLookup.remove(url);
						}
					}
				}

				// 从 nameLookup 移除
				removeMappingName(definition);

				// 从 corsLookup 中移除
				this.corsLookup.remove(definition.getHandlerMethod());
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void removeMappingName(MappingRegistration<T> definition) {
			String name = definition.getMappingName();
			if (name == null) {
				return;
			}
			HandlerMethod handlerMethod = definition.getHandlerMethod();
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				return;
			}
			if (oldList.size() <= 1) {
				this.nameLookup.remove(name);
				return;
			}
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
			for (HandlerMethod current : oldList) {
				if (!current.equals(handlerMethod)) {
					newList.add(current);
				}
			}
			this.nameLookup.put(name, newList);
		}
	}


	private static class MappingRegistration<T> {

		private final T mapping;

		private final HandlerMethod handlerMethod;

		private final List<String> directUrls;

		@Nullable
		private final String mappingName;

		public MappingRegistration(T mapping, HandlerMethod handlerMethod,
				@Nullable List<String> directUrls, @Nullable String mappingName) {

			Assert.notNull(mapping, "Mapping must not be null");
			Assert.notNull(handlerMethod, "HandlerMethod must not be null");
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
			this.directUrls = (directUrls != null ? directUrls : Collections.emptyList());
			this.mappingName = mappingName;
		}

		public T getMapping() {
			return this.mapping;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public List<String> getDirectUrls() {
			return this.directUrls;
		}

		@Nullable
		public String getMappingName() {
			return this.mappingName;
		}
	}


	/**
	 * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
	 * comparing the best match with a comparator in the context of the current request.
	 *
	 * 围绕匹配的 HandlerMethod 及其映射的瘦包装器，用于在当前请求的上下文中将最佳匹配与比较器进行比较。
	 */
	private class Match {

		/**
		 * mapping对象
		 */
		private final T mapping;

		/**
		 * HandlerMethod对象
		 */
		private final HandlerMethod handlerMethod;

		public Match(T mapping, HandlerMethod handlerMethod) {
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}


	private class MatchComparator implements Comparator<Match> {

		private final Comparator<T> comparator;

		public MatchComparator(Comparator<T> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}


	private static class EmptyHandler {

		@SuppressWarnings("unused")
		public void handle() {
			throw new UnsupportedOperationException("Not implemented");
		}
	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		static private boolean isSuspend(Method method) {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
			return function != null && function.isSuspend();
		}
	}

}
