package com.springstudy.msb.s_13.factoryBean;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/5/1 11:43 上午
 */
public class Main {

	/**
	 * 首先我们有一个MyFactoryBean。
	 *
	 * 1、MyFactoryBean在配置文件中配置了该bean对象，所以在new ClassPathXmlApplicationContext() ——> refresh()中就会创建好对应的MyFactoryBean对象，放入到一级缓存中。
	 *
	 * 在refresh()内部遍历bd，创建MyFactoryBean对象时，会在beanName的前面拼接&，然后以getBean(&+beanName)的形式创建的。
	 * 也只有加了&后，创建完MyFactoryBean对象就会直接返回MyFactoryBean对象，而不是调用和返回MyFactoryBean#getObject()！
	 *
	 * 在getBean(&+beanName)方法内部，会去掉&，得到真实的bd对应的beanName = beanName，然后用真实的bd对应的beanName去获取bd和创建MyFactoryBean对象，
	 *
	 * 创建完MyFactoryBean对象后，放入一级缓存，放入一级缓存中的MyFactoryBean对象，对应的beanName是"myFactoryBean"，并不是"&myFactoryBean"
	 *
	 * 在创建完毕MyFactoryBean对象和放入一级缓存之后的判断里面，会判断getBean()方法入参的beanName是否携带了&，
	 * 如果携带了，就代表获取的只是MyFactoryBean对象，所以不会去触发和调用MyFactoryBean#getObject()，而是直接返回MyFactoryBean对象
	 * 如果没有携带，那么在创建完毕MyFactoryBean对象之后，就会去调用MyFactoryBean#getObject()，并且返回MyFactoryBean#getObject()的对象！
	 *
	 * ⚠️题外：从这里我们可以看出，&这个标识符的作用，就是在得到FactoryBean实例对象之后(1、创建完成；2、或者之前创建过，然后直接从一级缓存中获取到)，
	 * 判断一下，是不是直接返回FactoryBean实例对象；
	 * 还是说获取对应的FactoryBean#getObject()进行返回（1、有可能是调用FactoryBean#getObject()返回的；2、也有可能直接调用过，然后从factoryBeanObjectCache map缓存里面返回的）；
	 * 加了&就获取FactoryBean#getObject()返回，没加就返回FactoryBean实例对象。
	 *
	 * 题外：如果FactoryBean#getObject()是多例的话，就不会放入factoryBeanObjectCache map缓存，而是每次都调用FactoryBean#getObject()进行创建
	 *
	 * 2、然后我们在进行显示调用context.getBean("&myFactoryBean")时，先去掉&，得到beanName = myFactoryBean，
	 * 由于之前已经初始化完毕过MyFactoryBean对象，并放入了一级缓存中，
	 * 所以通过myFactoryBean去一级缓存中获取对应的MyFactoryBean对象，由于context.getBean("&myFactoryBean")入参携带了&，所以直接返回MyFactoryBean对象
	 *
	 * 3、然后我们在进行显示调用context.getBean("myFactoryBean")，
	 * 由于之前已经初始化完毕过MyFactoryBean对象，并放入了一级缓存中，
	 * 所以通过myFactoryBean去一级缓存中获取对应的MyFactoryBean对象，MyFactoryBean对象是FactoryBean的接口，并且getBean(beanName)时未携带&，
	 * 那么就调用MyFactoryBean#getObject()进行返回
	 *
	 * ⚠️题外：如果是第一次调用MyFactoryBean#getObject()，并且是单例的，那么就会放入factoryBeanObjectCache map缓存中，后续直接从缓存中获取；
	 * 如果是多例，那么就不会放入缓存，每次都得调用MyFactoryBean#getObject()
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("msb/spring-13-factoryBean-config.xml");

		MyFactoryBean myFactoryBean = (MyFactoryBean) context.getBean("&myFactoryBean");
		System.out.println(myFactoryBean);
		Hello hello = (Hello) context.getBean("myFactoryBean");
		System.out.println(hello);
		Hello hello2 = (Hello) context.getBean("myFactoryBean");
		System.out.println(hello2);

		MyFactoryBean myFactoryBean2 = (MyFactoryBean) context.getBean("&myFactoryBean");
		System.out.println(myFactoryBean2);
	}

}