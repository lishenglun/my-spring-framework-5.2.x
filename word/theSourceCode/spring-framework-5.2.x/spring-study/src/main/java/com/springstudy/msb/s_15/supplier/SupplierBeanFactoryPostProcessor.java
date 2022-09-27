package com.springstudy.msb.s_15.supplier;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/5/7 9:28 上午
 */
public class SupplierBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

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
		BeanDefinition user = beanFactory.getBeanDefinition("user");
		// 因为从配置文件读取的bd是GenericBeanDefinition类型，并且BeanDefinition不具备setInstanceSupplier()方法，但是GenericBeanDefinition具有，
		// 所以转换为GenericBeanDefinition
		GenericBeanDefinition user2 = (GenericBeanDefinition) user;
		user2.setInstanceSupplier(CreateSupplier::createUser);
		user2.setBeanClass(User.class);
	}

}