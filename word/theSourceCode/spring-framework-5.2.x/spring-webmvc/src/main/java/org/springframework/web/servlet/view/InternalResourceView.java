/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.servlet.view;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Wrapper for a JSP or other resource within the same web application.
 * Exposes model objects as request attributes and forwards the request to
 * the specified resource URL using a {@link javax.servlet.RequestDispatcher}.
 *
 * <p>A URL for this view is supposed to specify a resource within the web
 * application, suitable for RequestDispatcher's {@code forward} or
 * {@code include} method.
 *
 * <p>If operating within an already included request or within a response that
 * has already been committed, this view will fall back to an include instead of
 * a forward. This can be enforced by calling {@code response.flushBuffer()}
 * (which will commit the response) before rendering the view.
 *
 * <p>Typical usage with {@link InternalResourceViewResolver} looks as follows,
 * from the perspective of the DispatcherServlet context definition:
 *
 * <pre class="code">&lt;bean id="viewResolver" class="org.springframework.web.servlet.view.InternalResourceViewResolver"&gt;
 *   &lt;property name="prefix" value="/WEB-INF/jsp/"/&gt;
 *   &lt;property name="suffix" value=".jsp"/&gt;
 * &lt;/bean&gt;</pre>
 *
 * Every view name returned from a handler will be translated to a JSP
 * resource (for example: "myView" -> "/WEB-INF/jsp/myView.jsp"), using
 * this view class by default.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see javax.servlet.RequestDispatcher#forward
 * @see javax.servlet.RequestDispatcher#include
 * @see javax.servlet.ServletResponse#flushBuffer
 * @see InternalResourceViewResolver
 * @see JstlView
 */
public class InternalResourceView extends AbstractUrlBasedView {

	// 是否始终都是执行Include的标识
	private boolean alwaysInclude = false;

	private boolean preventDispatchLoop = false;


	/**
	 * Constructor for use as a bean.
	 * @see #setUrl
	 * @see #setAlwaysInclude
	 */
	public InternalResourceView() {
	}

	/**
	 * Create a new InternalResourceView with the given URL.
	 * @param url the URL to forward to
	 * @see #setAlwaysInclude
	 */
	public InternalResourceView(String url) {
		super(url);
	}

	/**
	 * Create a new InternalResourceView with the given URL.
	 * @param url the URL to forward to
	 * @param alwaysInclude whether to always include the view rather than forward to it
	 */
	public InternalResourceView(String url, boolean alwaysInclude) {
		super(url);
		this.alwaysInclude = alwaysInclude;
	}


	/**
	 * Specify whether to always include the view rather than forward to it.
	 * <p>Default is "false". Switch this flag on to enforce the use of a
	 * Servlet include, even if a forward would be possible.
	 * @see javax.servlet.RequestDispatcher#forward
	 * @see javax.servlet.RequestDispatcher#include
	 * @see #useInclude(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void setAlwaysInclude(boolean alwaysInclude) {
		this.alwaysInclude = alwaysInclude;
	}

	/**
	 * Set whether to explicitly prevent dispatching back to the
	 * current handler path.
	 * <p>Default is "false". Switch this to "true" for convention-based
	 * views where a dispatch back to the current handler path is a
	 * definitive error.
	 */
	public void setPreventDispatchLoop(boolean preventDispatchLoop) {
		this.preventDispatchLoop = preventDispatchLoop;
	}

	/**
	 * An ApplicationContext is not strictly required for InternalResourceView.
	 */
	@Override
	protected boolean isContextRequired() {
		return false;
	}


