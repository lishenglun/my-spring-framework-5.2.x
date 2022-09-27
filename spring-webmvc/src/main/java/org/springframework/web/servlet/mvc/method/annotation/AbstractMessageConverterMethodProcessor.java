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

import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.*;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 处理返回值需要使用HttpMessageConverter写入response的基类，自己并未具体做处理，而是定义了相关工具
 *
 * Extends {@link AbstractMessageConverterMethodArgumentResolver} with the ability to handle method
 * return values by writing to the response with {@link HttpMessageConverter HttpMessageConverters}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractMessageConverterMethodProcessor extends AbstractMessageConverterMethodArgumentResolver
		implements HandlerMethodReturnValueHandler {

	/* Extensions associated with the built-in message converters */
	private static final Set<String> SAFE_EXTENSIONS = new HashSet<>(Arrays.asList(
			"txt", "text", "yml", "properties", "csv",
			"json", "xml", "atom", "rss",
			"png", "jpe", "jpeg", "jpg", "gif", "wbmp", "bmp"));

	private static final Set<String> SAFE_MEDIA_BASE_TYPES = new HashSet<>(
			Arrays.asList("audio", "image", "video"));

	private static final List<MediaType> ALL_APPLICATION_MEDIA_TYPES =
			Arrays.asList(MediaType.ALL, new MediaType("application"));

	private static final Type RESOURCE_REGION_LIST_TYPE/* 资源区域列表类型现在 */ =
			// 参数化类型参考
			new ParameterizedTypeReference<List<ResourceRegion>>() { }.getType();


	private static final UrlPathHelper decodingUrlPathHelper = new UrlPathHelper();

	private static final UrlPathHelper rawUrlPathHelper = new UrlPathHelper();

	static {
		rawUrlPathHelper.setRemoveSemicolonContent(false);
		rawUrlPathHelper.setUrlDecode(false);
	}


	private final ContentNegotiationManager contentNegotiationManager;

	private final Set<String> safeExtensions = new HashSet<>();


	/**
	 * Constructor with list of converters only.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters) {
		this(converters, null, null);
	}

	/**
	 * Constructor with list of converters and ContentNegotiationManager.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager contentNegotiationManager) {

		this(converters, contentNegotiationManager, null);
	}

	/**
	 * Constructor with list of converters and ContentNegotiationManager as well
	 * as request/response body advice instances.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager, @Nullable List<Object> requestResponseBodyAdvice) {

		super(converters, requestResponseBodyAdvice);

		this.contentNegotiationManager = (manager != null ? manager : new ContentNegotiationManager());
		this.safeExtensions.addAll(this.contentNegotiationManager.getAllFileExtensions());
		this.safeExtensions.addAll(SAFE_EXTENSIONS);
	}


	/**
	 * Creates a new {@link HttpOutputMessage} from the given {@link NativeWebRequest}.
	 * @param webRequest the web request to create an output message from
	 * @return the output message
	 */
	protected ServletServerHttpResponse createOutputMessage(NativeWebRequest webRequest) {
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		Assert.state(response != null, "No HttpServletResponse");
		return new ServletServerHttpResponse(response);
	}

	/**
	 * Writes the given return value to the given web request. Delegates to
	 * {@link #writeWithMessageConverters(Object, MethodParameter, ServletServerHttpRequest, ServletServerHttpResponse)}
	 */
	protected <T> void writeWithMessageConverters(T value, MethodParameter returnType, NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
		writeWithMessageConverters(value, returnType, inputMessage, outputMessage);
	}

	/**
	 * Writes the given return type to the given output message.
	 * @param value the value to write to the output message
	 * @param returnType the type of the value
	 * @param inputMessage the input messages. Used to inspect the {@code Accept} header.
	 * @param outputMessage the output message to write to
	 * @throws IOException thrown in case of I/O errors
	 * @throws HttpMediaTypeNotAcceptableException thrown when the conditions indicated
	 * by the {@code Accept} header on the request cannot be met by the message converters
	 * @throws HttpMessageNotWritableException thrown if a given message cannot
	 * be written by a converter, or if the content-type chosen by the server
	 * has no compatible converter.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	protected <T> void writeWithMessageConverters(@Nullable T value/* 返回值 */, MethodParameter returnType/* 返回值类型 */,
			ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {


		/* 1、获取返回值和返回值类型  */

		// 返回值
		Object body;
		// 返回值类型
		Class<?> valueType;
		// 目标类型（如果返回值当中存在泛型类型，那么这个泛型类型才是真正的目标类型）
		Type targetType;

		// 判断返回值是不是String类型， 如果是，则直接赋值
		if (value instanceof CharSequence) {
			body = value.toString();
			valueType = String.class;
			targetType = String.class;
		}
		else {
			body = value;
			// 获取返回值的类型（返回值 body 不为空则直接获取其类型，否则从返回结果类型 returnType 获取其返回值类型)
			valueType = getReturnValueType(body, returnType);
			// 获取返回值里面所带的泛型类型。如果返回值当中存在泛型类型，那么这个泛型类型才是真正的目标类型。
			targetType = GenericTypeResolver.resolveType(getGenericType/* 获取通用类型 */(returnType), returnType.getContainingClass());
		}

		// 判断返回值类型，是不是Resource类型
		if (isResourceType(value, returnType)) {
			// 设置响应头 Accept-Ranges
			outputMessage.getHeaders().set(HttpHeaders.ACCEPT_RANGES/* Accept-Ranges *//* 接受范围 */, "bytes");
			// 数据不为空 && 请求头中的Range不为空 && 响应码为200
			if (value != null && inputMessage.getHeaders().getFirst(HttpHeaders.RANGE/* Range */) != null &&
					outputMessage.getServletResponse().getStatus() == 200) {
				// 将返回值转换为Resource类型
				Resource resource = (Resource) value;
				try {
					// 获取请求头中的Range数据
					List<HttpRange> httpRanges = inputMessage.getHeaders().getRange();
					// 断点续传，客户端已下载一部分数据，此时需要设置响应码为206
					outputMessage.getServletResponse().setStatus(HttpStatus.PARTIAL_CONTENT.value()/* 206 */);
					// 获取需返回的那一段数据
					body = HttpRange.toResourceRegions/* 到资源区域 */(httpRanges, resource);
					valueType = body.getClass();
					targetType = RESOURCE_REGION_LIST_TYPE/* 资源区域列表类型 */;
				}
				catch (IllegalArgumentException ex) {
					outputMessage.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength());
					outputMessage.getServletResponse().setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
				}
			}
		}

		/* 2、为返回值，筛选出最合适的媒体类型 —— MediaType */

		// 选择使用的媒体类型（因为要返回响应结果了，所以我要找到我要返回的数据类型，是什么样的类型）
		MediaType selectedMediaType/* 选择的媒体类型 */ = null;

		/* 2.1、先看下响应头中有没有具体的媒体类型，有的话，就使用响应头中的媒体类型 */
		// 获取响应头中的媒体类型（响应头中的ContentType的值）
		MediaType contentType = outputMessage.getHeaders().getContentType();
		// 判断响应头中是否有具体的媒体类型
		// 如果存在媒体类型，且这个媒体类型不包含通配符，则作为可以使用的媒体类型
		boolean isContentTypePreset/* 是内容类型预设 */ = contentType != null && contentType.isConcrete/* 是具体的 */();
		if (isContentTypePreset) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found 'Content-Type:" + contentType + "' in response");
			}
			// 如果有，就用响应头中已有的媒体类型
			selectedMediaType = contentType;
		}
		/* 2.2、如果响应头中不存在具体的媒体类型，就获取客户端可以接收的媒体类型，以及服务器可以响应的媒体类型，然后进行匹配，得出可以使用的媒体类型 */
		else {
			HttpServletRequest request = inputMessage.getServletRequest();
			/**
			 * ContentNegotiationManager内容协商管理器：获取客户端支持接收的媒体类型(请求头accept字典)。
			 * 默认情况下内容协商管理器中，是从accept请求头中获取客户端接受的媒体类型
			 * 参考：https://blog.csdn.net/noob9527/article/details/116051071
			 */
			/* （1）从请求头中，获取客户端支持的可以接收的媒体类型集合。默认实现是，从accept请求头中获取。 */
			List<MediaType> acceptableTypes/* 可接受的类型 */ = getAcceptableMediaTypes/* 获取可接受的媒体类型 */(request);

			/*

			（2）获取服务器能够响应当前返回值的媒体类型集合
			>>> a、先从request作用域中获取服务器可以响应的媒体类型，有，就使用Request作用域里面的
			>>> b、没有，并且"支持的媒体类型集合"为空；则遍历http消息转换器，判断谁可以编写和响应结果值。如果可以，就获取该http消息转换器支持的媒体类型作为"服务器可以响应的媒体类型"。

			 */
			List<MediaType> producibleTypes/* 可生产类型 */ = getProducibleMediaTypes(request, valueType, targetType);

			/* （3）如果有返回值，但是服务器没有能够响应的媒体类型，就抛出异常 */
			if (body != null && producibleTypes.isEmpty()) {
				throw new HttpMessageNotWritableException(
						"No converter found for return value of type: "/* 未找到类型返回值的转换器 */ + valueType);
			}

			/* （4）根据客户端可以接收的媒体类型，和服务器可以响应的媒体类型，进行匹配，得出可以使用的媒体类型 */

			// 可以使用的媒体类型
			List<MediaType> mediaTypesToUse = new ArrayList<>();
			// 看下客户端可以接收的媒体类型，是否能与服务器可响应的媒体类型相匹配
			// 如果可以匹配，就代表是可以使用的媒体类型，于是就添加到"可以使用的媒体类型集合"中
			for (MediaType requestedType : acceptableTypes) {
				for (MediaType producibleType : producibleTypes) {
					if (requestedType.isCompatibleWith/* 兼容 */(producibleType)) {
						// ⚠️
						mediaTypesToUse.add(getMostSpecificMediaType(requestedType, producibleType));
					}
				}
			}

			/* （5）如果不存在可以使用的媒体类型，则抛出异常 */
			if (mediaTypesToUse.isEmpty()) {
				if (body != null) {
					throw new HttpMediaTypeNotAcceptableException(producibleTypes);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("No match for " + acceptableTypes + ", supported: " + producibleTypes);
				}
				return;
			}

			/* （6）对可以使用的媒体类型进行排序 */
			// 排序（按照MediaType的specificity和quality排序）
			MediaType.sortBySpecificityAndQuality(mediaTypesToUse);

			/* （7）从可以使用的媒体类型中，选出一个最合适当前返回值的媒体类型（主要考虑不包含通配符的，例如application/json;q=0.8） */
			for (MediaType mediaType : mediaTypesToUse) {
				if (mediaType.isConcrete()/* 是具体的 */) {
					// 如果返回的是一个对象，那么selectedMediaType = application/json;q=0.8
					selectedMediaType = mediaType;
					break;
				}
				else if (mediaType.isPresentIn(ALL_APPLICATION_MEDIA_TYPES)) {
					selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
					break;
				}
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Using '" + selectedMediaType + "', given " +
						acceptableTypes + " and supported " + producibleTypes);
			}
		}

		/* 3、如果返回值有合适的媒体类型，则进行编写返回值和响应返回值操作 */

		// 如果匹配到，则进行写入逻辑
		if (selectedMediaType != null) {

			// 移除质量值(quality)
			// 例如：application/json;q=0.8 移除后为 application/json
			selectedMediaType = selectedMediaType.removeQualityValue/* 移除质量值 */();


			/* 3.1、遍历HttpMessageConverter */

			/**
			 * 1、genericConverter = MappingJackson2XmlHttpMessageConverter
			 */
			for (HttpMessageConverter<?> converter : this.messageConverters) {


				/*

				3.2、判断是否有HttpMessageConverter(http消息转换器)支持转换编写目标类型的数据进行响应。

				 */

				// 判断converter是不是GenericHttpMessageConverter类型
				// GenericHttpMessageConverter<T> extends HttpMessageConverter<T>
				// （1）如果converter是GenericHttpMessageConverter类型，则转换为GenericHttpMessageConverter类型，
				// >>> 为了后面调用GenericHttpMessageConverter的canWrite()方法，判断是否支持编写对应类型的数据进行响应
				// >>> 因为HttpMessageConverter也有对应的canWrite()方法，只是入参不一样
				// （2）否则返回null
				GenericHttpMessageConverter genericConverter = (converter instanceof GenericHttpMessageConverter ?
						(GenericHttpMessageConverter<?>) converter : null);

				// 判断HttpMessageConverter(http消息转换器)是否支持转换目标类型的数据进行响应（是否支持转换编写目标类型的数据进行响应）
				// （1）如果genericConverter不为null，则证明，converter是直接实现GenericHttpMessageConverter接口，是GenericHttpMessageConverter接口的实例，
				// >>> 所以调用GenericHttpMessageConverter的canWrite()方法，进行判断
				// （2）如果genericConverter为null，则证明，converter是直接实现HttpMessageConverter接口，是HttpMessageConverter接口的实例
				// >>> 所以调用HttpMessageConverter的canWrite()方法，进行判断
				if (genericConverter != null ?
						((GenericHttpMessageConverter) converter).canWrite(targetType, valueType, selectedMediaType) :
						converter.canWrite(valueType, selectedMediaType)) {

					/* 3.3、⚠️如果有，则先调用RequestResponseBodyAdvice处理返回值，得到一个新的返回值 */

					// ⚠️执行ResponseBodyAdvice的实现类，处理返回值，得到一个新的返回值
					// 如果有 RequestResponseBodyAdvice，则可能需要对返回的结果做修改
					body = getAdvice().beforeBodyWrite/* 正文写之前 */(body/* 返回值 */, returnType/* 返回值类型 */, selectedMediaType,
							(Class<? extends HttpMessageConverter<?>>) converter.getClass(),
							inputMessage, outputMessage);

					/*

					3.4、⚠️如果通过RequestResponseBodyAdvice处理后，还有返回值；然后再调用http消息转换器(HttpMessageConverter)处理返回值并响应到客户端。

					例如：MappingJackson2XmlHttpMessageConverter就是将返回的对象变为一个json字符串返回到客户端。

					 */

					// body 非空，则进行写入
					if (body != null) {
						// 这个变量的用途是，打印是匿名类，需要有 final
						Object theBody = body;
						LogFormatUtils.traceDebug(logger, traceOn ->
								"Writing [" + LogFormatUtils.formatValue(theBody, !traceOn) + "]");
						// 添加 CONTENT_DISPOSITION 头，一般情况下用不到
						addContentDispositionHeader(inputMessage, outputMessage);
						if (genericConverter != null) {
							/**
							 * 1、MappingJackson2XmlHttpMessageConverter，间接继承️AbstractGenericHttpMessageConverter
							 */
							// ⚠️编写和响应内容
							genericConverter.write(body, targetType, selectedMediaType, outputMessage);
						}
						else {
							// ⚠️编写和响应内容
							((HttpMessageConverter) converter).write(body, selectedMediaType, outputMessage);
						}
					}
					else {
						if (logger.isDebugEnabled()) {
							logger.debug("Nothing to write: null body");
						}
					}

					/*

					3.5、HttpMessageConverter处理和响应完毕返回值之后，就结束当前方法的处理逻辑。代表请求也结束了。

					题外：⚠️从这里可以看出，只会有一个HttpMessageConverter处理和响应返回值。
					题外：解疑：这里就结束了当前方法，不会往下面走，因为，如果往下面走的话，如果body不为null，则代表没有匹配的消息转换器，则抛出异常。
					之前我还在纳闷，上面响应完body后，是哪里把body值置空的，找了半天没找到，原来是不会走下面的body!=null的判断了！
					另外下面的判断，由于只有body!=null，才代表没有匹配的消息转换器，才会抛出异常，所以即使body==null，没有匹配到对应的消息转换器，也不会报错。

					*/
					// return 返回，结束整个逻辑
					return;
				}
			}
		}

		/* 4、如果上面没有响应请求，并且返回值不为空，说明没有匹配的http消息转换器，则抛出异常 */

		// 如果到达此处，并且 body 非空，说明没有匹配的 HttpMessageConverter 转换器，则抛出 HttpMediaTypeNotAcceptableException 异常
		if (body != null) {
			Set<MediaType> producibleMediaTypes =
					(Set<MediaType>) inputMessage.getServletRequest()
							.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);

			if (isContentTypePreset || !CollectionUtils.isEmpty(producibleMediaTypes)) {
				throw new HttpMessageNotWritableException(
						"No converter for [" + valueType + "] with preset Content-Type '" + contentType + "'");
			}
			throw new HttpMediaTypeNotAcceptableException(this.allSupportedMediaTypes);
		}
	}

	/**
	 * Return the type of the value to be written to the response. Typically this is
	 * a simple check via getClass on the value but if the value is null, then the
	 * return type needs to be examined possibly including generic type determination
	 *
	 * 返回要写入响应的值的类型。通常这是通过 getClass 对值进行的简单检查，但如果值为 null，则需要检查返回类型，可能包括泛型类型确定
	 *
	 * (e.g. {@code ResponseEntity<T>}).
	 */
	protected Class<?> getReturnValueType(@Nullable Object value, MethodParameter returnType) {
		return (value != null ? value.getClass() : returnType.getParameterType()/* 参数类型 */);
	}

	/**
	 * Return whether the returned value or the declared return type extends {@link Resource}.
	 *
	 * 返回返回值或声明的返回类型是否扩展 {@link Resource}。
	 */
	protected boolean isResourceType(@Nullable Object value, MethodParameter returnType) {
		// 获取返回值类型
		Class<?> clazz = getReturnValueType(value, returnType);
		// 如果返回值不是InputStreamResource类型，并且是Resource类型，则返回true
		return clazz != InputStreamResource.class && Resource.class.isAssignableFrom(clazz);
	}

	/**
	 * Return the generic type of the {@code returnType} (or of the nested type
	 * if it is an {@link HttpEntity}).
	 *
	 * 返回 {@code returnType} 的泛型类型（如果它是 {@link HttpEntity}，则返回嵌套类型）。
	 */
	private Type getGenericType(MethodParameter returnType) {
		if (HttpEntity.class.isAssignableFrom(returnType.getParameterType())) {
			return ResolvableType.forType(returnType.getGenericParameterType()).getGeneric().getType();
		}
		else {
			return returnType.getGenericParameterType();
		}
	}

	/**
	 * Returns the media types that can be produced.
	 * @see #getProducibleMediaTypes(HttpServletRequest, Class, Type)
	 */
	@SuppressWarnings("unused")
	protected List<MediaType> getProducibleMediaTypes(HttpServletRequest request, Class<?> valueClass) {
		return getProducibleMediaTypes(request, valueClass, null);
	}

	/**
	 * Returns the media types that can be produced. The resulting media types are:
	 * <ul>
	 * <li>The producible media types specified in the request mappings, or
	 * <li>Media types of configured converters that can write the specific return value, or
	 * <li>{@link MediaType#ALL}
	 * </ul>
	 * @since 4.2
	 */
	@SuppressWarnings("unchecked")
	protected List<MediaType> getProducibleMediaTypes(
			HttpServletRequest request, Class<?> valueClass, @Nullable Type targetType) {

		/* 1、获取服务器可以响应的媒体类型 */

		 /* 1.1、先从request作用域中获取服务器可以响应的媒体类型，有，就使用Request作用域里面的 */

		// 题外：该属性的来源是@RequestMapping(producer = xxx)
		Set<MediaType> mediaTypes =
				(Set<MediaType>) request.getAttribute(
						// ⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️
						// ⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️
						HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE/* org.springframework.web.servlet.HandlerMapping.producibleMediaTypes */);

		// 如果非空，则使用该属性
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			// 服务器可产出的媒体类型集合
			return new ArrayList<>(mediaTypes);
		}
		/*

		1.2、如果request作用域里面没有"服务器可以响应的媒体类型"，并且"支持的媒体类型集合"为空，则遍历http消息转换器，判断谁可以编写和响应结果值。
		如果可以，就获取该http消息转换器支持的媒体类型作为"服务器可以响应的媒体类型"。

		*/
		// 如果 allSupportedMediaTypes 非空，则遍历 HttpMessageConverter 数组，进行类型匹配
		else if (!this.allSupportedMediaTypes/* 所有支持的媒体类型 */.isEmpty()) {
			// 服务器可产出的媒体类型集合
			List<MediaType> result = new ArrayList<>();
			/**
			 * messageConverters：消息的转换器，我返回的一些数据值，要进行固定类型的转换，经过这些类型转换之后，我才能匹配到我具体返回的mediaType有哪些
			 */
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				/**
				 * 1、MappingJackson2XmlHttpMessageConverter：
				 * 与如下的几个jar包相关，在spring mvc里面默认支持这些东西
				 * compile 'com.fasterxml.jackson.core:jackson-databind:2.12.3'
				 * compile 'com.fasterxml.jackson.core:jackson-annotations:2.12.3'
				 * compile 'com.fasterxml.jackson.core:jackson-core:2.12.3'
				 */
				if (converter instanceof GenericHttpMessageConverter && targetType != null) {
					// ⚠️判断http消息转换器，是否可以对返回值进行编写和响应，如果支持，就把获取该http消息转换器支持的媒体类型，放到服务器可产出的媒体类型集合中
					if (((GenericHttpMessageConverter<?>) converter).canWrite(targetType, valueClass, null)) {
						result.addAll(converter.getSupportedMediaTypes()/* 获取支持的媒体类型 */);
					}
				}
				// ⚠️判断http消息转换器，是否可以对返回值进行编写和响应，如果支持，就把获取该http消息转换器所有支持的媒体类型，放到服务器可产出的媒体类型集合中
				else if (converter.canWrite(valueClass, null)) {
					result.addAll(converter.getSupportedMediaTypes());
				}
			}
			return result;
		}
		// 其它，则返回 MediaType.ALL
		else {
			return Collections.singletonList(MediaType.ALL);
		}
	}

	private List<MediaType> getAcceptableMediaTypes(HttpServletRequest request)
			throws HttpMediaTypeNotAcceptableException {

		return this.contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
	}

	/**
	 * Return the more specific of the acceptable and the producible media types
	 * with the q-value of the former.
	 */
	private MediaType getMostSpecificMediaType(MediaType acceptType, MediaType produceType) {
		MediaType produceTypeToUse = produceType.copyQualityValue(acceptType);
		return (MediaType.SPECIFICITY_COMPARATOR.compare(acceptType, produceTypeToUse) <= 0 ? acceptType : produceTypeToUse);
	}

	/**
	 * Check if the path has a file extension and whether the extension is either
	 * on the list of {@link #SAFE_EXTENSIONS safe extensions} or explicitly
	 * {@link ContentNegotiationManager#getAllFileExtensions() registered}.
	 * If not, and the status is in the 2xx range, a 'Content-Disposition'
	 * header with a safe attachment file name ("f.txt") is added to prevent
	 * RFD exploits.
	 */
	private void addContentDispositionHeader(ServletServerHttpRequest request, ServletServerHttpResponse response) {
		HttpHeaders headers = response.getHeaders();
		if (headers.containsKey(HttpHeaders.CONTENT_DISPOSITION/* Content-Disposition */)) {
			return;
		}

		try {
			int status = response.getServletResponse().getStatus();
			if (status < 200 || status > 299) {
				return;
			}
		}
		catch (Throwable ex) {
			// ignore
		}

		HttpServletRequest servletRequest = request.getServletRequest();
		String requestUri = rawUrlPathHelper.getOriginatingRequestUri(servletRequest);

		int index = requestUri.lastIndexOf('/') + 1;
		String filename = requestUri.substring(index);
		String pathParams = "";

		index = filename.indexOf(';');
		if (index != -1) {
			pathParams = filename.substring(index);
			filename = filename.substring(0, index);
		}

		filename = decodingUrlPathHelper.decodeRequestString(servletRequest, filename);
		String ext = StringUtils.getFilenameExtension(filename);

		pathParams = decodingUrlPathHelper.decodeRequestString(servletRequest, pathParams);
		String extInPathParams = StringUtils.getFilenameExtension(pathParams);

		if (!safeExtension(servletRequest, ext) || !safeExtension(servletRequest, extInPathParams)) {
			headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt");
		}
	}

	@SuppressWarnings("unchecked")
	private boolean safeExtension(HttpServletRequest request, @Nullable String extension) {
		if (!StringUtils.hasText(extension)) {
			return true;
		}
		extension = extension.toLowerCase(Locale.ENGLISH);
		if (this.safeExtensions.contains(extension)) {
			return true;
		}
		String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (pattern != null && pattern.endsWith("." + extension)) {
			return true;
		}
		if (extension.equals("html")) {
			String name = HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE;
			Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(name);
			if (!CollectionUtils.isEmpty(mediaTypes) && mediaTypes.contains(MediaType.TEXT_HTML)) {
				return true;
			}
		}
		MediaType mediaType = resolveMediaType(request, extension);
		return (mediaType != null && (safeMediaType(mediaType)));
	}

	@Nullable
	private MediaType resolveMediaType(ServletRequest request, String extension) {
		MediaType result = null;
		String rawMimeType = request.getServletContext().getMimeType("file." + extension);
		if (StringUtils.hasText(rawMimeType)) {
			result = MediaType.parseMediaType(rawMimeType);
		}
		if (result == null || MediaType.APPLICATION_OCTET_STREAM.equals(result)) {
			result = MediaTypeFactory.getMediaType("file." + extension).orElse(null);
		}
		return result;
	}

	private boolean safeMediaType(MediaType mediaType) {
		return (SAFE_MEDIA_BASE_TYPES.contains(mediaType.getType()) ||
				mediaType.getSubtype().endsWith("+xml"));
	}

}
