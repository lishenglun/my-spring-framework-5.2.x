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

package org.springframework.core.convert.converter;

/**
 * 一、ConverterFactory：用于集中生产-"一个类型"转换为"某个类型的整个层次结构中的所有类型"的-Converter。属于一对多。
 * 也就是说，为了集中"某个类型的整个层次结构"的转换逻辑，也就是说：一个原始类型转换为"某个类型的整个层次结构中的所有类型"用的是同一套Converter的逻辑。
 *
 * 1、⚠️为了集中"某个类型的整个层次结构"的转换逻辑（集中了一个原始类型转换到"某个类型的整个层次结构"的转换逻辑），
 * 也就是说：一个原始类型转换为"某个类型的整个层次结构中的所有类型"用的是同一套Converter的逻辑；
 *
 * 题外：⚠️当需要集中整个类层次结构的转换逻辑时（例如，从转换String为Enum对象时），可以实现ConverterFactory。
 *
 * 例如：Integer extends Number、Long extends Number
 * >>> 如果是ConverterFactory<String,Number>；
 * >>> 那么String转换为Integer，String转换为Long，都由这个ConverterFactory<String,Number>生成的Converter来转换！
 *
 * 2、如果一对"原始类型和目标类型"匹配到ConverterFactory，就会通过ConverterFactory，️每次️动态生成一个转换指定"原始类型和目标类型"的Converter
 *
 * 题外：⚠️每次匹配到ConverterFactory，然后，每次调用ConverterFactory都会通过ConverterFactory动态生成一个Converter，然后再调用具体的Converter进行转换！
 *
 * 例如：Integer extends Number、Long extends Number
 * >>> 如果是ConverterFactory<String,Number>；
 * >>> 如果是String转换为Integer，那么这个ConverterFactory<String,Number>会动态生成一个String转Number的转换器：Converter<String，Number>
 * >>> 如果是String转换为Long，那么这个ConverterFactory<String,Number>会动态生成一个String转Long的转换器：Converter<String，Long>
 * >>> 这2个Converter的转换逻辑是一样的！
 *
 * 3、属于一对多。一个原始类型，可以转换为某个类型(R)的整个层级结构中的所有类型，也就是说：一个原始类型，可以转换为R类型，或者R类型下所有子孙中的任一员
 *
 * 题外：顾名思义，ConverterFactory就是产生Converter的一个工厂，用来获取对应的转换器。
 * 题外：ConverterFactory接口只支持从一个原类型转换为一个目标类型对应的子类型。
 *
 * 题外：⚠️但是通过ConverterFactory获取的Converter和我们直接定义的Converter不同：ConverterFactory获取的Converter，它的目标类型必须是ConverterFactory R类型的子类或者相同
 *
 * A factory for "ranged" converters that can convert objects from S to subtypes of R.
 * <p>Implementations may additionally implement {@link ConditionalConverter}.
 *
 * “范围”转换器的工厂，可以将对象从 S 转换为 R 的子类型。
 * <p>实现可以另外实现 {@link ConditionalConverter}。
 *
 * @author Keith Donald
 * @since 3.0
 * @param <S> the source type converters created by this factory can convert from
 * @param <R> the target range (or base) type converters created by this factory can convert to;
 * for example {@link Number} for a set of number subtypes.
 * @see ConditionalConverter
 */
// 参数化S为要转换的类型，R为定义可转换为的类范围的基本类型
public interface ConverterFactory<S, R> {

	/**
	 * 生产Converter（获取转换器）
	 *
	 * 题外：S是源类型，T是目标类型，T是R的一个实例(T要么是R的子类，或者T就是R)
	 *
	 * Get the converter to convert from S to target type T, where T is also an instance of R.
	 * 让转换器从 S 转换为目标类型 T，其中 T 也是 R 的一个实例。
	 *
	 * @param <T> the target type
	 *
	 * @param targetType the target type to convert to
	 *                   目标类型
	 *
	 * @return a converter from S to T —— 从S到T的转换器
	 */
	// <T extends R>,其中的【extends R】是范型的上限，包含了具体的一些子类实现，就是说有N对N的转换关系
	<T extends R> Converter<S, T> getConverter(Class<T> targetType);

}
