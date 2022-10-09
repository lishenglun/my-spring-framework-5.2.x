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

package org.springframework.core.convert.support;

import org.springframework.core.DecoratingProxy;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.*;
import org.springframework.core.convert.converter.*;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.*;

/**
 * 是一个通用的类型转换的基本实现，适用于大部分的情况
 *
 * Base {@link ConversionService} implementation suitable for use in most environments.
 * Indirectly implements {@link ConverterRegistry} as registration API through the
 * {@link ConfigurableConversionService} interface.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author David Haraburda
 * @since 3.0
 */
public class GenericConversionService implements ConfigurableConversionService {

	/**
	 * 不需要转换时使用的转换器
	 *
	 * General NO-OP converter used when conversion is not required. —— 不需要转换时使用的通用NO-OP转换器
	 */
	private static final GenericConverter NO_OP_CONVERTER = new NoOpConverter("NO_OP");

	/**
	 * 原始类型和目标类型没有转换器的标识
	 *
	 * Used as a cache entry when no converter is available.
	 * This converter is never returned.
	 *
	 * 当没有转换器可用时用作缓存条目。这个转换器永远不会被退回。
	 */
	private static final GenericConverter NO_MATCH = new NoOpConverter("NO_MATCH");

	// 管理在服务中注册的所有转换器
	private final Converters converters = new Converters();

	// 类型转换器缓存
	// key：原始类型和目标类型组成的key
	// value：GenericConverter
	private final Map<ConverterCacheKey, GenericConverter> converterCache = new ConcurrentReferenceHashMap<>(64);

	/* -------------------------------- 注册转换器 -------------------------------- */

	// ConverterRegistry implementation

	/**
	 * 注册Converter
	 *
	 * @param converter				Converter
	 */
	@Override
	public void addConverter(Converter<?, ?> converter) {
		// 获取Converter Class中的泛型，也就是获取对应的原始类型和目标类型
		ResolvableType[] typeInfo = getRequiredTypeInfo/* 获取所需的类型信息 */(converter.getClass(), Converter.class);

		if (typeInfo == null && converter instanceof DecoratingProxy) {
			typeInfo = getRequiredTypeInfo(((DecoratingProxy) converter).getDecoratedClass(), Converter.class);
		}
		if (typeInfo == null) {
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your " +
					"Converter [" + converter.getClass().getName() + "]; does the class parameterize those types?");
		}

