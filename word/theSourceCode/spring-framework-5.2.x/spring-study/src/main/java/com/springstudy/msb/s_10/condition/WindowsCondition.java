package com.springstudy.msb.s_10.condition;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/26 9:28 上午
 */
public class WindowsCondition implements Condition {

	/**
	 * @param context  判断条件能使用的上下文环境
	 * @param metadata 注解所在位置的注解元数据信息
	 */
	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// 获取ioc使用的beanFactory
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		// 获取类加载器
		ClassLoader classLoader = context.getClassLoader();
		// 获取当前环境信息
		Environment environment = context.getEnvironment();
		// 获取bean定义的注册类
		BeanDefinitionRegistry registry = context.getRegistry();

		// 获取当前系统名称
		String property = environment.getProperty("os.name");
		// 包含Windows则说明是windows系统，返回true
		/*if (property.contains("Windows")) {
			return true;
		}

		return false;*/

		return true;
	}

}