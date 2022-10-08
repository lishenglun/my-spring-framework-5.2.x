package com.springstudy.msb.other.converter;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 1、内置转换器全都在：{@link org.springframework.core.convert.support}包下
 * @date 2022/10/7 10:25
 */
@Configuration
public class ConverMain {

	public static void main(String[] args) {
		xmlConfiguration();
	}

	public static void annotationConfiguration() {
		ApplicationContext ac = new AnnotationConfigApplicationContext(ConverMain.class);
		System.out.println(ac);
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

	public static void demo() {
		// 里面添加了一堆spring的默认转换器
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