		// 用ConverterAdapter适配一下Converter，然后注册ConverterAdapter
		// 题外：ConverterAdapter间接实现GenericConverter
		addConverter(new ConverterAdapter(converter/* 转换器 */, typeInfo[0]/* 原始类型 */, typeInfo[1]/* 目标类型 */));
	}

	/**
	 * 注册Converter
	 *
	 * @param sourceType			原始类型
	 * @param targetType			目标类型
	 * @param converter				Converter
	 */
	@Override
	public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter) {
		addConverter(new ConverterAdapter(
				converter, ResolvableType.forClass(sourceType), ResolvableType.forClass(targetType)));
	}

	/**
	 * 注册GenericConverter
	 *
	 * @param converter			GenericConverter
	 */
	@Override
	public void addConverter(GenericConverter converter) {
		// 添加转换器
		this.converters.add(converter);
		invalidateCache();
	}

	/**
	 * 注册ConverterFactory
	 *
	 * @param factory			ConverterFactory
	 */
	@Override
	public void addConverterFactory(ConverterFactory<?, ?> factory) {
		// 获取ConverterFactory Class中的泛型，也就是获取对应的原始类型和目标类型
		ResolvableType[] typeInfo = getRequiredTypeInfo(factory.getClass(), ConverterFactory.class);

		if (typeInfo == null && factory instanceof DecoratingProxy) {
			typeInfo = getRequiredTypeInfo(((DecoratingProxy) factory).getDecoratedClass(), ConverterFactory.class);
		}

		if (typeInfo == null) {
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your " +
					"ConverterFactory [" + factory.getClass().getName() + "]; does the class parameterize those types?");
		}

		// 用ConverterFactoryAdapter适配一下ConverterFactory，然后注册ConverterFactoryAdapter
		// 题外：ConverterFactoryAdapter间接实现GenericConverter
		addConverter(new ConverterFactoryAdapter(factory/* 转换器工厂 */,
				new ConvertiblePair(typeInfo[0].toClass()/* 目标类型 */, typeInfo[1].toClass()/* 原始类型 */)));
	}

	@Override
	public void removeConvertible(Class<?> sourceType, Class<?> targetType) {
		this.converters.remove(sourceType, targetType);
		invalidateCache();
	}


	/* -------------------------------- 是否能够执行转换器 -------------------------------- */


	// ConversionService implementation

	@Override
	public boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		return canConvert((sourceType != null ? TypeDescriptor.valueOf(sourceType) : null),
				TypeDescriptor.valueOf(targetType));
	}

	@Override
	public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			return true;
		}
		GenericConverter converter = getConverter(sourceType, targetType);
		return (converter != null);
	}

	/**
	 * Return whether conversion between the source type and the target type can be bypassed.
	 * <p>More precisely, this method will return true if objects of sourceType can be
	 * converted to the target type by returning the source object unchanged.
	 * @param sourceType context about the source type to convert from
	 * (may be {@code null} if source is {@code null})
	 * @param targetType context about the target type to convert to (required)
	 * @return {@code true} if conversion can be bypassed; {@code false} otherwise
	 * @throws IllegalArgumentException if targetType is {@code null}
	 * @since 3.2
	 */
	public boolean canBypassConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			return true;
		}
		GenericConverter converter = getConverter(sourceType, targetType);
		return (converter == NO_OP_CONVERTER);
	}

	/* -------------------------------- 执行转换器 -------------------------------- */

	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T convert(@Nullable Object source, Class<T> targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		return (T) convert(source, TypeDescriptor.forObject(source), TypeDescriptor.valueOf(targetType));
	}

	/**
	 * ️从注册的转换器当中，通过原始类型和目标类型获取一个GenericConverter，然后通过GenericConverter转换数据，变成目标类型数据返回
	 *
	 * @param source 			原始数据
	 * @param sourceType 		原始类型
	 * @param targetType 		目标类型
	 * @return	转换器转换后的目标类型对象
	 */
	@Override
	@Nullable
	public Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			Assert.isTrue(source == null, "Source must be [null] if source type == [null]");
			return handleResult(null, targetType, convertNullSource(null, targetType));
		}
		if (source != null && !sourceType.getObjectType().isInstance(source)) {
			throw new IllegalArgumentException("Source to convert from must be an instance of [" +
					sourceType + "]; instead it was a [" + source.getClass().getName() + "]");
		}

		/* 1、⚠️从注册的转换器当中，通过源类型和目标类型，获取一个GenericConverter */
		GenericConverter converter = getConverter(sourceType, targetType);

		/* 2、有GenericConverter */
		if (converter != null) {

			/* (1)⚠️调用转换器，转换数据，变成目标类型对象 */
			Object result = ConversionUtils.invokeConverter(converter, source, sourceType, targetType);

			/* (2)返回转换后的目标类型对象 */
			return handleResult(sourceType, targetType, result);
		}

		/* 3、处理没找到GenericConverter的情况 */
		return handleConverterNotFound(source, sourceType, targetType);
	}

	/**
	 * Convenience operation for converting a source object to the specified targetType,
	 * where the target type is a descriptor that provides additional conversion context.
	 * Simply delegates to {@link #convert(Object, TypeDescriptor, TypeDescriptor)} and
	 * encapsulates the construction of the source type descriptor using
	 * {@link TypeDescriptor#forObject(Object)}.
	 * @param source the source object
	 * @param targetType the target type
	 * @return the converted value
	 * @throws ConversionException if a conversion exception occurred
	 * @throws IllegalArgumentException if targetType is {@code null},
	 * or sourceType is {@code null} but source is not {@code null}
	 */
	@Nullable
	public Object convert(@Nullable Object source, TypeDescriptor targetType) {
		return convert(source, TypeDescriptor.forObject(source), targetType);
	}

	@Override
	public String toString() {
		return this.converters.toString();
	}


	// Protected template methods

	/**
	 * Template method to convert a {@code null} source.
	 * <p>The default implementation returns {@code null} or the Java 8
	 * {@link java.util.Optional#empty()} instance if the target type is
	 * {@code java.util.Optional}. Subclasses may override this to return
	 * custom {@code null} objects for specific target types.
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the converted null object
	 */
	@Nullable
	protected Object convertNullSource(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (targetType.getObjectType() == Optional.class) {
			return Optional.empty();
		}
		return null;
	}

	/**
	 * 通过源类型和目标类型获取一个GenericConverter
	 *
	 * 题外：我们的Converter、GenericConverter、ConverterFactory都会转换成GenericConverter，
	 * 所以返回一个GenericConverter，代表了Converter、GenericConverter、ConverterFactory中的任一一个
	 *
	 * Hook method to lookup the converter for a given sourceType/targetType pair.
	 * First queries this ConversionService's converter cache.
	 * On a cache miss, then performs an exhaustive search for a matching converter.
	 * If no converter matches, returns the default converter.
	 * @param sourceType the source type to convert from
	 *                   源类型
	 * @param targetType the target type to convert to
	 *                   目标类型
	 * @return the generic converter that will perform the conversion,
	 * or {@code null} if no suitable converter was found
	 * @see #getDefaultConverter(TypeDescriptor, TypeDescriptor)
	 */
	@Nullable
	protected GenericConverter getConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
		/* 1、先从缓存中获取 */
		// 原始类型和目标类型组成一个缓存key
		ConverterCacheKey key = new ConverterCacheKey(sourceType, targetType);
		// 先从缓存中获取
		GenericConverter converter = this.converterCache.get(key);
		// 缓存中获取到了，就直接返回
		if (converter != null) {
			return (converter != NO_MATCH ? converter : null);
		}

		/* 2、缓存中没有，去Converters中获取转换器 */
		// 缓存中没有，去Converters中获取转换器
		// ⚠️根据原始类型层次结构和目标类型层次结构，挨个组合成"原始类型和目标类型键值对"，然后获取对应"原始类型和目标类型"的Converter
		converter = this.converters.find(sourceType, targetType);

		/* 3、Converters中没有获取到转换器，就去判断，如果原始类型是目标类型的子类，或者和目标类型相同，则返回"不需要转换时使用的转换器"；否则返回null */
		if (converter == null) {
			converter = getDefaultConverter(sourceType, targetType);
		}

		/* 4、有转换器，就放入缓存；然后返回转换器 */
		if (converter != null) {
			this.converterCache.put(key, converter);
			return converter;
		}

		/* 5、没有转换器，就放一个"原始类型和目标类型没有转换器的标识"；然后返回null */
		this.converterCache.put(key, NO_MATCH);
		return null;
	}

	/**
	 * 原始类型是目标类型的子类，或者和目标类型相同，则返回"不需要转换时使用的转换器"；否则返回null
	 *
	 * Return the default converter if no converter is found for the given sourceType/targetType pair.
	 * <p>Returns a NO_OP Converter if the source type is assignable to the target type.
	 * Returns {@code null} otherwise, indicating no suitable converter could be found.
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the default generic converter that will perform the conversion
	 */
	@Nullable
	protected GenericConverter getDefaultConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
		// 原始类型是目标类型的子类，或者和目标类型相同，则返回"不需要转换时使用的转换器"；否则返回null
		return (sourceType.isAssignableTo(targetType) ? NO_OP_CONVERTER/* 不需要转换时使用的转换器 */ : null);
	}


	// Internal helpers

	/**
	 * 获取Converter Class中的泛型
	 *
	 * @param converterClass
	 * @param genericIfc
	 * @return
	 */
	@Nullable
	private ResolvableType[] getRequiredTypeInfo(Class<?> converterClass, Class<?> genericIfc) {
		ResolvableType resolvableType = ResolvableType.forClass(converterClass).as(genericIfc);
		// 获取Converter Class中的泛型
		ResolvableType[] generics = resolvableType.getGenerics/* 获取泛型 */();

		// 检查一下，如果泛型个数小于2个，证明不符合要求，就返回null
		if (generics.length < 2) {
			return null;
		}

		// 检查一下，如果泛型当中，索引0和1有为nul的，也是不符合要求的，所以返回null
		Class<?> sourceType = generics[0].resolve();
		Class<?> targetType = generics[1].resolve();
		if (sourceType == null || targetType == null) {
			return null;
		}

		// 检查通过，没有问题，直接返回获取到的泛型
		return generics;
	}

	private void invalidateCache() {
		this.converterCache.clear();
	}

	@Nullable
	private Object handleConverterNotFound(
			@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {

		if (source == null) {
			assertNotPrimitiveTargetType(sourceType, targetType);
			return null;
		}
		if ((sourceType == null || sourceType.isAssignableTo(targetType)) &&
				targetType.getObjectType().isInstance(source)) {
			return source;
		}
		throw new ConverterNotFoundException(sourceType, targetType);
	}

	@Nullable
	private Object handleResult(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType, @Nullable Object result) {
		if (result == null) {
			assertNotPrimitiveTargetType/* 断言非原始目标类型 */(sourceType, targetType);
		}
		return result;
	}

	private void assertNotPrimitiveTargetType(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (targetType.isPrimitive/* 是原始的 */()) {
			throw new ConversionFailedException(sourceType, targetType, null,
					new IllegalArgumentException("A null value cannot be assigned to a primitive type"));
		}
	}


	/**
	 * 将convert适配为GenericConverter
	 *
	 * Adapts a {@link Converter} to a {@link GenericConverter}. —— 将{@link Converter}适配为{@link GenericConverter}。
	 */
	@SuppressWarnings("unchecked")
	private final class ConverterAdapter implements ConditionalGenericConverter {

		// 转换器
		private final Converter<Object, Object> converter;

		// 原始类型和目标类型组成的键值对
		private final ConvertiblePair typeInfo;

		// 目标类型
		private final ResolvableType targetType;

		/**
		 * ConverterAdapter
		 *
		 * @param converter			转换器
		 * @param sourceType		原始类型
		 * @param targetType		目标类型
		 */
		public ConverterAdapter(Converter<?, ?> converter, ResolvableType sourceType, ResolvableType targetType) {
			// 转换器
			this.converter = (Converter<Object, Object>) converter;
			// 原始类型和目标类型组成的键值对
			this.typeInfo = new ConvertiblePair(sourceType.toClass(), targetType.toClass());
			// 目标类型
			this.targetType = targetType;
		}

		/**
		 * 获取原始类型和目标类型组成的键值对
		 */
		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(this.typeInfo);
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// Check raw type first...
			if (this.typeInfo.getTargetType() != targetType.getObjectType()) {
				return false;
			}
			// Full check for complex generic type match required?
			ResolvableType rt = targetType.getResolvableType();
			if (!(rt.getType() instanceof Class) && !rt.isAssignableFrom(this.targetType) &&
					!this.targetType.hasUnresolvableGenerics()) {
				return false;
			}
			return !(this.converter instanceof ConditionalConverter) ||
					((ConditionalConverter) this.converter).matches(sourceType, targetType);
		}

		/**
		 * 调用真实的转换器，转换数据，变为目标类型对象返回
		 *
		 * @param source 			原始数据
		 * @param sourceType 		原始类型
		 * @param targetType 		目标类型
		 */
		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			if (source == null) {
				return convertNullSource(sourceType, targetType);
			}
			// ⚠️调用真实的转换器，转换数据，变为目标类型对象返回
			return this.converter.convert(source);
		}

		@Override
		public String toString() {
			return (this.typeInfo + " : " + this.converter);
		}
	}


	/**
	 * 将ConverterFactory适配为GenericConverter
	 *
	 * Adapts a {@link ConverterFactory} to a {@link GenericConverter}.
	 */
	@SuppressWarnings("unchecked")
	private final class ConverterFactoryAdapter implements ConditionalGenericConverter {

		private final ConverterFactory<Object, Object> converterFactory;

		private final ConvertiblePair typeInfo;

		public ConverterFactoryAdapter(ConverterFactory<?, ?> converterFactory, ConvertiblePair typeInfo) {
			this.converterFactory = (ConverterFactory<Object, Object>) converterFactory;
			this.typeInfo = typeInfo;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(this.typeInfo);
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			boolean matches = true;
			if (this.converterFactory instanceof ConditionalConverter) {
				matches = ((ConditionalConverter) this.converterFactory).matches(sourceType, targetType);
			}
			if (matches) {
				Converter<?, ?> converter = this.converterFactory.getConverter(targetType.getType());
				if (converter instanceof ConditionalConverter) {
					matches = ((ConditionalConverter) converter).matches(sourceType, targetType);
				}
			}
			return matches;
		}

		/**
		 * 先通过converterFactory获取转换器，然后转换数据，变为目标类型对象返回
		 *
		 * @param source 				原始数据
		 * @param sourceType 			原始类型
		 * @param targetType 			目标类型
		 * @return
		 */
		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 如果数据为空，就返回null，或者是Optional类型的话，就返回Optional.empty()
			if (source == null) {
				return convertNullSource(sourceType, targetType);
			}

			// 先通过converterFactory获取转换器，然后转换数据，变为目标类型对象返回
			return this.converterFactory.getConverter(targetType.getObjectType()).convert(source);
		}

		@Override
		public String toString() {
			return (this.typeInfo + " : " + this.converterFactory);
		}
	}


	/**
	 * 与转换器缓存一起使用的key
	 *
	 * Key for use with the converter cache. —— 与转换器缓存一起使用的密钥。
	 */
	private static final class ConverterCacheKey implements Comparable<ConverterCacheKey> {

		private final TypeDescriptor sourceType;

		private final TypeDescriptor targetType;

		public ConverterCacheKey(TypeDescriptor sourceType, TypeDescriptor targetType) {
			this.sourceType = sourceType;
			this.targetType = targetType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ConverterCacheKey)) {
				return false;
			}
			ConverterCacheKey otherKey = (ConverterCacheKey) other;
			return (this.sourceType.equals(otherKey.sourceType)) &&
					this.targetType.equals(otherKey.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() * 29 + this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return ("ConverterCacheKey [sourceType = " + this.sourceType +
					", targetType = " + this.targetType + "]");
		}

		@Override
		public int compareTo(ConverterCacheKey other) {
			int result = this.sourceType.getResolvableType().toString().compareTo(
					other.sourceType.getResolvableType().toString());
			if (result == 0) {
				result = this.targetType.getResolvableType().toString().compareTo(
						other.targetType.getResolvableType().toString());
			}
			return result;
		}
	}


	/**
	 * 存放所有的转换器
	 *
	 * Manages all converters registered with the service. —— 管理向服务注册的所有转换器
	 */
	private static class Converters {

		// 全局转换器集合
		private final Set<GenericConverter> globalConverters/* 全局转换器 */ = new LinkedHashSet<>();

		// 按"原始类型和目标类型"分组存放转换器的集合
		// key：ConvertiblePair：原始类型和目标类型组成的键值对
		// value：ConvertersForPair：存放相同原始类型和目标类型的转换器
		private final Map<ConvertiblePair, ConvertersForPair> converters = new LinkedHashMap<>(36);

		/**
		 * 注册转换器
		 *
		 * @param converter			转换器
		 *                          1、Converter的话，{@link GenericConversionService.ConverterAdapter}
		 *                          2、ConverterFactory的话，{@link GenericConversionService.ConverterFactoryAdapter}
		 *                          2、GenericConverter的话，不变
		 */
		public void add(GenericConverter converter) {
			// 获取"原始类型和目标类型组成的键值对"集合
			// 题外：虽然得到的是一个集合，但是一般只有一个元素，例如：GenericConversionService.ConverterAdapter#getConvertibleTypes()，固定只有一个元素！
			Set<ConvertiblePair> convertibleTypes/* 可转换类型 */ = converter.getConvertibleTypes();

			// 没有原始类型和目标类型，那么就将这个转换器，添加到全局转换器集合当中
			if (convertibleTypes == null) {
				Assert.state(converter instanceof ConditionalConverter,
						"Only conditional converters may return null convertible types");
				this.globalConverters.add(converter);
			}
			// 有原始类型和目标类型，就添加到非全局转换器集合当中
			else {
				for (ConvertiblePair convertiblePair : convertibleTypes) {
					// 获取存放"相同原始类型和目标类型"的ConvertersForPair
					ConvertersForPair convertersForPair = getMatchableConverters(convertiblePair);
					// 加转换器添加到ConvertersForPair中
					convertersForPair.add(converter);
				}
			}
		}

		/**
		 * 获取存放"相同原始类型和目标类型"的ConvertersForPair
		 *
		 * @param convertiblePair		原始类型和目标类型组成的键值对
		 */
		private ConvertersForPair getMatchableConverters(ConvertiblePair convertiblePair) {
			// 不存在convertiblePair，就创建对应的ConvertersForPair，然后注册到converters中
			// 存在convertiblePair，就获取对应的ConvertersForPair
			return this.converters.computeIfAbsent(convertiblePair, k -> new ConvertersForPair());
		}

		public void remove(Class<?> sourceType, Class<?> targetType) {
			this.converters.remove(new ConvertiblePair(sourceType, targetType));
		}

		/**
		 * 根据原始类型层次结构和目标类型层次结构，挨个组合成"原始类型和目标类型键值对"，然后获取对应"原始类型和目标类型"的Converter，找到一个就返回
		 *
		 * Find a {@link GenericConverter} given a source and target type.
		 * <p>This method will attempt to match all possible converters by working
		 * through the class and interface hierarchy of the types.
		 * @param sourceType the source type
		 * @param targetType the target type
		 * @return a matching {@link GenericConverter}, or {@code null} if none found
		 */
		@Nullable
		public GenericConverter find(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// Search the full type hierarchy —— 搜索完整的类型层次结构
			// 获取原始类型的层级结构
			List<Class<?>> sourceCandidates = getClassHierarchy/* 获取类层次结构 */(sourceType.getType());
			// 获取目标类型的层级结构
			List<Class<?>> targetCandidates = getClassHierarchy(targetType.getType());

			// 遍历原始类型的层级结构
			for (Class<?> sourceCandidate : sourceCandidates) {
				// 遍历目标类型的层级结构
				for (Class<?> targetCandidate : targetCandidates) {
					// 原始类型和目标类型组成的键值对组合
					ConvertiblePair convertiblePair = new ConvertiblePair(sourceCandidate, targetCandidate);
					// 通过原始类型和目标类型，获取Converter
					GenericConverter converter = getRegisteredConverter(sourceType, targetType, convertiblePair);
					// 获取到一个直接返回
					if (converter != null) {
						return converter;
					}
				}
			}
			return null;
		}

		/**
		 * 通过原始类型和目标类型，获取Converter
		 *
		 * @param sourceType			原始类型
		 * @param targetType			目标类型
		 * @param convertiblePair		原始类型和目标类型组成的键值对组合
		 * @return
		 */
		@Nullable
		private GenericConverter getRegisteredConverter(TypeDescriptor sourceType,
				TypeDescriptor targetType, ConvertiblePair convertiblePair) {

			/* 1、通过原始类型和目标类型组成的键值对组合，从"存放相同原始类型和目标类型的转换器集合"当中，获取满足【不是ConditionalGenericConverter条件类型的转换器 || 是ConditionalGenericConverter条件类型的转换器，但是条件匹配】条件的第一个转换器 */
			// Check specifically registered converters —— 检查专门注册的转换器
			// 通过原始类型和目标类型组成的键值对组合，获取"存放相同原始类型和目标类型的转换器对象"
			ConvertersForPair convertersForPair = this.converters.get(convertiblePair);
			if (convertersForPair != null) {
				// 从"存放相同原始类型和目标类型的转换器集合"当中，获取满足【不是ConditionalGenericConverter条件类型的转换器 || 是ConditionalGenericConverter条件类型的转换器，但是条件匹配】条件的第一个转换器
				GenericConverter converter = convertersForPair.getConverter(sourceType, targetType);
				if (converter != null) {
					return converter;
				}
			}

			/* 2、上面没有获取到，就从全局转换器集合中获取匹配原始类型和目标类型的转换器 */
			// Check ConditionalConverters for a dynamic match —— 检查ConditionalConverters以获取动态匹配
			for (GenericConverter globalConverter : this.globalConverters) {
				if (((ConditionalConverter) globalConverter).matches(sourceType, targetType)) {
					return globalConverter;
				}
			}
			return null;
		}

		/**
		 * Returns an ordered class hierarchy for the given type. —— 返回给定类型的有序类层次结构
		 *
		 * @param type the type
		 * @return an ordered list of all classes that the given type extends or implements
		 */
		private List<Class<?>> getClassHierarchy/* 获取类层次结构 */(Class<?> type) {
			List<Class<?>> hierarchy = new ArrayList<>(20);
			Set<Class<?>> visited = new HashSet<>(20);
			addToClassHierarchy(0, ClassUtils.resolvePrimitiveIfNecessary(type), false, hierarchy, visited);
			boolean array = type.isArray();

			int i = 0;
			while (i < hierarchy.size()) {
				Class<?> candidate = hierarchy.get(i);
				candidate = (array ? candidate.getComponentType() : ClassUtils.resolvePrimitiveIfNecessary(candidate));
				Class<?> superclass = candidate.getSuperclass();
				if (superclass != null && superclass != Object.class && superclass != Enum.class) {
					addToClassHierarchy(i + 1, candidate.getSuperclass(), array, hierarchy, visited);
				}
				addInterfacesToClassHierarchy(candidate, array, hierarchy, visited);
				i++;
			}

			if (Enum.class.isAssignableFrom(type)) {
				addToClassHierarchy(hierarchy.size(), Enum.class, array, hierarchy, visited);
				addToClassHierarchy(hierarchy.size(), Enum.class, false, hierarchy, visited);
				addInterfacesToClassHierarchy(Enum.class, array, hierarchy, visited);
			}

			addToClassHierarchy(hierarchy.size(), Object.class, array, hierarchy, visited);
			addToClassHierarchy(hierarchy.size(), Object.class, false, hierarchy, visited);
			return hierarchy;
		}

		private void addInterfacesToClassHierarchy(Class<?> type, boolean asArray,
				List<Class<?>> hierarchy, Set<Class<?>> visited) {

			for (Class<?> implementedInterface : type.getInterfaces()) {
				addToClassHierarchy(hierarchy.size(), implementedInterface, asArray, hierarchy, visited);
			}
		}

		private void addToClassHierarchy(int index, Class<?> type, boolean asArray,
				List<Class<?>> hierarchy, Set<Class<?>> visited) {

			if (asArray) {
				type = Array.newInstance(type, 0).getClass();
			}
			if (visited.add(type)) {
				hierarchy.add(index, type);
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ConversionService converters =\n");
			for (String converterString : getConverterStrings()) {
				builder.append('\t').append(converterString).append('\n');
			}
			return builder.toString();
		}

		private List<String> getConverterStrings() {
			List<String> converterStrings = new ArrayList<>();
			for (ConvertersForPair convertersForPair : this.converters.values()) {
				converterStrings.add(convertersForPair.toString());
			}
			Collections.sort(converterStrings);
			return converterStrings;
		}
	}


	/**
	 * 保存"相同键值对类型"的转换器，也就是存放相同原始类型和目标类型的转换器（管理通过ConvertiblePair注册的转换器）
	 *
	 * Manages converters registered with a specific {@link ConvertiblePair}. —— 管理向特定{@link ConvertiblePair}注册的转换器。
	 */
	private static class ConvertersForPair/* 对转换器 */ {

		// 转换器集合 —— 存放了相同原始类型和目标类型的转换器集合
		private final LinkedList<GenericConverter> converters = new LinkedList<>();

		/**
		 * 添加转换器
		 */
		public void add(GenericConverter converter) {
			this.converters.addFirst(converter);
		}

		/**
		 * 从"存放相同原始类型和目标类型的转换器集合"当中，获取满足【不是ConditionalGenericConverter条件类型的转换器 || 是ConditionalGenericConverter条件类型的转换器，但是条件匹配】条件的第一个转换器
		 *
		 * @param sourceType
		 * @param targetType
		 * @return
		 */
		@Nullable
		public GenericConverter getConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 遍历转换器
			for (GenericConverter converter : this.converters) {
				// 【不是ConditionalGenericConverter类型的转换器 || 是ConditionalGenericConverter类型的转换器，但是条件匹配】，就返回这个转换器
				if (!(converter instanceof ConditionalGenericConverter) ||
						((ConditionalGenericConverter) converter).matches(sourceType, targetType)) {
					return converter;
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return StringUtils.collectionToCommaDelimitedString/* 集合到逗号分隔的字符串 */(this.converters);
		}

	}


	/**
	 * Internal converter that performs no operation.
	 */
	private static class NoOpConverter implements GenericConverter {

		private final String name;

		public NoOpConverter(String name) {
			this.name = name;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return null;
		}

		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			return source;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

}
