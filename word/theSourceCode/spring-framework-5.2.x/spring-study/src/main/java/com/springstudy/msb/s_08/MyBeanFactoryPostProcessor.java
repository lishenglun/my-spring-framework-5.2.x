package com.springstudy.msb.s_08;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/20 11:01 上午
 */
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for overriding or adding
	 * properties even to eager-initializing beans.
	 * <p>
	 * 在标准初始化之后，修改应用程序上下文的内部bean工厂。所有bean定义都将被加载，但尚未实例化任何bean。这甚至可以覆盖或添加属性，甚至可以用于初始化bean。
	 *
	 * @param beanFactory the bean factory used by the application context
	 * @throws BeansException in case of errors
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("---------");
	}

}