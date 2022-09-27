package com.springstudy;

import com.springstudy.config.AppConfig;
import com.springstudy.config.NotConfiguration;
import com.springstudy.dao.MerchantDao;
import com.springstudy.dao.impl.RealNameDaoImpl;
import com.springstudy.service.BaSaiNuoNa;
import com.springstudy.service.BaSaiNuoNaRegister;
import com.springstudy.service.BaSaiNuoNaRegisterConfigrution;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 编译参考：https://www.cnblogs.com/huangxiufen/p/15003428.html
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/22 2:15 上午
 */

public class SpringStudyBoot {

	public static void main(String[] args) {
		test6();
		//test3();
		//test5();
	}

	public static void test6() {
		ApplicationContext context = new ClassPathXmlApplicationContext("spring-config.xml");
		Object a = context.getBean("a");
	}

	public static void test5() {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(BaSaiNuoNaRegisterConfigrution.class);
		//annotationConfigApplicationContext.refresh();
		BaSaiNuoNa bean = annotationConfigApplicationContext.getBean("baSaiNuoNa", BaSaiNuoNa.class);
		System.out.println(bean.getPuJinDaDi());
	}


	public static void test4() {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(NotConfiguration.class);
		//annotationConfigApplicationContext.addBeanFactoryPostProcessor();
		RealNameDaoImpl bean = annotationConfigApplicationContext.getBean(RealNameDaoImpl.class);
		System.out.println(bean);
	}


	/**
	 * 测试：配置类没有@Configuration，但是@Bean一个对象，看是否能获取到这个@Bean
	 * 答案：能获取得到
	 */
	public static void test3() {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(NotConfiguration.class);
		RealNameDaoImpl bean = annotationConfigApplicationContext.getBean(RealNameDaoImpl.class);
		System.out.println(bean);
	}

	public static void test2() {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
		annotationConfigApplicationContext.register(AppConfig.class);
		annotationConfigApplicationContext.refresh();
		MerchantDao bean = annotationConfigApplicationContext.getBean(MerchantDao.class);
		System.out.println("我执行完毕了，bean信息为：" + bean);
	}

	public static void test1() {
		//AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext("com.springstudy");
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
		System.out.println("=============");
		annotationConfigApplicationContext.register(User.class);

		/**
		 * 在invokeBeanFactoryPostProcessors()的getBeanFactoryPostProcessors()中才有了一个值，也就是AddBeanFactoryPostProcessorTest对象
		 */
		annotationConfigApplicationContext.addBeanFactoryPostProcessor(new AddBeanFactoryPostProcessorTest());
		// new AnnotationConfigApplicationContext("com.springstudy");已经被刷新过了，所以不需要再刷新，要不然会报错
		annotationConfigApplicationContext.refresh();
		User bean = annotationConfigApplicationContext.getBean(User.class);
		System.out.println(bean);

		annotationConfigApplicationContext.scan("com.springstudy");
		Member bean1 = annotationConfigApplicationContext.getBean(Member.class);
		System.out.println(bean1);
	}

}