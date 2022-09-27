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

package org.springframework.http.converter.json;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.*;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.TypeUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for Jackson based and content type independent
 * {@link HttpMessageConverter} implementations.
 *
 * <p>Compatible with Jackson 2.9 and higher, as of Spring 5.0.
 *
 * @author Arjen Poutsma
 * @author Keith Donald
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 4.1
 * @see MappingJackson2HttpMessageConverter
 */
public abstract class AbstractJackson2HttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

	private static final Map<String, JsonEncoding> ENCODINGS;

	static {
		ENCODINGS = new HashMap<>(JsonEncoding.values().length + 1);
		for (JsonEncoding encoding : JsonEncoding.values()) {
			ENCODINGS.put(encoding.getJavaName(), encoding);
		}
		ENCODINGS.put("US-ASCII", JsonEncoding.UTF8);
	}


	/**
	 * The default charset used by the converter.
	 */
	@Nullable
	@Deprecated
	public static final Charset DEFAULT_CHARSET = null;


	protected ObjectMapper objectMapper;

	@Nullable
	private Boolean prettyPrint;

	@Nullable
	private PrettyPrinter ssePrettyPrinter;


	protected AbstractJackson2HttpMessageConverter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
		prettyPrinter.indentObjectsWith(new DefaultIndenter("  ", "\ndata:"));
		this.ssePrettyPrinter = prettyPrinter;
	}

	protected AbstractJackson2HttpMessageConverter(ObjectMapper objectMapper, MediaType supportedMediaType) {
		this(objectMapper);
		setSupportedMediaTypes(Collections.singletonList(supportedMediaType));
	}

	protected AbstractJackson2HttpMessageConverter(ObjectMapper objectMapper, MediaType... supportedMediaTypes) {
		this(objectMapper);
		setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
	}


	/**
	 * Set the {@code ObjectMapper} for this view.
	 * If not set, a default {@link ObjectMapper#ObjectMapper() ObjectMapper} is used.
	 * <p>Setting a custom-configured {@code ObjectMapper} is one way to take further
	 * control of the JSON serialization process. For example, an extended
	 * {@link com.fasterxml.jackson.databind.ser.SerializerFactory}
	 * can be configured that provides custom serializers for specific types.
	 * The other option for refining the serialization process is to use Jackson's
	 * provided annotations on the types to be serialized, in which case a
	 * custom-configured ObjectMapper is unnecessary.
	 */
	public void setObjectMapper(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.objectMapper = objectMapper;
		configurePrettyPrint();
	}

	/**
	 * Return the underlying {@code ObjectMapper} for this view.
	 */
	public ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	/**
	 * Whether to use the {@link DefaultPrettyPrinter} when writing JSON.
	 * This is a shortcut for setting up an {@code ObjectMapper} as follows:
	 * <pre class="code">
	 * ObjectMapper mapper = new ObjectMapper();
	 * mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
	 * converter.setObjectMapper(mapper);
	 * </pre>
	 */
	public void setPrettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
		configurePrettyPrint();
	}

	private void configurePrettyPrint() {
		if (this.prettyPrint != null) {
			this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, this.prettyPrint);
		}
	}


	@Override
	public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
		return canRead(clazz, null, mediaType);
	}

	@Override
	public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
		if (!canRead(mediaType)) {
			return false;
		}
		// 获得方法入参的类型
		JavaType javaType = getJavaType(type, contextClass);
		AtomicReference<Throwable> causeRef = new AtomicReference<>();
		// 通过 ObjectMapper 判断是否能够反序列化
		if (this.objectMapper.canDeserialize(javaType, causeRef)) {
			return true;
		}
		logWarningIfNecessary(javaType, causeRef.get());
		return false;
	}

	@Override
	public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
		// 判断是否支持该 MediaType，也就是 Content-Type
		if (!canWrite(mediaType)) {
			return false;
		}
		if (mediaType != null && mediaType.getCharset() != null) {
			Charset charset = mediaType.getCharset();
			if (!ENCODINGS.containsKey(charset.name())) {
				return false;
			}
		}
		AtomicReference<Throwable> causeRef = new AtomicReference<>();
		// 通过 ObjectMapper 判断是否能够序列化
		if (this.objectMapper.canSerialize(clazz, causeRef)) {
			return true;
		}
		logWarningIfNecessary(clazz, causeRef.get());
		return false;
	}

	/**
	 * Determine whether to log the given exception coming from a
	 * {@link ObjectMapper#canDeserialize} / {@link ObjectMapper#canSerialize} check.
	 * @param type the class that Jackson tested for (de-)serializability
	 * @param cause the Jackson-thrown exception to evaluate
	 * (typically a {@link JsonMappingException})
	 * @since 4.3
	 */
	protected void logWarningIfNecessary(Type type, @Nullable Throwable cause) {
		if (cause == null) {
			return;
		}

		// Do not log warning for serializer not found (note: different message wording on Jackson 2.9)
		boolean debugLevel = (cause instanceof JsonMappingException && cause.getMessage().startsWith("Cannot find"));

		if (debugLevel ? logger.isDebugEnabled() : logger.isWarnEnabled()) {
			String msg = "Failed to evaluate Jackson " + (type instanceof JavaType ? "de" : "") +
					"serialization for type [" + type + "]";
			if (debugLevel) {
				logger.debug(msg, cause);
			}
			else if (logger.isDebugEnabled()) {
				logger.warn(msg, cause);
			}
			else {
				logger.warn(msg + ": " + cause);
			}
		}
	}

	@Override
	protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {

		JavaType javaType = getJavaType(clazz, null);
		return readJavaType(javaType, inputMessage);
	}

	@Override
	public Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {

		// 获得方法入参的类型
		JavaType javaType = getJavaType(type, contextClass);
		// 从请求中读取该类型的方法入参
		return readJavaType(javaType, inputMessage);
	}

	private Object readJavaType(JavaType javaType, HttpInputMessage inputMessage) throws IOException {
		MediaType contentType = inputMessage.getHeaders().getContentType();
		Charset charset = getCharset(contentType);

		boolean isUnicode = ENCODINGS.containsKey(charset.name());
		try {
			// 如果请求是 MappingJacksonInputMessage 类型，默认不是
			if (inputMessage instanceof MappingJacksonInputMessage) {
				Class<?> deserializationView = ((MappingJacksonInputMessage) inputMessage).getDeserializationView();
				if (deserializationView != null) {
					ObjectReader objectReader = this.objectMapper.readerWithView(deserializationView).forType(javaType);
					if (isUnicode) {
						return objectReader.readValue(inputMessage.getBody());
					}
					else {
						Reader reader = new InputStreamReader(inputMessage.getBody(), charset);
						return objectReader.readValue(reader);
					}
				}
			}
			if (isUnicode) {
				// 通过 ObjectMapper 从请求中读取该类型的方法入参
				return this.objectMapper.readValue(inputMessage.getBody(), javaType);
			}
			else {
				Reader reader = new InputStreamReader(inputMessage.getBody(), charset);
				return this.objectMapper.readValue(reader, javaType);
			}
		}
		catch (InvalidDefinitionException ex) {
			throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
		}
		catch (JsonProcessingException ex) {
			throw new HttpMessageNotReadableException("JSON parse error: " + ex.getOriginalMessage(), ex, inputMessage);
		}
	}

	private static Charset getCharset(@Nullable MediaType contentType) {
		if (contentType != null && contentType.getCharset() != null) {
			return contentType.getCharset();
		}
		else {
			return StandardCharsets.UTF_8;
		}
	}

	@Override
	protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {

		// 获取编码方式
		// 获取 Content-Type，例如 `application/json;charset=UTF-8`
		MediaType contentType = outputMessage.getHeaders().getContentType();
		// 从 Content-Type 获取编码方式，默认 UTF8
		JsonEncoding encoding = getJsonEncoding(contentType);
		// 构建一个 Json 生成器 `generator`，指定`输出流（响应）`和编码
		// 例如：UTF8JsonGenerator 对象（jackson-core 包）
		// 题外：要进行IO操作，所以获取当前的一个生成器
		JsonGenerator generator = this.objectMapper.getFactory().createGenerator(outputMessage.getBody(), encoding);
		try {
			// 设置前缀，默认没有
			writePrefix(generator, object);

			// 获得方法的返回结果对象 `value`，返回结果类型 `javaType`
			Object value = object;
			// 序列化视图
			Class<?> serializationView = null;
			FilterProvider filters = null;
			JavaType javaType = null;

			// 如果返回结果对象是 MappingJacksonValue 类型，没使用过
			if (object instanceof MappingJacksonValue) {
				MappingJacksonValue container = (MappingJacksonValue) object;
				value = container.getValue();
				serializationView = container.getSerializationView();
				filters = container.getFilters();
			}
			// 获取方法的返回结果的类型 `javaType`
			if (type != null && TypeUtils.isAssignable(type, value.getClass())) {
				javaType = getJavaType(type, null);
			}

			// 创建 ObjectWriter 对象 `objectWriter`，没有特殊配置通过 `this.objectMapper.writer()` 生成
			ObjectWriter objectWriter = (serializationView != null ?
					this.objectMapper.writerWithView(serializationView) : this.objectMapper.writer());
			if (filters != null) {
				objectWriter = objectWriter.with(filters);
			}
			if (javaType != null && javaType.isContainerType()) {
				objectWriter = objectWriter.forType(javaType);
			}
			// 获取序列化配置
			SerializationConfig config = objectWriter.getConfig();
			if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM) &&
					config.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
				objectWriter = objectWriter.with(this.ssePrettyPrinter);
			}
			// 通过我们的生成器generator，来写进去我们的值
			// **【重点】**通过 `objectWriter` 将返回结果进行序列化，设置到 `generator` 中
			objectWriter.writeValue(generator, value);

			// 设置后缀，默认没有
			writeSuffix(generator, object);
			// 让 `generator` 刷出数据，以 Json 格式输出，也就是会往响应中刷出 Json 格式的返回结果
			generator.flush();
		}
		catch (InvalidDefinitionException ex) {
			throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
		}
		catch (JsonProcessingException ex) {
			throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getOriginalMessage(), ex);
		}
	}

	/**
	 * Write a prefix before the main content.
	 * @param generator the generator to use for writing content.
	 * @param object the object to write to the output message.
	 */
	protected void writePrefix(JsonGenerator generator, Object object) throws IOException {
	}

	/**
	 * Write a suffix after the main content.
	 * @param generator the generator to use for writing content.
	 * @param object the object to write to the output message.
	 */
	protected void writeSuffix(JsonGenerator generator, Object object) throws IOException {
	}

	/**
	 * Return the Jackson {@link JavaType} for the specified type and context class.
	 * @param type the generic type to return the Jackson JavaType for
	 * @param contextClass a context class for the target type, for example a class
	 * in which the target type appears in a method signature (can be {@code null})
	 * @return the Jackson JavaType
	 */
	protected JavaType getJavaType(Type type, @Nullable Class<?> contextClass) {
		TypeFactory typeFactory = this.objectMapper.getTypeFactory();
		return typeFactory.constructType(GenericTypeResolver.resolveType(type, contextClass));
	}

	/**
	 * Determine the JSON encoding to use for the given content type.
	 * @param contentType the media type as requested by the caller
	 * @return the JSON encoding to use (never {@code null})
	 */
	protected JsonEncoding getJsonEncoding(@Nullable MediaType contentType) {
		if (contentType != null && contentType.getCharset() != null) {
			Charset charset = contentType.getCharset();
			JsonEncoding encoding = ENCODINGS.get(charset.name());
			if (encoding != null) {
				return encoding;
			}
		}
		return JsonEncoding.UTF8;
	}

	@Override
	@Nullable
	protected MediaType getDefaultContentType(Object object) throws IOException {
		if (object instanceof MappingJacksonValue) {
			object = ((MappingJacksonValue) object).getValue();
		}
		return super.getDefaultContentType(object);
	}

	@Override
	protected Long getContentLength(Object object, @Nullable MediaType contentType) throws IOException {
		if (object instanceof MappingJacksonValue) {
			object = ((MappingJacksonValue) object).getValue();
		}
		return super.getContentLength(object, contentType);
	}

}