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

package org.springframework.web.multipart.commons;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.util.Assert;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.AbstractMultipartHttpServletRequest;
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 注意，使用该实现的时候，必须引入额外的组件：CommonsFileUploadSupport。
 * CommonsFileUploadSupport组件在commons-fileupload jar包里面，所以要引入额外的commons-fileupload jar包
 *
 * Servlet-based {@link MultipartResolver} implementation for
 * <a href="https://commons.apache.org/proper/commons-fileupload">Apache Commons FileUpload</a>
 * 1.2 or above.
 *
 * <p>Provides "maxUploadSize", "maxInMemorySize" and "defaultEncoding" settings as
 * bean properties (inherited from {@link CommonsFileUploadSupport}). See corresponding
 * ServletFileUpload / DiskFileItemFactory properties ("sizeMax", "sizeThreshold",
 * "headerEncoding") for details in terms of defaults and accepted values.
 *
 * <p>Saves temporary files to the servlet container's temporary directory.
 * Needs to be initialized <i>either</i> by an application context <i>or</i>
 * via the constructor that takes a ServletContext (for standalone usage).
 *
 * @author Trevor D. Cook
 * @author Juergen Hoeller
 * @since 29.09.2003
 * @see #CommonsMultipartResolver(ServletContext)
 * @see #setResolveLazily
 * @see org.apache.commons.fileupload.servlet.ServletFileUpload
 * @see org.apache.commons.fileupload.disk.DiskFileItemFactory
 */
