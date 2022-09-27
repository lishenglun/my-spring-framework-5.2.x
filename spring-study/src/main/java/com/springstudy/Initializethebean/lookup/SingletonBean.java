package com.springstudy.Initializethebean.lookup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 5:17 下午
 */
@Component
public class SingletonBean {

	@Autowired
	private PrototypeBean bean;

	@Autowired
	private ApplicationContext applicationContext;

	@Lookup
	public PrototypeBean getPrototypeBean(){return null;}

	public void print() {
		/* 无效测试 */
		System.out.println("Bean SingletonBean's HashCode : {}" + bean.hashCode());

		/* 下面是解决方法 */

		/* 方式一：ApplicationContext */
		//PrototypeBean prototypeBean = applicationContext.getBean("prototypeBean",PrototypeBean.class);
		//System.out.println("Bean SingletonBean's HashCode : {}" + prototypeBean.hashCode());

		/* 方式二：@Lookup */
		//System.out.println("Bean SingletonBean's HashCode : {}" + getPrototypeBean().hashCode());
		bean.say();
	}

}