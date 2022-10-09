package com.springstudy.msb.other.converter;

import com.springstudy.msb.other.converter.GenericConverter.User;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.*;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 转换器
 *
 * 1、涉及的组件：
 * （1）类型转换服务{@link ConversionService}；
 *
 * （2）{@link Converter}；
 * （3）{@link ConverterFactory}；
 * （4）{@link GenericConverter}；
 *
 * （5）{@link ConditionalConverter}；
 * （6）{@link ConditionalGenericConverter} extends GenericConverter, ConditionalConverter
 *
 * 2、原理：直接研究{@link GenericConversionService}源码，就知道{@link ConversionService}怎么回事了。
 *
 * 不过就是提供了一些注册转换器的接口。后续在使用的时候，就是通过目标类型和原始类型获取转换器，然后调用转换器，转换数据。
 *
 * 需要注意的就是：
 * >>> （1）允许存在相同的原始类型和目标类型的转换器，会以此分组，专门存放一堆"相同的原始类型和目标类型的转换器"，但是最终只会获取第一个匹配到的转换器；
 * >>> （2）在注册转换器的时候，Converter会转换为ConverterAdapter，ConverterFactory会转换为ConverterFactoryAdapter。
 * >>> >>> ConverterAdapter和ConverterFactoryAdapter都是GenericConverter的实例，所以，也就是说，Converter和ConverterFactory，所有的转换器，最终都是以GenericConverter的形式存储的
 * >>> >>> 后续在使用的时候，如果原本就是GenericConverter实例，那么是直接调用；
 * >>> >>> 如果是ConverterAdapter那么就是调用ConverterAdapter，通过ConverterAdapter调用实际的Converter；
 * >>> >>> 如果是ConverterAdapter那么就是调用ConverterAdapter，通过ConverterAdapter调用实际的Converter；
 * >>> >>> 同理，如果是ConverterFactoryAdapter那么就是调用ConverterFactoryAdapter，通过ConverterFactoryAdapter获取实际的ConverterFactory，然后通过ConverterFactory获取Converter，然后调用Converter
 *
 * 3、题外：内置的Converter全都在：{@link org.springframework.core.convert.support}包下
 *
 * @date 2022/10/7 10:25
 */
@Configuration
public class ConversionServiceMain {

	public static void main(String[] args) {
		xmlConfiguration();
	}

	/**
	 * 一、populateBean()填充属性时，ConversionService的使用流程。
	 *
	 * 1、在AbstractApplicationContext#refresh() ——> AbstractApplicationContext#finishBeanFactoryInitialization()中
	 *
	 * 会判断beanFactory中有没有ConversionService，有的话就设置ConversionService(类型转换服务)到AbstractBeanFactory.conversionService变量上
	 *
	 * 2、在创建每个bean实例的时候，会创建一个BeanWrapperImpl，包装beanInstance，然后会初始化BeanWrapper。在初始化BeanWrapper的时候，会获取AbstractBeanFactory.conversionService变量上的ConversionService设置到BeanWrapperImpl中！
	 *
	 * AbstractAutowireCapableBeanFactory#doCreateBean() ——> AbstractAutowireCapableBeanFactory#createBeanInstance() ——> AbstractAutowireCapableBeanFactory#instantiateBean() ——> AbstractBeanFactory#initBeanWrapper()
	 *
	 * 3、在populateBean() ——> applyPropertyValues()️应用属性值到bean中，会调用BeanWrapperImpl#convertForProperty()转换属性，里面，当"不存在自定义的属性编辑器 && 存在类型转换服务 && 存在参数值 && 存在类型描述符"，并且ConversionService可以转换时，会调用ConversionService#converter()转换属性值类型！
	 *
	 * AbstractAutowireCapableBeanFactory#doCreateBean() ——> AbstractAutowireCapableBeanFactory#populateBean() ——> AbstractAutowireCapableBeanFactory#applyPropertyValues()️ ——> BeanWrapperImpl#convertForProperty() ——> AbstractNestablePropertyAccessor#convertIfNecessary() ——> this.typeConverterDelegate.convertIfNecessary = TypeConverterDelegate#convertIfNecessary()
	 */
	public static void xmlConfiguration() {
		ApplicationContext ac = new ClassPathXmlApplicationContext("msb/converter/other-converter.xml");
		User user = ac.getBean(User.class);
		System.out.println(user);
	}

	/**
	 * 试用ConversionService，转换一下，看看效果
	 */
	public static void demo() {
		// 注意：⚠️DefaultConversionService()构造器里面添加了一堆spring的默认转换器
		ConversionService conversionService = new DefaultConversionService();

		// String ==> Integer
		Integer num = conversionService.convert("12345", int.class);
		System.out.println(num);

		// boolean ==> String
		String s = conversionService.convert(false, String.class);
		System.out.println(s);

		//String ==>List
		//List<String> arrays = conversionService.convert("1,2,3", List.class);
		//System.out.println(arrays);
	}

}