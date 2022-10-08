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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.PathMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.cors.*;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AbstractHandlerMapping是HandlerMapping接口的抽象实现，所有的handlerMapping都要继承此抽象类
 * abstractHandlerMapping采用模板模式设计了HandlerMapping实现子类的整体结构，子类只需要通过模板方法提供一些初始值或者业务逻辑即可
 *
 * handlerMapping是根据request找到Handler和interceptors,获取Handler的过程通过模板方法getHandlerInternal交给了子类，
 * abstractHandlerMapping保存了所用配置的interceptor，在获取到handler之后，根据从request中提取的lookupPath将相应的interceptors装配进去
 * 当然子类也可以通过getHandlerInternal方法设置自己的interceptor。
 *
 * Abstract base class for {@link org.springframework.web.servlet.HandlerMapping}
 * implementations. Supports ordering, a default handler, handler interceptors,
 * including handler interceptors mapped by path patterns.
 *
 * <p>Note: This base class does <i>not</i> support exposure of the
 * {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}. Support for this attribute
 * is up to concrete subclasses, typically based on request URL mappings.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 07.04.2003
 * @see #getHandlerInternal
 * @see #setDefaultHandler
 * @see #setAlwaysUseFullPath
 * @see #setUrlDecode
 * @see org.springframework.util.AntPathMatcher
 * @see #setInterceptors
 * @see org.springframework.web.servlet.HandlerInterceptor
 */
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport
		implements HandlerMapping, Ordered, BeanNameAware {

	// 默认处理器，使用的对象类型是Object，子类实现的时候使用HandlerMethod,HandlerExecutionChain等
	@Nullable
	private Object defaultHandler;

	// url路径管理工具
	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	// 基于ant进行path匹配，解决/user/{id}的场景
	private PathMatcher pathMatcher = new AntPathMatcher();

	/**
	 * 用于配置springmvc的拦截器，有两种设置方式，
	 * 1、注册handlerMapping时通过属性设置
	 * 2、通过子类的extendInterceptors钩子方法进行设置
	 *
	 * 此集合并不会直接使用，而是通过initInterceptors()方法按照类型分配到mappedInterceptors和adaptedInterceptors中进行使用
	 */
	// 拦截器集合（两种类型：HandlerInterceptor、WebRequestInterceptor）
	private final List<Object> interceptors = new ArrayList<>();

	// 初始化后的拦截器handlerInterceptor数组
	// 拦截器适配集合
	private final List<HandlerInterceptor> adaptedInterceptors = new ArrayList<>();

	// 跨域配置源
	@Nullable
	private CorsConfigurationSource corsConfigurationSource;

	private CorsProcessor corsProcessor = new DefaultCorsProcessor();

	// 表示优先级的变量，值越大，优先级越低
	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered

	// 当前bean的名称
	@Nullable
	private String beanName;


	/**
	 * Set the default handler for this handler mapping.
	 * This handler will be returned if no specific mapping was found.
	 * <p>Default is {@code null}, indicating no default handler.
	 */
	public void setDefaultHandler(@Nullable Object defaultHandler) {
		this.defaultHandler = defaultHandler;
	}

	/**
	 * Return the default handler for this handler mapping,
	 * or {@code null} if none.
	 */
	@Nullable
	public Object getDefaultHandler() {
		return this.defaultHandler;
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setAlwaysUseFullPath(boolean)
	 */
	public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
		this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setAlwaysUseFullPath(alwaysUseFullPath);
		}
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setUrlDecode(boolean)
	 */
	public void setUrlDecode(boolean urlDecode) {
		this.urlPathHelper.setUrlDecode(urlDecode);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlDecode(urlDecode);
		}
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setRemoveSemicolonContent(boolean)
	 */
	public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
		this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setRemoveSemicolonContent(removeSemicolonContent);
		}
	}

	/**
	 * Set the UrlPathHelper to use for resolution of lookup paths.
	 * <p>Use this to override the default UrlPathHelper with a custom subclass,
	 * or to share common UrlPathHelper settings across multiple HandlerMappings
	 * and MethodNameResolvers.
	 */
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
		this.urlPathHelper = urlPathHelper;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlPathHelper(urlPathHelper);
		}
	}

	/**
	 * Return the UrlPathHelper implementation to use for resolution of lookup paths.
	 */
	public UrlPathHelper getUrlPathHelper() {
		return this.urlPathHelper;
	}

	/**
	 * Set the PathMatcher implementation to use for matching URL paths
	 * against registered URL patterns. Default is AntPathMatcher.
	 * @see org.springframework.util.AntPathMatcher
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		Assert.notNull(pathMatcher, "PathMatcher must not be null");
		this.pathMatcher = pathMatcher;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setPathMatcher(pathMatcher);
		}
	}

	/**
	 * Return the PathMatcher implementation to use for matching URL paths
	 * against registered URL patterns.
	 */
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}

	/**
	 * Set the interceptors to apply for all handlers mapped by this handler mapping.
	 * <p>Supported interceptor types are HandlerInterceptor, WebRequestInterceptor, and MappedInterceptor.
	 * Mapped interceptors apply only to request URLs that match its path patterns.
	 * Mapped interceptor beans are also detected by type during initialization.
	 * @param interceptors array of handler interceptors
	 * @see #adaptInterceptor
	 * @see org.springframework.web.servlet.HandlerInterceptor
	 * @see org.springframework.web.context.request.WebRequestInterceptor
	 */
	public void setInterceptors(Object... interceptors) {
		this.interceptors.addAll(Arrays.asList(interceptors));
	}

	/**
	 * Set the "global" CORS configurations based on URL patterns. By default the first
	 * matching URL pattern is combined with the CORS configuration for the handler, if any.
	 * @since 4.2
	 * @see #setCorsConfigurationSource(CorsConfigurationSource)
	 */
	public void setCorsConfigurations(Map<String, CorsConfiguration> corsConfigurations) {
		Assert.notNull(corsConfigurations, "corsConfigurations must not be null");
		if (!corsConfigurations.isEmpty()) {
			UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
			source.setCorsConfigurations(corsConfigurations);
			source.setPathMatcher(this.pathMatcher);
			source.setUrlPathHelper(this.urlPathHelper);
			source.setLookupPathAttributeName(LOOKUP_PATH);
			this.corsConfigurationSource = source;
		}
		else {
			this.corsConfigurationSource = null;
		}
	}

	/**
	 * Set the "global" CORS configuration source. By default the first matching URL
	 * pattern is combined with the CORS configuration for the handler, if any.
	 * @since 5.1
	 * @see #setCorsConfigurations(Map)
	 */
	public void setCorsConfigurationSource(CorsConfigurationSource corsConfigurationSource) {
		Assert.notNull(corsConfigurationSource, "corsConfigurationSource must not be null");
		this.corsConfigurationSource = corsConfigurationSource;
	}

	/**
	 * Configure a custom {@link CorsProcessor} to use to apply the matched
	 * {@link CorsConfiguration} for a request.
	 * <p>By default {@link DefaultCorsProcessor} is used.
	 * @since 4.2
	 */
	public void setCorsProcessor(CorsProcessor corsProcessor) {
		Assert.notNull(corsProcessor, "CorsProcessor must not be null");
		this.corsProcessor = corsProcessor;
	}

	/**
	 * Return the configured {@link CorsProcessor}.
	 */
	public CorsProcessor getCorsProcessor() {
		return this.corsProcessor;
	}

	/**
	 * Specify the order value for this HandlerMapping bean.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	protected String formatMappingName() {
		return this.beanName != null ? "'" + this.beanName + "'" : "<unknown>";
	}


	/**
	 * Initializes the interceptors.
	 * @see #extendInterceptors(java.util.List)
	 * @see #initInterceptors()
	 */
	@Override
	protected void initApplicationContext() throws BeansException {
		/* 一、初始化拦截器 */

		/* 1、钩子方法，用于对拦截器集合的扩展 */
		// 空实现 —— 用于注册自定义的拦截器到interceptors中，目前暂无子类实现。
		extendInterceptors(this.interceptors);

		/*

		2、️从所有的容器中获取MappedInterceptor拦截器，添加到拦截器适配器集合中(adaptedInterceptors)

		题外：MappedInterceptor implements HandlerInterceptor

		*/
		detectMappedInterceptors(this.adaptedInterceptors);

		/*

		3、将拦截器集合中的拦截器适配为HandlerInterceptor（如果是则不用适配，如果不是则需要适配），
		然后放入拦截器适配集合中(adaptedInterceptors)

		*/
		// 将interceptors集合中的interceptor适配成HandlerInterceptor类型，然后再添加到adaptedInterceptors中
		initInterceptors();
	}

	/**
	 * 提供给子类扩展拦截器
	 *
	 * Extension hook that subclasses can override to register additional interceptors,
	 * given the configured interceptors (see {@link #setInterceptors}).
	 * <p>Will be invoked before {@link #initInterceptors()} adapts the specified
	 * interceptors into {@link HandlerInterceptor} instances.
	 * <p>The default implementation is empty.
	 * @param interceptors the configured interceptor List (never {@code null}), allowing
	 * to add further interceptors before as well as after the existing interceptors
	 */
	protected void extendInterceptors(List<Object> interceptors) {
	}

	/**
	 * 扫描应用下的mappeedInterceptor，并添加到mappedInterceptors
	 *
	 * Detect beans of type {@link MappedInterceptor} and add them to the list
	 * of mapped interceptors.
	 * <p>This is called in addition to any {@link MappedInterceptor}s that may
	 * have been provided via {@link #setInterceptors}, by default adding all
	 * beans of type {@link MappedInterceptor} from the current context and its
	 * ancestors. Subclasses can override and refine this policy.
	 * @param mappedInterceptors an empty list to add to
	 */
	protected void detectMappedInterceptors(List<HandlerInterceptor> mappedInterceptors) {
		/**
		 * 1、MappedInterceptor：会根据请求路径做匹配，是否进行拦截
		 *
		 * 题外：MappedInterceptor implements HandlerInterceptor
		 *
		 * 2、这些MappedInterceptor是哪里来的？
		 *
		 * 当我们在配置文件中配置"拦截器的配置"时，然后在启动spring mvc容器时，会解析配置文件标签，
		 * 在"拦截器标签解析器"当中{@link org.springframework.web.servlet.config.InterceptorsBeanDefinitionParser}会解析拦截器配置
		 * 会把拦截器配置中的拦截器，解析为MappedInterceptor bd，注册到BeanFactory；这样后续实例化的也就是MappedInterceptor bean。
		 *
		 * 以下拦截器配置中的拦截器都是解析为MappedInterceptor bd。<bean>标签只是作为MappedInterceptor构造器的参数。
		 * 	<mvc:interceptors>
		 * 		<!--	1、配置拦截器，拦截指定路径	-->
		 * 		<mvc:interceptor>
		 * 			<mvc:mapping path="/test01"/>
		 * 			<bean class="com.springstudymvc.msb.mvc_04.controller.interceptor.HandlerMappingInterceptor"/>
		 * 		</mvc:interceptor>
		 *
		 * 		<!--	2、配置拦截器，拦截所有路径	-->
		 * 		<bean class="com.springstudymvc.msb.other.interceptor.MyInterceptor"/>
		 * 	</mvc:interceptors>
		 *
		 */
		/* 1、从所有的容器中获取MappedInterceptor拦截器，添加到mappedInterceptors中 */
		mappedInterceptors.addAll(BeanFactoryUtils.beansOfTypeIncludingAncestors(
				obtainApplicationContext(), MappedInterceptor.class, true, false).values());
	}

	/**
	 * 初始化interceptors,并适配HandlerInterceptors和WebRequestInterceptor
	 *
	 * Initialize the specified interceptors adapting
	 * {@link WebRequestInterceptor}s to {@link HandlerInterceptor}.
	 * @see #setInterceptors
	 * @see #adaptInterceptor
	 */
	protected void initInterceptors() {
		/*

		1、遍历拦截器集合(interceptors)，将拦截器适配为HandlerInterceptor（如果是则不用适配，如果不是则需要适配），
		然后放入拦截器适配集合中(adaptedInterceptors)

		 */
		if (!this.interceptors.isEmpty()) {
			for (int i = 0; i < this.interceptors.size(); i++) {
				Object interceptor = this.interceptors.get(i);
				if (interceptor == null) {
					throw new IllegalArgumentException("Entry number " + i + " in interceptors array is null"/* 拦截器数组中的条目号“+ i +”为空 */);
				}
				/**
				 * 1、adaptInterceptor()：
				 * (1)如果是HandlerInterceptor类型，则转换为HandlerInterceptor类型进行返回
				 * (2)如果是WebRequestInterceptor类型，则转换为WebRequestInterceptor类型，
				 * 然后创建一个WebRequestHandlerInterceptorAdapter对象进行返回，里面包装WebRequestInterceptor
				 * 题外：WebRequestHandlerInterceptorAdapter间接实现HandlerInterceptor
				 * (3)既不是HandlerInterceptor，也不是WebRequestInterceptor，就报错
				 */
				// 将interceptor适配成HandlerInterceptor类型，然后再添加到adaptedInterceptors中
				// 注意：HandlerInterceptor无需进行路径匹配，直接拦截全部
				this.adaptedInterceptors.add(adaptInterceptor/* ⚠️适配Interceptor */(interceptor));
			}
		}
	}

	/**
	 * 适配为HandlerInterceptor
	 *
	 * Adapt the given interceptor object to {@link HandlerInterceptor}.
	 * <p>By default, the supported interceptor types are
	 * {@link HandlerInterceptor} and {@link WebRequestInterceptor}. Each given
	 * {@link WebRequestInterceptor} is wrapped with
	 * {@link WebRequestHandlerInterceptorAdapter}.
	 * @param interceptor the interceptor
	 * @return the interceptor downcast or adapted to HandlerInterceptor
	 * @see org.springframework.web.servlet.HandlerInterceptor
	 * @see org.springframework.web.context.request.WebRequestInterceptor
	 * @see WebRequestHandlerInterceptorAdapter
	 */
	protected HandlerInterceptor adaptInterceptor(Object interceptor) {
		/* 1、如果是HandlerInterceptor类型，则转换为HandlerInterceptor类型进行返回 */
		if (interceptor instanceof HandlerInterceptor) {
			return (HandlerInterceptor) interceptor;
		}
		/*

		2、如果是WebRequestInterceptor类型，则转换为WebRequestInterceptor类型，
		然后创建一个WebRequestHandlerInterceptorAdapter对象进行返回，里面包装WebRequestInterceptor

		题外：WebRequestHandlerInterceptorAdapter间接实现HandlerInterceptor

		 */
		else if (interceptor instanceof WebRequestInterceptor) {
			return new WebRequestHandlerInterceptorAdapter((WebRequestInterceptor) interceptor);
		}
		/* 3、既不是HandlerInterceptor，也不是WebRequestInterceptor，就报错 */
		else {
			throw new IllegalArgumentException("Interceptor type not supported: "/* 不支持拦截器类型： */+ interceptor.getClass().getName());
		}
	}

	/**
	 * Return the adapted interceptors as {@link HandlerInterceptor} array.
	 * @return the array of {@link HandlerInterceptor HandlerInterceptor}s,
	 * or {@code null} if none
	 */
	@Nullable
	protected final HandlerInterceptor[] getAdaptedInterceptors() {
		return (!this.adaptedInterceptors.isEmpty() ?
				this.adaptedInterceptors.toArray(new HandlerInterceptor[0]) : null);
	}

	/**
	 * Return all configured {@link MappedInterceptor}s as an array.
	 * @return the array of {@link MappedInterceptor}s, or {@code null} if none
	 */
	@Nullable
	protected final MappedInterceptor[] getMappedInterceptors() {
		List<MappedInterceptor> mappedInterceptors = new ArrayList<>(this.adaptedInterceptors.size());
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			if (interceptor instanceof MappedInterceptor) {
				mappedInterceptors.add((MappedInterceptor) interceptor);
			}
		}
		return (!mappedInterceptors.isEmpty() ? mappedInterceptors.toArray(new MappedInterceptor[0]) : null);
	}


	/**
	 * Look up a handler for the given request, falling back to the default
	 * handler if no specific one is found.
	 * @param request current HTTP request
	 * @return the corresponding handler instance, or the default handler
	 * @see #getHandlerInternal
	 */
	@Override
	@Nullable
	public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {

		/* 1、根据请求路径，获取到处理请求的Handler。如果没有就获取默认的，如果没有默认的handler，就返回null */
		/**
		 * ⚠️1、BeanNameUrlHandlerMapping走的是AbstractUrlHandlerMapping（处理的是实现了Controller接口，或者HttpRequest接口的bean）
		 *
		 * 内部获取Handler的逻辑：
		 * >>> 获取请求路径，然后根据请求路径去handlerMap中查找对应的Handler，
		 * >>> 如果没找到，就去看下当前路径是不是根路径（"/"），如果是的，就获取一个RootDefault(根处理器)；如果不是，就获取一个默认的处理器；
		 * >>> 无论如何，只要获取到了Handler，都会用这个Handler去创建一个HandlerExecutionChain进行返回，里面包含了handler、和PathExposingHandlerInterceptor
		 *
		 * >>> 简略总结：通过请求路径获取Handler
		 *
		 * 注意：BeanNameUrlHandlerMapping返回的是HandlerExecutionChain，里面包含了handler、和PathExposingHandlerInterceptor
		 * 题外：handlerMap是AbstractUrlHandlerMapping中的变量
		 *
		 * ⚠️2、RequestMappingHandlerMapping走的是RequestMappingInfoHandlerMapping（处理的是@Controller中的方法）
		 * 内部获取Handler的逻辑：
		 * >>> 先根据请求路径，从"请求地址映射缓存urlLookup"中，获取到mapping；
		 * >>> 然后根据mapping，去"请求方法映射缓存mappingLockup"中，获取到对应的HandlerMethod，作为handler对象进行返回
		 *
		 * >>> 简略总结：通过请求路径获取HandlerMethod，作为handler对象
		 *
		 * 注意：RequestMappingHandlerMapping返回的是Handler
		 * 题外：urlLookup和mappingLockup都是MappingRegistry类中的map集合
		 */
		/**
		 * 1、BeanNameUrlHandlerMapping和RequestMappingHandlerMapping获取的Handler的区别
		 *
		 * BeanNameUrlHandlerMapping获取到的Handler是Handler，也就是是Controller，
		 * 而RequestMappingHandlerMapping获取到的是HandlerMethod作为Handler，也就是Controller中的方法
		 *
		 * 2、为什么BeanNameUrlHandlerMapping获取的直接是一个Controller，难道不需要知道具体的调用方法吗？
		 *
		 * 不需要，因为BeanNameUrlHandlerMapping处理的是实现了Controller接口，或者HttpRequest接口的bean！
		 * 它是固定的方法处理请求，所以只需要获取Handler即可。这也解释了为什么，在BeanNameUrlHandlerMapping中注册Handler的时候，
		 * 是以【有"/"为开头的beanName或者别名】作为url，然后url为key，value直接为handler bean的原因。
		 */
		// 获得Handler
		// ⚠️注意：返回的handler可能是HandlerMethod或者HandlerExecutionChain
		Object handler = getHandlerInternal(request);

		// 获得不到，则使用默认处理器
		if (handler == null) {
			handler = getDefaultHandler();
		}
		// 如果默认处理器也获取不到，则返回null
		if (handler == null) {
			return null;
		}

		// Bean name or resolved handler? —— beanName或已解析的Handler？

		// 如果找到的处理器是String类型，则代表是一个beanName，于是从Spring容器中找到对应的Bean作为处理器
		if (handler instanceof String) {
			String handlerName = (String) handler;
			handler = obtainApplicationContext().getBean(handlerName);
		}

		/*

		2、如果获取到了handler，就通过handler获取一个Handler执行链对象（HandlerExecutionChain）；然后往Handler执行链里面添加拦截器。

		（1）handler如果是一个Handler执行链类型， 就直接转换为Handler执行链；否则通过Handler创建一个Handler执行链对象（HandlerExecutionChain）
		（2）然后往Handler执行链添加拦截器。拦截器分为需要匹配路径的拦截器，和不需要匹配路径的拦截器；如果是需要匹配路径的拦截器，就匹配路径，
		如果路径匹配，才会添加到Handler执行链中；如果是不需要匹配路径的拦截器，则直接添加到Handler执行链中

		*/

		// 创建HandlerExecutionChain对象（包含处理器和拦截器）
		HandlerExecutionChain executionChain = getHandlerExecutionChain/* 获取handler执行链 */(handler, request);

		if (logger.isTraceEnabled()) {
			logger.trace("Mapped to " + handler);
		}
		else if (logger.isDebugEnabled() && !request.getDispatcherType().equals(DispatcherType.ASYNC)) {
			logger.debug("Mapped to " + executionChain.getHandler());
		}

		/* 3、针对跨域请求的处理 */

		// 判断是否有跨域的配置：1、如果当前Handler实现了跨域配置接口接口(CorsConfigurationSource)，或者存在全局的跨域配置源，就代表有跨域的配置；2、否则没有
		// 判断是不是预检请求
		if (hasCorsConfigurationSource(handler)/* 有跨域配置 */ || CorsUtils.isPreFlightRequest(request)) {
			/* 存在跨域配置 || 预检请求 */
			// 获取全局的跨域配置
			CorsConfiguration config = (this.corsConfigurationSource != null ? this.corsConfigurationSource.getCorsConfiguration(request) : null);
			// 获取当前Handler的跨域配置
			CorsConfiguration handlerConfig = getCorsConfiguration(handler, request);
			// 将"全局的跨域配置"和"当前Handler的跨域配置"进行合并
			config = (config != null ? config.combine(handlerConfig) : handlerConfig);
			// 里面的逻辑：判断是不是一个预检请求
			// 1、如果是一个预检请求，则替换原先的Handler为PreFlightHandler
			// 2、如果不是预检请求，则往Handler执行链中添加一个跨域拦截器（CorsInterceptor）
			executionChain = getCorsHandlerExecutionChain(request, executionChain, config);
		}

		return executionChain;
	}

	/**
	 * Look up a handler for the given request, returning {@code null} if no
	 * specific one is found. This method is called by {@link #getHandler};
	 * a {@code null} return value will lead to the default handler, if one is set.
	 * <p>On CORS pre-flight requests this method should return a match not for
	 * the pre-flight request but for the expected actual request based on the URL
	 * path, the HTTP methods from the "Access-Control-Request-Method" header, and
	 * the headers from the "Access-Control-Request-Headers" header thus allowing
	 * the CORS configuration to be obtained via {@link #getCorsConfiguration(Object, HttpServletRequest)},
	 * <p>Note: This method may also return a pre-built {@link HandlerExecutionChain},
	 * combining a handler object with dynamically determined interceptors.
	 * Statically specified interceptors will get merged into such an existing chain.
	 * @param request current HTTP request
	 * @return the corresponding handler instance, or {@code null} if none found
	 * @throws Exception if there is an internal error
	 */
	@Nullable
	protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;

	/**
	 * Build a {@link HandlerExecutionChain} for the given handler, including
	 * applicable interceptors.
	 * <p>The default implementation builds a standard {@link HandlerExecutionChain}
	 * with the given handler, the common interceptors of the handler mapping, and any
	 * {@link MappedInterceptor MappedInterceptors} matching to the current request URL. Interceptors
	 * are added in the order they were registered. Subclasses may override this
	 * in order to extend/rearrange the list of interceptors.
	 * <p><b>NOTE:</b> The passed-in handler object may be a raw handler or a
	 * pre-built {@link HandlerExecutionChain}. This method should handle those
	 * two cases explicitly, either building a new {@link HandlerExecutionChain}
	 * or extending the existing chain.
	 * <p>For simply adding an interceptor in a custom subclass, consider calling
	 * {@code super.getHandlerExecutionChain(handler, request)} and invoking
	 * {@link HandlerExecutionChain#addInterceptor} on the returned chain object.
	 * @param handler the resolved handler instance (never {@code null})
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain (never {@code null})
	 * @see #getAdaptedInterceptors()
	 */
	protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
		/*

		1、通过handler获取HandlerExecutionChain对象。
		如果handler是HandlerExecutionChain类型，就直接转换为HandlerExecutionChain类型；
		如果不是，就通过handler创建一个HandlerExecutionChain类型

		*/
		HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
				(HandlerExecutionChain) handler : new HandlerExecutionChain(handler));

		/*

		2、往handler执行链里面添加拦截器。
		（1）遍历拦截器，判断是不是需要匹配路径的拦截器(MappedInterceptor)，如果是的话，就判断"拦截器支持的路径"与"请求路径"是否匹配，如果匹配，就添加到Handler执行链中
		（2）如果是不需要匹配路径的拦截器，就直接加入到Handler执行链中

		 */
		// 获得请求路径
		String lookupPath = this.urlPathHelper.getLookupPathForRequest(request, LOOKUP_PATH);

		// 遍历拦截器，获得请求匹配的拦截器
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {

			// 判断拦截器的类型是不是MappedInterceptor。
			// 如果是，就代表是需要匹配路径的拦截器，于是就判断"拦截器支持的路径"与"请求路径"是否匹配，如果匹配，就添加到Handler执行链中
			if (interceptor instanceof MappedInterceptor/* 映射拦截器 */) {
				/* 需要匹配路径的拦截器（先判断路径是够匹配，匹配才加入到Handler执行链中） */

				MappedInterceptor mappedInterceptor = (MappedInterceptor) interceptor;
				/**
				 * 1、题外：interceptor也可以配置匹配的path。例如：
				 *
				 * 	<mvc:interceptors>
				 * 		<mvc:interceptor>
				 * 			<mvc:mapping path="/test01"/>
				 * 			<bean class="com.springstudymvc.msb.mvc_04.controller.interceptor.HandlerMappingInterceptor"/>
				 * 		</mvc:interceptor>
				 * 	</mvc:interceptors>
				 *
				 * （1）如果interceptor配置了对应的拦截路径，那么该拦截器，只会拦截对应路径的请求；其它的请求都不拦截。
				 * （2）如果interceptor没有配置对应的拦截路径，那么就代表拦截所有请求
				 *
				 * 2、题外：MappedInterceptor bean的由来
				 *
				 * 配置文件中配置的拦截器，最终在容器启动，标签解析的时候，会把拦截器配置中的拦截器全部解析为MappedInterceptor bd。
				 * <bean>是作为一个MappedInterceptor构造器的参数
				 *
				 * 3、题外：如下配置文件中的拦截器配置，在容器启动，进行标签解析的时候，是注册MappedInterceptor bd到BeanFactory，
				 * 也就是说，如下配置文件中的拦截器配置，是一个MappedInterceptor，和上面【1、题外】配置了拦截器路径的拦截器配置，都是同一种类型，也就是MappedInterceptor。
				 * 虽然如下配置文件中的拦截器配置，但是代表的却是拦截所有的请求路径。所以下面条件判断会成立！
				 *
				 * <mvc:interceptors>
				 * 		<bean class="com.springstudymvc.msb.mvc_04.controller.interceptor.HandlerMappingInterceptor"/>
				 * </mvc:interceptors>
				 *
				 */
				// 根据请求路径匹配拦截器
				if (mappedInterceptor.matches(lookupPath, this.pathMatcher)) {
					chain.addInterceptor(mappedInterceptor.getInterceptor()/* 获取拦截器 */);
				}
			}
			// 如果不是MappedInterceptor类型的拦截器，就代表拦截所有路径，于是就直接添加到Handler执行链中
			else {
				/* 不需要匹配路径的拦截器（直接加入到Handler执行链中） */

				chain.addInterceptor(interceptor);
			}
		}
		return chain;
	}

	/**
	 * 判断是否有跨域的配置。
	 * 如果当前Handler实现了跨域配置接口接口(CorsConfigurationSource)，或者存在全局的跨域配置源，就代表有跨域的配置
	 *
	 * Return {@code true} if there is a {@link CorsConfigurationSource} for this handler.
	 *
	 * 如果此处理程序有 {@link CorsConfigurationSource}，则返回 {@code true}。
	 *
	 * @since 5.2
	 */
	protected boolean hasCorsConfigurationSource(Object handler) {
		// 1、获取具体的handler
		if (handler instanceof HandlerExecutionChain) {
			handler = ((HandlerExecutionChain) handler).getHandler();
		}
		// 2、判断是否有跨域配置
		return (handler instanceof CorsConfigurationSource || this.corsConfigurationSource != null);
	}

	/**
	 * Retrieve the CORS configuration for the given handler.
	 * @param handler the handler to check (never {@code null}).
	 * @param request the current request.
	 * @return the CORS configuration for the handler, or {@code null} if none
	 * @since 4.2
	 */
	@Nullable
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {

		/* 1、获取Handler */
		Object resolvedHandler = handler;
		if (handler instanceof HandlerExecutionChain) {
			resolvedHandler = ((HandlerExecutionChain) handler).getHandler();
		}

		/*  2、获取当前handler的跨域配置 */
		if (resolvedHandler instanceof CorsConfigurationSource) {
			return ((CorsConfigurationSource) resolvedHandler).getCorsConfiguration(request);
		}

		return null;
	}

	/**
	 * Update the HandlerExecutionChain for CORS-related handling.
	 * <p>For pre-flight requests, the default implementation replaces the selected
	 * handler with a simple HttpRequestHandler that invokes the configured
	 * {@link #setCorsProcessor}.
	 * <p>For actual requests, the default implementation inserts a
	 * HandlerInterceptor that makes CORS-related checks and adds CORS headers.
	 * @param request the current request
	 * @param chain the handler chain
	 * @param config the applicable CORS configuration (possibly {@code null})
	 * @since 4.2
	 */
	protected HandlerExecutionChain getCorsHandlerExecutionChain(HttpServletRequest request,
			HandlerExecutionChain chain, @Nullable CorsConfiguration config) {
		/*

		1、判断是不是一个预检请求
		如果是一个预检请求，则替换原先的Handler为PreFlightHandler

		 */
		if (CorsUtils.isPreFlightRequest(request)) {
			// 获取原先的拦截器
			HandlerInterceptor[] interceptors = chain.getInterceptors();
			// 创建一个新的Handler执行链，目的是为了替换原先的Handler为PreFlightHandler
			return new HandlerExecutionChain(new PreFlightHandler(config), interceptors);
		}
		/* 2、如果不是预检请求，则往Handler执行链中添加一个跨域拦截器（CorsInterceptor） */
		else {
			chain.addInterceptor(0, new CorsInterceptor(config));
			return chain;
		}
	}


	private class PreFlightHandler implements HttpRequestHandler, CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public PreFlightHandler(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
			corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}


	private class CorsInterceptor extends HandlerInterceptorAdapter implements CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public CorsInterceptor(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
				throws Exception {

			// Consistent with CorsFilter, ignore ASYNC dispatches
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			if (asyncManager.hasConcurrentResult()) {
				return true;
			}

			return corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}

}
