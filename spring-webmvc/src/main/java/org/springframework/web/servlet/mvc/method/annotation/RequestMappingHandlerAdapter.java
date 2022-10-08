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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.*;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.*;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extension of {@link AbstractHandlerMethodAdapter} that supports
 * {@link RequestMapping @RequestMapping} annotated {@link HandlerMethod HandlerMethods}.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers},
 * or alternatively, to re-configure all argument and return value types,
 * use {@link #setArgumentResolvers} and {@link #setReturnValueHandlers}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 * @see HandlerMethodArgumentResolver
 * @see HandlerMethodReturnValueHandler
 */
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

	/**
	 * MethodFilter that matches {@link InitBinder @InitBinder} methods. —— 匹配 {@link InitBinder @InitBinder} 方法的 MethodFilter。
	 */
	// 过滤包含了@InitBinder的方法
	public static final MethodFilter INIT_BINDER_METHODS = method ->
			AnnotatedElementUtils.hasAnnotation(method, InitBinder.class);

	/**
	 * MethodFilter that matches {@link ModelAttribute @ModelAttribute} methods.
	 */
	// 过滤不包含@RequestMapping，但是包含@ModelAttribute的方法
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method ->
			(!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class) &&
					AnnotatedElementUtils.hasAnnotation(method, ModelAttribute.class));


	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	// 用于给处理器方法和注释了@ModelAttribute的方法设置参数
	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	// 用于给注释了@initBinder的方法设置参数
	// 题外：注意，有很多个地方往里面添加了值
	@Nullable
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers/* 初始化绑定器的参数解析器 */;

	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	// 用于将处理器的返回值，处理成ModelAndView的类型
	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	@Nullable
	private List<ModelAndViewResolver> modelAndViewResolvers;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	private List<HttpMessageConverter<?>> messageConverters;

	/**
	 * ResponseBodyAdvice接口：可以修改用@ResponseBody返回的结果值
	 */
	/**
	 * 【实现了ResponseBodyAdvice接口的@ControllerAdvice Bean】参考：{@link com.springstudymvc.msb.mvc_07.response.ResponseInfoControllerAdvice}
	 */
	// 存储，实现了RequestBodyAdvice接口或者ResponseBodyAdvice接口的@ControllerAdvice Bean
	private final List<Object> requestResponseBodyAdvice = new ArrayList<>();

	// 全局的初始化器
	@Nullable
	private WebBindingInitializer webBindingInitializer;

	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");

	@Nullable
	private Long asyncRequestTimeout;

	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

	private boolean ignoreDefaultModelOnRedirect = false;

	private int cacheSecondsForSessionAttributeHandlers = 0;

	private boolean synchronizeOnSession = false;

	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	@Nullable
	private ConfigurableBeanFactory beanFactory;

	// 存储的是，解析好的，某个Controller中所有的@SessionAttributes注解属性值。这样下次就可以不用重复解析了，而是直接从缓存中获取！
	// key：Controller
	// value：SessionAttributesHandler：解析和存储当前Controller上的所有@SessionAttributes的属性值
	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<>(64);

	// 用于缓存@controllerAdvice bean里面标注了@InitBinder的方法
	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap<>();

	/**
	 * 1、modelAttributeCache和modelAttributeAdviceCache的区别：
	 * （1）modelAttributeCache是表示当前Controller中的，带了@ModelAttribute，但是没带@RequestMapping注解的方法
	 * （2）modelAttributeAdviceCache表示全局的(@ControllerAdvice bean里面的)，带了@ModelAttribute，但是没带@RequestMapping注解的方法
	 */
	// 缓存的是，当前Controller中，带了@ModelAttribute，但是没带@RequestMapping注解的方法
	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<>(64);

	// 缓存的是，ControllerAdviceBean中，带了@ModelAttribute，但是没带@RequestMapping注解的方法
	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap<>();

	public RequestMappingHandlerAdapter() {
		this.messageConverters = new ArrayList<>(4);
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(new StringHttpMessageConverter());
		try {
			this.messageConverters.add(new SourceHttpMessageConverter<>());
		}
		catch (Error err) {
			// Ignore when no TransformerFactory implementation is available
		}
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null);
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		}
		else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the argument resolvers for {@code @InitBinder} methods, or possibly
	 * {@code null} if not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null);
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return (this.returnValueHandlers != null ? this.returnValueHandlers.getHandlers() : null);
	}

	/**
	 * Provide custom {@link ModelAndViewResolver ModelAndViewResolvers}.
	 * <p><strong>Note:</strong> This method is available for backwards
	 * compatibility only. However, it is recommended to re-write a
	 * {@code ModelAndViewResolver} as {@link HandlerMethodReturnValueHandler}.
	 * An adapter between the two interfaces is not possible since the
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method
	 * cannot be implemented. Hence {@code ModelAndViewResolver}s are limited
	 * to always being invoked at the end after all other return value
	 * handlers have been given a chance.
	 * <p>A {@code HandlerMethodReturnValueHandler} provides better access to
	 * the return type and controller method information and can be ordered
	 * freely relative to other return value handlers.
	 */
	public void setModelAndViewResolvers(@Nullable List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * Return the configured {@link ModelAndViewResolver ModelAndViewResolvers}, or {@code null}.
	 */
	@Nullable
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return this.modelAndViewResolvers;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Provide the converters to use in argument resolvers and return value
	 * handlers that support reading and/or writing to the body of the
	 * request and response.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Add one or more {@code RequestBodyAdvice} instances to intercept the
	 * request before it is read and converted for {@code @RequestBody} and
	 * {@code HttpEntity} method arguments.
	 */
	public void setRequestBodyAdvice(@Nullable List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * Add one or more {@code ResponseBodyAdvice} instances to intercept the
	 * response before {@code @ResponseBody} or {@code ResponseEntity} return
	 * values are written to the response body.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply
	 * to every DataBinder instance.
	 */
	public void setWebBindingInitializer(@Nullable WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none. —— 返回配置的 WebBindingInitializer，如果没有则返回 {@code null}。
	 */
	@Nullable
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * Set the default {@link AsyncTaskExecutor} to use when a controller method
	 * return a {@link Callable}. Controller methods can override this default on
	 * a per-request basis by returning an {@link WebAsyncTask}.
	 * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
	 * It's recommended to change that default in production as the simple executor
	 * does not re-use threads.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Specify the amount of time, in milliseconds, before concurrent handling
	 * should time out. In Servlet 3, the timeout begins after the main request
	 * processing thread has exited and ends when the request is dispatched again
	 * for further processing of the concurrently produced result.
	 * <p>If this value is not set, the default timeout of the underlying
	 * implementation is used.
	 * @param timeout the timeout value in milliseconds
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * Configure {@code CallableProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[0]);
	}

	/**
	 * Configure {@code DeferredResultProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[0]);
	}

	/**
	 * Configure the registry for reactive library types to be supported as
	 * return values from controller methods.
	 * @since 5.0.5
	 */
	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
	}

	/**
	 * Return the configured reactive type registry of adapters.
	 * @since 5.0
	 */
	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively a controller method
	 * can declare a {@link RedirectAttributes} argument and use it to provide
	 * attributes for a redirect.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false} but new applications should
	 * consider setting it to {@code true}.
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Specify the strategy to store session attributes with. The default is
	 * {@link org.springframework.web.bind.support.DefaultSessionAttributeStore},
	 * storing session attributes in the HttpSession with the same attribute
	 * name as in the model.
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * Cache content produced by {@code @SessionAttributes} annotated handlers
	 * for the given number of seconds.
	 * <p>Possible values are:
	 * <ul>
	 * <li>-1: no generation of cache-related headers</li>
	 * <li>0 (default value): "Cache-Control: no-store" will prevent caching</li>
	 * <li>1 or higher: "Cache-Control: max-age=seconds" will ask to cache content;
	 * not advised when dealing with session attributes</li>
	 * </ul>
	 * <p>In contrast to the "cacheSeconds" property which will apply to all general
	 * handlers (but not to {@code @SessionAttributes} annotated handlers),
	 * this setting will apply to {@code @SessionAttributes} handlers only.
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * Set if controller execution should be synchronized on the session,
	 * to serialize parallel invocations from the same client.
	 * <p>More specifically, the execution of the {@code handleRequestInternal}
	 * method will get synchronized if this flag is "true". The best available
	 * session mutex will be used for the synchronization; ideally, this will
	 * be a mutex exposed by HttpSessionMutexListener.
	 * <p>The session mutex is guaranteed to be the same object during
	 * the entire lifetime of the session, available under the key defined
	 * by the {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a
	 * safe reference to synchronize on for locking on the current session.
	 * <p>In many cases, the HttpSession reference itself is a safe mutex
	 * as well, since it will always be the same object reference for the
	 * same active logical session. However, this is not guaranteed across
	 * different servlet containers; the only 100% safe way is a session mutex.
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see org.springframework.web.util.WebUtils#getSessionMutex(javax.servlet.http.HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter names if needed
	 * (e.g. for default attribute names).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions
	 * in method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * Return the owning factory of this bean instance, or {@code null} if none.
	 */
	@Nullable
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		/*

		1、获取所有容器中的所有的@ControllerAdvice bean，然后进行解析，具体如下：
		（1）获取ControllerAdviceBean中，带了@ModelAttribute，但是没带@RequestMapping注解的方法，保存在modelAttributeAdviceCache集合中
		（2）获取ControllerAdviceBean中，带了@InitBinder注解的方法，保存到initBinderAdviceCache集合中
		（3）判断当前@ControllerAdvice Bean是否实现了RequestBodyAdvice接口或者ResponseBodyAdvice接口，
		是的话就保存当前@ControllerAdvice Bean到requestResponseBodyAdvice集合中

		*/
		// Do this first, it may add ResponseBody advice beans —— 首先执行此操作，它可能会添加ResponseBody advice bean
		// 初始化标注了@ControllerAdvice的类的相关属性
		initControllerAdviceCache();

		/* 2、初始化所有"参数解析器" */

		// 初始化argumentResolvers属性
		if (this.argumentResolvers == null) {
			// 题外：默认的参数解析器一共是26个
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}

		/* 3、初始化所有"@InitBinder的参数解析器" */

		// 初始化initBinderArgumentResolvers属性
		if (this.initBinderArgumentResolvers/* 始化绑定器的参数解析器 */  == null) {
			// 题外：默认的InitBinderArgumentResolver初始化绑定器的参数解析器，一共是12个
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}

		/* 4、初始化所有"返回值处理器" */

		// 初始化returnValueHandlers属性
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initControllerAdviceCache() {
		// 判断当前应用程序上下文是否为空，如果为空，直接返回
		if (getApplicationContext() == null) {
			return;
		}

		/* 1、获取所有容器中的所有标注了@ControllerAdvice的bean，生成对应的ControllerAdviceBean对象 */
		// 获取所有容器中的所有标注了@ControllerAdvice的bean，生成对应的ControllerAdviceBean对象，并进行排序
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());

		// 存储"是@ControllerAdvice bean，且实现了RequestBodyAdvice或者ResponseBodyAdvice接口"的bean对象
		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();

		/* 2、遍历刚刚获取到的所有的ControllerAdviceBean */

		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}

			/* 2.1、获取ControllerAdviceBean中，带了@ModelAttribute，但是没带@RequestMapping注解的方法，保存在modelAttributeAdviceCache集合中 */

			// 扫描@ControllerAdvice bean中，带了`@ModelAttribute`，但是没带`@RequestMapping`注解的方法，添加到`modelAttributeAdviceCache`属性中
			// 题外：带了`@ModelAttribute`，但是没带`@RequestMapping`注解的方法；该类方法用于在执行方法前修改Model对象
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType, MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods);
			}

			/* 2.2、获取ControllerAdviceBean中，带了@InitBinder注解的方法，保存到initBinderAdviceCache集合中 */

			// 扫描有`@InitBinder`注解的方法，添加到`initBinderAdviceCache`属性中
			// 题外：`@InitBinder`注解的方法，该类方法用于在执行方法前，初始化数据绑定器
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType, INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(adviceBean, binderMethods);
			}

			/*

			2.3、判断当前@ControllerAdvice Bean是否实现了RequestBodyAdvice接口或者ResponseBodyAdvice接口，
			是的话就保存当前@ControllerAdvice Bean到requestResponseBodyAdvice集合中

			*/
			/**
			 * 1、【实现了ResponseBodyAdvice接口的@ControllerAdvice Bean】参考：{@link com.springstudymvc.msb.mvc_07.response.ResponseInfoControllerAdvice}
			 */
			// 如果ControllerAdviceBean是RequestBodyAdvice或ResponseBodyAdvice接口的子类，添加到requestResponseBodyAdviceBeans中
			if (RequestBodyAdvice.class.isAssignableFrom(beanType) || ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
			}
		}
		// ⚠️将所有实现了RequestBodyAdvice或者ResponseBodyAdvice接口的ControllerAdviceBean添加到this.requestResponseBodyAdvice属性中
		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}

		// 打印日志
		if (logger.isDebugEnabled()) {
			int modelSize = this.modelAttributeAdviceCache.size();
			int binderSize = this.initBinderAdviceCache.size();
			int reqCount = getBodyAdviceCount(RequestBodyAdvice.class);
			int resCount = getBodyAdviceCount(ResponseBodyAdvice.class);
			if (modelSize == 0 && binderSize == 0 && reqCount == 0 && resCount == 0) {
				logger.debug("ControllerAdvice beans: none");
			}
			else {
				logger.debug("ControllerAdvice beans: " + modelSize + " @ModelAttribute, " + binderSize +
						" @InitBinder, " + reqCount + " RequestBodyAdvice, " + resCount + " ResponseBodyAdvice");
			}
		}
	}

	// Count all advice, including explicit registrations.. —— 计算所有建议，包括显式注册..

	private int getBodyAdviceCount(Class<?> adviceType) {
		List<Object> advice = this.requestResponseBodyAdvice;
		return RequestBodyAdvice.class.isAssignableFrom(adviceType) ?
				RequestResponseBodyAdviceChain.getAdviceByType(advice, RequestBodyAdvice.class).size() :
				RequestResponseBodyAdviceChain.getAdviceByType(advice, ResponseBodyAdvice.class).size();
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(30);

		/* 1、注解的参数解析器 */
		// Annotation-based argument resolution —— 基于注解的参数解析
		// 添加按"注解解析参数"的解析器
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		/* 2、根据特定参数类型解析参数的参数解析器 */
		// Type-based argument resolution —— 基于类型的参数解析
		// 添加按类型解析参数的解析器
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());
		resolvers.add(new MapMethodProcessor());
		resolvers.add(new ErrorsMethodArgumentResolver());
		resolvers.add(new SessionStatusMethodArgumentResolver());
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());

		/* 3、自定义的参数解析器 */
		// Custom arguments —— 自定义参数
		// 添加自定义参数解析器，主要用于解析自定义类型
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		/* 4、解析所有参数类型的参数解析器 */
		// Catch-all —— 包罗万象
		// 最后两个解析器，可以解析所有类型的参数
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

	/**
	 * Return the list of argument resolvers to use for {@code @InitBinder}
	 * methods including built-in and custom resolvers.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(20);

		/* 1、注解的参数解析器 */
		// Annotation-based argument resolution —— 基于注解的参数解析
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		/* 2、根据类型的参数解析器 */
		// Type-based argument resolution —— 基于类型的参数解析
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		/* 3、自定义的参数解析器 */
		// Custom arguments —— 自定义参数
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		/* 4、解析所有参数类型的解析器 */
		// Catch-all —— 包罗万象
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(20);

		/* 1、处理单一返回值类型的返回值处理器 */
		// Single-purpose return value types —— 单一用途的返回值类型
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		handlers.add(new ModelMethodProcessor());
		handlers.add(new ViewMethodReturnValueHandler());
		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters(),
				this.reactiveAdapterRegistry, this.taskExecutor, this.contentNegotiationManager));
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));
		handlers.add(new HttpHeadersReturnValueHandler());
		handlers.add(new CallableMethodReturnValueHandler());
		handlers.add(new DeferredResultMethodReturnValueHandler());
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));

		/* 2、注解的返回值处理器 */
		// Annotation-based return value types —— 基于注解的返回值类型
		handlers.add(new ModelAttributeMethodProcessor(false));
		// 处理@ResponseBody的
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));

		/* 3、处理多种返回值类型的返回值处理器 */
		// Multi-purpose return value types —— 多用途返回值类型
		handlers.add(new ViewNameMethodReturnValueHandler());
		handlers.add(new MapMethodProcessor());

		/* 4、自定义返回值处理器 */
		// Custom return value types —— 自定义返回值类型
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		/* 5、处理所有返回值的返回值处理器 */
		// Catch-all —— 包罗万象
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		}
		else {
			handlers.add(new ModelAttributeMethodProcessor(true));
		}

		return handlers;
	}


	/**
	 * Always return {@code true} since any method argument and return value
	 * type will be processed in some way. A method argument not recognized
	 * by any HandlerMethodArgumentResolver is interpreted as a request parameter
	 * if it is a simple type, or as a model attribute otherwise. A return value
	 * not recognized by any HandlerMethodReturnValueHandler will be interpreted
	 * as a model attribute.
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;

		/*

		1、检查请求：
		（1）看是否支持当前请求方法，不支持则报错；
		（2）是否必须存在session，如果必须，则判断是否实际存在session对象，不存在的话，就报错

		 */
		// 校验请求（HttpMethod和Session的校验）
		checkRequest(request);

		/* 2、执行处理请求的方法 */
		// Execute invokeHandlerMethod in synchronized block if required.
		// 如果synchronizeOnSession为true，则对session进行同步，否则不同步
		if (this.synchronizeOnSession/* false */) {
			// 同步相同 Session 的逻辑，默认情况false
			HttpSession session = request.getSession(false);
			if (session != null) {
				// 获取Session的锁对象
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					// ⚠️
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {
				// No HttpSession available -> no mutex necessary —— 没有可用的 HttpSession -> 不需要互斥锁
				// ⚠️
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		}
		else {
			// No synchronization on session demanded at all... —— 完全不需要会话同步...
			// ⚠️
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		/* 3、响应不包含'Cache-Control'响应头的处理 */

		if (!response.containsHeader(HEADER_CACHE_CONTROL/* Cache-Control */)) {
			/* 响应不包含'Cache-Control'响应头头 */

			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				prepareResponse(response);
			}
		}

		return mav;
	}

	/**
	 * This implementation always returns -1. An {@code @RequestMapping} method can
	 * calculate the lastModified value, call {@link WebRequest#checkNotModified(long)},
	 * and return {@code null} if the result of that call is {@code true}.
	 */
	@Override
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}


	/**
	 * Return the {@link SessionAttributesHandler} instance for the given handler type
	 * (never {@code null}).
	 */
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		// 先看缓存中有没有对应Controller的SessionAttributesHandler，
		// （1）有的话直接获取
		// （2）没有的话再创建SessionAttributesHandler和存储
		return this.sessionAttributesHandlerCache.computeIfAbsent(
				handlerMethod.getBeanType(),
				// ⚠️在SessionAttributesHandler里面，解析了当前Controller上的所有的@SessionAttributs属性值，并进行存储
				type -> new SessionAttributesHandler(type, this.sessionAttributeStore));
	}

	/**
	 * Invoke the {@link RequestMapping} handler method preparing a {@link ModelAndView}
	 * if view resolution is required.
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	@Nullable
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod/* 处理具体请求的方法 */) throws Exception {

		/* 1、创建ServletWebRequest对象，封装request和response */
		ServletWebRequest webRequest = new ServletWebRequest(request, response);

		try {
			/*

			2、创建数据绑定工厂（WebDataBinderFactory），里面包含了当前Controller中的@InitBinder方法和全局@InitBinder方法。
			（1）当前controller中的@InitBinder方法是在这里面进行识别的
			（2）然后将全局@InitBinder方法与当前controller中的@InitBinder方法进行合并。其中，全局@InitBinder方法优先。

			题外：全局的@InitBinder方法是在初始化适配器的时候，RequestMappingHandlerAdapter#afterProperties()里面进行初始化的
			题外：方便后面在进行值处理的过程中，能通过@InitBinder方法来处理相关值。

			数据绑定工厂：处理数据绑定的问题

			 */
			/**
			 * 1、@InitBinder：作用于HandlerMethod(@Controller中的方法)，表示为当前控制器注册一个属性编辑器，同时可以设置属性的工作。所谓的属性编辑器可以理解就是帮助我们完成参数绑定。
			 *
			 * 对webDataBinder进行初始化，且只对当前的Controller有效
			 *
			 * 注意：@InitBinder标注的方法必须有一个参数WebDataBinder
			 *
			 * 题外：@InitBinder是我们前置的条件，在具体调用handler处理请求之前，我必须要把这些数据绑定器获取到，只有获取到了才能在实际调用handler处理请求的时候，进行应用
			 *
			 * 2、@InitBinder作用：数据绑定
			 */
			// binderFactory作用：创建一堆数据绑定的对象，方便进行任何参数的绑定。由当前的factory获取具体的数据绑定对象，由具体的数据绑定对象来进行参数绑定
			// 参数绑定：传过来一个参数，要对我们请求里面的属性做一个匹配

			// 创建WebDataBinderFactory对象，此对象用来创建WebDataBinder对象，进行参数绑定，
			// 实现参数跟String之间的类型转换，参数解析器在进行参数解析的过程中会用到WebDataBinder
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);

			/*

			3、创建模型工厂（ModelFactory），里面包含了当前Controller上的@SessionAttributes设置的属性值，以及当前Controller中和全局的@ModelAttribute方法
			（1）获取当前Controller上的@SessionAttributes，然后解析@SessionAttributes里面设置的的属性值，并进行存储
			（2）获取当前Controller中和全局的，标注了@ModelAttribute，但是没有标注@RequestMapping的方法进行存储。
			>>> 其中全局方法优先。当前Controller中的@ModelAttribute方法是这里面进行识别的；全局的@ModelAttribute方法是在初始化适配器的时候，RequestMappingHandlerAdapter#afterProperties()里面进行初始化的

			题外：⚠️里面将@InitBinder方法，适配为可执行的HandlerMethod（InvocableHandlerMethod），并设置了：InitBinder的参数解析器、数据绑定工厂、参数名称发现器

			*/
			/**
			 * 1、spring mvc中3个用于参数传递的对象：Model、ModelAndView、HashMap
			 *
			 * 2、我们在创建Model的时候，可以往里面设置具体的属性值，ModelFactory是用来处理具体的Model值的。由ModelFactory创建具体的Moder对象，
			 * 方便我们往里面设置具体的属性值，在前端进行回显或进行参数操作
			 */
			// 创建ModelFactory对象，此对象主要用来处理model，主要是两个功能，1是在处理器具体处理之前对model进行初始化，2是在处理完请求后对model参数进行更新
			ModelFactory modelFactory/* 模型工厂 */ = getModelFactory(handlerMethod, binderFactory);

			/*

			4、创建执行请求的对象（ServletInvocableHandlerMethod），里面包装了HandlerMethod。然后往里面设置：(1)参数处理器；(2)返回值处理器；(3)数据绑定工厂；(4)参数名称发现器。

			题外：ServletInvocableHandlerMethod：执行请求的对象，由当前对象去调用我们的请求处理方法(HandlerMethod)
			️题外：️之所以要用ServletInvocableHandlerMethod来包装原先的handlerMethod，是因为ServletInvocableHandlerMethod在HandlerMethod的基础之上，具备：
			（1）设置"响应状态码"的能力
			（2）通过"返回值处理器"处理"返回值"的能力；

			 */
			// 创建一个执行请求的对象，由当前对象去调用我们的请求处理方法
			// 创建ServletInvocableHandlerMethod对象，并设置其相关属性，实际的请求处理就是通过此对象来完成的，参数绑定、处理请求以及返回值处理都在里边完成
			ServletInvocableHandlerMethod/* Servlet可调用处理程序方法 */ invocableMethod/* 可调用方法 */ = createInvocableHandlerMethod(handlerMethod);
			// 设置参数解析器
			if (this.argumentResolvers != null) {
				invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
			}
			// 设置返回值处理器
			if (this.returnValueHandlers != null) {
				invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
			}
			// 设置参数绑定工厂对象
			invocableMethod.setDataBinderFactory(binderFactory);
			// 设置参数名称发现器
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);

			/*

			5、创建ModelAndViewContainer对象，用于保存Model和View对象。最主要的目的是为了返回一个ModelAndView对象，返回进行视图渲染和返回。

			*/

			// 创建ModelAndViewContainer对象，用于保存Model和View对象
			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			// 将flashMap中的数据设置到model中
			// flashMap：方便重定向时传递参数用的，因为默认重定向只能在url上传递参数，这种方式参数容易暴露，而且长度有限
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));

			/*

			6、通过modelFactory，初始化Model：
			（1）根据之前解析好的当前Controller上的@SessionAttributes的属性名，去session作用域中获取对应的属性值，然后将获取到的属性值放入ModelAndViewContainer中；
			（2）执行当前Controller中的和全局的@ModelAttribute方法，然后将返回值设置到ModelAndViewContainer中；
			（3）判断控制器方法(Controller中具体处理请求的方法)的参数，是否被@ModelAttribute修饰，
			>>> 如果方法参数被@ModelAttribute修饰，并且@ModelAttribute中设置的"参数名"在当前Controller上的@SessionAttributes中有设置，
			>>> 则根据@ModelAttribute中设置的"参数名"去"session作用域"里面获取对应的参数值，然后设置到ModelAndViewContainer中

			题外：设置到ModelAndViewContainer中是调用ModelAndViewContainer#addAtrtribute()，
			>>> 而调用ModelAndViewContainer#addAtrtribute()设置属性值，就是调用Model，往Model中设置属性值！

			 */
			// 使用modelFactory将@SessionAttributes属性值和@ModelAttribute方法的返回值设置到model中
			modelFactory.initModel(webRequest, mavContainer, invocableMethod);

			// 设置ignoreDefaultModelOnRedirect
			mavContainer.setIgnoreDefaultModelOnRedirect/* 设置在重定向时忽略默认模型 */(this.ignoreDefaultModelOnRedirect);

			/*

			7、异步请求处理

			当我们handler返回一个task，则在返回值处理器里面会开启一个异步请求，异步请求处理完成之后，它会重新调用doDispatch()进行处理，
			所以doDispatch()里面要处理对应的异步请求。

			所以当时异步请求进来，并且有异步结果时，就要把HandlerMethod由ServletInvocableHandlerMethod，变成一个ConcurrentResultHandlerMethod(并发结果HandlerMethod)，
			然后进行调用，处理并发的结果，进行响应。

			*/

			// 创建异步处理请求对象(因为要进行异步请求，所以请求要进行转换，把当前请求，转换为异步请求对象)
			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			// 设置超时时间
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);

			// 获取异步请求管理器（因为所有异步处理的执行过程，不管是开启、还是注册监听器，都是由管理器来完成）
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			// 设置异步请求
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			// 设置拦截器
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			// 设置拦截器
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

			// 判断异步管理器里面，有没有执行结果，如果有执行结果，则会把mavContainer、invocableMethod做一个替换，如果没有结果，则还是按照正常的逻辑处理往下执行，该怎么走还是怎么走
			// 如果当前异步请求已经处理并得到结果，则将返回的结果放到mavContainer对象中，然后将invocable对象进行包装转换，转成需要的执行对象然后开始执行

			// 题外：异步请求是说，我们的主线程一直沿着主路往下走，但中间会开辟一条分支出来，不会影响主线程往下走
			if (asyncManager.hasConcurrentResult()/* 有并发结果 */) {
				// 获取结果
				Object result = asyncManager.getConcurrentResult();
				// 获取并发结果上下文，也就是获取一个ModelAndViewContainer
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				// 清除并发结果
				asyncManager.clearConcurrentResult();
				LogFormatUtils.traceDebug(logger, traceOn -> {
					String formatted = LogFormatUtils.formatValue(result, !traceOn);
					return "Resume with async result [" + formatted + "]";
				});
				// 转换具体的invocable执行对象，
				// 当转换完成之后，把我们正常的处理结果进行invokeAndHandle()调用执行
				invocableMethod = invocableMethod.wrapConcurrentResult/* 包装并发结果 */(result);
			}

			/* 8、调用处理请求的方法 */

			// ⚠️执行调用(调用我们接收请求、处理请求的方法)
			invocableMethod.invokeAndHandle(webRequest, mavContainer);


			/**
			 * 题外：⚠️如果在上面调用具体handler的过程中，返回的是一个异步任务，那么一定会开启线程，执行异步处理；
			 * >>> 在开启之前，肯定是把"是否开启异步处理的标识"设置了为true，再开启异步线程的！
			 * >>> 所以等我们走到这一步的时候，就可以感知得到！
			 */
			// 判断是否启动了异步处理，如果启动了，则直接返回null，不用干其它事情了；如果没有启动异步处理，则接着按照一个正常的请求往下走。
			if (asyncManager.isConcurrentHandlingStarted/* 是并发处理开始 */()) {
				return null;
			}

			/* 9、获取ModelAndView对象返回，将返回值和需要存储的数据结果放进去，有了ModelAndView后面就可以进行视图渲染和回显 */

			// 获取ModelAndView对象
			// 处理完请求后的后置处理，此处一共做了三件事，
			// 1、调用ModelFactory的updateModel方法更新model，包括设置SessionAttribute和给Model设置BinderResult
			// 2、根据mavContainer创建了ModelAndView
			// 3、如果mavContainer里的model是RedirectAttributes类型，则将其设置到FlashMap
			return getModelAndView(mavContainer, modelFactory, webRequest);
		}
		finally {// 题外：如果中间抛出异常了，也会执行该方法
			// 标记请求完成
			webRequest.requestCompleted();
		}
	}

	/**
	 * Create a {@link ServletInvocableHandlerMethod} from the given {@link HandlerMethod} definition.
	 * @param handlerMethod the {@link HandlerMethod} definition
	 * @return the corresponding {@link ServletInvocableHandlerMethod} (or custom subclass thereof)
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
		return new ServletInvocableHandlerMethod(handlerMethod);
	}

	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {

		/* 1、获取SessionAttributesHandler，里面解析了当前Controller上的@SessionAttributes属性值，然后进行存储 */

		SessionAttributesHandler sessionAttrHandler/* 会话属性处理器、Session属性处理器 */ = getSessionAttributesHandler(handlerMethod);

		/* 2、获取当前Controller中和全局的，标注了@ModelAttribute，但是没有标注@RequestMapping的方法 */

		/* 2.1、获取当前Controller中标注了@ModelAttribute，但是没有标注@RequestMapping的方法 */

		// 获取处理器类的类型
		// 获取方法所归属的Controller bean的类型
		Class<?> handlerType = handlerMethod.getBeanType();
		// 获取处理器类中注释了@modelAttribute而且没有注释@RequestMapping的类型，第一个获取后添加到缓存，以后直接从缓存中获取
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
			this.modelAttributeCache.put(handlerType, methods);
		}

		// 存储所有标注了@ModelAttribute，但是没有标注@RequestMapping的方法
		// 题外：先往里面存储全局的@ModelAttribute方法，后存储当前Controller的@ModelAttribute方法
		List<InvocableHandlerMethod> attrMethods = new ArrayList<>();

		/* 2.2、获取全局的的，标注了@ModelAttribute，但是没有标注@RequestMapping的方法 */

		// Global methods first —— 全局方法优先
		// （1）先添加全局的@ModelAttribute方法
		this.modelAttributeAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		});

		/* 合并存储当前Controller中的以及全局的，标注了@ModelAttribute，但是没有标注@RequestMapping的方法。全局方法优先。 */

		// 注意：全局方法优先，当前Controller方法靠后：先往添加全局的@ModelAttribute方法，后存储当前Controller的@ModelAttribute方法
		// （2）后添加当前处理器定义的@ModelAttribute方法
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}

		/* 3、创建ModelFactory对象，往里面设置SessionAttributesHandler、attrMethods、WebDataBinderFactory */

		// 新建ModelFactory对象，此处需要三个参数，第一个是注释了@ModelAttribute的方法，第二个是WebDataBinderFactory,第三个是SessionAttributeHandler
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}

	/**
	 * 创建一个可以调用的HandlerMethod
	 *
	 * @param factory	所有@InitBinder方法
	 * @param bean		当前Controller bean
	 * @param method	所有@ModelAttribute，但是没标注@RequestMapping的方法
	 * @return InvocableHandlerMethod
	 */
	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
		/* 1、创建一个可以调用的HandlerMethod */
		// 创建一个可以调用的HandlerMethod
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);

		/* 2、往刚刚创建的"可以调用的HandlerMethod"里面设置一些属性值 */
		// 设置HandlerMethod的参数解析器
		if (this.argumentResolvers != null) {
			attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		// 设置HandlerMethod的参数名称发现器
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		// 设置HandlerMethod的数据绑定工厂
		attrMethod.setDataBinderFactory(factory);

		/* 3、返回刚刚创建的可以调用的HandlerMethod */
		return attrMethod;
	}

	/**
	 * 创建数据绑定工厂（WebDataBinderFactory），里面包含了当前Controller中的@InitBinder方法和全局@InitBinder方法。
	 * 	（1）当前controller中的@InitBinder方法是在这里面进行识别的
	 * 	（2）然后将全局@InitBinder方法与当前controller中的@InitBinder方法进行合并。其中，全局@InitBinder方法优先。
	 *
	 * 	题外：全局的@InitBinder方法是在初始化适配器的时候，RequestMappingHandlerAdapter#afterProperties()里面进行初始化的
	 * 	题外：方便后面在进行值处理的过程中，能通过@InitBinder方法来处理相关值。
	 *
	 * @param handlerMethod
	 * @return
	 * @throws Exception
	 */
	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		// 获取方法所归属的Controller bean的类型
		Class<?> handlerType = handlerMethod.getBeanType();

		/* 1、获取当前Controller中的@InitBinder方法 */

		// 检查initBinderCache缓存中是否存在当前Controller中的@initBinder方法
		Set<Method> methods = this.initBinderCache.get(handlerType);
		// 如果缓存中没有，则查找当前Controller中的@initBinder方法，并设置到缓存中
		if (methods == null) {
			// 获取当前Controller中所有被@InitBinder修饰的方法
			// 题外：MethodIntrospector.selectMethods()是获取所有方法，
			// >>> INIT_BINDER_METHODS是对获取到的方法做过滤；
			// >>> 具体是：过滤得到方法上包含了@InitBinder的方法
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			this.initBinderCache.put(handlerType, methods);
		}

		// 定义保存当前Controller中的@InitBinder方法，和全局的@InitBinder方法的集合
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();

		/* 2、获取全局的@InitBinder的方法 */
		// 注意：全局的@InitBinder方法是在初始化适配器的时候，进行识别的！
		/**
		 * 题外：在@ControllerAdvice修饰的类中定义的@InitBinder方法，就是属于全局的@InitBinder方法。例如：
		 *
		 *       @ControllerAdvice
		 *       public class ControllerAdviceController {
		 *
		 *           @InitBinder("a")
		 *           public void a(WebDataBinder binder){
		 * 				binder.setFieldDefaultPrefix("a.");
		 *           }
		 *
		 *       }
		 */
		// Global methods first —— 全局方法优先
		// 将所有符合条件的全局@InitBinder方法，添加到initBinderMethods
		this.initBinderAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			// 看是否归属于ControllerAdvice这个类型
			if (controllerAdviceBean.isApplicableToBeanType/* 适用于Bean类型 */(handlerType)) {
				// 获取controllerAdviceBean的bean对象
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					// ⚠️里面将@InitBinder方法，适配为可执行的HandlerMethod：InvocableHandlerMethod，
					// 并设置了：InitBinder的参数解析器、数据绑定工厂、参数名称发现器
					initBinderMethods.add(/* ⚠️InvocableHandlerMethod */createInitBinderMethod(bean, method));
				}
			}
		});

		/* 3、将"全局的@InitBinder方法"和"当前Controller中的@InitBinder方法"进行合并 */
		/**
		 * 题外：initBinderMethods是个List，以及从下面的合并操作，看得出来，如果有相同的@InitBinder方法，全局的@InitBinder方法和Controller的@InitBinder方法，并不会覆盖！
		 */
		// 将当前handler中的initBinder方法添加到initBinderMethods
		for (Method method : methods) {
			// 创建当前方法对应的bean对象
			Object bean = handlerMethod.getBean();
			// ⚠️里面将@InitBinder方法，适配为可执行的HandlerMethod（InvocableHandlerMethod），并设置了：InitBinder的参数解析器、数据绑定工厂、参数名称发现器
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}

		/* 4、创建数据绑定工厂(DataBinderFactory)，传入当前Controller中的@InitBinder方法和全局@InitBinder方法 */

		// DataBinderFactory = ServletRequestDataBinderFactory
		return createDataBinderFactory(initBinderMethods);
	}

	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		/*

		1、创建一个InvocableHandlerMethod(执行方法的对象)，在其构造方法内部：
		（1）初始化了方法参数对象！当然是一个空壳对象！并不具备参数值 —— initMethodParameters()；
		（2）识别了方法上的@ReponseStatus注解中的响应状态码和响应原因。

		 */
		// InvocableHandlerMethod：可调用处理方法的对象
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);

		/* 2、往InvocableHandlerMethod里面设置一些属性值：InitBinder的参数解析器、数据绑定工厂、参数名称发现器 */

		// (1)设置所有的InitBinder的参数解析器
		if (this.initBinderArgumentResolvers/* 初始化绑定器的参数解析器 */ != null) {
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		// (2)设置数据绑定工厂
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		// (3)设置参数名称发现器
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * Template method to create a new InitBinderDataBinderFactory instance.
	 * <p>The default implementation creates a ServletRequestDataBinderFactory.
	 * This can be overridden for custom ServletRequestDataBinder subclasses.
	 * @param binderMethods {@code @InitBinder} methods
	 * @return the InitBinderDataBinderFactory instance to use
	 * @throws Exception in case of invalid state or arguments
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {

		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer()/* ⚠️获取全局初始化器 */);
	}

	@Nullable
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer,
			ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {

		/* 1、更新model */

		/**
		 * 在之前的处理过程中，我们有往Model里面设置具体的属性值（@ModelAttribute），
		 * 而这块是因为我前面经过了一系列的处理逻辑，所以我现在要把我们的Model对象，进行相关的更新操作
		 */
		// 更新model(设置sessionAttributes和给model设置BindingResult)
		modelFactory.updateModel(webRequest, mavContainer);

		/*

		2、如果请求已经处理完成了，则ModelAndView对象返回为null，这样后续就不会进行视图解析和渲染了

		题外：什么样的情况是请求已经处理完成了？@ResponseBody修饰方法的时候。@ResponseBody所修饰的方法，在处理返回值的时候，是由RequestResponseBodyMethodProcessor进行处理的，
		里面会将请求是否处理完成的标识设置为true，因为里面的逻辑会将方法返回结果以json的形式响应回客户端！直接完成响应！所以请求完成了，后续不需要再处理该请求了！

		*/

		// 情况一，如果 mavContainer 已处理过了，则返回“空”的 ModelAndView 对象。
		if (mavContainer.isRequestHandled()) {
			return null;
		}

		/*

		3、如果请求没有处理完成，就通过ModelAndViewContainer返回一个ModelAndView对象进行返回，并设置相关属性，有：view、model、status

		 */

		// 情况二，如果mavContainer未处理，则基于`mavContainer`生成ModelAndView对象
		ModelMap model = mavContainer.getModel();
		/**
		 * 在正常的spring mvc请求流程里面，它是需要一个view的，因为在这里面有一个非常重要的对象，叫做ModelAndView，
		 * 我是需要这个模型对象和视图对象的，但现在是没有模型对象和视图对象的
		 */
		// 创建ModelAndView对象，并设置相关属性
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());

		// 判断ModelAndViewContainer中的view是不是viewName
		// 如果ModelAndViewContainer中的view不是viewName，就代表view是一个View实例对象，就将当前view设置到ModelAndView里面去
		if (!mavContainer.isViewReference()) {
			mav.setView((View) mavContainer.getView());
		}

		// 判断ModelAndViewContainer中的model是不是一个重定向model
		if (model instanceof RedirectAttributes/* 重定向属性 */) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
		}
		return mav;
	}

}