public class CommonsMultipartResolver extends CommonsFileUploadSupport
		implements MultipartResolver, ServletContextAware {

	// 是否是懒处理的
	// 如果为true的话，会将"解析请求的操作"放到DefaultMultipartHttpServletRequest的initializeMultipart()方法中，只有在实际调用的时候才会被调用
	// 如果值为false的话，那么会先调用parseRequest()方法来处理request请求，然后将处理的结果放到DefaultMultipartHttpServletRequest
	private boolean resolveLazily = false;

	/**
	 * Constructor for use as bean. Determines the servlet container's
	 * temporary directory via the ServletContext passed in as through the
	 * ServletContextAware interfac懒typically by a WebApplicationContext).
	 * @see #setServletContext
	 * @see org.springframework.web.context.ServletContextAware
	 * @see org.springframework.web.context.WebApplicationContext
	 */
	public CommonsMultipartResolver() {
		super();
	}

	/**
	 * Constructor for standalone usage. Determines the servlet container's
	 * temporary directory via the given ServletContext.
	 * @param servletContext the ServletContext to use
	 */
	public CommonsMultipartResolver(ServletContext servletContext) {
		this();
		setServletContext(servletContext);
	}


	/**
	 * Set whether to resolve the multipart request lazily at the time of
	 * file or parameter access.
	 * <p>Default is "false", resolving the multipart elements immediately, throwing
	 * corresponding exceptions at the time of the {@link #resolveMultipart} call.
	 * Switch this to "true" for lazy multipart parsing, throwing parse exceptions
	 * once the application attempts to obtain multipart files or parameters.
	 */
	public void setResolveLazily(boolean resolveLazily) {
		this.resolveLazily = resolveLazily;
	}

	/**
	 * Initialize the underlying {@code org.apache.commons.fileupload.servlet.ServletFileUpload}
	 * instance. Can be overridden to use a custom subclass, e.g. for testing purposes.
	 * @param fileItemFactory the Commons FileItemFactory to use
	 * @return the new ServletFileUpload instance
	 */
	@Override
	protected FileUpload newFileUpload(FileItemFactory fileItemFactory) {
		return new ServletFileUpload(fileItemFactory);
	}

	@Override
	public void setServletContext(ServletContext servletContext) {
		if (!isUploadTempDirSpecified()) {
			getFileItemFactory().setRepository(WebUtils.getTempDir(servletContext));
		}
	}

	/**
	 * 判断是不是一个上传请求
	 * @param request the servlet request to be evaluated
	 * @return
	 */
	@Override
	public boolean isMultipart(HttpServletRequest request) {
		// 判断是不是一个上传请求
		// （1）如果content-type请求头的属性值是以"multipart/"开头的，那么就代表是一个上传请求
		// （2）否则不是一个上传请求
		return ServletFileUpload.isMultipartContent(request);
	}

	@Override
	public MultipartHttpServletRequest resolveMultipart(final HttpServletRequest request) throws MultipartException {
		Assert.notNull(request, "Request must not be null");
		/**
		 * 1、提示：无论是不是懒处理，最终返回的都是DefaultMultipartHttpServletRequest对象，
		 * 只是在构建DefaultMultipartHttpServletRequest对象时传入的参数不一样。
		 * （1）在非懒处理时，是直接获取上传文件需要用到的属性值，作为构造参数，设置到DefaultMultipartHttpServletRequest里面去
		 * （2）而是懒处理时，不会立马获取上传文件需要用到的属性值，而是把"获取的逻辑"放在一个初始化方法 - initializeMultipart()里面，
		 * >>> 后续再调用该方法，获取上传文件需要用到的属性值，设置到DefaultMultipartHttpServletRequest里面去
		 */
		// 判断是不是懒处理的
		if (this.resolveLazily) {
			return new DefaultMultipartHttpServletRequest(request) {
				@Override
				protected void initializeMultipart() {
					// 解析请求参数
					MultipartParsingResult parsingResult = parseRequest(request);
					/**
					 * 1、题外：表单在进行提交的时候，每一个都是一个File对象，后面有一个文件名称，你在页面显示的时候可以点提交，
					 * 但是必须要要上传一个文件名称，我需要知道当前的文件是哪一个，后面才能进行上传
					 */
					// 设置上传的文件
					setMultipartFiles(parsingResult.getMultipartFiles()/* 获取上传的文件 */);
					// 设置一些属性
					setMultipartParameters(parsingResult.getMultipartParameters());
					// 设置ContentType属性
					setMultipartParameterContentTypes(parsingResult.getMultipartParameterContentTypes());
				}
			};
		}
		else {
			// 解析请求参数
			MultipartParsingResult parsingResult = parseRequest(request);
			// multipartFiles：表示上传的文件
			// multipartParameters：表示上传的参数
			// multipartParameterContentTypes：表示我们上传的2个参数的content-type分别是啥
			return new DefaultMultipartHttpServletRequest(request, parsingResult.getMultipartFiles(),
					parsingResult.getMultipartParameters(), parsingResult.getMultipartParameterContentTypes());
		}
	}

	/**
	 * 解析请求，转成MultipartParsingResult对象
	 *
	 * 解析请求中包含的参数，而在解析的时候会判断，到底是普通的表单属性，还是一个文件类型的表单属性，解析到这2个属性后，放到不同的集合里面，
	 * 最终总结成一个整体的对象，叫MultipartParsingResult
	 *
	 * Parse the given servlet request, resolving its multipart elements.
	 * @param request the request to parse
	 * @return the parsing result
	 * @throws MultipartException if multipart resolution failed.
	 */
	protected MultipartParsingResult parseRequest(HttpServletRequest request) throws MultipartException {
		// 从当前当前中，获取请求编码
		String encoding = determineEncoding(request);

		// 按照请求的编码，获取一个FileUpload对象，装载到CommonsFileUploadSupport的property属性都会被装入这个对象中
		// prepareFileUpload是继承自CommonsFileUploadSupport的函数，会比较请求的编码和XML中配置的编码，如果不一样，会拒绝处理
		FileUpload fileUpload = prepareFileUpload(encoding);
		try {
			// 解析请求中表单项的所有参数。一个参数值，封装成一个FileItem，最终封装成一个FileItem集合
			// 注意：有的FileItem可能不是文件类型，只是一个参数值
			// 题外：表单项中的参数值，有可能是文件类型，也有可能是普通参数类型，需要对这2个类型的值做分别的处理
			// 对请求中的multipart文件进行具体的处理
			List<FileItem> fileItems = ((ServletFileUpload) fileUpload).parseRequest(request);
			return parseFileItems(fileItems, encoding);
		}
		catch (FileUploadBase.SizeLimitExceededException ex) {
			throw new MaxUploadSizeExceededException(fileUpload.getSizeMax(), ex);
		}
		catch (FileUploadBase.FileSizeLimitExceededException ex) {
			throw new MaxUploadSizeExceededException(fileUpload.getFileSizeMax(), ex);
		}
		catch (FileUploadException ex) {
			throw new MultipartException("Failed to parse multipart servlet request", ex);
		}
	}

	/**
	 * Determine the encoding for the given request.
	 * Can be overridden in subclasses.
	 * <p>The default implementation checks the request encoding,
	 * falling back to the default encoding specified for this resolver.
	 * @param request current HTTP request
	 * @return the encoding for the request (never {@code null})
	 * @see javax.servlet.ServletRequest#getCharacterEncoding
	 * @see #setDefaultEncoding
	 */
	protected String determineEncoding(HttpServletRequest request) {
		String encoding = request.getCharacterEncoding();
		if (encoding == null) {
			encoding = getDefaultEncoding();
		}
		return encoding;
	}

	@Override
	public void cleanupMultipart(MultipartHttpServletRequest request) {
		if (!(request instanceof AbstractMultipartHttpServletRequest) ||
				((AbstractMultipartHttpServletRequest) request).isResolved()) {
			try {
				cleanupFileItems(request.getMultiFileMap());
			}
			catch (Throwable ex) {
				logger.warn("Failed to perform multipart cleanup for servlet request", ex);
			}
		}
	}

}
