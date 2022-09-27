package com.springstudy.msb.s_07.selfAware;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.*;
//import org.springframework.context.support.ApplicationContextAwareProcessor;

import java.security.AccessControlContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 参考：{@link ApplicationContextAwareProcessor}
 * @date 2022/4/19 9:08 下午
 */
public class MyAwareProcessor implements BeanPostProcessor {

	private final ConfigurableApplicationContext applicationContext;

	public MyAwareProcessor(ConfigurableApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (!(bean instanceof EnvironmentAware || bean instanceof EmbeddedValueResolverAware ||
				bean instanceof ResourceLoaderAware || bean instanceof ApplicationEventPublisherAware ||
				bean instanceof MessageSourceAware ||
				bean instanceof ApplicationContextAware/* ApplicationContextAware */)){
			return bean;
		}

		AccessControlContext acc = null;

		if (System.getSecurityManager() != null) {
			acc = this.applicationContext.getBeanFactory().getAccessControlContext();
		}

		((ApplicationContextAware)bean).setApplicationContext(applicationContext);

		return bean;
	}

}