package com.springstudy.importspring.ImportSelector.UserImportSelectThree;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Proxy;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 8:14 下午
 */
public class ImportThreeBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

		System.out.println(bean.getClass());
		if (bean.getClass().equals(UserThreeDaoImpl.class)) {

			Object finalBean = bean;
			bean = Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class<?>[]{UserThreeDao.class}, (p, m, s) -> {
				System.out.println("代理方法执行了");

				return m.invoke(finalBean, s);
			});
		}
		return bean;
	}

}