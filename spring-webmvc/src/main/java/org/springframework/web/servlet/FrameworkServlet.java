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

package org.springframework.web.servlet;

import org.springframework.beans.BeanUtils;
import org.springframework.context.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Base servlet for Spring's web framework. Provides integration with
 * a Spring application context, in a JavaBean-based overall solution.
 *
 * <p>This class offers the following functionality:
 * <ul>
 * <li>Manages a {@link org.springframework.web.context.WebApplicationContext
 * WebApplicationContext} instance per servlet. The servlet's configuration is determined
 * by beans in the servlet's namespace.
 * <li>Publishes events on request processing, whether or not a request is
 * successfully handled.
 * </ul>
 *
 * <p>Subclasses must implement {@link #doService} to handle requests. Because this extends
 * {@link HttpServletBean} rather than HttpServlet directly, bean properties are
 * automatically mapped onto it. Subclasses can override {@link #initFrameworkServlet()}
 * for custom initialization.
 *
 * <p>Detects a "contextClass" parameter at the servlet init-param level,
 * falling back to the default context class,
 * {@link org.springframework.web.context.support.XmlWebApplicationContext
 * XmlWebApplicationContext}, if not found. Note that, with the default
 * {@code FrameworkServlet}, a custom context class needs to implement the
 * {@link org.springframework.web.context.ConfigurableWebApplicationContext
 * ConfigurableWebApplicationContext} SPI.
 *
 * <p>Accepts an optional "contextInitializerClasses" servlet init-param that
 * specifies one or more {@link org.springframework.context.ApplicationContextInitializer
 * ApplicationContextInitializer} classes. The managed web application context will be
 * delegated to these initializers, allowing for additional programmatic configuration,
 * e.g. adding property sources or activating profiles against the {@linkplain
 * org.springframework.context.ConfigurableApplicationContext#getEnvironment() context's
 * environment}. See also {@link org.springframework.web.context.ContextLoader} which
 * supports a "contextInitializerClasses" context-param with identical semantics for
 * the "root" web application context.
 *
 * <p>Passes a "contextConfigLocation" servlet init-param to the context instance,
 * parsing it into potentially multiple file paths which can be separated by any
 * number of commas and spaces, like "test-servlet.xml, myServlet.xml".
 * If not explicitly specified, the context implementation is supposed to build a
 * default location from the namespace of the servlet.
 *
 * <p>Note: In case of multiple config locations, later bean definitions will
 * override ones defined in earlier loaded files, at least when using Spring's
 * default ApplicationContext implementation. This can be leveraged to
 * deliberately override certain bean definitions via an extra XML file.
 *
 * <p>The default namespace is "'servlet-name'-servlet", e.g. "test-servlet" for a
 * servlet-name "test" (leading to a "/WEB-INF/test-servlet.xml" default location
 * with XmlWebApplicationContext). The namespace can also be set explicitly via
 * the "namespace" servlet init-param.
 *
 * <p>As of Spring 3.1, {@code FrameworkServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful in Servlet
 * 3.0+ environments, which support programmatic registration of servlet instances. See
 * {@link #FrameworkServlet(WebApplicationContext)} Javadoc for details.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @author Phillip Webb
 * @see #doService
 * @see #setContextClass
 * @see #setContextConfigLocation
 * @see #setContextInitializerClasses
 * @see #setNamespace
 */
@SuppressWarnings("serial")
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {

	/**
	 * Suffix for WebApplicationContext namespaces. If a servlet of this class is
	 * given the name "test" in a context, the namespace used by the servlet will
	 * resolve to "test-servlet".
	 */
	public static final String DEFAULT_NAMESPACE_SUFFIX = "-servlet";

	/**
	 * Default context class for FrameworkServlet.
	 * @see org.springframework.web.context.support.XmlWebApplicationContext
	 */
	public static final Class<?> DEFAULT_CONTEXT_CLASS = XmlWebApplicationContext.class;

	/**
	 * Prefix for the ServletContext attribute for the WebApplicationContext.
	 * The completion is the servlet name.
	 */
	// org.springframework.web.servlet.CONTEXT.
	public static final String SERVLET_CONTEXT_PREFIX = FrameworkServlet.class.getName() + ".CONTEXT.";

	/**
	 * 参数值的分割符。
	 * >>> 单个<init-param>字符串值中，多个值之间的分隔符。例如：
	 * 	<context-param>
	 * 		<param-name>contextConfigLocation</param-name>
	 * 		<param-value>classpath:msb/mvc_01/spring-01.xml,classpath:msb/mvc_01/spring-02.xml</param-value>
	 * 	</context-param>
	 * 	那么对应的value值就是两个：（1）classpath:msb/mvc_01/spring-01.xml；（2）msb/mvc_01/spring-02.xml
	 *
	 * Any number of these characters are considered delimiters between
	 * multiple values in a single init-param String value.
	 *
	 * 任意数量的这些字符都被视为单个<init-param>字符串值中多个值之间的分隔符。
	 */
	private static final String INIT_PARAM_DELIMITERS/* 初始化参数分隔符 */ = ",; \t\n";


	/**
	 * 根据这个属性值，去ServletContext中查找对应的WebApplicationContext
	 *
	 * ServletContext attribute to find the WebApplicationContext in. —— 在ServletContext属性中查找WebApplicationContext。
	 * */
	@Nullable
	private String contextAttribute;

	/** WebApplicationContext implementation class to create. */
	private Class<?> contextClass = DEFAULT_CONTEXT_CLASS;

	/** WebApplicationContext id to assign. */
	@Nullable
	private String contextId;

	/** Namespace for this servlet. */
	@Nullable
	private String namespace;

	/** Explicit context config location. */
	@Nullable
	private String contextConfigLocation;

	/** Actual ApplicationContextInitializer instances to apply to the context. */
	private final List<ApplicationContextInitializer<ConfigurableApplicationContext>> contextInitializers =
			new ArrayList<>();

	/**
	 * 通过init-param设置的以逗号分隔的ApplicationContextInitializer(应用程序上下文初始化器)类名称。
	 *
	 * Comma-delimited ApplicationContextInitializer class names set through init param. */
	@Nullable
	private String contextInitializerClasses;

	/**
	 * 是否将WebApplicationContext设置到ServletContext中的标识
	 * true：设置
	 * false：不设置
	 *
	 * Should we publish the context as a ServletContext attribute?. —— 我们应该将context发布为ServletContext属性吗？
	 * */
	private boolean publishContext = true;

	/** Should we publish a ServletRequestHandledEvent at the end of each request?. */
	private boolean publishEvents = true;

	/** Expose LocaleContext and RequestAttributes as inheritable for child threads?. */
	private boolean threadContextInheritable = false;

	/** Should we dispatch an HTTP OPTIONS request to {@link #doService}?. */
	private boolean dispatchOptionsRequest = false;

	/** Should we dispatch an HTTP TRACE request to {@link #doService}?. */
	private boolean dispatchTraceRequest = false;

	/** Whether to log potentially sensitive info (request params at DEBUG + headers at TRACE). */
	private boolean enableLoggingRequestDetails = false;

	/** WebApplicationContext for this servlet. */
	@Nullable
	private WebApplicationContext webApplicationContext;

	/** If the WebApplicationContext was injected via {@link #setApplicationContext}. */
	private boolean webApplicationContextInjected = false;

	/**
	 * 标记是否接收到ContextRefreshedEvent事件
	 *
	 * Flag used to detect whether onRefresh has already been called. */
	private volatile boolean refreshEventReceived = false;

	/** Monitor for synchronized onRefresh execution. */
	private final Object onRefreshMonitor = new Object();


	/**
	 * Create a new {@code FrameworkServlet} that will create its own internal web
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
	 * indicates which {@link ApplicationContextInitializer} classes should be used to
	 * further configure the internal application context prior to refresh().
	 * @see #FrameworkServlet(WebApplicationContext)
	 */
	public FrameworkServlet() {
	}

	/**
	 * Create a new {@code FrameworkServlet} with the given web application context. This
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
	 * ConfigurableApplicationContext#refresh() refreshed}. If it (a) is an implementation
	 * of {@link ConfigurableWebApplicationContext} and (b) has <strong>not</strong>
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
	 * <li>Any {@link ApplicationContextInitializer ApplicationContextInitializers} specified through the
	 * "contextInitializerClasses" init-param or through the {@link
	 * #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called</li>
	 * </ul>
	 * If the context has already been refreshed or does not implement
	 * {@code ConfigurableWebApplicationContext}, none of the above will occur under the
	 * assumption that the user has performed these actions (or not) per his or her
	 * specific needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public FrameworkServlet(WebApplicationContext webApplicationContext) {
		this.webApplicationContext = webApplicationContext;
	}


	/**
	 * Set the name of the ServletContext attribute which should be used to retrieve the
	 * {@link WebApplicationContext} that this servlet is supposed to use.
	 */
	public void setContextAttribute(@Nullable String contextAttribute) {
		this.contextAttribute = contextAttribute;
	}

	/**
	 * Return the name of the ServletContext attribute which should be used to retrieve the
	 * {@link WebApplicationContext} that this servlet is supposed to use.
	 */
	@Nullable
	public String getContextAttribute() {
		return this.contextAttribute;
	}

	/**
	 * Set a custom context class. This class must be of type
	 * {@link org.springframework.web.context.WebApplicationContext}.
	 * <p>When using the default FrameworkServlet implementation,
	 * the context class must also implement the
	 * {@link org.springframework.web.context.ConfigurableWebApplicationContext}
	 * interface.
	 * @see #createWebApplicationContext
	 */
	public void setContextClass(Class<?> contextClass) {
		this.contextClass = contextClass;
	}

	/**
	 * Return the custom context class.
	 */
	public Class<?> getContextClass() {
		// XmlWebApplicationContext.class
		return this.contextClass;
	}

	/**
	 * Specify a custom WebApplicationContext id,
	 * to be used as serialization id for the underlying BeanFactory.
	 */
	public void setContextId(@Nullable String contextId) {
		this.contextId = contextId;
	}

	/**
	 * Return the custom WebApplicationContext id, if any.
	 */
	@Nullable
	public String getContextId() {
		return this.contextId;
	}

	/**
	 * Set a custom namespace for this servlet,
	 * to be used for building a default context config location.
	 */
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	/**
	 * Return the namespace for this servlet, falling back to default scheme if
	 * no custom namespace was set: e.g. "test-servlet" for a servlet named "test".
	 */
	public String getNamespace() {
		return (this.namespace != null ? this.namespace : getServletName() + DEFAULT_NAMESPACE_SUFFIX);
	}

	/**
	 * Set the context config location explicitly, instead of relying on the default
	 * location built from the namespace. This location string can consist of
	 * multiple locations separated by any number of commas and spaces.
	 */
	public void setContextConfigLocation(@Nullable String contextConfigLocation) {
		this.contextConfigLocation = contextConfigLocation;
	}

	/**
	 * Return the explicit context config location, if any.
	 */
	@Nullable
	public String getContextConfigLocation() {
		return this.contextConfigLocation;
	}

	/**
	 * Specify which {@link ApplicationContextInitializer} instances should be used
	 * to initialize the application context used by this {@code FrameworkServlet}.
	 * @see #configureAndRefreshWebApplicationContext
	 * @see #applyInitializers
	 */
	@SuppressWarnings("unchecked")
	public void setContextInitializers(@Nullable ApplicationContextInitializer<?>... initializers) {
		if (initializers != null) {
			for (ApplicationContextInitializer<?> initializer : initializers) {
				this.contextInitializers.add((ApplicationContextInitializer<ConfigurableApplicationContext>) initializer);
			}
		}
	}

	/**
	 * Specify the set of fully-qualified {@link ApplicationContextInitializer} class
	 * names, per the optional "contextInitializerClasses" servlet init-param.
	 * @see #configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext)
	 * @see #applyInitializers(ConfigurableApplicationContext)
	 */
	public void setContextInitializerClasses(String contextInitializerClasses) {
		this.contextInitializerClasses = contextInitializerClasses;
	}

	/**
	 * Set whether to publish this servlet's context as a ServletContext attribute,
	 * available to all objects in the web container. Default is "true".
	 * <p>This is especially handy during testing, although it is debatable whether
	 * it's good practice to let other application objects access the context this way.
	 */
	public void setPublishContext(boolean publishContext) {
		this.publishContext = publishContext;
	}

	/**
	 * Set whether this servlet should publish a ServletRequestHandledEvent at the end
	 * of each request. Default is "true"; can be turned off for a slight performance
	 * improvement, provided that no ApplicationListeners rely on such events.
	 * @see org.springframework.web.context.support.ServletRequestHandledEvent
	 */
	public void setPublishEvents(boolean publishEvents) {
		this.publishEvents = publishEvents;
	}

	/**
	 * Set whether to expose the LocaleContext and RequestAttributes as inheritable
	 * for child threads (using an {@link java.lang.InheritableThreadLocal}).
	 * <p>Default is "false", to avoid side effects on spawned background threads.
	 * Switch this to "true" to enable inheritance for custom child threads which
	 * are spawned during request processing and only used for this request
	 * (that is, ending after their initial task, without reuse of the thread).
	 * <p><b>WARNING:</b> Do not use inheritance for child threads if you are
	 * accessing a thread pool which is configured to potentially add new threads
	 * on demand (e.g. a JDK {@link java.util.concurrent.ThreadPoolExecutor}),
	 * since this will expose the inherited context to such a pooled thread.
	 */
	public void setThreadContextInheritable(boolean threadContextInheritable) {
		this.threadContextInheritable = threadContextInheritable;
	}

	/**
	 * Set whether this servlet should dispatch an HTTP OPTIONS request to
	 * the {@link #doService} method.
	 * <p>Default in the {@code FrameworkServlet} is "false", applying
	 * {@link javax.servlet.http.HttpServlet}'s default behavior (i.e.enumerating
	 * all standard HTTP request methods as a response to the OPTIONS request).
	 * Note however that as of 4.3 the {@code DispatcherServlet} sets this
	 * property to "true" by default due to its built-in support for OPTIONS.
	 * <p>Turn this flag on if you prefer OPTIONS requests to go through the
	 * regular dispatching chain, just like other HTTP requests. This usually
	 * means that your controllers will receive those requests; make sure
	 * that those endpoints are actually able to handle an OPTIONS request.
	 * <p>Note that HttpServlet's default OPTIONS processing will be applied
	 * in any case if your controllers happen to not set the 'Allow' header
	 * (as required for an OPTIONS response).
	 */
	public void setDispatchOptionsRequest(boolean dispatchOptionsRequest) {
		this.dispatchOptionsRequest = dispatchOptionsRequest;
	}

	/**
	 * Set whether this servlet should dispatch an HTTP TRACE request to
	 * the {@link #doService} method.
	 * <p>Default is "false", applying {@link javax.servlet.http.HttpServlet}'s
	 * default behavior (i.e. reflecting the message received back to the client).
	 * <p>Turn this flag on if you prefer TRACE requests to go through the
	 * regular dispatching chain, just like other HTTP requests. This usually
	 * means that your controllers will receive those requests; make sure
	 * that those endpoints are actually able to handle a TRACE request.
	 * <p>Note that HttpServlet's default TRACE processing will be applied
	 * in any case if your controllers happen to not generate a response
	 * of content type 'message/http' (as required for a TRACE response).
	 */
	public void setDispatchTraceRequest(boolean dispatchTraceRequest) {
		this.dispatchTraceRequest = dispatchTraceRequest;
	}

	/**
	 * Whether to log request params at DEBUG level, and headers at TRACE level.
	 * Both may contain sensitive information.
	 * <p>By default set to {@code false} so that request details are not shown.
	 * @param enable whether to enable or not
	 * @since 5.1
	 */
	public void setEnableLoggingRequestDetails(boolean enable) {
		this.enableLoggingRequestDetails = enable;
	}

	/**
	 * Whether logging of potentially sensitive, request details at DEBUG and
	 * TRACE level is allowed.
	 * @since 5.1
	 */
	public boolean isEnableLoggingRequestDetails() {
		return this.enableLoggingRequestDetails;
	}

	/**
	 * Called by Spring via {@link ApplicationContextAware} to inject the current
	 * application context. This method allows FrameworkServlets to be registered as
	 * Spring beans inside an existing {@link WebApplicationContext} rather than
	 * {@link #findWebApplicationContext() finding} a
	 * {@link org.springframework.web.context.ContextLoaderListener bootstrapped} context.
	 * <p>Primarily added to support use in embedded servlet containers.
	 * @since 4.0
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		if (this.webApplicationContext == null && applicationContext instanceof WebApplicationContext) {
			this.webApplicationContext = (WebApplicationContext) applicationContext;
			this.webApplicationContextInjected = true;
		}
	}


	/**
	 * Overridden method of {@link HttpServletBean}, invoked after any bean properties
	 * have been set. Creates this servlet's WebApplicationContext.
	 */
	@Override
	protected final void initServletBean() throws ServletException {
		getServletContext().log("Initializing Spring "/* 初始化spring */ + getClass().getSimpleName() + " '" + getServletName() + "'");
		if (logger.isInfoEnabled()) {
			logger.info("Initializing Servlet '" + getServletName() + "'");
		}
		// 记录启动时间
		long startTime = System.currentTimeMillis();

		try {
			/* 1、️初始化spring mvc容器 */
			// 创建或刷新WebApplicationContext实例，并对servlet功能所使用的变量进行初始化
			this.webApplicationContext = initWebApplicationContext();

			// 空实现。模版方法。
			initFrameworkServlet();
		}
		catch (ServletException | RuntimeException ex) {
			logger.error("Context initialization failed", ex);
			throw ex;
		}

		if (logger.isDebugEnabled()) {
			String value = this.enableLoggingRequestDetails ?
					"shown which may lead to unsafe logging of potentially sensitive data" :
					"masked to prevent unsafe logging of potentially sensitive data";
			logger.debug("enableLoggingRequestDetails='" + this.enableLoggingRequestDetails +
					"': request parameters and headers will be " + value);
		}

		if (logger.isInfoEnabled()) {
			logger.info("Completed initialization in " + (System.currentTimeMillis() - startTime) + " ms");
		}
	}

	/**
	 * 此处同学们需要知道一个原理，所有的前后端交互的框架都是以servlet为基础的，所以在使用springmvc的时候，默认会把自己的容器设置成ServletContext
	 * 的属性，默认根容器的key为WebApplicaitonContext.Root,定义在WebApplicationContext中，所以在获取的时候只需要调用ServletContext.getAttribute即可
	 *
	 * Initialize and publish the WebApplicationContext for this servlet.
	 * <p>Delegates to {@link #createWebApplicationContext} for actual creation
	 * of the context. Can be overridden in subclasses.
	 * @return the WebApplicationContext instance
	 * @see #FrameworkServlet(WebApplicationContext)
	 * @see #setContextClass
	 * @see #setContextConfigLocation
	 */
	protected WebApplicationContext initWebApplicationContext() {

		/* 1、获取根容器，也就是spring容器 */

		// 获得根WebApplicationContext对象 —— 获取到的是spring容器
		WebApplicationContext/* Web应用程序上下文 */ rootContext =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext());

		/*

		2、获取当前容器（WebApplicationContext），也就是spring mvc容器

		题外：下面是获取spring mvc容器的几种方式。根据不同的配置方式来选择合理的方式获取spring mvc容器

		*/

		WebApplicationContext wac = null;

		/*

		2.1、如果构造方法中已经传入WebApplicationContext属性，则直接使用。一般很少采用这种方式。

		题外：此方式主要用于servlet3.0之后的环境，通过ServletContext.addServlet()注册servlet时，
		此时就可以在创建FrameworkServlet，和其子类的时候通过构造方法传递已经准备好的WebApplicationContext，
		然后就会把该值赋予给this.webApplicationContext。也只有这种情况，this.webApplicationContext才不会为空。

		*/
		if (this.webApplicationContext != null) {
			// A context instance was injected at construction time -> use it
			wac = this.webApplicationContext;
			// 如果是ConfigurationWebApplicationContext类型，并且未激活，则进行初始化
			if (wac instanceof ConfigurableWebApplicationContext) {
				ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) wac;
				// 判断是否激活
				if (!cwac.isActive()) {
					/* 未激活 */

					// The context has not yet been refreshed -> provide services such as
					// setting the parent context, setting the application context id, etc
					if (cwac.getParent() == null) {
						// The context instance was injected without an explicit parent -> set
						// the root application context (if any; may be null) as the parent
						// ⚠️如果父容器不存在，那么就设置父容器为根容器
						cwac.setParent(rootContext);
					}
					// ⚠️如果WebApplicationContext未激活，那么就配置和刷新WebApplicationContext
					configureAndRefreshWebApplicationContext(cwac);
				}
			}
		}

		/*

		2.2、如果web.xml中配置了contextAttribute上下文属性值，则从ServletContext中，获取contextAttribute上下文属性值对应的WebApplicationContext

		*/

		if (wac == null) {
			// No context instance was injected at construction time -> see if one
			// has been registered in the servlet context. If one exists, it is assumed
			// that the parent context (if any) has already been set and that the
			// user has performed any initialization such as setting the context id
			// 上面的翻译：在构建时没有注入上下文实例 -> 查看是否已在 servlet 上下文中注册。
			// >>> 如果存在，则假定已设置父上下文（如果有）并且用户已执行任何初始化，例如设置上下文 ID
			/**
			 * 1、从servletContext获取对应的WebApplicationContext对象
			 * 此方式需要在配置Servlet的时候，将servletContext中的WebApplicationContext的name配置到contextAttribute属性就可以，例如：
			 * <servlet>
			 * <servlet-name>mvc-test</servlet-name>
			 * <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
			 * 	<init-param>
			 * 		<param-name>contextAttribute</param-name>
			 * 		<param-value>mashibing</param-value>
			 * 	</init-param>
			 * 	<load-on-startup>1</load-on-startup>
			 * </servlet>
			 */
			// 根据配置的contextAttribute上下文属性值，从ServletContext中，查找WebApplicationContext
			wac = findWebApplicationContext();
		}

		/*

		2.3、当前面两种方式都没有获取到WebApplicationContext的时候，则去创建一个WebApplicationContext，并且配置和刷新WebApplicationContext

		⚠️题外：在内部刷新容器的时候，会发布容器刷新完成事件，该事件会触发spring mvc内置的9大组件的初始化！如果收到了容器刷新事件，会设置 refreshEventReceived = true

		*/

		if (wac == null) {
			// No context instance is defined for this servlet -> create a local one
			// 上面翻译：没有为此 servlet 定义上下文实例 -> 创建一个本地实例

			// 当前面两种方式都没有获取到WebApplicationContext的时候，则去创建一个WebApplicationContext对象
			// 题外：一般情况下都是使用这样的方式
			wac = createWebApplicationContext(rootContext/* 父容器 */);
		}

		/*

		3、判断是否收到了刷新事件。如果没有刷新，那么就手动触发刷新，初始化spring mvc内置的9大组件

		只有在WebApplicationContext不是具有支持刷新的ConfigurableApplicationContext，所以不会发送刷新事件！
		或者通过构造器注入的WebApplicationContext已经被刷新，但是没有发送刷新事件！
		所以在这里手动触发刷新。

		*/

		// 将contextRefreshedEvent事件没有触发时调用此方法，模板方法，可以在子类重写
		if (!this.refreshEventReceived/* 收到刷新事件 */) {
			/* 如果没收到刷新事件 */

			// Either the context is not a ConfigurableApplicationContext with refresh
			// support or the context injected at construction time had already been
			// refreshed -> trigger initial onRefresh manually here.
			// 上面翻译：上下文不是具有支持刷新的ConfigurableApplicationContext，或者在构造时注入的上下文已经被刷新 -> 就在此处手动触发初始onRefresh。

			synchronized (this.onRefreshMonitor) {
				// ⚠️内部初始化了spring mvc内置的9大组件
				// Dispatcher#onRefresh()
				onRefresh(wac);
			}
		}

		/* 4、往ServletContext里面设置WebApplicationContext */

		// 判断是否将WebApplicationContext设置到ServletContext中
		if (this.publishContext) {
			// Publish the context as a servlet context attribute. —— 将context发布为servlet的context attribute
			String attrName = getServletContextAttributeName();
			getServletContext().setAttribute(attrName, wac);
		}

		return wac;
	}

	/**
	 * Retrieve a {@code WebApplicationContext} from the {@code ServletContext}
	 * attribute with the {@link #setContextAttribute configured name}. The
	 * {@code WebApplicationContext} must have already been loaded and stored in the
	 * {@code ServletContext} before this servlet gets initialized (or invoked).
	 * <p>Subclasses may override this method to provide a different
	 * {@code WebApplicationContext} retrieval strategy.
	 * @return the WebApplicationContext for this servlet, or {@code null} if not found
	 * @see #getContextAttribute()
	 */
	@Nullable
	protected WebApplicationContext findWebApplicationContext() {
		/* 1、获取配置的contextAttribute属性值（上下文属性值） */
		// 题外：一般不会去配置
		String attrName = getContextAttribute();
		// 判断有没有配置contextAttribute属性值
		if (attrName == null) {
			// 没有配置contextAttribute属性值就直接返回null
			return null;
		}

		/* 2、根据配置的contextAttribute上下文属性值，去ServletContext中，获取到对应的WebApplicationContext */
		WebApplicationContext wac =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext(), attrName);

		/* 3、如果ServletContext中不存在contextAttribute上下文属性值对应的WebApplicationContext，则抛出异常IllegalStateException */
		if (wac == null) {
			throw new IllegalStateException("No WebApplicationContext found: initializer not registered?"/* 未找到WebApplicationContext：初始化程序未注册？ */);
		}

		/* 4、返回找到的WebApplicationContext */
		return wac;
	}

	/**
	 * Instantiate the WebApplicationContext for this servlet, either a default
	 * {@link org.springframework.web.context.support.XmlWebApplicationContext}
	 * or a {@link #setContextClass custom context class}, if set.
	 * <p>This implementation expects custom contexts to implement the
	 * {@link org.springframework.web.context.ConfigurableWebApplicationContext}
	 * interface. Can be overridden in subclasses.
	 * <p>Do not forget to register this servlet instance as application listener on the
	 * created context (for triggering its {@link #onRefresh callback}, and to call
	 * {@link org.springframework.context.ConfigurableApplicationContext#refresh()}
	 * before returning the context instance.
	 * @param parent the parent ApplicationContext to use, or {@code null} if none
	 * @return the WebApplicationContext for this servlet
	 * @see org.springframework.web.context.support.XmlWebApplicationContext
	 */
	protected WebApplicationContext createWebApplicationContext(@Nullable ApplicationContext parent/* 父容器 */) {
		/* 1、实例化WebApplicationContext */

		// 获取servlet的初始化参数contextClass,如果没有配置，默认为XmlWebApplicationContext.class
		Class<?> contextClass = getContextClass();
		// 如果非ConfigurableWebApplicationContext类型，抛出ConfigurableWebApplicationContext异常
		if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
			throw new ApplicationContextException(
					"Fatal initialization error in servlet with name '" + getServletName() +
					"': custom WebApplicationContext class [" + contextClass.getName() +
					"] is not of type ConfigurableWebApplicationContext");
		}
		// 通过反射方式实例化contextClass
		ConfigurableWebApplicationContext wac =
				(ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);

		/* 2、设置环境对象 */
		wac.setEnvironment(getEnvironment());

		/* 3、设置当前容器的父容器 */
		// 设置父容器
		// parent为在ContextLoaderListener中创建的实例，在ContextLoaderListener加载的时候初始化的WebApplicationContext类型实例
		wac.setParent(parent);

		/* 4、设置spring mvc配置文件的路径 */
		/**
		 * 例如：
		 * 	<servlet>
		 * 		<servlet-name>mvc-test</servlet-name>
		 * 		<servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
		 * 		<!--	spring mvc的配置文件位置	-->
		 * 		<init-param>
		 * 			<param-name>contextConfigLocation</param-name>
		 * 			<param-value>classpath:msb/mvc_01/mvc-01.xml</param-value>
		 * 		</init-param>
		 * 		<load-on-startup>1</load-on-startup>
		 * 	</servlet>
		 * 	那么configLocation = classpath:msb/mvc_01/mvc-01.xml
		 */
		// 获取contextConfigLocation属性，配置在servlet初始化参数中
		String configLocation = getContextConfigLocation();
		if (configLocation != null) {
			// 将设置的contextConfigLocation参数传给wac,默认传入WEB-INFO/servletName-servlet.xml
			wac.setConfigLocation(configLocation);
		}

		/* 5、初始化spring mvc容器 */
		// ⚠️配置和刷新WebApplicationContext
		configureAndRefreshWebApplicationContext(wac);

		return wac;
	}

	protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac) {
		/* 1、设置容器的id值，作为当前容器的唯一标识 */
		// 如果wac使用了默认编号，则重新设置id属性
		if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
			/* 如果相等 */

			// The application context id is still set to its original default value
			// -> assign a more useful id based on available information
			// 使用contextId属性
			if (this.contextId != null) {
				wac.setId(this.contextId);
			}
			// 自动生成
			else {
				// Generate default id... —— 生成默认标识...
				wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
						ObjectUtils.getDisplayString(getServletContext().getContextPath()) + '/' + getServletName());
			}
		}

		/* 2、往WebApplicationContext中，设置servletContext、servletConfig、namespace、以及添加"初始化spring mvc九大组件的监听器" */

		/**
		 * 1、ServletContext和ServletConfig的区别
		 * （1）ServletContext：全局应用程序共享对象，官方叫servlet上下文。整个工程所有的servlet的上下文对象，包含整个web.xml文件中的所有配置
		 * >>> ⚠️服务器会为每一个工程创建一个对象，这个对象就是ServletContext对象。这个对象全局唯一，而且工程内部的所有servlet都共享这个对象。所以叫全局应用程序共享对象。
		 * >>> web.xml里面配置的所有项，都会放到ServletContext里面（题外：web.xml里面配置的所有项，就表示上下文）
		 *
		 * （2）ServletConfig：一个Servlet的配置对象。表示的是web.xml中配置的某个servlet的配置。
		 */
		// 设置wac的servletContext、servletConfig、namespace属性
		wac.setServletContext(getServletContext());
		wac.setServletConfig(getServletConfig());
		wac.setNamespace(getNamespace());
		// ⚠️该监听器用于初始化spring mvc九大组件
		// 添加监听器sourceFilteringListener到wac中,实际监听的是ContextRefreshListener所监听的事件，监听ContextRefreshedEvent事件，
		// 当接收到消息之后会调用onApplicationEvent方法，调用onRefresh方法，并将refreshEventReceived标志设置为true，表示已经refresh过
		wac.addApplicationListener(new SourceFilteringListener/* 资源过滤器的监听器 */(wac, new ContextRefreshListener()/* 容器刷新完毕的监听器 */));

		/* 3、加载我们对应的系统属性 */

		// The wac environment's #initPropertySources will be called in any case when the context
		// is refreshed; do it eagerly here to ensure servlet property sources are in place for
		// use in any post-processing or initialization that occurs below prior to #refresh
		// 上面的翻译：wac环境的initPropertySources在任何情况下都会在上下文刷新时被调用；在这里急切地做，以确保servlet属性源在刷新之前发生在下面的任何后处理或初始化中使用
		// 获取环境对象并且添加相关的属性
		ConfigurableEnvironment env = wac.getEnvironment();
		if (env instanceof ConfigurableWebEnvironment) {
			// 对环境对象，进行初始化属性资源的配置工作
			// 初始化属性资源
			((ConfigurableWebEnvironment) env).initPropertySources(getServletContext(), getServletConfig());
		}

		/* 4、钩子方法，用作后置处理WebApplicationContext */
		postProcessWebApplicationContext(wac);

		/* 5、获取web.xml中配置的初始化器，进行实例化、然后执行初始化器 */
		applyInitializers/* 应用初始化器 */(wac);

		/* 6、初始化spring mvc容器 */
		wac.refresh();
	}

	/**
	 * Instantiate the WebApplicationContext for this servlet, either a default
	 * {@link org.springframework.web.context.support.XmlWebApplicationContext}
	 * or a {@link #setContextClass custom context class}, if set.
	 * Delegates to #createWebApplicationContext(ApplicationContext).
	 * @param parent the parent WebApplicationContext to use, or {@code null} if none
	 * @return the WebApplicationContext for this servlet
	 * @see org.springframework.web.context.support.XmlWebApplicationContext
	 * @see #createWebApplicationContext(ApplicationContext)
	 */
	protected WebApplicationContext createWebApplicationContext(@Nullable WebApplicationContext parent) {
		return createWebApplicationContext((ApplicationContext) parent/* 父容器 */);
	}

	/**
	 * Post-process the given WebApplicationContext before it is refreshed
	 * and activated as context for this servlet.
	 * <p>The default implementation is empty. {@code refresh()} will
	 * be called automatically after this method returns.
	 * <p>Note that this method is designed to allow subclasses to modify the application
	 * context, while {@link #initWebApplicationContext} is designed to allow
	 * end-users to modify the context through the use of
	 * {@link ApplicationContextInitializer ApplicationContextInitializers}.
	 * @param wac the configured WebApplicationContext (not refreshed yet)
	 * @see #createWebApplicationContext
	 * @see #initWebApplicationContext
	 * @see ConfigurableWebApplicationContext#refresh()
	 */
	protected void postProcessWebApplicationContext(ConfigurableWebApplicationContext wac) {
	}

	/**
	 * Delegate the WebApplicationContext before it is refreshed to any
	 * {@link ApplicationContextInitializer} instances specified by the
	 * "contextInitializerClasses" servlet init-param.
	 * <p>See also {@link #postProcessWebApplicationContext}, which is designed to allow
	 * subclasses (as opposed to end-users) to modify the application context, and is
	 * called immediately before this method.
	 * @param wac the configured WebApplicationContext (not refreshed yet)
	 * @see #createWebApplicationContext
	 * @see #postProcessWebApplicationContext
	 * @see ConfigurableApplicationContext#refresh()
	 */
	protected void applyInitializers/* 应用初始化器(其实就是，执行初始化器) */(ConfigurableApplicationContext wac) {
		/* 1、获取初始化器 */

		/* 1.1、获取web.xml中配置的globalInitializerClasses属性对应的初始化器！—— 全局初始化器 */

		// 获取web.xml中配置的globalInitializerClasses属性值。
		// globalInitializerClasses属性值对应的是一些初始化器，有分割符号进行分割。
		String globalClassNames = getServletContext().getInitParameter(
				ContextLoader.GLOBAL_INITIALIZER_CLASSES_PARAM/* globalInitializerClasses *//* 全局初始化器类 */);
		if (globalClassNames != null) {
			for (String className : StringUtils.tokenizeToStringArray(globalClassNames, INIT_PARAM_DELIMITERS/* 参数值的分割符 */)) {
				// 添加初始化器
				this.contextInitializers.add(loadInitializer(className, wac));
			}
		}

		/* 1.2、获取<init-param>设置的以逗号分隔的初始化器 —— ApplicationContextInitializer(应用程序上下文初始化器) */

		// 获取<init-param>设置的以逗号分隔的ApplicationContextInitializer(应用程序上下文初始化器)类名称
		// contextInitializerClasses对应的是一些初始化器，有分割符号进行分割。
		if (this.contextInitializerClasses != null) {
			for (String className : StringUtils.tokenizeToStringArray(this.contextInitializerClasses, INIT_PARAM_DELIMITERS/* 参数值的分割符 */)) {
				// 添加初始化器
				this.contextInitializers.add(loadInitializer(className, wac));
			}
		}

		/* 2、对初始化器集合进行排序 */

		// 对初始化器，进行排序
		AnnotationAwareOrderComparator.sort(this.contextInitializers);

		/* 3、遍历所有初始化器，执行每个初始化器的初始化方法 */

		// 遍历所有初始化器，执行每个初始化器的初始化方法
		for (ApplicationContextInitializer<ConfigurableApplicationContext> initializer : this.contextInitializers) {
			// 执行初始化器的初始化方法
			initializer.initialize(wac);
		}

	}

	@SuppressWarnings("unchecked")
	private ApplicationContextInitializer<ConfigurableApplicationContext> loadInitializer(
			String className, ConfigurableApplicationContext wac) {
		try {
			Class<?> initializerClass = ClassUtils.forName(className, wac.getClassLoader());
			Class<?> initializerContextClass =
					GenericTypeResolver.resolveTypeArgument(initializerClass, ApplicationContextInitializer.class);
			if (initializerContextClass != null && !initializerContextClass.isInstance(wac)) {
				throw new ApplicationContextException(String.format(
						"Could not apply context initializer [%s] since its generic parameter [%s] " +
						"is not assignable from the type of application context used by this " +
						"framework servlet: [%s]", initializerClass.getName(), initializerContextClass.getName(),
						wac.getClass().getName()));
			}
			return BeanUtils.instantiateClass(initializerClass, ApplicationContextInitializer.class);
		}
		catch (ClassNotFoundException ex) {
			throw new ApplicationContextException(String.format("Could not load class [%s] specified " +
					"via 'contextInitializerClasses' init-param", className), ex);
		}
	}

	/**
	 * Return the ServletContext attribute name for this servlet's WebApplicationContext.
	 * <p>The default implementation returns
	 * {@code SERVLET_CONTEXT_PREFIX + servlet name}.
	 * @see #SERVLET_CONTEXT_PREFIX
	 * @see #getServletName
	 */
	public String getServletContextAttributeName() {
		return SERVLET_CONTEXT_PREFIX/* org.springframework.web.servlet.CONTEXT. */ + getServletName();
	}

	/**
	 * Return this servlet's WebApplicationContext.
	 */
	@Nullable
	public final WebApplicationContext getWebApplicationContext() {
		return this.webApplicationContext;
	}


	/**
	 * This method will be invoked after any bean properties have been set and
	 * the WebApplicationContext has been loaded. The default implementation is empty;
	 * subclasses may override this method to perform any initialization they require.
	 * @throws ServletException in case of an initialization exception
	 */
	protected void initFrameworkServlet() throws ServletException {
	}

	/**
	 * Refresh this servlet's application context, as well as the
	 * dependent state of the servlet.
	 * @see #getWebApplicationContext()
	 * @see org.springframework.context.ConfigurableApplicationContext#refresh()
	 */
	public void refresh() {
		WebApplicationContext wac = getWebApplicationContext();
		if (!(wac instanceof ConfigurableApplicationContext)) {
			throw new IllegalStateException("WebApplicationContext does not support refresh: " + wac);
		}
		((ConfigurableApplicationContext) wac).refresh();
	}

	/**
	 * Callback that receives refresh events from this servlet's WebApplicationContext.
	 * <p>The default implementation calls {@link #onRefresh},
	 * triggering a refresh of this servlet's context-dependent state.
	 * @param event the incoming ApplicationContext event
	 */
	public void onApplicationEvent(ContextRefreshedEvent event) {
		// 标记"收到刷新事件"为true
		this.refreshEventReceived/* 收到刷新事件 */ = true;
		synchronized (this.onRefreshMonitor) {
			// 处理事件中的ApplicationContext对象，
			// 默认空实现。子类DispatcherServlet#onRefresh()会实现，里面初始化了️spring mvc内置的9大组件
			onRefresh(event.getApplicationContext()/* 获取到的是：XmlWebApplicationContext */);
		}
	}

	/**
	 * Template method which can be overridden to add servlet-specific refresh work.
	 * Called after successful context refresh.
	 * <p>This implementation is empty.
	 * @param context the current WebApplicationContext
	 * @see #refresh()
	 */
	protected void onRefresh(ApplicationContext context) {
		// For subclasses: do nothing by default.
	}

	/**
	 * Close the WebApplicationContext of this servlet.
	 * @see org.springframework.context.ConfigurableApplicationContext#close()
	 */
	@Override
	public void destroy() {
		getServletContext().log("Destroying Spring FrameworkServlet '" + getServletName() + "'");
		// Only call close() on WebApplicationContext if locally managed...
		if (this.webApplicationContext instanceof ConfigurableApplicationContext && !this.webApplicationContextInjected) {
			((ConfigurableApplicationContext) this.webApplicationContext).close();
		}
	}


	/**
	 * Override the parent class implementation in order to intercept PATCH requests. —— 覆盖父类实现以拦截 PATCH 请求。
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		// 获取请求方法
		HttpMethod httpMethod = HttpMethod.resolve(request.getMethod());

		// 处理PATCH请求
		if (httpMethod == HttpMethod.PATCH || httpMethod == null) {
			processRequest(request, response);
		}
		// 处理其它类型的请求
		else {
			// ⚠️
			// 题外：里面是根据请求方式调用对应的doGet()、doPost()之类的方法，像这些方法，当前类都有重写；
			// doGet()、doPost()之类的方法，最终都是调用到processRequest()方法进行统一处理
			super.service(request, response);
		}
	}

	/**
	 * Delegate GET requests to processRequest/doService.
	 * <p>Will also be invoked by HttpServlet's default implementation of {@code doHead},
	 * with a {@code NoBodyResponse} that just captures the content length.
	 * @see #doService
	 * @see #doHead
	 */
	@Override
	protected final void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate POST requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate PUT requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doPut(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate DELETE requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate OPTIONS requests to {@link #processRequest}, if desired.
	 * <p>Applies HttpServlet's standard OPTIONS processing otherwise,
	 * and also if there is still no 'Allow' header set after dispatching.
	 * @see #doService
	 */
	@Override
	protected void doOptions(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		// 如果 dispatcherOptionsRequest 为true，则处理该请求，默认为true
		if (this.dispatchOptionsRequest || CorsUtils.isPreFlightRequest(request)) {
			// 处理请求
			processRequest(request, response);
			if (response.containsHeader("Allow")) {
				// 如果响应Header包含allow,则不需要提交给父方法处理
				// Proper OPTIONS response coming from a handler - we're done.
				return;
			}
		}

		// Use response wrapper in order to always add PATCH to the allowed methods
		// 调用父方法，并在响应Header的allow增加patch的值
		super.doOptions(request, new HttpServletResponseWrapper(response) {
			@Override
			public void setHeader(String name, String value) {
				if ("Allow".equals(name)) {
					value = (StringUtils.hasLength(value) ? value + ", " : "") + HttpMethod.PATCH.name();
				}
				super.setHeader(name, value);
			}
		});
	}

	/**
	 * Delegate TRACE requests to {@link #processRequest}, if desired.
	 * <p>Applies HttpServlet's standard TRACE processing otherwise.
	 * @see #doService
	 */
	@Override
	protected void doTrace(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		// 如果 dispatchTraceRequest 为 true ，则处理该请求，默认为 false
		if (this.dispatchTraceRequest) {
			processRequest(request, response);
			// 如果响应的内容类型为 "message/http" ，则不需要交给父方法处理
			if ("message/http".equals(response.getContentType())) {
				// Proper TRACE response coming from a handler - we're done.
				return;
			}
		}
		// 调用父方法
		super.doTrace(request, response);
	}

	/**
 	 * 1、注意：为什么doGet()、doPost()、doPut()、doDelete()调用的都是processRequest()这个方法？
	 * 不同的请求类型，只是在解析参数的时候不一样，但是其它方面的处理逻辑都是一样的，所以提取出公共的逻辑放在一个方法内
	 *
	 * Process this request, publishing an event regardless of the outcome.
	 * <p>The actual event handling is performed by the abstract
	 * {@link #doService} template method.
	 */
	protected final void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		/* 1、记录当前时间，用于计算"处理请求花费的时间" */
		long startTime = System.currentTimeMillis();
		// 记录异常的对象，用于保存整个请求处理过程中发生的异常，方便后续处理
		Throwable failureCause = null;

		/* 2、LocaleContext、RequestAttributes */

		// 获取LocaleContextHolder中原来保存的LocaleContext(LocaleContext：保存的本地化信息)
		LocaleContext/* 语言环境 */ previousLocaleContext/* 以前的语言环境上下文 */ = LocaleContextHolder.getLocaleContext();
		// ⚠️获取当前请求的LocaleContext = SimpleLocaleContext
		LocaleContext localeContext = buildLocaleContext(request);

		// 获取RequestContextHolder中原来保存的RequestAttribute(RequestAttribute：管理request和session的属性)
		RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes()/* 获取请求属性值 */;
		// ⚠️获取当前请求的ServletRequestAttribute
		ServletRequestAttributes requestAttributes = buildRequestAttributes(request, response, previousAttributes);

		/* 3、异步管理器 */

		// 获取异步管理器
		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
		// 往拦截器集合里面添加拦截器
		asyncManager.registerCallableInterceptor/* 注册可调用拦截器 */(FrameworkServlet.class.getName(), new RequestBindingInterceptor());

		// 里面就设置了两个属性值：将当前请求的LocaleContext和ServletRequestAttribute设置到LocaleContextHolder和RequestContextHolder
		initContextHolders(request, localeContext, requestAttributes);

		/* 4、执行真正的逻辑 */

		// 上面只是做一些准备工作，获取了一些参数、语言方式，仅此而已

		try {
			// ⚠️执行真正的逻辑
			doService(request, response);
		}
		catch (ServletException | IOException ex) {
			// 记录抛出的异常
			failureCause = ex;
			throw ex;
		}
		catch (Throwable ex) {
			// 记录抛出的异常
			failureCause = ex;

			// 嵌套的servlet异常
			throw new NestedServletException("Request processing failed", ex);
		}

		finally {
			/* 5、恢复原来的LocaleContext和ServletRequestAttributes到LocaleContextHolder和RequestContextHolder中 */
			resetContextHolders(request, previousLocaleContext, previousAttributes);
			if (requestAttributes != null) {
				requestAttributes.requestCompleted();
			}
			// 如果日志级别为debug，则打印请求日志
			logResult(request, response, failureCause, asyncManager);

			/* 6、发布ServletRequestHandledEvent请求处理完成事件 */
			publishRequestHandledEvent(request, response, startTime, failureCause);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's
	 * primary locale as current locale.
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext, or {@code null} if none to bind
	 * @see LocaleContextHolder#setLocaleContext
	 */
	@Nullable
	protected LocaleContext buildLocaleContext(HttpServletRequest request) {
		return new SimpleLocaleContext(request.getLocale());
	}

	/**
	 * Build ServletRequestAttributes for the given request (potentially also
	 * holding a reference to the response), taking pre-bound attributes
	 * (and their type) into consideration.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param previousAttributes pre-bound RequestAttributes instance, if any
	 * @return the ServletRequestAttributes to bind, or {@code null} to preserve
	 * the previously bound instance (or not binding any, if none bound before)
	 * @see RequestContextHolder#setRequestAttributes
	 */
	@Nullable
	protected ServletRequestAttributes buildRequestAttributes(HttpServletRequest request,
			@Nullable HttpServletResponse response, @Nullable RequestAttributes previousAttributes) {

		if (previousAttributes == null || previousAttributes instanceof ServletRequestAttributes) {
			// 持有当前请求和响应
			return new ServletRequestAttributes(request, response);
		}
		else {
			return null;  // preserve the pre-bound RequestAttributes instance
		}
	}

	/**
	 * 初始化上下文持有者：设置一下LocaleContext和ServletRequestAttributes这2个属性到ThreadLocal中
	 *
	 * @param request
	 * @param localeContext
	 * @param requestAttributes
	 */
	private void initContextHolders(HttpServletRequest request,
			@Nullable LocaleContext localeContext, @Nullable RequestAttributes requestAttributes) {

		// 设置LocaleContext属性到ThreadLocal中
		if (localeContext != null) {
			LocaleContextHolder.setLocaleContext(localeContext, this.threadContextInheritable);
		}
		// 设置RequestAttributes属性到ThreadLocal中
		if (requestAttributes != null) {
			RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);
		}
	}

	private void resetContextHolders(HttpServletRequest request,
			@Nullable LocaleContext prevLocaleContext, @Nullable RequestAttributes previousAttributes) {

		LocaleContextHolder.setLocaleContext(prevLocaleContext, this.threadContextInheritable);
		RequestContextHolder.setRequestAttributes(previousAttributes, this.threadContextInheritable);
	}

	private void logResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable Throwable failureCause, WebAsyncManager asyncManager) {

		if (!logger.isDebugEnabled()) {
			return;
		}

		String dispatchType = request.getDispatcherType().name();
		boolean initialDispatch = request.getDispatcherType().equals(DispatcherType.REQUEST);

		if (failureCause != null) {
			if (!initialDispatch) {
				// FORWARD/ERROR/ASYNC: minimal message (there should be enough context already)
				if (logger.isDebugEnabled()) {
					logger.debug("Unresolved failure from \"" + dispatchType + "\" dispatch: " + failureCause);
				}
			}
			else if (logger.isTraceEnabled()) {
				logger.trace("Failed to complete request", failureCause);
			}
			else {
				logger.debug("Failed to complete request: " + failureCause);
			}
			return;
		}

		if (asyncManager.isConcurrentHandlingStarted()) {
			logger.debug("Exiting but response remains open for further handling");
			return;
		}

		int status = response.getStatus();
		String headers = ""; // nothing below trace

		if (logger.isTraceEnabled()) {
			Collection<String> names = response.getHeaderNames();
			if (this.enableLoggingRequestDetails) {
				headers = names.stream().map(name -> name + ":" + response.getHeaders(name))
						.collect(Collectors.joining(", "));
			}
			else {
				headers = names.isEmpty() ? "" : "masked";
			}
			headers = ", headers={" + headers + "}";
		}

		if (!initialDispatch) {
			logger.debug("Exiting from \"" + dispatchType + "\" dispatch, status " + status + headers);
		}
		else {
			HttpStatus httpStatus = HttpStatus.resolve(status);
			logger.debug("Completed " + (httpStatus != null ? httpStatus : status) + headers);
		}
	}

	private void publishRequestHandledEvent(HttpServletRequest request, HttpServletResponse response,
			long startTime, @Nullable Throwable failureCause) {

		// 如果开启发布事件
		if (this.publishEvents && this.webApplicationContext != null) {
			// 无论请求是否执行成功都会发布消息
			// Whether or not we succeeded, publish an event.
			long processingTime = System.currentTimeMillis() - startTime;
			// 创建ServletRequestHandledEvent事件，并进行发布
			this.webApplicationContext.publishEvent(
					new ServletRequestHandledEvent(this,
							request.getRequestURI(), request.getRemoteAddr(),
							request.getMethod(), getServletConfig().getServletName(),
							WebUtils.getSessionId(request), getUsernameForRequest(request),
							processingTime, failureCause, response.getStatus()));
		}
	}

	/**
	 * Determine the username for the given request.
	 * <p>The default implementation takes the name of the UserPrincipal, if any.
	 * Can be overridden in subclasses.
	 * @param request current HTTP request
	 * @return the username, or {@code null} if none found
	 * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
	 */
	@Nullable
	protected String getUsernameForRequest(HttpServletRequest request) {
		Principal userPrincipal = request.getUserPrincipal();
		return (userPrincipal != null ? userPrincipal.getName() : null);
	}


	/**
	 * Subclasses must implement this method to do the work of request handling,
	 * receiving a centralized callback for GET, POST, PUT and DELETE.
	 * <p>The contract is essentially the same as that for the commonly overridden
	 * {@code doGet} or {@code doPost} methods of HttpServlet.
	 * <p>This class intercepts calls to ensure that exception handling and
	 * event publication takes place.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 * @see javax.servlet.http.HttpServlet#doGet
	 * @see javax.servlet.http.HttpServlet#doPost
	 */
	protected abstract void doService(HttpServletRequest request, HttpServletResponse response)
			throws Exception;


	/**
	 * ApplicationListener endpoint that receives events from this servlet's WebApplicationContext
	 * only, delegating to {@code onApplicationEvent} on the FrameworkServlet instance.
	 */
	private class ContextRefreshListener implements ApplicationListener<ContextRefreshedEvent> {

		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
			FrameworkServlet.this.onApplicationEvent(event);
		}
	}


	/**
	 * 这个拦截器的作用：
	 * (1)在具体逻辑处理之前，设置一下LocaleContext和ServletRequestAttributes这2个属性（设置到ThreadLocal中，供后续获取）
	 * (2)在请求执行完毕之后，把LocaleContext和ServletRequestAttributes这2个属性清空掉（从ThreadLocal中清空掉）
	 *
	 * CallableProcessingInterceptor implementation that initializes and resets
	 * FrameworkServlet's context holders, i.e. LocaleContextHolder and RequestContextHolder.
	 */
	private class RequestBindingInterceptor implements CallableProcessingInterceptor {

		/**
		 * 在具体逻辑处理之前，设置一下LocaleContext和ServletRequestAttributes这2个属性（设置到ThreadLocal中，供后续获取）
		 *
		 * @param webRequest
		 * @param task the task for the current async request
		 * @param <T>
		 */
		@Override
		public <T> void preProcess(NativeWebRequest webRequest, Callable<T> task) {
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
				// ⚠️
				initContextHolders(request, buildLocaleContext(request),
						buildRequestAttributes(request, response, null)/* ServletRequestAttributes */);
			}
		}

		/**
		 * 在请求执行完毕之后，把LocaleContext和ServletRequestAttributes这2个属性清空掉（从ThreadLocal中清空掉）
		 *
		 * @param webRequest
		 * @param task the task for the current async request
		 * @param concurrentResult the result of concurrent processing, which could
		 * be a {@link Throwable} if the {@code Callable} raised an exception
		 * @param <T>
		 */
		@Override
		public <T> void postProcess(NativeWebRequest webRequest, Callable<T> task, Object concurrentResult) {
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				// ⚠️
				resetContextHolders(request, null, null);
			}
		}

	}

}