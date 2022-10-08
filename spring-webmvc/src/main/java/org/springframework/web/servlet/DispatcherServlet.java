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

package org.springframework.web.servlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 在以前使用servlet的时候，是一个请求一个servlet，现在spring mvc里面，是所有的请求，都走一个servlet，就是spring mvc的DispatcherServlet，
 * 然后由DispatcherServlet根据请求路径，分发请求到不同的handler
 *
 *
 * Central dispatcher for HTTP request handlers/controllers, e.g. for web UI controllers
 * or HTTP-based remote service exporters. Dispatches to registered handlers for processing
 * a web request, providing convenient mapping and exception handling facilities.
 *
 * <p>This servlet is very flexible: It can be used with just about any workflow, with the
 * installation of the appropriate adapter classes. It offers the following functionality
 * that distinguishes it from other request-driven web MVC frameworks:
 *
 * <ul>
 * <li>It is based around a JavaBeans configuration mechanism.
 *
 * <li>It can use any {@link HandlerMapping} implementation - pre-built or provided as part
 * of an application - to control the routing of requests to handler objects. Default is
 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping} and
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * HandlerMapping objects can be defined as beans in the servlet's application context,
 * implementing the HandlerMapping interface, overriding the default HandlerMapping if
 * present. HandlerMappings can be given any bean name (they are tested by type).
 *
 * <li>It can use any {@link HandlerAdapter}; this allows for using any handler interface.
 * Default adapters are {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter},
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}, for Spring's
 * {@link org.springframework.web.HttpRequestHandler} and
 * {@link org.springframework.web.servlet.mvc.Controller} interfaces, respectively. A default
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * will be registered as well. HandlerAdapter objects can be added as beans in the
 * application context, overriding the default HandlerAdapters. Like HandlerMappings,
 * HandlerAdapters can be given any bean name (they are tested by type).
 *
 * <li>The dispatcher's exception resolution strategy can be specified via a
 * {@link HandlerExceptionResolver}, for example mapping certain exceptions to error pages.
 * Default are
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver},
 * {@link org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver}, and
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}.
 * These HandlerExceptionResolvers can be overridden through the application context.
 * HandlerExceptionResolver can be given any bean name (they are tested by type).
 *
 * <li>Its view resolution strategy can be specified via a {@link ViewResolver}
 * implementation, resolving symbolic view names into View objects. Default is
 * {@link org.springframework.web.servlet.view.InternalResourceViewResolver}.
 * ViewResolver objects can be added as beans in the application context, overriding the
 * default ViewResolver. ViewResolvers can be given any bean name (they are tested by type).
 *
 * <li>If a {@link View} or view name is not supplied by the user, then the configured
 * {@link RequestToViewNameTranslator} will translate the current request into a view name.
 * The corresponding bean name is "viewNameTranslator"; the default is
 * {@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}.
 *
 * <li>The dispatcher's strategy for resolving multipart requests is determined by a
 * {@link org.springframework.web.multipart.MultipartResolver} implementation.
 * Implementations for Apache Commons FileUpload and Servlet 3 are included; the typical
 * choice is {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}.
 * The MultipartResolver bean name is "multipartResolver"; default is none.
 *
 * <li>Its locale resolution strategy is determined by a {@link LocaleResolver}.
 * Out-of-the-box implementations work via HTTP accept header, cookie, or session.
 * The LocaleResolver bean name is "localeResolver"; default is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
 *
 * <li>Its theme resolution strategy is determined by a {@link ThemeResolver}.
 * Implementations for a fixed theme and for cookie and session storage are included.
 * The ThemeResolver bean name is "themeResolver"; default is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver}.
 * </ul>
 *
 * <p><b>NOTE: The {@code @RequestMapping} annotation will only be processed if a
 * corresponding {@code HandlerMapping} (for type-level annotations) and/or
 * {@code HandlerAdapter} (for method-level annotations) is present in the dispatcher.</b>
 * This is the case by default. However, if you are defining custom {@code HandlerMappings}
 * or {@code HandlerAdapters}, then you need to make sure that a corresponding custom
 * {@code RequestMappingHandlerMapping} and/or {@code RequestMappingHandlerAdapter}
 * is defined as well - provided that you intend to use {@code @RequestMapping}.
 *
 * <p><b>A web application can define any number of DispatcherServlets.</b>
 * Each servlet will operate in its own namespace, loading its own application context
 * with mappings, handlers, etc. Only the root application context as loaded by
 * {@link org.springframework.web.context.ContextLoaderListener}, if any, will be shared.
 *
 * <p>As of Spring 3.1, {@code DispatcherServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful in Servlet
 * 3.0+ environments, which support programmatic registration of servlet instances.
 * See the {@link #DispatcherServlet(WebApplicationContext)} javadoc for details.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @see org.springframework.web.HttpRequestHandler
 * @see org.springframework.web.servlet.mvc.Controller
 * @see org.springframework.web.context.ContextLoaderListener
 */
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {

	/** Well-known name for the MultipartResolver object in the bean factory for this namespace. */
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

	/** Well-known name for the LocaleResolver object in the bean factory for this namespace. */
	public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

	/** Well-known name for the ThemeResolver object in the bean factory for this namespace. */
	public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

	/**
	 * Well-known name for the HandlerMapping object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerMappings" is turned off.
	 * @see #setDetectAllHandlerMappings
	 */
	public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

	/**
	 * Well-known name for the HandlerAdapter object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerAdapters" is turned off.
	 * @see #setDetectAllHandlerAdapters
	 */
	public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

	/**
	 * Well-known name for the HandlerExceptionResolver object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerExceptionResolvers" is turned off.
	 * @see #setDetectAllHandlerExceptionResolvers
	 */
	public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

	/**
	 * Well-known name for the RequestToViewNameTranslator object in the bean factory for this namespace.
	 */
	public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

	/**
	 * Well-known name for the ViewResolver object in the bean factory for this namespace.
	 * Only used when "detectAllViewResolvers" is turned off.
	 * @see #setDetectAllViewResolvers
	 */
	public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

	/**
	 * Well-known name for the FlashMapManager object in the bean factory for this namespace.
	 */
	public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

	/**
	 * Request attribute to hold the current web application context.
	 * Otherwise only the global web app context is obtainable by tags etc.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#findWebApplicationContext
	 */
	public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

	/**
	 * Request attribute to hold the current LocaleResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocaleResolver
	 */
	public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeResolver
	 */
	public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeSource, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeSource
	 */
	public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

	/**
	 * Name of request attribute that holds a read-only {@code Map<String,?>}
	 * with "input" flash attributes saved by a previous request, if any.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getInputFlashMap(HttpServletRequest)
	 */
	public static final String INPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the "output" {@link FlashMap} with
	 * attributes to save for a subsequent request.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getOutputFlashMap(HttpServletRequest)
	 */
	public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the {@link FlashMapManager}.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getFlashMapManager(HttpServletRequest)
	 */
	public static final String FLASH_MAP_MANAGER_ATTRIBUTE = DispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

	/**
	 * Name of request attribute that exposes an Exception resolved with a
	 * {@link HandlerExceptionResolver} but where no view was rendered
	 * (e.g. setting the status code).
	 */
	// org.springframework.web.servlet.DispatcherServlet.EXCEPTION
	public static final String EXCEPTION_ATTRIBUTE = DispatcherServlet.class.getName() + ".EXCEPTION";

	/** Log category to use when no mapped handler is found for a request. */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Name of the class path resource (relative to the DispatcherServlet class)
	 * that defines DispatcherServlet's default strategy names.
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

	/**
	 * Common prefix that DispatcherServlet's default strategy attributes start with.
	 */
	private static final String DEFAULT_STRATEGIES_PREFIX = "org.springframework.web.servlet";

	/** Additional logger to use when no mapped handler is found for a request. */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	private static final Properties defaultStrategies;

	static {
		// Load default strategy implementations from properties file.
		// This is currently strictly internal and not meant to be customized
		// by application developers.
		try {
			/**
			 * 1、扩展：
			 *
			 * 如果自己有一个独特的处理器，正常情况下，我都是定义bean对象，把bean对象定义好之后，然后直接从容器获取该bean对象，
			 * 但是如果自己要重新打一个包呢？也就是说，有一天我觉得这个东西不好使，我对他进行扩展，自定义一个处理器，
			 * 但是需要每次都要把我们自定义的处理器写到配置文件中去，才会生效，这样很麻烦。我想把我自己定义好的处理器作为一个默认的处理器，怎么办？
			 * 可以在我们当前的应用程序里面，定义一个完全一模一样的路径包，和我们自己的DispatcherServlet.properties配置文件，然后它就会覆盖掉spring的DispatcherServlet.properties配置文件
			 *
			 * 简单概括：我们在当前源码的基础上做一个二次开发，开发自己的框架，框架内部可以写一个自己的DispatcherServlet.properties文件，
			 * 路径和它一样即可，就可以覆盖掉原先的DispatcherServlet.properties文件，就可以采用我们自己的策略类，作为默认的策略！
			 *
			 * 好处：采取这种文件覆盖的方式，就可以使得我们的策略类作为默认的策略类，并且可以使我们不用每次都在配置文件中配置！每次都需要配置的话是很麻烦的。这种方式很简化！
			 *
			 * 题外：如果是在自己项目中，在运行的时候，是以jar包的方式导入进来的话，它是不会覆盖的；但如果是编译源码环境的话（在源码之上进行二次开发一个框架），它会覆盖。
			 * 因为编译源码时，我们二次开发的框架属于当前项目，当前项目的优先级是最高的！—— 虽然先加的是spring里面的，但是加载完成之后，因为我们当前项目中存在同名文件，就把它给覆盖掉了！
			 *
			 * 题外：jar包是不覆盖的，但是编译源码会覆盖！
			 *
			 */
			ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH/* DispatcherServlet.properties */, DispatcherServlet.class);
			defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
		}
	}

	/**
	 *
	 * Detect all HandlerMappings or just expect "handlerMapping" bean?. —— 检测所有 HandlerMappings 还是只期待“handlerMapping”bean？
	 *
	 * */
	private boolean detectAllHandlerMappings/* 检测所有处理程序映射 */ = true;

	/** Detect all HandlerAdapters or just expect "handlerAdapter" bean?. */
	private boolean detectAllHandlerAdapters = true;

	/** Detect all HandlerExceptionResolvers or just expect "handlerExceptionResolver" bean?. */
	private boolean detectAllHandlerExceptionResolvers = true;

	/** Detect all ViewResolvers or just expect "viewResolver" bean?. */
	private boolean detectAllViewResolvers = true;

	/** Throw a NoHandlerFoundException if no Handler was found to process this request? *.*/
	private boolean throwExceptionIfNoHandlerFound = false;

	/** Perform cleanup of request attributes after include request?. */
	private boolean cleanupAfterInclude = true;

	// 以下是9大组件

	/**
	 * multipart数据文件处理器
	 *
	 * MultipartResolver used by this servlet.
	 * */
	// 文件解析器
	@Nullable
	private MultipartResolver multipartResolver/* 多部分解析器 */;

	/**
	 * 语言处理器，提供国际化的支持
	 *
	 * LocaleResolver used by this servlet. —— 此servlet使用的LocaleResolver。 */
	// 语言环境解析器
	@Nullable
	private LocaleResolver localeResolver;

	/**
	 * 主题处理器，设置需要应用的整体格式
	 *
	 * ThemeResolver used by this servlet. */
	// 主题解析器
	@Nullable
	private ThemeResolver themeResolver;

	/**
	 * 处理器匹配器，返回请求对应的处理器和拦截器
	 *
	 * List of HandlerMappings used by this servlet. */
	// 处理器的映射器
	@Nullable
	private List<HandlerMapping> handlerMappings;

	/**
	 * 处理器适配器，用于执行处理器
	 *
	 * List of HandlerAdapters used by this servlet. */
	// 处理器的适配器
	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	/**
	 * 异常处理器，用于解析处理器发生的异常
	 *
	 * List of HandlerExceptionResolvers used by this servlet. */
	// 处理器的异常解析器
	@Nullable
	private List<HandlerExceptionResolver> handlerExceptionResolvers;

	/**
	 * 视图名称转换器
	 *
	 * RequestToViewNameTranslator used by this servlet. */
	// 视图名称转换器
	@Nullable
	private RequestToViewNameTranslator viewNameTranslator;

	/**
	 * FlashMap管理器，负责重定向保存参数到临时存储（默认session）中
	 *
	 * FlashMapManager used by this servlet. */
	// 参数传递管理器
	@Nullable
	private FlashMapManager flashMapManager;

	/**
	 * 视图解析器。根据视图名称和语言，获取View视图
	 *
	 * 视图解析器，作用：根据controller方法返回的逻辑视图名称，解析为物理视图地址。
	 *
	 * 例如：
	 * （1）spring mvc配置文件中配置视图解析器：
	 *
	 *     <bean class="org.springframework.web.servlet.view.InternalResourceViewResolver">
	 *         <property name="suffix" value=".jsp"></property>
	 *         <property name="prefix" value="/WEB-INF/jsp/"></property>
	 *     </bean>
	 *
	 * （2）指定逻辑视图名为success，经过视图解析器，解析为jsp物理路径：/WEB-INF/pages/success.jsp。说白了，也就是拼接前缀和后缀！
	 *
	 * 		@RequestMapping(/hello)
	 * 		public String hello() {
	 * 		    return "success";
	 * 		}
	 *
	 * List of ViewResolvers used by this servlet. */
	// 视图解析器
	@Nullable
	private List<ViewResolver> viewResolvers;


	/**
	 * Create a new {@code DispatcherServlet} that will create its own internal web
	 * application context based on defaults and values provided through servlet
	 * init-params. Typically used in Servlet 2.5 or earlier environments, where the only
	 * option for servlet registration is through {@code web.xml} which requires the use
	 * of a no-arg constructor.
	 * <p>Calling {@link #setContextConfigLocation} (init-param 'contextConfigLocation')
	 * will dictate which XML files will be loaded by the
	 * {@linkplain #DEFAULT_CONTEXT_CLASS default XmlWebApplicationContext}
	 * <p>Calling {@link #setContextClass} (init-param 'contextClass') overrides the
	 * default {@code XmlWebApplicationContext} and allows for specifying an alternative class,
	 * such as {@code AnnotationConfigWebApplicationContext}.
	 * <p>Calling {@link #setContextInitializerClasses} (init-param 'contextInitializerClasses')
	 * indicates which {@code ApplicationContextInitializer} classes should be used to
	 * further configure the internal application context prior to refresh().
	 * @see #DispatcherServlet(WebApplicationContext)
	 */
	public DispatcherServlet() {
		super();
		setDispatchOptionsRequest(true);
	}

	/**
	 * Create a new {@code DispatcherServlet} with the given web application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based registration
	 * of servlets is possible through the {@link ServletContext#addServlet} API.
	 * <p>Using this constructor indicates that the following properties / init-params
	 * will be ignored:
	 * <ul>
	 * <li>{@link #setContextClass(Class)} / 'contextClass'</li>
	 * <li>{@link #setContextConfigLocation(String)} / 'contextConfigLocation'</li>
	 * <li>{@link #setContextAttribute(String)} / 'contextAttribute'</li>
	 * <li>{@link #setNamespace(String)} / 'namespace'</li>
	 * </ul>
	 * <p>The given web application context may or may not yet be {@linkplain
	 * ConfigurableApplicationContext#refresh() refreshed}. If it has <strong>not</strong>
	 * already been refreshed (the recommended approach), then the following will occur:
	 * <ul>
	 * <li>If the given context does not already have a {@linkplain
	 * ConfigurableApplicationContext#setParent parent}, the root application context
	 * will be set as the parent.</li>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #postProcessWebApplicationContext} will be called</li>
	 * <li>Any {@code ApplicationContextInitializer}s specified through the
	 * "contextInitializerClasses" init-param or through the {@link
	 * #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called if the
	 * context implements {@link ConfigurableApplicationContext}</li>
	 * </ul>
	 * If the context has already been refreshed, none of the above will occur, under the
	 * assumption that the user has performed these actions (or not) per their specific
	 * needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public DispatcherServlet(WebApplicationContext webApplicationContext) {
		super(webApplicationContext);
		setDispatchOptionsRequest(true);
	}


	/**
	 * Set whether to detect all HandlerMapping beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerMapping" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerMapping, despite multiple HandlerMapping beans being defined in the context.
	 */
	public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
		this.detectAllHandlerMappings = detectAllHandlerMappings;
	}

	/**
	 * Set whether to detect all HandlerAdapter beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerAdapter" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerAdapter, despite multiple HandlerAdapter beans being defined in the context.
	 */
	public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
		this.detectAllHandlerAdapters = detectAllHandlerAdapters;
	}

	/**
	 * Set whether to detect all HandlerExceptionResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerExceptionResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerExceptionResolver, despite multiple HandlerExceptionResolver beans being defined in the context.
	 */
	public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
		this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
	}

	/**
	 * Set whether to detect all ViewResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "viewResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * ViewResolver, despite multiple ViewResolver beans being defined in the context.
	 */
	public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
		this.detectAllViewResolvers = detectAllViewResolvers;
	}

	/**
	 * Set whether to throw a NoHandlerFoundException when no Handler was found for this request.
	 * This exception can then be caught with a HandlerExceptionResolver or an
	 * {@code @ExceptionHandler} controller method.
	 * <p>Note that if {@link org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler}
	 * is used, then requests will always be forwarded to the default servlet and a
	 * NoHandlerFoundException would never be thrown in that case.
	 * <p>Default is "false", meaning the DispatcherServlet sends a NOT_FOUND error through the
	 * Servlet response.
	 * @since 4.0
	 */
	public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
		this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
	}

	/**
	 * Set whether to perform cleanup of request attributes after an include request, that is,
	 * whether to reset the original state of all request attributes after the DispatcherServlet
	 * has processed within an include request. Otherwise, just the DispatcherServlet's own
	 * request attributes will be reset, but not model attributes for JSPs or special attributes
	 * set by views (for example, JSTL's).
	 * <p>Default is "true", which is strongly recommended. Views should not rely on request attributes
	 * having been set by (dynamic) includes. This allows JSP views rendered by an included controller
	 * to use any model attributes, even with the same names as in the main JSP, without causing side
	 * effects. Only turn this off for special needs, for example to deliberately allow main JSPs to
	 * access attributes from JSP views rendered by an included controller.
	 */
	public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
		this.cleanupAfterInclude = cleanupAfterInclude;
	}


	/**
	 * This implementation calls {@link #initStrategies}.
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		/**
		 * 1、为什么要在容器刷新完成后，才去初始化spring mvc内置的9大组件？—— 也就是为什么要放在finishRefresh()里面才开始初始化spring mvc内置的9大组件
		 *
		 * （1）因为在初始化spring mvc九大组件的时候，要先从容器中获取这9大组件对应的bean对象。
		 * 也只有在容器刷新完成后，这9大组件才可能实例化完成，并存在容器当中了，这样我才获取得到，所以，我要在容器刷新完成后，才去初始化spring mvc九大组件。
		 *
		 * 题外：finishRefresh()的上一个方法finishBeanFactoryInitialization()，是用来对单例bean进行实例化的；
		 * >>> 只有它执行完毕，才可能会实例化完毕spring mvc九大组件，放入容器中；
		 * >>> 所以我必须等待它执行完毕，等待它把所有的bean实例化完成了，后面我才能从容器中获取得到内置的组件对象。
		 *
		 * （2）某些组件在初始化的时候，需要扫描所有bean，初始化对应的信息。只有在容器刷新完成后，bean对象才创建好了。
		 * 例如：HandlerMapping，需要去扫描所有的bean，检测是不是一个controller，然后初始化controller信息。
		 *
		 * 注意：有可能容器中并没有spring mvc内置的组件，如果没有，那么就会去DispatcherServlet.properties文件中获取默认的组件！
		 */
		// ⚠️初始化spring mvc内置的9大组件
		// ⚠️题外：初始化完成"spring mvc内置的9大组件"之后，意味着spring mvc也准备好了，可以接收和处理请求了！
		initStrategies(context);
	}

	/**
	 * Initialize the strategy objects that this servlet uses.
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 */
	protected void initStrategies(ApplicationContext context) {
		/*

		1、初始化MultipartResolver：处理上传请求，将普通的request封装成MultipartHttpServletRequest

		题外：主要用来处理文件上传（专门进行文件上传的工作的，例如：如果表单里面包含了文件上传，必须要包含一个MultipartResolver属性）
		题外：我们可以定义MultipartResolver bean，然后放入容器中，这样获取到的就是我们自定义的MultipartResolver了

		内部逻辑：
		(1)先从容器中获取，️如果有的话，就拿过来用；
		(2)没有的话，就默认设置为null，代表不提供MultipartResolver功能

		 */
		initMultipartResolver/* 多部分解析器 */(context);

		/*

		2、初始化LocaleResolver:主要用来处理国际化配置,
		基于URL参数的配置(AcceptHeaderLocaleResolver)，基于session的配置(SessionLocaleResolver)，基于cookie的配置(CookieLocaleResolver)

		题外：在进行国际化处理的时候，需要注意一件事：想要适配国际化，需要有独特的视图处理器的，例如：ResourceBundleViewResolver

		内部逻辑：
		(1)先从容器中获取，有的话，就直接拿过来用；
		(2)没有的话，就从DispatcherServlet.properties文件中，获取配置的默认的LocaleResolver。
		>>> 默认的LocaleResolver = AcceptHeaderLocaleResolver。

		 */
		initLocaleResolver/* 语言环境解析器 */(context);

		/*

		3、初始化ThemeResolver:主要用来设置主题Theme

		内部逻辑：先从容器中获取，有的话，就直接拿过来用；没有的话，就从DispatcherServlet.properties文件中，获取配置的默认的ThemeResolver。
		默认的ThemeResolver = FixedThemeResolver

		 */
		initThemeResolver/* 主题解析器 */(context);

		/*

		4、初始化HandlerMapping:映射器，用来将对应的request跟controller进行对应

		内部逻辑：
		(1)先判断是否检测所有的HandlerMapping，如果检测所有的HandlerMapping，那么就从所有的容器中获取HandlerMapping（这种方式获取到的可能是多个）；
		(2)如果不检测所有的HandlerMapping，那么就从当前容器中获取HandlerMapping（这种方式获取到的只可能是一个）。
		(3)如果从容器中未能获取到HandlerMapping，那么就从DispatcherServlet.properties文件中获取配置的默认的HandlerMapping

		 */
		initHandlerMappings/* 处理程序的映射器 */(context);

		/*

		5、初始化HandlerAdapter:处理适配器，主要包含Http请求处理器适配器，简单控制器处理器适配器，注解方法处理器适配器

		内部逻辑：
		(1)先判断是否检测所有的HandlerAdapter，如果检测所有的HandlerAdapter，那么就从所有的容器中获取HandlerAdapter（这种方式获取到的可能是多个）；
		(2)如果不检测所有的HandlerAdapter，那么就从当前容器中获取HandlerAdapter（这种方式获取到的只可能是一个）。
		(3)如果从容器中未能获取到HandlerAdapter，那么就从DispatcherServlet.properties文件中获取配置的默认的HandlerAdapter

		 */
		initHandlerAdapters/* 处理程序的适配器 */(context);

		/*

		6、初始化HandlerExceptionResolver:基于HandlerExceptionResolver接口的异常处理

		内部逻辑：
		(1)先判断是否检测所有的HandlerExceptionResolver，如果检测所有的HandlerExceptionResolver，那么就从所有的容器中获取HandlerExceptionResolver（这种方式获取到的可能是多个）；
		(2)如果不检测所有的HandlerExceptionResolver，那么就从当前容器中获取HandlerExceptionResolver（这种方式获取到的只可能是一个）。
		(3)如果从容器中未能获取到HandlerExceptionResolver，那么就从DispatcherServlet.properties文件中获取配置的默认的HandlerAdapter

		 */
		initHandlerExceptionResolvers/* 处理程序异常的解析器 */(context);

		/*

		7、初始化RequestToViewNameTranslator:当controller处理器方法没有返回一个View对象或逻辑视图名称，并且在该方法中没有直接往response的输出流里面写数据的时候，
		spring将会采用约定好的方式提供一个逻辑视图名称

		内部逻辑：
		(1)先从容器中获取，有的话，就直接拿过来用；
		(2)没有的话，就从DispatcherServlet.properties文件中，获取配置的默认的RequestViewNameTranslator
		>>> 默认的RequestViewNameTranslator = DefaultRequestToViewNameTranslator

		 */
		initRequestToViewNameTranslator/* 请求查看姓名翻译 */(context);

		/*

		8、初始化ViewResolver: 将ModelAndView选择合适的视图，进行渲染的处理器

		内部逻辑：
		(1)先判断是否检测所有的ViewResolver，如果检测所有的ViewResolver，那么就从所有的容器中获取ViewResolver（这种方式获取到的可能是多个）；
		(2)如果不检测所有的ViewResolver，那么就从当前容器中获取ViewResolver（这种方式获取到的只可能是一个）。
		(3)如果从容器中未能获取到ViewResolver，那么就从DispatcherServlet.properties文件中获取配置的默认的ViewResolver
		>>> 默认的ViewResolver = InternalResourceViewResolver

		 */
		initViewResolvers/* 视图解析器 */(context);

		/*

		9、初始化FlashMapManager: 提供请求存储属性，可供其他请求使用

		内部逻辑：
		(1)先从容器中获取，有的话，就直接拿过来用；
		(2)没有的话，就从DispatcherServlet.properties文件中，获取配置的默认的FlashMapManager
		>>> 默认的FlashMapManager = SessionFlashMapManager

		 */
		initFlashMapManager/* Flash地图管理器 */(context);
	}

	/**
	 * 初始化DispatcherServlet使用的MultipartResolver
	 * >>> 先从容器中获取，️如果有的话，就拿过来用；没有的话，就默认为null，代表不提供MultipartResolver功能
	 *
	 * Initialize the MultipartResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * no multipart handling is provided.
	 *
	 * 初始化此类使用的 MultipartResolver。
	 * <p>如果在此命名空间的 BeanFactory 中没有使用给定名称定义 bean，则不提供多部分处理。
	 */
	private void initMultipartResolver(ApplicationContext context) {
		try {
			/* 1、先从容器中获取MultipartResolver，有的话就用容器中获取到的 */

			// 从容器中获取beanName为multipartResolver，类型为MultipartResolver的Bean
			// 题外：先从一级缓存获取，一级缓存没有就获取对应的bd进行创建
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME/* multipartResolver */ /* 多部分解析器 */,
					MultipartResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.multipartResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.multipartResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex/* 没有找到bd异常 */) {
			/* 2、如果容器中没有获取到MultipartResolver，则设置为null，代表不提供MultipartResolver功能  */

			// Default is no multipart resolver. —— ️默认是没有多部分解析器。

			// 容器中没有找到beanName为multipartResolver，类型为MultipartResolver的bd，则自动会报错：NoSuchBeanDefinitionException
			// 所以会进入到这里面
			// 如果容器中没有获取到MultipartResolver，则设置MultipartResolver=null，代表不提供该功能
			this.multipartResolver = null;
			if (logger.isTraceEnabled()) {
				logger.trace("No MultipartResolver '" + MULTIPART_RESOLVER_BEAN_NAME + "' declared");
			}
		}
	}

	/**
	 * Initialize the LocaleResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to AcceptHeaderLocaleResolver.
	 */
	private void initLocaleResolver(ApplicationContext context) {
		try {
			/* 1、先从容器中获取LocaleResolver，有的话就用容器中获取到的 */

			// 先从容器中获取beanName为localeResolver，类型为LocaleResolver的bean
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME/* localeResolver */, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.localeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.localeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			/*

			2、如果容器中没有获取到LocaleResolver，则从DispatcherServlet.properties文件中，获取配置的默认的LocaleResolver

			题外：默认的LocaleResolver = AcceptHeaderLocaleResolver

			*/

			// We need to use the default. —— 我们需要使用默认值

			// 容器中没有找到beanName为localeResolver，类型为LocaleResolver的bd，则自动会报错：NoSuchBeanDefinitionException
			// 所以会进入到这里面
			// 如果容器中没有获取到LocaleResolver，则从DispatcherServlet.properties文件中，获取配置的默认的LocaleResolver
			// 题外：默认的LocaleResolver = AcceptHeaderLocaleResolver
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No LocaleResolver '" + LOCALE_RESOLVER_BEAN_NAME +
						"': using default [" + this.localeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ThemeResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to a FixedThemeResolver.
	 */
	private void initThemeResolver(ApplicationContext context) {
		try {
			/* 1、先从容器中获取ThemeResolver，有的话就用容器中获取到的 */

			// 先从容器中获取beanName为themeResolver，类型为ThemeResolver的bean
			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME/* themeResolver */, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.themeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.themeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			/*

			2、如果容器中没有获取到ThemeResolver，则从DispatcherServlet.properties文件中，获取配置的默认的ThemeResolver

			题外：默认的ThemeResolver = FixedThemeResolver

			*/

			// We need to use the default. —— 我们需要使用默认值

			// 容器中没有找到beanName为themeResolver，类型为ThemeResolver的bd，则自动会报错：NoSuchBeanDefinitionException
			// 所以会进入到这里面
			// 如果容器中没有获取到ThemeResolver，则从DispatcherServlet.properties文件中，获取配置的默认的ThemeResolver
			// 题外：默认的ThemeResolver = FixedThemeResolver
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ThemeResolver '" + THEME_RESOLVER_BEAN_NAME +
						"': using default [" + this.themeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * 初始化HandlerMappings
	 *
	 * Initialize the HandlerMappings used by this class.
	 * <p>If no HandlerMapping beans are defined in the BeanFactory for this namespace,
	 * we default to BeanNameUrlHandlerMapping.
	 */
	private void initHandlerMappings(ApplicationContext context) {
		// 将handlerMappings置空
		this.handlerMappings = null;
		/*

		1、判断是否检测所有的HandlerMapping，如果检测所有的HandlerMapping，就从所有容器中获取HandlerMapping

		注意：通过这种方式获取的话，可能获取到多个HandlerMapping

		 */
		// 判断是否检测所有的HandlerMapping，默认为true
		// 如果检测所有的HandlerMapping，就扫描所有容器中的所有已注册的HandlerMapping bean，添加到handlerMappings中
		// 注意：通过这种方式获获取，可能获取到多个HandlerMapping
		if (this.detectAllHandlerMappings/* 检测所有HandlerMapping(处理程序映射)，默认true */) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			// 上面翻译：查找 ApplicationContext 中的所有 HandlerMapping，包括祖先上下文。

			/**
			 * 1、题外：handlerMappings是一个集合，从这点可以看出，我是可以定义多个HandlerMapping的。
			 * 2、注意：虽然可以存在多个HandlerMapping，但是在处理的时候，只会获取一个进行处理。
			 * >>> 多个HandlerMapping，会按照优先级进行排序。
			 * >>> 优先按照顺序来进行匹配处理，匹配到一个，就只用这一个执行，不会再匹配其它的HandlerMapping去执行
			 */
			// 从所有容器中获取HandlerMapping bean，添加到handlerMappings中
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			if (!matchingBeans.isEmpty()) {
				// 添加到handlerMappings中
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order. —— 我们按排序顺序保持HandlerMappings。
				// 排序
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		}
		/*

		2、如果不检测所有的HandlerMapping，就从当前容器中获取HandlerMapping

		注意：通过这种方式获取的话，只能获取到一个HandlerMapping

		 */
		// 如果不检测所有的HandlerMapping，则只在当前容器中获取beanName为handlerMapping，类型为HandlerMapping，添加到handlerMappings中
		// 注意：通过这种方式获获取，只能获取到一个HandlerMapping
		else {
			try {
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME/* handlerMapping */, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

		/*

		3、如果从容器中没有获取到HandlerMapping，则从DispatcherServlet.properties文件中，获取配置好的默认的HandlerMapping

		题外：以这种方式获取的顺序就是DispatcherServlet.properties文件中的顺序！所以我们在配置的时候，想要顺序的话，就可以调整配置的顺序！

		 */
		/**
		 * DispatcherServlet.properties文件中，配置好的默认的HandlerMapping：
		 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping}
		 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
		 * {@link org.springframework.web.servlet.function.support.RouterFunctionMapping}
		 */
		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		// 确保我们至少有一个 HandlerMapping，如果没有找到其他映射，则注册一个默认的 HandlerMapping。

		// 判断上面，是否获取到了HandlerMapping
		if (this.handlerMappings == null) {
			// 如果上面，没有获取到HandlerMapping，则从DispatcherServlet.properties文件中，获取配置的默认的HandlerMapping
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerMappings declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet_test.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * <p>If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 */
	private void initHandlerAdapters(ApplicationContext context) {
		// 将handlerAdapters置空
		this.handlerAdapters = null;
		/*

		1、判断是否检测所有的HandlerAdapter，如果检测所有的HandlerAdapter，就从所有容器中获取HandlerAdapter

		注意：通过这种方式获取的话，可能获取到多个HandlerAdapter

		 */
		if (this.detectAllHandlerAdapters/* 检测所有HandlerAdapter(处理程序适配器)，默认为true */) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			// 上面翻译：查找 ApplicationContext 中的所有 HandlerAdapter，包括祖先上下文。

			/**
			 * 1、题外：handlerAdapters是一个集合，从这点可以看出，我是可以定义多个HandlerAdapter的。
			 * 2、注意：虽然可以有多个HandlerAdapter，但是在使用的时候，只会优先选择一个来执行，不会再选择其它HandlerAdapter进行执行
			 */
			// 从所有容器中获取HandlerAdapter bean，添加到handlerAdapters中
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				// 添加到handlerAdapters
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order. —— 我们按排序顺序保持HandlerAdapters。
				// 排序
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		}
		/*

		2、如果不检测所有的HandlerAdapter，就从当前容器中获取HandlerAdapter

		注意：通过这种方式获取的话，只能获取到一个HandlerAdapter

		 */
		else {
			try {
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME/* handlerAdapter */, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}

		/*

		3、如果从容器中没有获取到HandlerAdapter，则从DispatcherServlet.properties文件中，获取配置好的默认的HandlerAdapter

		题外：默认的HandlerAdapter有：HttpRequestHandlerAdapter,SimpleControllerHandlerAdapter,RequestMappingHandlerAdapter
		题外：以这种方式获取的顺序就是DispatcherServlet.properties文件中的顺序！所以我们在配置的时候，想要顺序的话，就可以调整配置的顺序！

		 */
		/**
		 * DispatcherServlet.properties文件中，获取配置好的默认的HandlerAdapter：
		 * {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter}
		 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}
		 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
		 * {@link org.springframework.web.servlet.function.support.HandlerFunctionAdapter}
		 */
		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		// 上面的翻译：如果没有找到其他适配器，则通过注册默认的 HandlerAdapter 来确保我们至少有一些 HandlerAdapter。

		// 判断上面，是否从容器中获取到了HandlerAdapter
		if (this.handlerAdapters == null) {
			// 如果上面，没有获取到HandlerAdapter，则从DispatcherServlet.properties文件中，获取配置的默认的HandlerAdapter
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerAdapters declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerExceptionResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to no exception resolver.
	 */
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		// 置空handlerExceptionResolvers
		this.handlerExceptionResolvers = null;
		/*

		1、判断是否检测所有的HandlerExceptionResolver，如果检测所有的HandlerException，就从容器中获取所有的HandlerExceptionResolver

		注意：这种方式获取的话，可以获取到多个HandlerExceptionResolver

		 */
		// 自动扫描handlerExceptionResolver类型的bean
		if (this.detectAllHandlerExceptionResolvers/* 检测所有HandlerExceptionResolver(处理程序异常的解析器) */) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including ancestor contexts.

			/**
			 * 1、注意：handlerExceptionResolvers是一个集合，也就是说我们可以定义多个HandlerExceptionResolver
			 */
			// 从所有的容器中获取HandlerExceptionResolver
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				// 添加到handlerExceptionResolvers中
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order. —— 我们按排序顺序保持 HandlerExceptionResolver。
				// 排序
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		}
		/*

		2、如果不检测所有的HandlerExceptionResolver，那么就从当前容器中获取HandlerExceptionResolver

		注意：这种方式获取的话，只能获取到一个HandlerExceptionResolver

		*/
		else {
			try {
				// 从当前容器中获取beanName=handlerExceptionResolver，类型为HandlerExceptionResolver的bean
				HandlerExceptionResolver her =
						context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME/* handlerExceptionResolver */, HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
			}
		}

		/*

		3、如果从容器中没有获取到HandlerExceptionResolver，则从DispatcherServlet.properties中，获取配置好的默认的HandlerExceptionResolver

		题外：默认的HandlerExceptionResolver：ExceptionHandlerExceptionResolver,ResponseStatusExceptionResolver,DefaultHandlerExceptionResolver
		题外：以这种方式获取的顺序就是DispatcherServlet.properties文件中的顺序！所以我们在配置的时候，想要顺序的话，就可以调整配置的顺序！

		 */
		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		// 上面的翻译：如果没有找到其他解析器，则通过注册默认的HandlerExceptionResolvers来确保我们至少有一些HandlerExceptionResolvers。

		// 判断是否从容器中获取到了HandlerExceptionResolver
		if (this.handlerExceptionResolvers == null) {
			// 如果没有从容器中获取到HandlerExceptionResolver，则从DispatcherServlet.properties中，获取配置好的默认的HandlerExceptionResolver
			// 题外：默认的HandlerExceptionResolver：ExceptionHandlerExceptionResolver,ResponseStatusExceptionResolver,DefaultHandlerExceptionResolver
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerExceptionResolvers declared in servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet_test.properties");
			}
		}
	}

	/**
	 * Initialize the RequestToViewNameTranslator used by this servlet instance.
	 * <p>If no implementation is configured then we default to DefaultRequestToViewNameTranslator.
	 */
	private void initRequestToViewNameTranslator/* 请求查看姓名翻译 */(ApplicationContext context) {
		try {
			/* 1、先从容器中获取RequestToViewNameTranslator，有的话就使用容器中的 */

			// 先从容器中获取到beanName=viewNameTranslator，类型为RequestToViewNameTranslator的bean
			this.viewNameTranslator =
					context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME/* viewNameTranslator *//* 查看姓名翻译 */,
							RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.viewNameTranslator.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.viewNameTranslator);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			/*

			2、如果容器中没有获取到RequestViewNameTranslator，则从DispatcherServlet.properties中，获取配置好的默认的RequestToViewNameTranslator

			题外：默认的RequestToViewNameTranslator = DefaultRequestToViewNameTranslator

			*/

			// We need to use the default. —— 我们需要使用默认值。

			// 容器中没有找到beanName为viewNameTranslator，类型为RequestToViewNameTranslator的bd，则自动会报错：NoSuchBeanDefinitionException
			// 所以会进入到这里面
			// 如果容器中没有获取到RequestToViewNameTranslator，则会从DispatcherServlet.properties中，获取配置好的默认的RequestToViewNameTranslator
			// 题外：默认的RequestToViewNameTranslator = DefaultRequestToViewNameTranslator
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No RequestToViewNameTranslator '" + REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME +
						"': using default [" + this.viewNameTranslator.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * <p>If no ViewResolver beans are defined in the BeanFactory for this
	 * namespace, we default to InternalResourceViewResolver.
	 */
	private void initViewResolvers(ApplicationContext context) {
		// 置空viewResolvers
		this.viewResolvers = null;

		/*

		1、判断是否检测所有ViewResolver，如果检测所有的ViewResolver，那么就从所有容器中获取所有ViewResolver

		注意：这种方式获取，可能获取到多个

		 */
		/// 自动扫描 ViewResolver 类型的 Bean
		if (this.detectAllViewResolvers/* 检测所有ViewResolver(视图解析器) */) {
			// Find all ViewResolvers in the ApplicationContext, including ancestor contexts.
			// 上面翻译：查找 ApplicationContext 中的所有 ViewResolver，包括祖先上下文。

			/**
			 * 1、题外：viewResolvers是一个集合，从这点可以看出，我们可以定义多个ViewResolver
			 */
			// 从所有容器中获取ViewResolver
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				// 添加到viewResolvers集合中
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				// We keep ViewResolvers in sorted order. —— 我们保持ViewResolvers的排序
				// 排序
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		}
		/*

		2、如果不检测所有的ViewResolver，那么就从当前容器中获取ViewResolver

		注意：这种方式获取的话，只会获取到一个ViewResolver

		 */
		else {
			try {
				// 从当前容器中获取beanName=viewResolver，类型为ViewResolver的bean
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME/* viewResolver */, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later. —— 忽略，我们稍后会添加一个默认的ViewResolver
			}
		}

		/*

		3、如果容器中没有获取到ViewResolver，那么就从DispatcherServlet.properties文件中，获取配置好的默认的ViewResolver

		题外：默认的ViewResolver：InternalResourceViewResolver
		题外：以这种方式获取的顺序就是DispatcherServlet.properties文件中的顺序！所以我们在配置的时候，想要顺序的话，就可以调整配置的顺序！

		 */

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		// 上面的翻译：如果没有找到其他解析器，请通过注册默认ViewResolver，来确保我们至少有一个ViewResolver

		// 判断上面，是否从容器中获取到ViewResolver
		if (this.viewResolvers == null) {
			// 如果从容器中没有获取到ViewResolver的话，就从DispatcherServlet.properties文件中，获取配置好的默认的ViewResolver
			// 题外：默认的ViewResolver：InternalResourceViewResolver
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ViewResolvers declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * <p>If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 */
	private void initFlashMapManager/* flash地图管理器 */(ApplicationContext context) {
		try {
			/* 1、先从容器中获取FlashMapManager，有的话直接使用容器中获取到的 */
			// 从容器中获取beanName=flashMapManager，类型为FlashMapManager的bean
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME/* flashMapManager */, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.flashMapManager.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.flashMapManager);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			/*

			2、如果从容器中未获取到FlashMapManager，那么就直接从DispatcherServlet.properties中，获取配置好的默认的FlashMapManager

			题外：默认的FlashMapManager = SessionFlashMapManager

			 */

			// We need to use the default. —— 我们需要使用默认值

			// 如果从容器中没有找到beanName=flashMapManager，类型为FlashMapManager的bean的bd，则自动会报错：NoSuchBeanDefinitionException，所以会进入到这里面

			// 如果容器中没有获取到FlashMapManager，则会从DispatcherServlet.properties中，获取配置好的默认的FlashMapManager
			// 题外：默认的FlashMapManager = SessionFlashMapManager
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No FlashMapManager '" + FLASH_MAP_MANAGER_BEAN_NAME +
						"': using default [" + this.flashMapManager.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Return this servlet's ThemeSource, if any; else return {@code null}.
	 * <p>Default is to return the WebApplicationContext as ThemeSource,
	 * provided that it implements the ThemeSource interface.
	 * @return the ThemeSource, if any
	 * @see #getWebApplicationContext()
	 */
	@Nullable
	public final ThemeSource getThemeSource() {
		return (getWebApplicationContext() instanceof ThemeSource ? (ThemeSource) getWebApplicationContext() : null);
	}

	/**
	 * Obtain this servlet's MultipartResolver, if any.
	 * @return the MultipartResolver used by this servlet, or {@code null} if none
	 * (indicating that no multipart support is available)
	 */
	@Nullable
	public final MultipartResolver getMultipartResolver() {
		return this.multipartResolver;
	}

	/**
	 * Return the configured {@link HandlerMapping} beans that were detected by
	 * type in the {@link WebApplicationContext} or initialized based on the
	 * default set of strategies from {@literal DispatcherServlet.properties}.
	 * <p><strong>Note:</strong> This method may return {@code null} if invoked
	 * prior to {@link #onRefresh(ApplicationContext)}.
	 * @return an immutable list with the configured mappings, or {@code null}
	 * if not initialized yet
	 * @since 5.0
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return (this.handlerMappings != null ? Collections.unmodifiableList(this.handlerMappings) : null);
	}

	/**
	 * Return the default strategy object for the given strategy interface.
	 * <p>The default implementation delegates to {@link #getDefaultStrategies},
	 * expecting a single object in the list.
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the corresponding strategy object
	 * @see #getDefaultStrategies
	 */
	protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
		// ⚠️从DispatcherServlet.properties文件中获取strategyInterface类型对应的所有Class，然后实例化其对象返回
		List<T> strategies = getDefaultStrategies(context, strategyInterface);
		if (strategies.size() != 1) {
			throw new BeanInitializationException(
					"DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
		}
		// 也就是获取DispatcherServlet.properties文件中配置的strategyInterface类型对应的第一个Class的实例对象
		return strategies.get(0);
	}

	/**
	 * Create a List of default strategy objects for the given strategy interface.
	 * <p>The default implementation uses the "DispatcherServlet.properties" file (in the same
	 * package as the DispatcherServlet class) to determine the class names. It instantiates
	 * the strategy objects through the context's BeanFactory.
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the List of corresponding strategy objects
	 */
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		/*

		1、获取DispatcherServlet.properties文件中配置的，strategyInterface类型对应的所有实现类，
		然后实例化其对象，进行返回

		 */
		// 获取strategyInterface的全限定类名
		String key = strategyInterface.getName();
		// 从defaultStrategies中，通过"strategyInterface的全限定类名"获取到对应的所有的"实现类的全限定类名"，以逗号分割
		String value = defaultStrategies.getProperty(key);

		// 根据value中的"实现类的全限定类名"，创建对应的对象
		if (value != null) {
			// 基于","分隔，分割value，得到"实现类的全限定类名"数组
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);

			// 创建strategyInterface集合
			List<T> strategies = new ArrayList<>(classNames.length);

			// 遍历"实现类的全限定类名"数组，创建对应的对象，添加到strategies中
			for (String className : classNames) {
				try {
					// 获得"实现类的全限定类名"对应的Class
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
					// 根据额"实现类的全限定类名"对应的Class，创建对应的对象
					Object strategy = createDefaultStrategy(context, clazz);
					// 添加到strategies中
					strategies.add((T) strategy);
				}
				catch (ClassNotFoundException ex) {
					throw new BeanInitializationException(
							"Could not find DispatcherServlet's default strategy class [" + className +
							"] for interface [" + key + "]", ex);
				}
				catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" +
							className + "] for interface [" + key + "]", err);
				}
			}
			// 返回strategies
			return strategies;
		}
		else {
			return new LinkedList<>();
		}
	}

	/**
	 * Create a default strategy.
	 * <p>The default implementation uses
	 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}.
	 * @param context the current WebApplicationContext
	 * @param clazz the strategy implementation class to instantiate
	 * @return the fully configured strategy instance
	 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean
	 */
	protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
		return context.getAutowireCapableBeanFactory().createBean(clazz);
	}


	/**
	 * Exposes the DispatcherServlet-specific request attributes and delegates to {@link #doDispatch}
	 * for the actual dispatching.
	 */
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// 记录日志信息：如果日志级别为DEBUG，则打印请求日志
		logRequest(request);

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		// 上面翻译：在包含的情况下，保留请求属性的快照，以便能够在包含后恢复原始属性。

		/* 1、如果是一个include请求，就把该请求的属性做一个快照备份，方便后续进行恢复 */

		// 当是一个include请求时，对当前request的attribute做一个快照备份，方便之后进行恢复
		// 题外：include请求：页面<include>标签，嵌套的时候的请求
		Map<String, Object> attributesSnapshot/* 属性快照 */ = null;
		// 判断当前请求，是不是一个include请求
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			// 获取所有的属性名
			Enumeration<?> attrNames = request.getAttributeNames();
			// 遍历属性
			while (attrNames.hasMoreElements()) {
				// 获取属性名
				String attrName = (String) attrNames.nextElement();
				if (this.cleanupAfterInclude/* 包含后清理 */ || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX/* org.springframework.web.servlet */)) {
					// 将属性名和属性值存入attributesSnapshot中
					attributesSnapshot.put(attrName, request.getAttribute(attrName)/* 根据属性名，获取属性值 */);
				}
			}
		}

		/* 2、给request设置一些属性值 —— 设置"Spring框架中的常用对象"到request属性中 */

		// Make framework objects available to handlers and view objects. —— 使框架对象可用于处理程序和视图对象。

		// 设置"Spring框架中的常用对象"到request属性中
		// 题外：这四个属性会在handler和view中使用

		/**
		 * 1、主题
		 * （1）概念：网站风格。在网站进行展示的时候，可以有不同的主题风格。就跟桌面上，下载的主题软件一样，可以一键更换图标、背景图片，就形成了不同的网站风格，也就是不同的主题
		 * （2）作用：一键更换的图标、背景图片等东西，这些东西都归属于静态资源，我们可以把这些静态资源，全部都改成某个配置文件里面的属性，指向某一个路径，
		 * 当我在点击切换的时候，就可以一键把对应的所有的图标，样式做一个整体的替换，而不用前端人员去一个个的去修改对应的属性值了，这就是主题存在的意义和价值。
		 * (简单概括：主题的作用：就是用于一键替换，网站的样式、图标、图片之类的东西，改变网站风格，而不是逐一进行替换 —— 也就是把静态资源做一个统一的替换而已)
		 *
		 * 2、ThemeSource和ThemeResolver的关系：ThemeSource是ThemeResolver处理过程中需要用到的对象
		 */
		// 把当前上下文对象WebApplicationContext，设置到request中
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.CONTEXT */, getWebApplicationContext());
		// 把当前的语言处理器，设置到request中
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER */, this.localeResolver);
		// 把当前的主题处理器，设置到request中
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.THEME_RESOLVER */, this.themeResolver);
		// 把当前的主题资源，设置到request中
		request.setAttribute(THEME_SOURCE_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.THEME_SOURCE */, getThemeSource());

		/**
		 * flashMapManager：方便重定向请求时，传递参数（方便重定向请求时，把参数设置到flashMapManager，进行传递）
		 * 因为：重定向请求时没有提交参数的功能，如果想传递参数，就必须要写到url，而url有长度的限制同时还容易对外暴露，所以可以利用flashMap来传递参数
		 */
		// FlashMap的相关配置，主要用于Redirect重定向请求时，传递参数，此处有一个应用场景:如果post请求是提交表单，提交完之后redirect到一个显示订单的页面，
		// 此时需要知道一些订单的信息，但redirect本身没有提交参数的功能，如果想传递参数，那么就必须要写到url，而url有长度的限制同时还容易对外暴露，
		// 所以，此时可以使用flashMap来传递参数。
		if (this.flashMapManager != null) {
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.INPUT_FLASH_MAP */,
						Collections.unmodifiableMap(inputFlashMap));
			}
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.OUTPUT_FLASH_MAP */,
					new FlashMap());
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.FLASH_MAP_MANAGER */,
					this.flashMapManager);
		}

		/* 3、执行请求的分发 */

		try {
			// ⚠️执行请求的分发
			doDispatch(request, response);
		}
		finally {
			// 判断是否开启了异步请求
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted/* 是并发处理开始 */()) {
				/* 没有开启 */

				// Restore the original attribute snapshot, in case of an include. —— 恢复原始属性快照，以防包含。
				// 还原request快照的属性
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
		}
	}

	private void logRequest(HttpServletRequest request) {
		LogFormatUtils.traceDebug(logger, traceOn -> {
			String params;
			if (isEnableLoggingRequestDetails()) {
				params = request.getParameterMap().entrySet().stream()
						.map(entry -> entry.getKey() + ":" + Arrays.toString(entry.getValue()))
						.collect(Collectors.joining(", "));
			}
			else {
				params = (request.getParameterMap().isEmpty() ? "" : "masked");
			}

			String queryString = request.getQueryString();
			String queryClause = (StringUtils.hasLength(queryString) ? "?" + queryString : "");
			String dispatchType = (!request.getDispatcherType().equals(DispatcherType.REQUEST) ?
					"\"" + request.getDispatcherType().name() + "\" dispatch for " : "");
			String message = (dispatchType + request.getMethod() + " \"" + getRequestUri(request) +
					queryClause + "\", parameters={" + params + "}");

			if (traceOn) {
				List<String> values = Collections.list(request.getHeaderNames());
				String headers = values.size() > 0 ? "masked" : "";
				if (isEnableLoggingRequestDetails()) {
					headers = values.stream().map(name -> name + ":" + Collections.list(request.getHeaders(name)))
							.collect(Collectors.joining(", "));
				}
				return message + ", headers={" + headers + "} in DispatcherServlet '" + getServletName() + "'";
			}
			else {
				return message;
			}
		});
	}

	/**
	 * 实际处理请求，分发到处理器中。
	 * （1）内层捕获的是，请求处理的过程中，抛出的异常。异常信息会设置到dispatcherException变量，然后在processorDispatcherResult()中进行处理
	 * （2）外层处理的是，渲染页面时，抛出的异常，主要是处理processDispatchResult()抛出的异常
	 *
	 * Process the actual dispatching to the handler.
	 * <p>The handler will be obtained by applying the servlet's HandlerMappings in order.
	 * The HandlerAdapter will be obtained by querying the servlet's installed HandlerAdapters
	 * to find the first that supports the handler class.
	 * <p>All HTTP methods are handled by this method. It's up to HandlerAdapters or handlers
	 * themselves to decide which methods are acceptable.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 */
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		/* 一些变量的准备工作 */

		// processedRequest：实际处理请求时，所用的request对象。最开始，"实际处理请求的请求对象"是"接收请求时的request对象"
		// 题外：如果不是上传请求，processedRequest则是接收请求时的request对象；如果是上传请求，则会封装为上传类型的request对象
		HttpServletRequest processedRequest = request;
		// 处理请求的处理器链（包含对应的handler(处理器)和interceptor(拦截器)） —— 也就是说我们的处理器执行链是什么样的
		HandlerExecutionChain mappedHandler = null;
		// 是不是上传请求的标识(true：是上传请求；false：不是上传请求)
		boolean multipartRequestParsed = false;

		/**
		 * 1、AsyncContext：上下文嘛，保存了跟异步请求相关的一些信息
		 *
		 * 2、它虽然是异步消息的处理，但是最根本最核心的，还是一个Servlet
		 *
		 * 3、想支持异步请求
		 */
		// 获取异步管理器
		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		/**
		 * 这里有两个try..catch，代表的是2个阶段。也就是，把请求流程被为了2部分。
		 * （1）部分1：请求处理阶段 - 执行后台业务逻辑
		 * 也就是内层try。内层的try代码逻辑是用来处理请求的，catch是用来捕获请求处理过程中产生的异常 —— 请求处理异常
		 * （2）部分2：页面渲染阶段 - 页面怎么进行数据填充，怎么回显
		 * 也就是外层try。外层的try代码逻辑是用来进行页面渲染的，catch是用来捕获页面渲染过程中产生的异常 —— 页面渲染异常
		 * 题外：内层处理的是请求，外层处理的是视图渲染
		 */
		try {

			// 封装model和view的容器
			ModelAndView mv = null;
			// 全局的异常对象，保存"请求处理过程中发生的异常"
			// 注意：不包含页面渲染过程中抛出的异常
			Exception dispatchException/* 调度异常 */ = null;

			try {

				/*

				1、检测是否是上传请求
				（1）如果是上传请求，就用文件解析器（MultipartResolver）对原生request进行包装，包装为上传请求的request；
				（2）否则返回原先的request

				 */
				/**
				 * 1、题外：上传请求的包装：如果是上传请求，则会通过multipartResolver将其封装成MultipartHttpServletRequest对象
				 * 2、题外：在进行上传的时候，必然会指定content-type，其属性值，都是"multipart/"开头的，代表这是一个上传请求
				 */
				processedRequest = checkMultipart(request);
				/**
				 * processedRequest最开始是指向request的，也就是等于request的；
				 * 如果processedRequest不等于request，那么就证明，processedRequest被改变了，被包装成了一个上传请求，也就是说，当前请求是一个上传请求；
				 * 否则就不是一个上传请求
				 */
				// 判断当前的请求，是不是一个上传请求
				multipartRequestParsed = (processedRequest != request);

				/*

				2、获取处理当前请求的Handler执行链，里面包含了：(1)处理器(handler：controller)；(2)拦截器

				题外：牵扯到HandlerMapping。通过HandlerMapping找到处理请求的handler执行链。

				*/

				/**
				 * 1、之所以是链
				 * 我们一般写都是具体的请求名称，但是有的时候会写通配符，如果是通配符的话，就比较麻烦了，除了获取到我们的controller之外，
				 * 还有一个东西叫interceptor(拦截器)，我这里面会进行相关的拦截器处理工作
				 */
				// Determine handler for the current request. —— 确定当前请求的处理程序。
				// 获取处理器 —— 获取请求对应的HandlerExecutionChain对象（包含HandlerMethod和HandlerInterceptor拦截器）
				// 题外：handler直接理解为controller即可
				mappedHandler = getHandler(processedRequest);
				if (mappedHandler == null) {
					// mappedHandler=null，意味着没有对应的controller能处理当前的请求，则根据配置抛出异常或返回404错误
					noHandlerFound(processedRequest, response);
					return;
				}

				/*

				3、获取处理器的适配器（根据handler，找到支持处理当前handler的HandlerAdapter）

				题外：有一个非常重要的适配器：RequestMappingHandlerAdapter，用来执行@Controller方式定义的Controller中的方法。
				题外：其它适配器比较简单，因为其它的方式实现Controller比较简单，只需要直接调用对应的方法即可

				 */
				/**
				 * 1、适配器存在的意义
				 *
				 * handler有多种实现方式，通过adapter对其进行统一编码，统一调用，抽象统一处理！
				 *
				 * 题外：通过HandlerAdapter可以支持任意的类作为处理器
				 *
				 * 在spring mvc里面，并没有对处理器做任何的限制，处理器可以以任何合理的方式来表现，
				 * 可以是一个类，可以是一个方法，还可以是别的合理的方式。
				 * 由于形式多种，不统一，编码上，没法进行抽象的统一处理。所以我们在进行实际处理的时候，要把处理器转换成我们能够接受的合适的方式，
				 * 这样才能够对它进行执行，这也就是适配器存在的价值。
				 * 也就是说：处理器转换为适配器，经过适配器处理之后，我们成为一种标准的方式来进行处理
				 *
				 * 例子：就像每个国家对应的电压不同，中国220V，日本110V，它要进行一个变压器，变压器转换完成之后，我的电器就可以随便使用了
				 *
				 * 2、为什么handler要进行适配？
				 * Controller有多种不同的实现，通过适配器来统一解决不同实现的方法调用问题。
				 * 尤其是@Controller的实现方式，里面的方法都是用户自定义的，在程序启动之前，完全不知道有什么方法，所以需要适配器来解决不同方法的统一调用问题
				 *
				 * 3、不同的adapter匹配了不同的请求方式、不同的controller
				 */
				/**
				 * 1、org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter：处理"实现HttpRequestHandler接口的Handler"的适配器
				 *
				 * 2、org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter：处理"实现Controller接口的Handler"的适配器
				 *
				 * 3、org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter：处理"HandlerMethod"的适配器，也就是由@Controller标注的Handler
				 *
				 * 在其afterProperties()方法里面：
				 * >>>	1、获取所有容器中的所有的@ControllerAdvice bean，然后进行解析，具体如下：
				 * >>>	（1）获取ControllerAdviceBean中，带了@ModelAttribute，但是没带@RequestMapping注解的方法，保存在modelAttributeAdviceCache集合中
				 * >>>	（2）获取ControllerAdviceBean中，带了@InitBinder注解的方法，保存到initBinderAdviceCache集合中
				 * >>>	（3）判断当前@ControllerAdvice Bean是否实现了RequestBodyAdvice接口或者ResponseBodyAdvice接口，是的话就保存当前@ControllerAdvice Bean到requestResponseBodyAdvice集合中
				 * >>>	2、初始化所有"参数解析器"
				 * >>>	3、初始化所有"@InitBinder的参数解析器"
				 * >>>	4、初始化所有"返回值处理器"
				 *
				 * 4、org.springframework.web.servlet.function.support.HandlerFunctionAdapter：处理"实现HandlerFunction接口的Handler"的适配器
				 */
				// Determine handler adapter for the current request. —— 确定当前请求的处理程序适配器。
				// 获取当前处理器对应的适配器对象
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				/*

				4、处理last_modified，看下浏览器的资源是否过期，如果没过期，就直接返回304，让浏览器使用之前缓存的结果，以达到减少数据量的传输。

				last_modified作用：当我们请求静态资源的时候，可用于判断客户端的静态资源是否过期；
				没过期，服务器就不用再读取资源返回了，直接返回304表示未过期，让浏览器直接使用之前缓存的结果即可。
				主要目的是为了，减少浏览的时候，数据量的传输。

				*/
				// Process last-modified header, if supported by the handler. —— 如果处理程序支持，处理last-modified请求头

				// 处理GET、HEAD请求里面的last-Modified请求
				// 当浏览器第一次向服务器请求资源时，服务器会在响应头中包含一个last_modified的属性，代表资源最后修改的时间，
				// 在浏览器以后发送请求的时候，会同时发送之前接收到的last_modified，
				// 服务器接收到带last_modified的请求后，会跟实际资源的最后修改时间做对比(会跟你当前服务器里面存放的资源的时间做对比)，
				// 如果过期了返回新的资源；否则直接返回304表示未过期，让浏览器直接使用之前缓存的结果即可，这样就不用每次都读取资源返回
				// (注意：请求是固定要发的，但是响应的请求里面，可能是带数据，也可能是不带数据的)
				String method = request.getMethod();
				boolean isGet = "GET".equals(method);
				// HEAD作用：获取请求的头信息
				if (isGet || "HEAD".equals(method)) {
					// 获取请求中服务器端最后被修改时间
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (new ServletWebRequest(request, response).checkNotModified/* 检查未修改 */(lastModified) && isGet) {
						return;
					}
				}

				/* 5、执行拦截器的前置方法 */
				// 执行拦截器的前置方法 —— 执行响应的Interceptor的preHandler()
				// 注意：该方法如果有一个拦截器的前置处理返回false，则开始倒序触发所有的拦截器的 已完成处理
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				/* 6、通过适配器来处理我们具体的请求。适配器内部调用处理器，来处理请求。 */
				// Actually invoke the handler. —— 实际上调用处理程序。
				/**
				 * 1、RequestMappingHandlerAdapter
				 *
				 * RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter，所以走的是AbstractHandlerMethodAdapter
				 *
				 * RequestMappingHandlerAdapter内部相对复杂，有一大堆处理
				 *
				 * 2、HttpRequestHandlerAdapter
				 *
				 * 内部很简单，直接调用HttpRequestHandler#handleRequest(request, response);
				 *
				 * 3、SimpleControllerHandlerAdapter
				 *
				 * 内部很简单，直接调用Controller#handleRequest(request, response);
				 */
				// 实际处理请求的地方：真正的调用处理器方法，返回视图
				// 题外：mv = ModeAndView，最终返回到前端进行属性回显的时候，用的就是这个ModeAndView对象
				mv/* ModeAndView */ = ha/* HandleAdapter */.handle(processedRequest, response, mappedHandler.getHandler());

				/* 如果当前请求中启动了异步处理，那么直接结束当前方法，中断当前线程后续的处理！继而当前线程会被销毁！由异步线程处理完毕之后，返回结果！ */
				// 判断当前有没有启动异步处理，如果启动了异步处理，则直接返回null，中断当前线程后续的处理！继而当前线程会被销毁！由异步线程处理完毕之后，返回结果！
				// 题外：如果在上面调用具体handler的过程中，返回值是一个异步任务，那么一定会开启线程，执行异步处理；
				// >>> 在开启之前，肯定是把"是否开启异步处理的标识"设置了为true，再开启异步线程的！
				// >>> 所以等我们走到这一步的时候，就可以感知得到！
				if (asyncManager.isConcurrentHandlingStarted()/* 是并发处理开始 */) {
					return;
				}

				/*

				7、当view为空时，就设置一个默认的视图名称(默认的视图名称是根据请求路径作为一个视图名称)。

				题外：牵扯viewNameTranslator视图名称转换器。当view为空时，然后存在视图名称转换器，就利用视图名称转换器返回一个默认的视图名称，
				常用的viewNameTranslator是DefaultRequestToViewNameTranslator

				*/
				/**
				 * 1、viewName视图名称有可能为空吗？
				 * 有，例如加了一个@ResponseBody，就是不返回视图，直接把结果值进行返回，这个时候时候视图名称就为空。
				 *
				 * 2、默认的视图名称转换器
				 * viewNameTranslator = DefaultRequestToViewNameTranslator
				 * 里面根据请求路径作为一个视图名称
				 */
				// 当view视图为空时，则设置一个默认的视图
				// 题外：当我们在返回视图的时候，如果没有对应的ViewName，它就会给你返回一个默认的视图，有了视图之后，后面才能对它进行渲染，页面上才能看到对应的效果
				applyDefaultViewName(processedRequest, mv);

				/* 8、执行拦截器的后置方法 */
				// 执行拦截器的后置方法 —— 执行响应的interceptor的postHandler()
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			}
			/* 🤔️9、如果请求处理阶段发生了异常，就将异常设置到"处理器异常解析器"中 */
			catch (Exception ex) {
				// 记录异常（将抛出的异常交给全局的异常对象）
				dispatchException = ex;
			}
			catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				// 上面的翻译：从4.3开始，我们也在处理HandlerMethod抛出的错误，使它们可用于@ExceptionHandler方法和其他场景。

				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}

			/* 10、处理返回结果，里面包含：渲染页面、处理异常、执行Interceptor的afterCompletion()，等操作 */
			// 处理返回结果，也包括处理异常、渲染页面、触发Interceptor的afterCompletion
			// 题外：也就是说，你现在已经返回了一个View，有了对应的视图了，当你有了对应的操作之后，我开始进行相关的回显，包括页面渲染等相关操作
			// >>> 这个时候，你可以在页面看到具体的效果东西了，所以直接进行渲染，回显就OK了
			// 题外：当我把页面渲染完成之后，就可以返回给浏览器，让浏览器进行回显
			processDispatchResult/* 处理调度结果 */(processedRequest, response, mappedHandler, mv, dispatchException);
		}
		catch (Exception ex) {
			// 已完成处理 拦截器
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		}
		catch (Throwable err) {
			// 完成处理激活触发器
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		}
		/* 11、释放资源 */
		finally {
			// 判断是否启动了异步请求
			if (asyncManager.isConcurrentHandlingStarted()/* 是并发处理开始 */) {
				// Instead of postHandle and afterCompletion —— 而不是postHandle和afterCompletion
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			}
			else {
				// Clean up any resources used by a multipart request. —— 清理多部分请求使用的任何资源。
				// 如果有对应的一些上传请求资源，就把它清空掉
				// 删除上传请求的资源
				if (multipartRequestParsed) {
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

	/**
	 * Do we need view name translation?
	 */
	private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
		// ModelAndView不为空 && 不存在view
		if (mv != null && !mv.hasView()) {
			// 获取一个默认的视图名称
			String defaultViewName = getDefaultViewName(request);
			// 可以获取到一个默认的视图名称，就设置到ModelAndView的view变量中的
			if (defaultViewName != null) {
				mv.setViewName(defaultViewName);
			}
		}
	}

	/**
	 * Handle the result of handler selection and handler invocation, which is
	 * either a ModelAndView or an Exception to be resolved to a ModelAndView.
	 */
	private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, @Nullable ModelAndView mv,
			@Nullable Exception exception) throws Exception {

		// 是否为处理异常时生成的ModelAndView对象标识，也就是说是不是错误视图对象
		boolean errorView = false;

		/*

		1、如果请求处理过程中有异常抛出，则处理异常
		（1）里面具体是，通过异常解析器解析异常，得到一个ModelAndView，代表了"错误视图"，然后回显即可；
		（2）如果没有一个异常解析器返回ModelAndView，则会把当前异常往外抛出，一直会抛给tomcat，由tomcat会对其进行处理，返回给前端！

		*/

		// 如果处理请求的过程中有异常抛出，则处理异常
		if (exception != null) {
			// 判断异常类型是不是ModelAndViewDefiningException
			// 如果异常类型是ModelAndViewDefiningException的话，就从从ModelAndViewDefiningException中获得ModelAndView错误视图对象
			if (exception instanceof ModelAndViewDefiningException/* ModelAndView定义的异常 */) {
				logger.debug("ModelAndViewDefiningException encountered", exception);

				mv = ((ModelAndViewDefiningException) exception).getModelAndView();
			}
			// 如果不是ModelAndViewDefiningException，则调用异常解析器处理异常，得到ModelAndView错误视图对象
			else {
				// 获取到HandlerMethod
				Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
				// ⚠️处理异常
				mv = processHandlerException(request, response, handler, exception);
				// 判断是不是错误事务
				errorView = (mv != null);
			}
		}

		/*

		2、渲染页面

		（1）通过语言环境解析器，解析到当前的语言环境（Locale）；然后往response里面设置语言环境(国际化相关的设置)
		（2）设置响应的状态码
		（3）获取view对象
		>>>（1）会用视图解析器，解析视图名称，得到视图对象view。
		>>> 常用的视图解析器：InternalResourceViewResolver，里面就是把视图名称拼接上前缀和后缀，得到一个物理视图地址！
		（4）然后通过view对象渲染页面
		>>>（1）将model里面的数据放入request作用域里面
		>>>（2）获取物理视图地址作为新的请求路径
		>>>（3）获取请求转发器
		>>>（4）先看下当前请求是不是一个include请求（即不是从外部进来的顶级HTTP请求），是的话就用请求转发器进行include；
		>>>（5）如果不是include请求，就用请求转发器执行转发(forward)操作

		题外：如果使用了@ReponseBody，那么在对应的返回值处理器(RequestResponseBodyMethodProcessor)，就已经处理好了对应的返回数据，会把结果作为一个json响应出去了，不会走这里的渲染视图逻辑！
		因为在RequestResponseBodyMethodProcessor里面会将请求是否处理完成的标识设置为true，后续在getModelAndView()时，发现请求已经处理完成了，所以返回的ModelAndView是一个null，
		由于返回null，所以这里就不会走下面的渲染页面操作！

		*/

		// Did the handler return a view to render? —— 处理程序是否返回要渲染的视图？
		// 是否进行页面渲染
		if (mv != null && !mv.wasCleared()/* 被清除 */) {
			// ⚠️渲染页面
			render(mv, request, response);
			// 清理请求中的错误消息属性
			// 因为上述的情况中，processHandlerException会通过WebUtils设置错误消息属性，所以需要进行清理
			if (errorView) {
				WebUtils.clearErrorRequestAttributes(request);
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("No view rendering, null ModelAndView returned.");
			}
		}

		/* 3、如果启动了异步处理，则返回 */

		// 如果启动了异步处理则返回
		if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
			// Concurrent handling started during a forward
			return;
		}

		/* 4、发出请求处理完成通知，触发Interceptor的afterCompletion() —— handler执行完后，以及视图回显完毕后执行的方法 */

		// 发出请求处理完成通知，触发Interceptor的afterCompletion
		if (mappedHandler != null) {
			// Exception (if any) is already handled.. —— 异常（如果有）已被处理..
			mappedHandler.triggerAfterCompletion(request, response, null);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's primary locale as current locale.
	 * <p>The default implementation uses the dispatcher's LocaleResolver to obtain the current locale,
	 * which might change during a request.
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext
	 */
	@Override
	protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		}
		else {
			// 判断localeResolver是否为空
			// 如果不为空，执行进行处理
			// 如果等于空，直接从里面进行处理
			// 也就是说，是否要经过当前处理器的处理工作
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

	/**
	 * Convert the request into a multipart request, and make multipart resolver available.
	 * <p>If no multipart resolver is set, simply use the existing request.
	 * @param request current HTTP request
	 * @return the processed request (multipart wrapper if necessary)
	 * @see MultipartResolver#resolveMultipart
	 */
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
		// 判断有没有处理上传文件的组件 && 判断是不是一个上传请求
		// 题外：默认multipartResolver为null的，也就是不支持文件上传！
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			/* 如果存在文件上传组件，并且这是一个上传文件的请求(该请求是一个涉及到文件的请求) */

			// 看一下是不是本地的请求
			if (WebUtils.getNativeRequest/* 获取本机请求 */(request, MultipartHttpServletRequest.class) != null) {
				if (request.getDispatcherType().equals(DispatcherType.REQUEST)) {
					logger.trace("Request already resolved to MultipartHttpServletRequest, e.g. by MultipartFilter");
				}
			}
			// 当前处理过程中，是否出现了异常信息
			else if (hasMultipartException(request)) {
				logger.debug("Multipart resolution previously failed for current request - " +
						"skipping re-resolution for undisturbed error rendering");
			}
			else {
				try {
					// 处理上传请求，其实就是，将普通的request封装成MultipartHttpServletRequest，然后返回。
					// 题外：只是把当前请求做一个封装，然后返回包装后的请求 —— 将HttpServletRequest请求封装成MultipartHttpServletRequest对象
					// 题外：之所以封装是因为：当我封装好了，在进行实际处理的时候，就可以通过getFile()获取上传的文件
					return this.multipartResolver.resolveMultipart(request);
				}
				catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
					}
					else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		return request;
	}

	/**
	 * Check "javax.servlet.error.exception" attribute for a multipart exception.
	 */
	private boolean hasMultipartException(HttpServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
		while (error != null) {
			if (error instanceof MultipartException) {
				return true;
			}
			error = error.getCause();
		}
		return false;
	}

	/**
	 * Clean up any resources used by the given multipart request (if any).
	 * @param request current HTTP request
	 * @see MultipartResolver#cleanupMultipart
	 */
	protected void cleanupMultipart(HttpServletRequest request) {
		if (this.multipartResolver != null) {
			MultipartHttpServletRequest multipartRequest =
					WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
			if (multipartRequest != null) {
				this.multipartResolver.cleanupMultipart(multipartRequest);
			}
		}
	}

	/**
	 * 返回当前request匹配的interceptor和handler
	 *
	 * Return the HandlerExecutionChain for this request.
	 * <p>Tries all handler mappings in order.
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain, or {@code null} if no handler could be found
	 */
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		/*

		1、遍历所有的handlerMapping，通过handlerMapping，找到处理当前请求的handler执行链，包含Handler和interceptor。
		如果有一个handlerMapping可以返回一个handler执行链，就返回这个handler执行链，终止再使用其它的handlerMapping进行寻找。

		 */
		if (this.handlerMappings != null) {
			/**
			 * 1、BeanNameUrlHandlerMapping：匹配的是URL请求，两种实现方式：实现Controller接口、实现HttpRequestHandler接口
			 * 2、RequestMappingHandlerMapping：处理@Controller、@RequestMapping
			 */
			// 遍历所有的HandlerMapping，调用HandlerMapping#getHandler()方法，找到处理请求的handler执行链，包含Handler和interceptor
			// 只要其中一个HandlerMapping返回了HandlerExecutionChain，就返回，不再执行下面的HandlerMapping
			for (HandlerMapping mapping : this.handlerMappings) {
				HandlerExecutionChain handler = mapping.getHandler(request);
				if (handler != null) {
					// 取到一个HandlerExecutionChain，就停止
					return handler;
				}
			}
		}
		return null;
	}

	/**
	 * No handler found -> set appropriate HTTP response status.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception if preparing the response failed
	 */
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {
			pageNotFoundLogger.warn("No mapping for " + request.getMethod() + " " + getRequestUri(request));
		}
		if (this.throwExceptionIfNoHandlerFound) {
			throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
					new ServletServerHttpRequest(request).getHeaders());
		}
		else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Return the HandlerAdapter for this handler object.
	 * @param handler the handler object to find an adapter for
	 * @throws ServletException if no HandlerAdapter can be found for the handler. This is a fatal error.
	 */
	protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		/* 1、遍历适配器，返回支持处理当前处理器的适配器 */
		if (this.handlerAdapters != null) {
			/**
			 * 1、org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter：处理"实现HttpRequestHandler接口的Handler"的适配器
			 *
			 * 2、org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter：处理"实现Controller接口的Handler"的适配器
			 *
			 * 3、org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter：处理"HandlerMethod"的适配器，也就是由@Controller标注的Handler
			 *
			 * 在其afterProperties()方法里面：
			 * >>>	1、获取所有容器中的所有的@ControllerAdvice bean，然后进行解析，具体如下：
			 * >>>	（1）获取ControllerAdviceBean中，带了@ModelAttribute，但是没带@RequestMapping注解的方法，保存在modelAttributeAdviceCache集合中
			 * >>>	（2）获取ControllerAdviceBean中，带了@InitBinder注解的方法，保存到initBinderAdviceCache集合中
			 * >>>	（3）判断当前@ControllerAdvice Bean是否实现了RequestBodyAdvice接口或者ResponseBodyAdvice接口，是的话就保存当前@ControllerAdvice Bean到requestResponseBodyAdvice集合中
			 * >>>	2、初始化所有"参数解析器"
			 * >>>	3、初始化所有"@InitBinder的参数解析器"
			 * >>>	4、初始化所有"返回值处理器"
			 *
			 * 4、org.springframework.web.servlet.function.support.HandlerFunctionAdapter：处理"实现HandlerFunction接口的Handler"的适配器
			 */
			for (HandlerAdapter adapter : this.handlerAdapters) {
				if (adapter.supports(handler)) {
					return adapter;
				}
			}
		}
		throw new ServletException("No adapter for handler [" + handler +
				"]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

	/**
	 * Determine an error ModelAndView via the registered HandlerExceptionResolvers.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the time of the exception
	 * (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to
	 * @throws Exception if no error ModelAndView found
	 */
	@Nullable
	protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
			@Nullable Object handler, Exception ex) throws Exception {

		// Success and error responses may use different content types
		// 移除 PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE 属性
		request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE/* 可生产的媒体类型 */);

		/* 1、遍历异常解析器，用异常解析器解析异常，生成ModelAndView，代表了"错误视图" */

		// Check registered HandlerExceptionResolvers... —— 检查已注册的HandlerExceptionResolvers...

		// 遍历异常解析器，用异常解析器解析异常，生成ModelAndView对象，代表了"错误视图"对象
		ModelAndView exMv = null;
		if (this.handlerExceptionResolvers != null) {
			// 遍历异常解析器
			for (HandlerExceptionResolver resolver : this.handlerExceptionResolvers) {
				/**
				 * 1、org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver：处理HandlerMethod时的Handler
				 *
				 * 走的AbstractHandlerExceptionResolver
				 *
				 * 题外：如果内部调用异常处理方法而报错，会被捕捉到，然后返回null值，这样下一个异常处理器，就可以尝试处理了。
				 * >>> 例如，调用异常方法的时候，参数解析器无法对异常处理方法的参数进行解析，而抛出异常，会被try..catch...捕捉到，返回null。
				 *
				 * 2、org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver：处理特定异常类型
				 *
				 * 走的AbstractHandlerExceptionResolver
				 *
				 * 3、org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver：
				 *
				 * 走的AbstractHandlerExceptionResolver
				 */
				// ⚠️异常解析器解析异常，生成ModelAndView返回
				exMv = resolver.resolveException(request, response, handler, ex);
				// 成功生成ModelAndView返回，结束循环
				// >>> 如果有一个异常解析器返回了ModelAndView，则使用这个ModelAndView，不会再用其它异常解析器进行处理
				// >>> 如果一个异常解析器返回null，则遍历下一个异常解析器进行处理
				if (exMv != null) {
					break;
				}
			}
		}

		/* 2、如果有异常处理器解析异常返回了ModelAndView，并且view和model不为空，则返回当前ModelAndView；否则返回null */

		if (exMv != null) {

			// 如果是一个空视图（view为null，并且model也是空），则返回null
			if (exMv.isEmpty()) {
				// 向request作用域当中设置异常对象
				request.setAttribute(EXCEPTION_ATTRIBUTE/* org.springframework.web.servlet.DispatcherServlet.EXCEPTION */, ex);
				return null;
			}

			// We might still need view name translation for a plain error model... —— 对于简单的错误模型，我们可能仍然需要视图名称翻译......

			// 没有视图名称，则设置默认视图名称
			if (!exMv.hasView()) {
				String defaultViewName = getDefaultViewName(request);
				if (defaultViewName != null) {
					exMv.setViewName(defaultViewName);
				}
			}

			if (logger.isTraceEnabled()) {
				logger.trace("Using resolved error view: " + exMv, ex);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Using resolved error view: " + exMv);
			}

			// 往request域中设置错误信息
			WebUtils.exposeErrorRequestAttributes/* 公开错误请求属性 */(request, ex, getServletName());

			return exMv;
		}

		/* 3、如果没有一个异常解析器返回ModelAndView，则会把当前异常往外抛出，一直会抛给tomcat，由tomcat会对其进行处理，返回给前端！  */

		// 未生成ModelAndView对象，则抛出异常
		// 注意：这里抛出的异常，是最开始第一时间发生的异常；不会是异常解析器里面的异常，异常解析器里面发生了异常，只会返回null
		throw ex;
	}

	/**
	 * Render the given ModelAndView.
	 * <p>This is the last stage in handling a request. It may involve resolving the view by name.
	 * @param mv the ModelAndView to render
	 * @param request current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @throws ServletException if view is missing or cannot be resolved
	 * @throws Exception if there's a problem rendering the view
	 */
	protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {

		/* 1、通过语言环境解析器，解析到当前的语言环境Locale；然后往response里面设置请求的语言环境(国际化相关的设置) */

		// Determine locale for request and apply it to the response. —— 确定请求的语言环境并将其应用于响应。
		/**
		 * 国际化相关的设置，获取到现在相关的语言是啥，通过值处理器来进行处理
		 */
		// 解析request中获得Locale对象，并设置到response中
		Locale locale =
				(this.localeResolver != null ? this.localeResolver.resolveLocale(request) : request.getLocale());
		response.setLocale(locale);

		/* 2、获取视图对象 - View */

		// 获得 View 对象
		View view;
		// 使用 viewName 获得 View 对象
		String viewName = mv.getViewName();

		/*

		2.1、⚠️如果视图名称不为空，则遍历视图解析器，用视图解析器解析视图名称，得到视图对象View
		如果有一个视图解析器可以解析视图名称，得到视图对象就返回该视图对象，不再走后面的视图解析器。

		常用的视图解析器：InternalResourceViewResolver

		 */
		if (viewName != null) {
			// ⚠️
			/**
			 * 用ViewResolver视图解析器来对Controller方法返回的逻辑视图名称进行解析，得到真正的物理地址
			 * 其实也就是拼接我们在配置文件中配置的前缀和后缀，完成具体的view
			 */
			// We need to resolve the view name.
			// 使用viewName获得View对象
			view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
			if (view == null) {
				// 获取不到，抛出 ServletException 异常
				throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
						"' in servlet with name '" + getServletName() + "'");
			}
		}
		/*

		2.2、如果视图名称为空，则代表ModelAndView对象中包含实际的View对象，那么就直接使用这个View对象

		 */
		// 直接使用 ModelAndView 对象的 View 对象
		else {
			// No need to lookup: the ModelAndView object contains the actual View object. —— 无需查找：ModelAndView对象包含实际的View对象。
			// 直接使用ModelAndView对象中的View对象
			view = mv.getView();
			if (view == null) {
				// 获取不到，抛出 ServletException 异常
				throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
						"View object in servlet with name '" + getServletName() + "'");
			}
		}

		// Delegate to the View object for rendering. —— 委托给View对象进行渲染。
		// 打印日志
		if (logger.isTraceEnabled()) {
			logger.trace("Rendering view [" + view + "] ");
		}
		try {
			/* 3、设置响应的状态码 */

			// 设置响应的状态码（🤔️之前在调用完controller方法之后，已经设置了一次状态码，为什么这里还要进行设置？？）
			if (mv.getStatus() != null) {
				response.setStatus(mv.getStatus().value());
			}

			/* 4、得到view对象之后，通过view对象渲染页面 */

			// ⚠️渲染页面
			// AbstractView
			view.render(mv.getModelInternal()/* 获取modelMap数据 */, request, response);
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Error rendering view [" + view + "]", ex);
			}
			throw ex;
		}
	}

	/**
	 * Translate the supplied request into a default view name.
	 * @param request current HTTP servlet request
	 * @return the view name (or {@code null} if no default found)
	 * @throws Exception if view name translation failed
	 */
	@Nullable
	protected String getDefaultViewName(HttpServletRequest request) throws Exception {
		/**
		 * viewNameTranslator = DefaultRequestToViewNameTranslator
		 */
		// 如果视图名称转换器不为空，就用视图名称转换器去获取一个viewName；否则返回null
		return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
	}

	/**
	 * Resolve the given view name into a View object (to be rendered).
	 * <p>The default implementations asks all ViewResolvers of this dispatcher.
	 * Can be overridden for custom resolution strategies, potentially based on
	 * specific model attributes or request parameters.
	 * @param viewName the name of the view to resolve
	 * @param model the model to be passed to the view
	 * @param locale the current locale
	 * @param request current HTTP servlet request
	 * @return the View object, or {@code null} if none found
	 * @throws Exception if the view cannot be resolved
	 * (typically in case of problems creating an actual View object)
	 * @see ViewResolver#resolveViewName
	 */
	@Nullable
	protected View resolveViewName(String viewName, @Nullable Map<String, Object> model,
			Locale locale, HttpServletRequest request) throws Exception {
		/*

		1、遍历视图解析器，用视图解析器解析视图名称，得到一个视图对象View。
		如果有一个视图解析器可以解析视图名称，得到视图对象就返回该视图对象，不再走后面的视图解析器。

		 */
		if (this.viewResolvers != null) {
			// 遍历视图解析器
			for (ViewResolver viewResolver : this.viewResolvers) {
				/**
				 * 1、常用的视图解析器对象：InternalResourceViewResolver
				 * InternalResourceViewResolver间接继承AbstractCachingViewResolver，所以走的是AbstractCachingViewResolver
				 */
				// 通过视图解析器，解析视图名称，得到视图对象 - View。
				View view = viewResolver.resolveViewName(viewName, locale);
				// 如果有哪个视图解析器，可以解析得到视图对象，就直接返回该视图对象，不再走后面的视图解析器进行解析
				if (view != null) {
					return view;
				}
			}
		}

		return null;
	}

	private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, ex);
		}
		throw ex;
	}

	/**
	 * Restore the request attributes after an include.
	 * @param request current HTTP request
	 * @param attributesSnapshot the snapshot of the request attributes before the include
	 */
	@SuppressWarnings("unchecked")
	private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?, ?> attributesSnapshot) {
		// Need to copy into separate Collection here, to avoid side effects
		// on the Enumeration when removing attributes.
		Set<String> attrsToCheck = new HashSet<>();
		Enumeration<?> attrNames = request.getAttributeNames();
		while (attrNames.hasMoreElements()) {
			String attrName = (String) attrNames.nextElement();
			if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
				attrsToCheck.add(attrName);
			}
		}

		// Add attributes that may have been removed
		attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

		// Iterate over the attributes to check, restoring the original value
		// or removing the attribute, respectively, if appropriate.
		for (String attrName : attrsToCheck) {
			Object attrValue = attributesSnapshot.get(attrName);
			if (attrValue == null) {
				request.removeAttribute(attrName);
			}
			else if (attrValue != request.getAttribute(attrName)) {
				request.setAttribute(attrName, attrValue);
			}
		}
	}

	private static String getRequestUri(HttpServletRequest request) {
		String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
		if (uri == null) {
			uri = request.getRequestURI();
		}
		return uri;
	}

}