	/**
	 * Render the internal resource given the specified model.
	 * This includes setting the model as request attributes.
	 * 渲染给定模型的内部资源。这包括将模型设置为请求属性。
	 */
	@Override
	protected void renderMergedOutputModel(
			Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {

		/* 1、将model里面的数据放入request作用域里面 */

		// 将model里面的数据放入request作用域里面
		// Expose the model object as request attributes. —— 将模型对象公开为请求属性。
		exposeModelAsRequestAttributes(model, request);

		/* 2、往请求中设置一些属性，Locale、TimeZone(时区)、LocalizationContext */

		// Expose helpers as request attributes, if any. —— 如果有，将助手公开为请求属性。
		// 往请求中设置一些属性，Locale、TimeZone(时区)、LocalizationContext
		exposeHelpers(request);

		/* 3、获取物理视图地址作为新的请求路径 */

		// Determine the path for the request dispatcher. —— 确定请求调度程序的路径。
		// 获取刚刚解析好的物理视图地址，作为转发的路径
		String dispatcherPath/* 调度地址 */ = prepareForRendering(request, response);


		/* 4、获取请求转发器 —— RequestDispatcher */
		/**
		 * 1、转发的原生写法：
		 *
		 * // 获取请求转发器
		 * RequestDispatcher requestDispatcher = request.getRequestDispatcher(path)
		 * // 转发
		 * requestDispatcher.forward(ServletRequest request, ServletResponse response);
		 *
		 * 2、重定向的原生写法：response.sendRedirect(path);
		 */
		// Obtain a RequestDispatcher for the target resource (typically a JSP). —— 获取目标资源（通常是 JSP）的RequestDispatcher请求调度对象。
		// 获取请求转发器
		RequestDispatcher rd = getRequestDispatcher(request, dispatcherPath);
		if (rd == null) {
			throw new ServletException("Could not get RequestDispatcher for [" + getUrl() +
					"]: Check that the corresponding file exists within your web application archive!");
		}

		/*

		forward与include的区别：

		区别1：
		forward方法是把请求的内容转发到另外的一个servlet，或者jsp.
		include是把另一个servlet，或者jsp处理过后的内容拿过来，与此时servlet的内容一同输出。

		区别2：
		forward在调用者程序中设置的响应状态码和响应头不会被忽略,在被调用者程序中设置的响应状态码和响应头也不会被忽略。
		include会被忽略.. 这也是乱码的一个原因.. 所以在使用include时候,要在调用程序中写上response.setContentType("text/html;charset=utf-8") ，
		不管你是不是用filter统一处理过字符.

		 */

		/*

		5、判断当前请求，是否是一个包含请求，即不是从外部进来的顶级HTTP请求。
		如果是一个包含请求，就用请求转发器执行include操作。

		 */

		// 看下是否有include页面
		// If already included or response already committed, perform include, else forward.
		// 上面的翻译：如果已经包含或响应已经提交，则执行包含，否则转发。
		if (useInclude(request, response)) {
			/**
			 * 调用forward()方法时必须注意下列两点：
			 * 在HTTP回应被“确认”（committed）以前才能调用forward()方法（这里的“确认”是指将HTTP回应的内容主体送回用户端），否则将拋出IllegalStateException异常。[include没有这种情况]
			 * 调用forward()方法后，原先存放在HttpResponse对象中的内容将会自动被清除(也就是在forward方法前,使用PrintWriter,ServletOutputStream输出的内容都被忽略)
			 * [include不会]
			 *
			 * forward在调用者程序中设置的响应状态码和响应头不会被忽略,在被调用者程序中设置的响应状态码和响应头也不会被忽略。
			 * include会被忽略.. 这也是乱码的一个原因.. 所以在使用include时候,要在调用程序中写上response.setContentType("text/html;charset=utf-8") ，
			 * 不管你是不是用filter统一处理过字符.
			 */
			response.setContentType(getContentType());
			if (logger.isDebugEnabled()) {
				logger.debug("Including [" + getUrl() + "]");
			}
			rd.include(request, response);
		}
		/*

		6、如果不是一个包含请求，就用请求转发器执行转发(forward)操作。

		感悟：视图解析器就是通过Controller方法返回的字符串作为逻辑视图名称，和web.xml中配置的前缀和后缀，相拼接，得到一个物理视图地址，比如后缀是.jsp，那么就是得到一个jsp页面的物理地址。
		然后从这里可以看出，渲染视图，其实就是"转发"得到的"jsp页面的物理地址"，然后tomcat内部会读取jsp页面进行返回！

		*/
		else {
			// Note: The forwarded resource is supposed to determine the content type itself.
			if (logger.isDebugEnabled()) {
				logger.debug("Forwarding to [" + getUrl() + "]");
			}
			/**
			 * 1、从这里开始就是tomcat里面的代码了，由tomcat把请求进行返回
			 *
			 * 当我们把我们的响应给到tomcat之后，tomcat会把响应请求传输给浏览器，当浏览器接收到响应请求之后，直接进行页面回显即可
			 * 我们把我们的请求路径给到tomcat，tomcat会根据对应的请求路径，找到对应的页面进行返回
			 * tomcat在接收到spring mvc返回的响应处理结果之后，tomcat里面做了什么事情？
			 *
			 * 我把我们的请求和响应交给tomcat，里面包含了指定页面的请求路径，然后tomcat回根据请求路径，找到对应的静态页面进行加载，然后把数据填充进去，然后再回显到浏览器
			 *
			 * 2、tomcat与spring mvc的交互流程：
			 * 浏览器发送一个请求，是先交给了tomcat；tomcat接收到请求，经过了一系列的操作之后，才能到DispatcherServlet里面去；
			 * 在DispatcherServlet里面，经过一系列的复杂处理流程之后，返回了一个处理结果，把这个结果给到了tomcat，tomcat再根据我们的处理结果，把相关的数据、页面，回显到浏览器里面
			 */
			// 转发
			// ⚠️注意：从这里开始就是tomcat里面的代码了，由tomcat把请求进行返回
			rd.forward(request, response);
		}
	}

	/**
	 * Expose helpers unique to each rendering operation. This is necessary so that
	 * different rendering operations can't overwrite each other's contexts etc.
	 * <p>Called by {@link #renderMergedOutputModel(Map, HttpServletRequest, HttpServletResponse)}.
	 * The default implementation is empty. This method can be overridden to add
	 * custom helpers as request attributes.
	 * @param request current HTTP request
	 * @throws Exception if there's a fatal error while we're adding attributes
	 * @see #renderMergedOutputModel
	 * @see JstlView#exposeHelpers
	 */
	protected void exposeHelpers(HttpServletRequest request) throws Exception {
	}

	/**
	 * Prepare for rendering, and determine the request dispatcher path
	 * to forward to (or to include).
	 * <p>This implementation simply returns the configured URL.
	 * Subclasses can override this to determine a resource to render,
	 * typically interpreting the URL in a different manner.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @return the request dispatcher path to use
	 * @throws Exception if preparations failed
	 * @see #getUrl()
	 */
	protected String prepareForRendering(HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		// 获取刚刚解析好的物理视图地址
		String path = getUrl();
		Assert.state(path != null, "'url' not set");

		if (this.preventDispatchLoop) {
			String uri = request.getRequestURI();
			if (path.startsWith("/") ? uri.equals(path) : uri.equals(StringUtils.applyRelativePath(uri, path))) {
				throw new ServletException("Circular view path [" + path + "]: would dispatch back " +
						"to the current handler URL [" + uri + "] again. Check your ViewResolver setup! " +
						"(Hint: This may be the result of an unspecified view, due to default view name generation.)");
			}
		}
		return path;
	}

	/**
	 * Obtain the RequestDispatcher to use for the forward/include.
	 * <p>The default implementation simply calls
	 * {@link HttpServletRequest#getRequestDispatcher(String)}.
	 * Can be overridden in subclasses.
	 * @param request current HTTP request
	 * @param path the target URL (as returned from {@link #prepareForRendering})
	 * @return a corresponding RequestDispatcher
	 */
	@Nullable
	protected RequestDispatcher getRequestDispatcher(HttpServletRequest request, String path) {
		// org.apache.catalina.connector.RequestFacade
		return request.getRequestDispatcher(path);
	}

	/**
	 * Determine whether to use RequestDispatcher's {@code include} or
	 * {@code forward} method.
	 *
	 * 确定是使用 RequestDispatcher 的 {@code include} 还是 {@code forward} 方法。
	 *
	 * <p>Performs a check whether an include URI attribute is found in the request,
	 * indicating an include request, and whether the response has already been committed.
	 * In both cases, an include will be performed, as a forward is not possible anymore.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @return {@code true} for include, {@code false} for forward
	 * @see javax.servlet.RequestDispatcher#forward
	 * @see javax.servlet.RequestDispatcher#include
	 * @see javax.servlet.ServletResponse#isCommitted
	 * @see org.springframework.web.util.WebUtils#isIncludeRequest
	 */
	protected boolean useInclude(HttpServletRequest request, HttpServletResponse response) {
		return (this.alwaysInclude/* 是否始终都是执行Include的标识 */ ||
				WebUtils.isIncludeRequest(request)/* 判断给定的请求，是否是包含请求，即不是从外部进来的顶级HTTP请求 */ ||
				response.isCommitted()/* 响应已经完成 */);
	}

}
