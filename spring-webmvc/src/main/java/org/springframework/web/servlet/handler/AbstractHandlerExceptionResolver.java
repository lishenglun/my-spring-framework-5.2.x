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

package org.springframework.web.servlet.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

/**
 * Abstract base class for {@link HandlerExceptionResolver} implementations.
 *
 * <p>Supports mapped {@linkplain #setMappedHandlers handlers} and
 * {@linkplain #setMappedHandlerClasses handler classes} that the resolver
 * should be applied to and implements the {@link Ordered} interface.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.0
 */
public abstract class AbstractHandlerExceptionResolver implements HandlerExceptionResolver, Ordered {

	private static final String HEADER_CACHE_CONTROL = "Cache-Control";


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	// 优先级，默认最低
	private int order = Ordered.LOWEST_PRECEDENCE;

	// 当前异常解析器可以处理的handler集合
	// (1)默认为空，则代表当前异常解析器，可以处理所有handler
	// (2)如果有值，则代表当前异常解析器，只处理特定的handler，不在此集合内的handler不处理
	@Nullable
	private Set<?> mappedHandlers/* 映射处理程序 */;

	// 当前异常解析器可以处理的handler类
	@Nullable
	private Class<?>[] mappedHandlerClasses;

	@Nullable
	private Log warnLogger;

	// 阻止响应数据缓存（响应http头部，加入不缓存的头部）默认不阻止
	private boolean preventResponseCaching/* 防止响应缓存 */ = false;


	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Specify the set of handlers that this exception resolver should apply to. —— 指定此异常解析器应应用于的处理程序集。
	 * <p>The exception mappings and the default error view will only apply to the specified handlers.
	 * <p>If no handlers or handler classes are set, the exception mappings and the default error
	 * view will apply to all handlers. This means that a specified default error view will be used
	 * as a fallback for all exceptions; any further HandlerExceptionResolvers in the chain will be
	 * ignored in this case.
	 */
	public void setMappedHandlers(Set<?> mappedHandlers) {
		this.mappedHandlers = mappedHandlers;
	}

	/**
	 * Specify the set of classes that this exception resolver should apply to. —— 指定此异常解析器应应用于的类集。
	 * <p>The exception mappings and the default error view will only apply to handlers of the
	 * specified types; the specified types may be interfaces or superclasses of handlers as well.
	 * <p>If no handlers or handler classes are set, the exception mappings and the default error
	 * view will apply to all handlers. This means that a specified default error view will be used
	 * as a fallback for all exceptions; any further HandlerExceptionResolvers in the chain will be
	 * ignored in this case.
	 */
	public void setMappedHandlerClasses(Class<?>... mappedHandlerClasses) {
		this.mappedHandlerClasses = mappedHandlerClasses;
	}

	/**
	 * Set the log category for warn logging. The name will be passed to the underlying logger
	 * implementation through Commons Logging, getting interpreted as a log category according
	 * to the logger's configuration. If {@code null} or empty String is passed, warn logging
	 * is turned off.
	 * <p>By default there is no warn logging although subclasses like
	 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}
	 * can change that default. Specify this setting to activate warn logging into a specific
	 * category. Alternatively, override the {@link #logException} method for custom logging.
	 * @see org.apache.commons.logging.LogFactory#getLog(String)
	 * @see java.util.logging.Logger#getLogger(String)
	 */
	public void setWarnLogCategory(String loggerName) {
		this.warnLogger = (StringUtils.hasLength(loggerName) ? LogFactory.getLog(loggerName) : null);
	}

	/**
	 * Specify whether to prevent HTTP response caching for any view resolved
	 * by this exception resolver.
	 * <p>Default is {@code false}. Switch this to {@code true} in order to
	 * automatically generate HTTP response headers that suppress response caching.
	 */
	public void setPreventResponseCaching(boolean preventResponseCaching) {
		this.preventResponseCaching = preventResponseCaching;
	}


