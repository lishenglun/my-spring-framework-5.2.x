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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerMethodExceptionResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link AbstractHandlerMethodExceptionResolver} that resolves exceptions
 * through {@code @ExceptionHandler} methods.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers}.
 * Or alternatively to re-configure all argument and return value types use
 * {@link #setArgumentResolvers} and {@link #setReturnValueHandlers(List)}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ExceptionHandlerExceptionResolver extends AbstractHandlerMethodExceptionResolver
		implements ApplicationContextAware, InitializingBean {

	/**
	 * 自定义的方法参数处理器
	 */
	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	/**
	 * 方法参数解析器组合
	 */
	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	/**
	 * 自定义的执行结果处理器
	 */
	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	/**
	 * 执行结果处理器组合
	 */
	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	/**
	 * HTTP 消息转换器
	 */
	private List<HttpMessageConverter<?>> messageConverters;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	/**
	 * 响应体的后置通知器（响应体的后置增强器）
	 */
	// 实现了ResponseBodyAdvice接口的@ControllerAdvice bean
	private final List<Object> responseBodyAdvice = new ArrayList<>();

	@Nullable
	private ApplicationContext applicationContext;

	// 当前controller的异常处理缓存。里面包含了当前Controller中的异常处理方法（当前Controller中的@ExceptionHandler修饰的方法）
	// key：一般存储Controller
	// value：ExceptionHandlerMethodResolver：里面存储了异常类型和对应处理方法的映射关系
	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache =
			new ConcurrentHashMap<>(64);

	// 全局的异常处理缓存。里面包含了全局的异常处理方法（@ControllerAdvice bean中的@ExceptionHandler修饰的方法）
	// key：ControllerAdviceBean
	// value：ExceptionHandlerMethodResolver：里面存储了异常类型和对应处理方法的映射关系
	private final Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> exceptionHandlerAdviceCache =
			new LinkedHashMap<>();


	public ExceptionHandlerExceptionResolver() {
		// 初始化 messageConverters
		this.messageConverters = new ArrayList<>();
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
	public HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		return this.argumentResolvers;
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
	public HandlerMethodReturnValueHandlerComposite getReturnValueHandlers() {
		return this.returnValueHandlers;
	}

	/**
	 * Set the message body converters to use.
	 * <p>These converters are used to convert from and to HTTP requests and responses.
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
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Return the configured {@link ContentNegotiationManager}.
	 */
	public ContentNegotiationManager getContentNegotiationManager() {
		return this.contentNegotiationManager;
	}

	/**
	 * Add one or more components to be invoked after the execution of a controller
	 * method annotated with {@code @ResponseBody} or returning {@code ResponseEntity}
	 * but before the body is written to the response with the selected
	 * {@code HttpMessageConverter}.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		this.responseBodyAdvice.clear();
		if (responseBodyAdvice != null) {
			this.responseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	@Override
	public void setApplicationContext(@Nullable ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Nullable
	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}


	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBodyAdvice beans —— 首先这样做，它可能会添加ResponseBodyAdvice bean
		/*

		1、初始化@ControllerAdvice bean中的全局异常处理方法（也就是@ExceptionHandler修饰的方法），
		和全局的响应体后置通知器(也就是实现了ResponseBodyAdvice接口的@ControllerAdvice bean)

		 */
		// 初始化exceptionHandlerAdviceCache、responseBodyAdvice
		initExceptionHandlerAdviceCache();

		/* 2、初始化参数解析器 */
		// 初始化 argumentResolvers 参数
		if (this.argumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}

		/* 3、初始化返回值处理器 */
		// 初始化 returnValueHandlers 参数
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initExceptionHandlerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}

		/* 1、获取所有容器中所有的@ControllerAdvice bean */
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());

		// 遍历@ControllerAdvice bean
		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: "/* ControllerAdviceBean 的不可解析类型 */ + adviceBean);
			}

			/* 2、识别@ControllerAdvice bean中的被@ExceptionHandler修饰的方法，然后注册异常类型和处理方法的映射关系到"全局异常处理方法集合"中 */

			// 解析@ControllerAdvice bean中所有被@ExceptionHandler修饰的方法
			// 然后遍历@ExceptionHandler修饰的方法，提取方法处理的异常类型，并注册"异常类型"与"处理方法"的映射关系
			// 注意：1、先是获取当前方法上的@ExceptionHandler中配置的异常类型作为当前方法处理的异常类型；2、如果没有，再获取方法参数上的异常类型，作为当前方法处理的异常类型。
			// 注意：在注册"异常类型"与"处理方法"的映射关系时，如果"异常类型"之前已经存在"处理方法"，并且不是同一个方法；也就是说有2个方法处理同一异常类型，则抛出异常。
			ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(beanType);

			// 注册全局的异常处理方法（有@ExceptionHandler修饰的方法，则添加到exceptionHandlerAdviceCache中）
			if (resolver.hasExceptionMappings()) {
				this.exceptionHandlerAdviceCache.put(adviceBean, resolver);
			}

			/* 3、看下@ControllerAdvice bean是否实现了ResponseBodyAdvice接口，实现了，则添加到"responseBodyAdvice响应体的后置通知器"中 */

			// 如果@ControllerAdvice bean实现了ResponseBodyAdvice接口，则添加到responseBodyAdvice中
			if (ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				this.responseBodyAdvice.add(adviceBean);
			}
		}

		if (logger.isDebugEnabled()) {
			int handlerSize = this.exceptionHandlerAdviceCache.size();
			int adviceSize = this.responseBodyAdvice.size();
			if (handlerSize == 0 && adviceSize == 0) {
				logger.debug("ControllerAdvice beans: none");
			}
			else {
				logger.debug("ControllerAdvice beans: " +
						handlerSize + " @ExceptionHandler, " + adviceSize + " ResponseBodyAdvice");
			}
		}
	}

	/**
	 * Return an unmodifiable Map with the {@link ControllerAdvice @ControllerAdvice}
	 * beans discovered in the ApplicationContext. The returned map will be empty if
	 * the method is invoked before the bean has been initialized via
	 * {@link #afterPropertiesSet()}.
	 */
	public Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> getExceptionHandlerAdviceCache() {
		return Collections.unmodifiableMap(this.exceptionHandlerAdviceCache);
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	protected List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		/* 1、注解参数解析器 */
		// Annotation-based argument resolution —— 基于注解的参数解析
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		/* 2、根据参数类型来进行解析的参数解析器 */
		// Type-based argument resolution —— 基于类型的参数解析
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());

		/* 自定义参数解析器 */
		// Custom arguments —— 自定义参数
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	protected List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

		/* 1、单一用途的返回值类型处理器 */
		// Single-purpose return value types —— 单一用途的返回值类型
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		handlers.add(new ModelMethodProcessor());
		handlers.add(new ViewMethodReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(
				getMessageConverters(), this.contentNegotiationManager, this.responseBodyAdvice));

		/* 2、注解的返回值类型处理器 */
		// Annotation-based return value types —— 基于注解的返回值类型
		handlers.add(new ModelAttributeMethodProcessor(false));
		handlers.add(new RequestResponseBodyMethodProcessor(
				getMessageConverters(), this.contentNegotiationManager, this.responseBodyAdvice));

		/* 3、多用途返回值类型处理器 */
		// Multi-purpose return value types —— 多用途返回值类型
		handlers.add(new ViewNameMethodReturnValueHandler());
		handlers.add(new MapMethodProcessor());

		/* 4、自定义返回值类型处理器 */
		// Custom return value types —— 自定义返回值类型
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		/* 5、处理所有类型返回值的处理器 */
		// Catch-all —— 包罗万象
		handlers.add(new ModelAttributeMethodProcessor(true));

		return handlers;
	}


	/**
	 * Find an {@code @ExceptionHandler} method and invoke it to handle the raised exception.
	 *
	 * 找到一个{@code @ExceptionHandler}方法并调用它来处理引发的异常。
	 */
	@Override
	@Nullable
	protected ModelAndView doResolveHandlerMethodException(HttpServletRequest request,
			HttpServletResponse response, @Nullable HandlerMethod handlerMethod, Exception exception) {

		// 启示：这些步骤和我们在处理正常请求时是差不多的

		/*

		1、先从当前Controller中获取能够处理当前异常的@ExceptionHandler修饰的方法；
		如果获取不到，再从全局Controller中能够处理当前异常的@ExceptionHandler修饰的方法；
		只要获取到了处理异常的方法，就用其创建一个ServletInvocableHandlerMethod，并往里面设置参数解析器和返回值处理器；如果没有获取到，就返回null

		题外：ServletInvocableHandlerMethod用于实际执行方法

		 */
		// ⚠️获得异常对应的ServletInvocableHandlerMethod对象，里面的handlerMethod是处理异常的方法
		ServletInvocableHandlerMethod exceptionHandlerMethod = getExceptionHandlerMethod(handlerMethod, exception);
		if (exceptionHandlerMethod == null) {
			return null;
		}
		// 参数解析器
		if (this.argumentResolvers != null) {
			exceptionHandlerMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		// 设置返回值处理器
		if (this.returnValueHandlers != null) {
			exceptionHandlerMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
		}

		/* 2、用request和response创建一个ServletWebRequest对象 */
		ServletWebRequest webRequest = new ServletWebRequest(request, response);

		/* 3、创建空壳的ModelAndViewContainer对象 */
		ModelAndViewContainer mavContainer = new ModelAndViewContainer();

		/* 4、调用异常处理方法 */
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Using @ExceptionHandler " + exceptionHandlerMethod);
			}
			// 执行处理该异常的方法 ServletInvocableHandlerMethod 的调用，主要是对参数和返回值及进行处理，通过ModelAndViewContainer作为中间变量
			// 将一些视图名、参数放到ModelAndViewContainer中

			// 异常原因
			Throwable cause = exception.getCause();
			if (cause != null) {
				// Expose cause as provided argument as well —— 也将原因作为提供的论据公开
				exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception, cause, handlerMethod);
			}
			else {
				// Otherwise, just the given exception as-is —— 否则，只是给定的异常
				// ⚠️
				exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception, handlerMethod);
			}
		}
		catch (Throwable invocationEx) {
			/* 5、如果在调用异常处理方法的时候，发生异常了，则直接返回null */

			// Any other than the original exception (or its cause) is unintended here,
			// probably an accident (e.g. failed assertion or the like).
			// 发生异常，则直接返回
			if (invocationEx != exception && invocationEx != exception.getCause() && logger.isWarnEnabled()) {
				logger.warn("Failure in @ExceptionHandler " + exceptionHandlerMethod, invocationEx);
			}
			// Continue with default processing of the original exception...

			/**
			 * ⚠️如果在执行异常方法的时候，里面报错了，会被try..catch...捕捉到，然后返回null值，
			 * 这样最终当前异常解析器返回的就是null值，然后会走下一个异常解析器进行处理！
			 */
			return null;
		}

		/*

		6、如果在调用异常处理方法的时候，请求已经处理完毕了，则返回空的ModelAndView对象，代表后续不用进行视图渲染，并且不会走下一个异常解析器进行解析了

		题外：这点很重要，只要当前处理器返回了错误视图对象，那么就不会走下一个异常解析器进行解析了

		*/
		if (mavContainer.isRequestHandled()) {

			return new ModelAndView();
		}
		/* 7、如果在调用异常处理方法的时候，请求未处理完毕，则用ModelAndViewContainer创建出一个ModelAndView进行返回 */
		else {
			// 获取model
			ModelMap model = mavContainer.getModel();
			// 获取http状态
			HttpStatus status = mavContainer.getStatus();
			// 用ModelAndViewContainer创建出一个ModelAndView，并设置相关属性
			ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, status);
			mav.setViewName(mavContainer.getViewName());
			if (!mavContainer.isViewReference()) {
				mav.setView((View) mavContainer.getView());
			}
			if (model instanceof RedirectAttributes) {
				Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
			// 返回ModelAndView
			return mav;
		}
	}

	/**
	 * Find an {@code @ExceptionHandler} method for the given exception. The default
	 * implementation searches methods in the class hierarchy of the controller first
	 * and if not found, it continues searching for additional {@code @ExceptionHandler}
	 * methods assuming some {@linkplain ControllerAdvice @ControllerAdvice}
	 * Spring-managed beans were detected.
	 * @param handlerMethod the method where the exception was raised (may be {@code null})
	 * @param exception the raised exception
	 * @return a method to handle the exception, or {@code null} if none
	 */
	@Nullable
	protected ServletInvocableHandlerMethod getExceptionHandlerMethod(
			@Nullable HandlerMethod handlerMethod, Exception exception) {

		// controller类型
		Class<?> handlerType = null;

		/*

		1、首先，如果handlerMethod不为空，则先识别当前controller中所有被@ExceptionHandler修饰的方法，然后从中选出可以处理当前异常的方法，
		然后用这个方法，创建一个ServletInvocableHandlerMethod进行返回

		*/
		// 首先，如果handlerMethod非空，则先从当前Controller中获取能够处理当前异常的@ExceptionHandler修饰的方法
		if (handlerMethod != null) {
			// Local exception handler methods on the controller class itself.
			// To be invoked through the proxy, even in case of an interface-based proxy.
			// 上面的翻译：控制器类本身的本地异常处理程序方法。通过代理调用，即使是基于接口的代理。

			// 获取handlerMethod所在的Controller类型
			handlerType = handlerMethod.getBeanType();

			/* 1.1、先从当前controller异常处理缓存中，获取当前controller的"异常处理方法解析器" */
			// 从缓存中获取Controller对应的"异常处理方法解析器(ExceptionHandlerMethodResolver)"
			ExceptionHandlerMethodResolver/* 异常处理方法解析器 */ resolver = this.exceptionHandlerCache.get(handlerType);

			/*

			1.2、如果缓存中不存在，则创建当前controller的"异常处理方法解析器"，然后放入"当前controller异常处理缓存"中。
			在创建当前controller的"异常处理方法解析器"时，️里面查找了前类中@ExceptionHandler修饰的方法，并注册了"异常类型"和"异常处理方法"的映射关系

			*/
			if (resolver == null) {
				// 如果缓存中不存在，就构建一个Controller的"异常处理方法解析器(ExceptionHandlerMethodResolver)"
				// ⚠️里面查找了前类中@ExceptionHandler修饰的方法，并注册了"异常类型"和"处理方法"的映射关系
				resolver = new ExceptionHandlerMethodResolver/* 异常处理方法解析器 */(handlerType);
				this.exceptionHandlerCache.put(handlerType, resolver);
			}

			/*

			1.3、用当前controller的"异常处理方法解析器"，去获取能够处理当前异常类型的方法

			 */
			// ⚠️从当前Controller中，获取能够处理当前异常的方法
			Method method = resolver.resolveMethod(exception);

			/*

			1.4、如果从当前Controller中，可以获取到异常对应的处理方法，则创建ServletInvocableHandlerMethod对象进行返回
			题外：ServletInvocableHandlerMethod里面包装了异常处理方法和当前Controller bean

			 */
			if (method != null) {
				return new ServletInvocableHandlerMethod(handlerMethod.getBean(), method);
			}

			// For advice applicability check below (involving base packages, assignable types
			// and annotation presence), use target class instead of interface-based proxy.
			// 上面的翻译：对于下面的建议适用性检查（涉及基本包、可分配类型和注解存在），使用目标类而不是基于接口的代理。

			// 获得 handlerType 的原始类。因为，此处有可能是代理对象
			if (Proxy.isProxyClass(handlerType)) {
				handlerType = AopUtils.getTargetClass(handlerMethod.getBean());
			}
		}

		/*

		2、如果无法从当前Controller中获取到处理当前异常的方法，则从@ControllerAdvice修饰的全局Controller中获取到能够处理当前异常的被@ExceptionHandler修饰的方法，
		然后用这个方法，创建一个ServletInvocableHandlerMethod进行返回

		 */

		// 如果无法从当前Controller中获取到处理当前异常的方法，则遍历所有的ControllerAdviceBean，获取能够支持处理当前异常的被@ExceptionHandler修饰的方法
		for (Map.Entry<ControllerAdviceBean, ExceptionHandlerMethodResolver> entry : this.exceptionHandlerAdviceCache.entrySet()) {
			// 单个@Controller bean
			ControllerAdviceBean advice = entry.getKey();

			// 判断当前的@ControllerAdvice bean是否可以作用于当前handler
			if (advice.isApplicableToBeanType(handlerType)) {
				/* 如果当前@ControllerAdvice bean支持处理当前的handler */

				// 获取全局controller的"异常处理方法解析器"
				ExceptionHandlerMethodResolver resolver = entry.getValue();
				// 用全局controller的"异常处理方法解析器"，去获取能够处理当前异常类型的方法
				Method method = resolver.resolveMethod(exception);
				if (method != null) {
					// 如果从全局Controller中，可以获取到异常对应的处理方法，则创建ServletInvocableHandlerMethod对象进行返回
					// 题外：ServletInvocableHandlerMethod里面包装了异常处理方法和当前Controller bean
					return new ServletInvocableHandlerMethod(advice.resolveBean(), method);
				}
			}
		}

		/* 3、如果当前controller中和全局controller中，都没有处理当前异常的@ExceptionHandler修饰的方法，则返回null */

		return null;
	}

}