	/**
	 * Check whether this resolver is supposed to apply (i.e. if the supplied handler
	 * matches any of the configured {@linkplain #setMappedHandlers handlers} or
	 * {@linkplain #setMappedHandlerClasses handler classes}), and then delegate
	 * to the {@link #doResolveException} template method.
	 */
	@Override
	@Nullable
	public ModelAndView resolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex) {

		/**
		 * 1、ExceptionHandlerExceptionResolver走的是AbstractHandlerMethodExceptionResolver
		 */
		/* 1、判断当前异常解析器，是否支持处理当前handler */

		// 注意：其实这个判断无效，因为任何一个异常解析器都返回true
		if (shouldApplyTo(request, handler)) {
			// 阻止缓存
			prepareResponse(ex, response);

			/* 2、如果当前异常解析器支持解析当前异常，则正式解析异常，返回modelAndView */
			/**
			 * 1、ExceptionHandlerExceptionResolver走的是AbstractHandlerMethodExceptionResolver
			 * 内部大致逻辑：
			 * (1)先从当前Controller中获取能够处理当前异常的@ExceptionHandler修饰的方法；如果获取不到，再从全局Controller中能够处理当前异常的@ExceptionHandler修饰的方法；
			 * 如果没有获取到，就返回null；只要获取到了处理异常的方法，就用其创建一个ServletInvocableHandlerMethod，并往里面设置参数解析器和返回值处理器；
			 * (2)然后直接调用异常处理方法
			 * (3)如果在调用异常处理方法的时候，发生异常了，则直接返回null
			 * (4)如果在调用异常处理方法的时候，请求已经处理完毕了，则返回空的ModelAndView对象，代表后续不用再处理了
			 * (5)如果在调用异常处理方法的时候，请求未处理完毕，则用ModelAndViewContainer创建出一个ModelAndView进行返回，供后续的视图渲染处理！
			 *
			 * 2、DefaultHandlerExceptionResolver
			 * 内部大致逻辑：
			 * (1)处理给定的特定异常，如果可以处理就返回一个ModelAndView，不可以处理就返回null。常见的可处理异常是：NoHandlerFoundException
			 *
			 * 3、ResponseStatusExceptionResolver
			 * 内部大致逻辑：处理异常类实现了ResponseStatusException接口，或者异常类上有@ResponseStatus注解的情况。
			 * (1)如果异常类是实现了ResponseStatusException接口，则转换为ResponseStatusException类型，通过转换为ResponseStatusException类型，提取对应的信息，往response里面设置对应的状态码和原因，然后创建空的ModelAndView对象进行返回
			 * (2)如果是异常类上有@ResponseStatus注解的情况，则提取异常类上@ResponseStatus中的信息，往response里面设置对应的状态码和原因，然后创建空的ModelAndView对象进行返回
			 * (3)如果2种情况不符合，则直接返回null
			 */
			// ⚠️如果支持，则异常解析器正式解析异常，返回modelAndView
			ModelAndView result = doResolveException(request, response, handler, ex);

			// 如果ModelAndView对象非空，则进行返回
			if (result != null) {
				// Print debug message when warn logger is not enabled.
				if (logger.isDebugEnabled() && (this.warnLogger == null || !this.warnLogger.isWarnEnabled())) {
					logger.debug("Resolved [" + ex + "]" + (result.isEmpty() ? "" : " to " + result));
				}
				// Explicitly configured warn logger in logException method. —— 在logException()方法中显式配置警告记录器。
				logException/* 日志异常 */(ex, request);
			}
			return result;
		}
		/* 2、如果当前异常解析器，不支持解析当前异常，直接返回null */
		else {
			return null;
		}
	}

	/**
	 * Check whether this resolver is supposed to apply to the given handler.
	 * <p>The default implementation checks against the configured
	 * {@linkplain #setMappedHandlers handlers} and
	 * {@linkplain #setMappedHandlerClasses handler classes}, if any.
	 * @param request current HTTP request
	 * @param handler the executed handler, or {@code null} if none chosen
	 * at the time of the exception (for example, if multipart resolution failed)
	 * @return whether this resolved should proceed with resolving the exception
	 * for the given request and handler
	 * @see #setMappedHandlers
	 * @see #setMappedHandlerClasses
	 */
	protected boolean shouldApplyTo(HttpServletRequest request, @Nullable Object handler) {
		if (handler != null) {
			/* 1、先看，当前异常解析器可以处理的handler集合里面是否包含当前handler，如果包含，就代表当前异常解析器可以处理当前handler */
			if (this.mappedHandlers != null && this.mappedHandlers.contains(handler)) {
				return true;
			}

			/*

			2、如果"当前异常解析器可以处理的handler集合"为空，或者不当前handler，
			则从"当前异常解析器可以处理的handler类"中判断，是否包含当前handler类，如果包含，就代表当前异常解析器可以处理当前handler

			*/
			if (this.mappedHandlerClasses != null) {
				for (Class<?> handlerClass : this.mappedHandlerClasses) {
					if (handlerClass.isInstance(handler)) {
						return true;
					}
				}
			}
		}

		// Else only apply if there are no explicit handler mappings. —— 否则仅适用于没有显式处理程序映射的情况

		/* 3、"当前异常解析器可以处理的handler集合"以及"当前异常解析器可以处理的handler类"都为空，则代表当前异常解析器可以处理任意handler */
		// 题外：这个条件几乎每个异常解析器都成立，所以每个异常解析器都可以处理任意handler
		return (this.mappedHandlers == null && this.mappedHandlerClasses == null);
	}

	/**
	 * Log the given exception at warn level, provided that warn logging has been
	 * activated through the {@link #setWarnLogCategory "warnLogCategory"} property.
	 * <p>Calls {@link #buildLogMessage} in order to determine the concrete message to log.
	 * @param ex the exception that got thrown during handler execution
	 * @param request current HTTP request (useful for obtaining metadata)
	 * @see #setWarnLogCategory
	 * @see #buildLogMessage
	 * @see org.apache.commons.logging.Log#warn(Object, Throwable)
	 */
	protected void logException(Exception ex, HttpServletRequest request) {
		if (this.warnLogger != null && this.warnLogger.isWarnEnabled()) {
			this.warnLogger.warn(buildLogMessage(ex, request));
		}
	}

	/**
	 * Build a log message for the given exception, occurred during processing the given request.
	 * @param ex the exception that got thrown during handler execution
	 * @param request current HTTP request (useful for obtaining metadata)
	 * @return the log message to use
	 */
	protected String buildLogMessage(Exception ex, HttpServletRequest request) {
		return "Resolved [" + ex + "]";
	}

	/**
	 * Prepare the response for the exceptional case.
	 * <p>The default implementation prevents the response from being cached,
	 * if the {@link #setPreventResponseCaching "preventResponseCaching"} property
	 * has been set to "true".
	 * @param ex the exception that got thrown during handler execution
	 * @param response current HTTP response
	 * @see #preventCaching
	 */
	protected void prepareResponse(Exception ex, HttpServletResponse response) {
		if (this.preventResponseCaching/* 防止响应缓存 *//* 默认false */) {
			preventCaching(response);
		}
	}

	/**
	 * Prevents the response from being cached, through setting corresponding
	 * HTTP {@code Cache-Control: no-store} header.
	 * @param response current HTTP response
	 */
	protected void preventCaching(HttpServletResponse response) {
		response.addHeader(HEADER_CACHE_CONTROL, "no-store");
	}


	/**
	 * Actually resolve the given exception that got thrown during handler execution,
	 * returning a {@link ModelAndView} that represents a specific error page if appropriate.
	 * <p>May be overridden in subclasses, in order to apply specific exception checks.
	 * Note that this template method will be invoked <i>after</i> checking whether this
	 * resolved applies ("mappedHandlers" etc), so an implementation may simply proceed
	 * with its actual exception handling.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the time
	 * of the exception (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding {@code ModelAndView} to forward to,
	 * or {@code null} for default processing in the resolution chain
	 */
	@Nullable
	protected abstract ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex);

}
